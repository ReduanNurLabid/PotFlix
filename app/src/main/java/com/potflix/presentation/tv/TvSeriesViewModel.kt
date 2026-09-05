package com.potflix.presentation.tv

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
import com.potflix.data.local.entity.toLocalMovieEntity

@HiltViewModel
class TvSeriesViewModel @Inject constructor(
    private val repository: MovieRepository,
    private val watchlistRepository: WatchlistRepository
) : ViewModel() {

    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories = _categories.asStateFlow()

    private val _trendingTv = MutableStateFlow<List<Movie>>(emptyList())
    val trendingTv = _trendingTv.asStateFlow()

    private val _categoryTv = MutableStateFlow<Map<String, List<Movie>>>(emptyMap())
    val categoryTv = _categoryTv.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _isHeroInWatchlist = MutableStateFlow(false)
    val isHeroInWatchlist = _isHeroInWatchlist.asStateFlow()

    fun toggleHeroWatchlist() {
        val heroMovie = _trendingTv.value.firstOrNull() ?: return
        viewModelScope.launch {
            val entity = heroMovie.toLocalMovieEntity()
            if (_isHeroInWatchlist.value) {
                watchlistRepository.removeFromWatchlist(entity)
            } else {
                watchlistRepository.addToWatchlist(entity)
            }
        }
    }

    private var loadDataJob: kotlinx.coroutines.Job? = null
    private var heroWatchlistJob: kotlinx.coroutines.Job? = null

    init {
        loadData()
    }

    fun refresh() {
        loadData()
    }

    private fun loadData() {
        loadDataJob?.cancel()
        loadDataJob = viewModelScope.launch {
            _isLoading.value = true
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val trendingRes = repository.getTrendingSuggestions("tv")
                    val top10 = trendingRes.getOrDefault(emptyList()).take(10)
                    _trendingTv.value = top10

                    val hero = top10.firstOrNull()
                    if (hero != null) {
                        heroWatchlistJob?.cancel()
                        heroWatchlistJob = viewModelScope.launch {
                            watchlistRepository.isInWatchlist(hero.url).collectLatest { inWatchlist ->
                                _isHeroInWatchlist.value = inWatchlist
                            }
                        }
                        if (hero.backdrop == null || hero.poster == null) {
                            repository.getMovieDetails(hero).onSuccess { detailedHero ->
                                val currentList = _trendingTv.value.toMutableList()
                                if (currentList.isNotEmpty()) {
                                    currentList[0] = detailedHero
                                    _trendingTv.value = currentList
                                }
                            }
                        }
                    }

                    repository.getCategories().onSuccess { dbCategories ->
                        val dynamicCategories = mutableListOf<Category>()
                        val dynamicCategoryMovies = mutableMapOf<String, List<Movie>>()
                        
                        val tvCategories = dbCategories.filter { 
                            it.id.contains("tvshows") || it.id.contains("Series", ignoreCase = true) 
                        }
                        val selectedCategories = tvCategories.sortedBy { it.name }
                        
                        selectedCategories.forEach { category ->
                            repository.getLatestMovies(category.id).onSuccess { moviesInCat ->
                                if (moviesInCat.isNotEmpty()) {
                                    dynamicCategories.add(category)
                                    dynamicCategoryMovies[category.id] = moviesInCat.take(15)
                                }
                            }
                        }
                        _categories.value = dynamicCategories
                        _categoryTv.value = dynamicCategoryMovies
                    }
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    android.util.Log.e("TvSeriesViewModel", "Error loading tv series data", e)
                }
            } finally {
                kotlinx.coroutines.delay(500)
                _isLoading.value = false
            }
        }
    }
}
