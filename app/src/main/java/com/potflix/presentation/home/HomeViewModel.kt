package com.potflix.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.potflix.domain.model.Category
import com.potflix.domain.model.Movie
import com.potflix.domain.repository.MovieRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.potflix.data.repository.WatchlistRepository
import com.potflix.data.local.entity.LocalMovieEntity
import com.potflix.data.local.entity.toLocalMovieEntity

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: MovieRepository,
    private val watchlistRepository: WatchlistRepository,
    private val application: android.app.Application
) : ViewModel() {

    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories = _categories.asStateFlow()

    private val _trendingMovies = MutableStateFlow<List<Movie>>(emptyList())
    val trendingMovies = _trendingMovies.asStateFlow()

    private val _categoryMovies = MutableStateFlow<Map<String, List<Movie>>>(emptyMap())
    val categoryMovies = _categoryMovies.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    
    private val _isHeroInWatchlist = MutableStateFlow(false)
    val isHeroInWatchlist = _isHeroInWatchlist.asStateFlow()
    
    val isSyncing = MutableStateFlow(false)
    val syncProgress = MutableStateFlow("Syncing Database...")

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage = _toastMessage.asStateFlow()

    val watchHistory = repository.getWatchHistoryFlow()

    fun clearToastMessage() {
        _toastMessage.value = null
    }

    fun toggleHeroWatchlist() {
        val heroMovie = _trendingMovies.value.firstOrNull() ?: return
        viewModelScope.launch {
            val entity = heroMovie.toLocalMovieEntity()
            if (_isHeroInWatchlist.value) {
                watchlistRepository.removeFromWatchlist(entity)
            } else {
                watchlistRepository.addToWatchlist(entity)
            }
        }
    }

        init {
        val prefs = application.getSharedPreferences("potflix_prefs", android.content.Context.MODE_PRIVATE)
        val lastSync = prefs.getLong("last_sync_time", 0L)
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastSync > 24 * 60 * 60 * 1000L && lastSync != 0L) {
            _toastMessage.value = "It's been 24 hours. Consider manually syncing the database in Settings!"
        }
        
        loadHomeData()
        
        androidx.work.WorkManager.getInstance(application)
            .getWorkInfosForUniqueWorkLiveData("ManualSync")
            .observeForever { workInfos ->
                if (workInfos.isNullOrEmpty()) return@observeForever
                val workInfo = workInfos[0]
                
                val currentlySyncing = workInfo.state == androidx.work.WorkInfo.State.RUNNING || 
                                       workInfo.state == androidx.work.WorkInfo.State.ENQUEUED
                                       
                val wasSyncing = isSyncing.value
                isSyncing.value = currentlySyncing
                
                if (wasSyncing && !currentlySyncing) {
                    // Sync just finished, reload data
                    loadHomeData()
                }
            }
    }

    private var loadDataJob: kotlinx.coroutines.Job? = null

    fun refresh() {
        loadHomeData()
    }

    fun removeFromWatchHistory(movie: Movie) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            repository.removeFromWatchHistory(movie.url)
        }
    }

    private fun loadHomeData() {
        loadDataJob?.cancel()
        loadDataJob = viewModelScope.launch {
            _isLoading.value = true
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    // 1. Get Trending Banner (Top 10 mixed movies/series)
                    val trendingRes = repository.getTrendingSuggestions("all")
                    val top10 = trendingRes.getOrDefault(emptyList()).take(10)
                    _trendingMovies.value = top10
                    
                    val hero = top10.firstOrNull()
                    if (hero != null) {
                        launch(kotlinx.coroutines.Dispatchers.Main) {
                            watchlistRepository.isInWatchlist(hero.url).collectLatest { inWatchlist ->
                                _isHeroInWatchlist.value = inWatchlist
                            }
                        }
                        // Enrich hero poster/backdrop if missing
                        if (hero.backdrop == null || hero.poster == null) {
                            repository.getMovieDetails(hero).onSuccess { detailedHero ->
                                val currentList = _trendingMovies.value.toMutableList()
                                if (currentList.isNotEmpty()) {
                                    currentList[0] = detailedHero
                                    _trendingMovies.value = currentList
                                }
                            }
                        }
                    }

                    // 2. Load all actual categories from the database off the main thread
                    repository.getCategories().onSuccess { dbCategories ->
                        val dynamicCategories = mutableListOf<Category>()
                        val dynamicCategoryMovies = mutableMapOf<String, List<Movie>>()
                        
                        dbCategories.forEach { category ->
                            repository.getLatestMovies(category.id).onSuccess { moviesInCat ->
                                if (moviesInCat.isNotEmpty()) {
                                    dynamicCategories.add(category)
                                    dynamicCategoryMovies[category.id] = moviesInCat.take(15)
                                }
                            }
                        }
                        
                        _categories.value = dynamicCategories
                        _categoryMovies.value = dynamicCategoryMovies
                    }
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    android.util.Log.e("HomeViewModel", "Error loading home data", e)
                }
            } finally {
                _isLoading.value = false
            }
        }
    }
}
