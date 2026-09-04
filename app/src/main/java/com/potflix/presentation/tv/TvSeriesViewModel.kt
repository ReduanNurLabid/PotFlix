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

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _isLoading.value = true
            
            launch {
                repository.getTrendingSuggestionsFlow("tv").collect { tvShows ->
                    val top10 = tvShows.take(10)
                    _trendingTv.value = top10

                    val hero = top10.firstOrNull()
                    if (hero != null) {
                        launch {
                            watchlistRepository.isInWatchlist(hero.url).collectLatest { inWatchlist ->
                                _isHeroInWatchlist.value = inWatchlist
                            }
                        }
                    }
                    
                    top10.forEach { movie ->
                        launch {
                            repository.getMovieDetails(movie).onSuccess { detailedMovie ->
                                if (detailedMovie.poster != null) {
                                    val currentList = _trendingTv.value.toMutableList()
                                    val index = currentList.indexOfFirst { it.url == movie.url }
                                    if (index != -1) {
                                        currentList[index] = detailedMovie
                                        _trendingTv.value = currentList
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            repository.getCategories().onSuccess { dbCategories ->
                val dynamicCategories = mutableListOf<Category>()
                val dynamicCategoryMovies = mutableMapOf<String, List<Movie>>()
                
                // Filter FOR TV show categories
                val tvCategories = dbCategories.filter { 
                    it.id.contains("tvshows") || it.id.contains("Series", ignoreCase = true) 
                }
                val selectedCategories = tvCategories.shuffled().take(7)
                
                selectedCategories.forEach { category ->
                    repository.getLatestMovies(category.id).onSuccess { moviesInCat ->
                        if (moviesInCat.isNotEmpty()) {
                            val items = moviesInCat.take(15)
                            dynamicCategories.add(category)
                            dynamicCategoryMovies[category.id] = items
                            
                            // Lazy fetch TMDB details for the row items
                            items.forEach { movie ->
                                launch {
                                    repository.getMovieDetails(movie).onSuccess { detailedMovie ->
                                        if (detailedMovie.poster != null) {
                                            val currentMap = _categoryTv.value.toMutableMap()
                                            val currentList = currentMap[category.id]?.toMutableList() ?: return@launch
                                            val index = currentList.indexOfFirst { it.url == movie.url }
                                            if (index != -1) {
                                                currentList[index] = detailedMovie
                                                currentMap[category.id] = currentList
                                                _categoryTv.value = currentMap
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                _categories.value = dynamicCategories
                _categoryTv.value = dynamicCategoryMovies
            }
            
            _isLoading.value = false
        }
    }
}
