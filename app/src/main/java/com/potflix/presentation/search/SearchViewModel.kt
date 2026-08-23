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
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: MovieRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val prefs = context.getSharedPreferences("search_prefs", Context.MODE_PRIVATE)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Movie>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _recentSearches = MutableStateFlow<List<String>>(
        prefs.getString("recent", "")?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
    )
    val recentSearches = _recentSearches.asStateFlow()

    private val _suggestedMovies = MutableStateFlow<List<Movie>>(emptyList())
    val suggestedMovies = _suggestedMovies.asStateFlow()

    init {
        // Fetch suggestions for the empty state
        viewModelScope.launch {
            repository.getTrendingSuggestions("all")
                .onSuccess { _suggestedMovies.value = it }
        }

        _searchQuery
            .debounce(500)
            .filter { it.isNotEmpty() }
            .onEach { query ->
                performSearch(query)
            }
            .launchIn(viewModelScope)
    }

    fun saveRecentSearch(query: String) {
        if (query.isBlank()) return
        val currentList = _recentSearches.value.toMutableList()
        if (currentList.contains(query)) {
            currentList.remove(query)
        }
        currentList.add(0, query)
        if (currentList.size > 3) currentList.removeLast()
        _recentSearches.value = currentList
        prefs.edit().putString("recent", currentList.joinToString(",")).apply()
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
