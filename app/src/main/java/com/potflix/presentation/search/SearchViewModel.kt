package com.potflix.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.potflix.domain.model.Movie
import com.potflix.domain.repository.MovieRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: MovieRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Movie>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _recentSearches = MutableStateFlow<List<String>>(emptyList())
    val recentSearches = _recentSearches.asStateFlow()

    init {
        _searchQuery
            .debounce(500)
            .filter { it.length >= 2 }
            .onEach { query ->
                saveRecentSearch(query)
                performSearch(query)
            }
            .launchIn(viewModelScope)
    }

    private fun saveRecentSearch(query: String) {
        val currentList = _recentSearches.value.toMutableList()
        if (currentList.contains(query)) {
            currentList.remove(query)
        }
        currentList.add(0, query)
        if (currentList.size > 10) currentList.removeLast()
        _recentSearches.value = currentList
    }

    fun onQueryChange(query: String) {
        _searchQuery.value = query
        if (query.isEmpty()) {
            _searchResults.value = emptyList()
        }
    }

    private fun performSearch(query: String) {
        viewModelScope.launch {
            _isLoading.value = true
            repository.searchMovies(query).onSuccess { movies ->
                _searchResults.value = movies
                
                movies.forEach { movie ->
                    launch {
                        repository.getMovieDetails(movie).onSuccess { detailedMovie ->
                            if (detailedMovie.poster != null) {
                                val currentList = _searchResults.value.toMutableList()
                                val index = currentList.indexOfFirst { it.url == movie.url }
                                if (index != -1) {
                                    currentList[index] = detailedMovie
                                    _searchResults.value = currentList
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
