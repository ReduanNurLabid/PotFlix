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
import com.potflix.data.local.dao.LocalMovieDao
import com.potflix.data.local.entity.toLocalMovieEntity

@HiltViewModel
class TvSeriesViewModel @Inject constructor(
    private val repository: MovieRepository,
    private val localMovieDao: LocalMovieDao
) : ViewModel() {

    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories = _categories.asStateFlow()

    private val _trendingSeries = MutableStateFlow<List<Movie>>(emptyList())
    val trendingSeries = _trendingSeries.asStateFlow()

    private val _categorySeries = MutableStateFlow<Map<String, List<Movie>>>(emptyMap())
    val categorySeries = _categorySeries.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _isHeroInWatchlist = MutableStateFlow(false)
    val isHeroInWatchlist = _isHeroInWatchlist.asStateFlow()

    fun toggleHeroWatchlist() {
        val heroMovie = _trendingSeries.value.firstOrNull() ?: return
        viewModelScope.launch {
            val entity = heroMovie.toLocalMovieEntity()
            if (_isHeroInWatchlist.value) {
                localMovieDao.removeFromWatchlist(entity)
            } else {
                localMovieDao.addToWatchlist(entity)
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
                repository.getTrendingSuggestionsFlow("tv").collect { movies ->
                    val top10 = movies.take(10)
                    _trendingSeries.value = top10

                    val hero = top10.firstOrNull()
                    if (hero != null) {
                        launch {
                            localMovieDao.isInWatchlist(hero.url).collectLatest { inWatchlist ->
                                _isHeroInWatchlist.value = inWatchlist
                            }
                        }
                    }
                    
                    top10.forEach { movie ->
                        launch {
                            repository.getMovieDetails(movie).onSuccess { detailedMovie ->
                                if (detailedMovie.poster != null) {
                                    val currentList = _trendingSeries.value.toMutableList()
                                    val index = currentList.indexOfFirst { it.url == movie.url }
                                    if (index != -1) {
                                        currentList[index] = detailedMovie
                                        _trendingSeries.value = currentList
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            repository.getCategories().onSuccess { dbCategories ->
                val dynamicCategories = mutableListOf<Category>()
                val dynamicCategorySeries = mutableMapOf<String, List<Movie>>()
                
                // Only take categories that are probably TV Series
                val seriesCategories = dbCategories.filter { 
                    it.name.contains("series", ignoreCase = true) || 
                    it.name.contains("tv", ignoreCase = true) ||
                    it.name.contains("drama", ignoreCase = true)
                }
                
                seriesCategories.forEach { category ->
                    repository.getLatestMovies(category.id).onSuccess { seriesInCat ->
                        if (seriesInCat.isNotEmpty()) {
                            val items = seriesInCat.take(15)
                            dynamicCategories.add(category)
                            dynamicCategorySeries[category.id] = items
                            
                            items.forEach { movie ->
                                launch {
                                    repository.getMovieDetails(movie).onSuccess { detailedMovie ->
                                        if (detailedMovie.poster != null) {
                                            val currentMap = _categorySeries.value.toMutableMap()
                                            val currentList = currentMap[category.id]?.toMutableList() ?: return@launch
                                            val index = currentList.indexOfFirst { it.url == movie.url }
                                            if (index != -1) {
                                                currentList[index] = detailedMovie
                                                currentMap[category.id] = currentList
                                                _categorySeries.value = currentMap
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                _categories.value = dynamicCategories
                _categorySeries.value = dynamicCategorySeries
            }
            _isLoading.value = false
        }
    }
}
