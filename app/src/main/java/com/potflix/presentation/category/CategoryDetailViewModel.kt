package com.potflix.presentation.category

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.potflix.domain.model.Movie
import com.potflix.domain.repository.MovieRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CategoryDetailViewModel @Inject constructor(
    private val repository: MovieRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val categoryId: String = checkNotNull(savedStateHandle["categoryId"])
    val categoryName: String = checkNotNull(savedStateHandle["categoryName"])
    private val type: String = savedStateHandle["type"] ?: "category"

    private val _movies = MutableStateFlow<List<Movie>>(emptyList())
    val movies = _movies.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    init {
        loadMovies()
    }

    private fun loadMovies() {
        viewModelScope.launch {
            _isLoading.value = true
            // Load a much larger limit for "See All", like 100 movies
            val result = if (type == "genre_movie") {
                repository.getMoviesByGenre(categoryId.toLong(), "movie", 100)
            } else if (type == "genre_tv") {
                repository.getMoviesByGenre(categoryId.toLong(), "tv", 100)
            } else {
                repository.getLatestMovies(categoryId, 100)
            }
            
            result.onSuccess { loadedMovies ->
                _movies.value = loadedMovies
                
                // Fetch details for loaded movies lazily
                loadedMovies.forEach { movie ->
                    launch {
                        repository.getMovieDetails(movie).onSuccess { detailedMovie ->
                            if (detailedMovie.poster != null) {
                                val currentList = _movies.value.toMutableList()
                                val index = currentList.indexOfFirst { it.url == movie.url }
                                if (index != -1) {
                                    currentList[index] = detailedMovie
                                    _movies.value = currentList
                                }
                            }
                        }
                    }
                }
            }
            _isLoading.value = false
        }
    }
}
