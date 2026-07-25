package com.potflix.presentation.movies

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
class MoviesViewModel @Inject constructor(
    private val repository: MovieRepository,
    private val localMovieDao: LocalMovieDao
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

    fun toggleHeroWatchlist() {
        val heroMovie = _trendingMovies.value.firstOrNull() ?: return
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
                repository.getTrendingSuggestionsFlow("movie").collect { movies ->
                    val top10 = movies.take(10)
                    _trendingMovies.value = top10

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
                                    val currentList = _trendingMovies.value.toMutableList()
                                    val index = currentList.indexOfFirst { it.url == movie.url }
                                    if (index != -1) {
                                        currentList[index] = detailedMovie
                                        _trendingMovies.value = currentList
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
                
                // Only take categories that are probably movies
                val movieCategories = dbCategories.filter { 
                    it.name.contains("movie", ignoreCase = true) || 
                    it.name.contains("top 250", ignoreCase = true)
                }
                
                movieCategories.forEach { category ->
                    repository.getLatestMovies(category.id).onSuccess { moviesInCat ->
                        if (moviesInCat.isNotEmpty()) {
                            val items = moviesInCat.take(15)
                            dynamicCategories.add(category)
                            dynamicCategoryMovies[category.id] = items
                            
                            items.forEach { movie ->
                                launch {
                                    repository.getMovieDetails(movie).onSuccess { detailedMovie ->
                                        if (detailedMovie.poster != null) {
                                            val currentMap = _categoryMovies.value.toMutableMap()
                                            val currentList = currentMap[category.id]?.toMutableList() ?: return@launch
                                            val index = currentList.indexOfFirst { it.url == movie.url }
                                            if (index != -1) {
                                                currentList[index] = detailedMovie
                                                currentMap[category.id] = currentList
                                                _categoryMovies.value = currentMap
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                _categories.value = dynamicCategories
                _categoryMovies.value = dynamicCategoryMovies
            }
            _isLoading.value = false
        }
    }
}
