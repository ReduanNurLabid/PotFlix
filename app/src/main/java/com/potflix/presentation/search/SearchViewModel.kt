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

    private val _rawSearchResults = MutableStateFlow<List<Movie>>(emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _selectedTypeFilter = MutableStateFlow("All")
    val selectedTypeFilter = _selectedTypeFilter.asStateFlow()

    private val _selectedSortFilter = MutableStateFlow("Relevance")
    val selectedSortFilter = _selectedSortFilter.asStateFlow()

    private val _selectedCategoryFilter = MutableStateFlow("All")
    val selectedCategoryFilter = _selectedCategoryFilter.asStateFlow()

    private val _availableCategories = MutableStateFlow<List<String>>(emptyList())
    val availableCategories = _availableCategories.asStateFlow()

    private val _trendingSearches = MutableStateFlow<List<String>>(
        listOf(
            "Stranger Things", "Breaking Bad", "Game of Thrones", "Oppenheimer",
            "The Last of Us", "Dune", "Succession", "Avatar", "Interstellar", "Dark"
        )
    )
    val trendingSearches = _trendingSearches.asStateFlow()

    private val _recentSearches = MutableStateFlow<List<String>>(
        prefs.getString("recent", "")?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
    )
    val recentSearches = _recentSearches.asStateFlow()

    private val _suggestedMovies = MutableStateFlow<List<Movie>>(emptyList())
    val suggestedMovies = _suggestedMovies.asStateFlow()

    val searchResults: StateFlow<List<Movie>> = combine(
        _rawSearchResults,
        _selectedTypeFilter,
        _selectedCategoryFilter,
        _selectedSortFilter
    ) { results, typeFilter, catFilter, sortFilter ->
        var list = results

        // 1. Type filtering
        when (typeFilter) {
            "Movies" -> list = list.filter { it.type != "tv" }
            "TV Shows" -> list = list.filter { it.type == "tv" }
            "Animation" -> list = list.filter {
                it.type.equals("Animation", ignoreCase = true) || 
                it.categoryId?.contains("Animation", ignoreCase = true) == true
            }
        }

        // 2. Category filtering
        if (catFilter != "All") {
            list = list.filter {
                it.categoryId?.contains(catFilter, ignoreCase = true) == true ||
                it.language?.contains(catFilter, ignoreCase = true) == true
            }
        }

        // 3. Sorting
        when (sortFilter) {
            "Latest" -> list.sortedByDescending { it.year ?: 0 }
            "Top Rated" -> list.sortedByDescending { it.rating ?: 0.0 }
            else -> list
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Load initial suggested content for empty discovery state
        viewModelScope.launch {
            repository.getTrendingSuggestions("all")
                .onSuccess { movies ->
                    _suggestedMovies.value = movies
                    val dynamicTrending = movies.map { it.title.trim() }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .take(12)
                    if (dynamicTrending.isNotEmpty()) {
                        _trendingSearches.value = dynamicTrending
                    }
                }

            repository.getCategories()
                .onSuccess { cats ->
                    val names = cats.map { it.name }.filter { it.isNotBlank() }.distinct()
                    _availableCategories.value = names
                }
        }

        _searchQuery
            .debounce(400)
            .filter { it.isNotEmpty() }
            .onEach { query ->
                performSearch(query)
            }
            .launchIn(viewModelScope)
    }

    fun setTypeFilter(filter: String) {
        _selectedTypeFilter.value = filter
    }

    fun setSortFilter(sort: String) {
        _selectedSortFilter.value = sort
    }

    fun setCategoryFilter(category: String) {
        _selectedCategoryFilter.value = category
    }

    fun resetFilters() {
        _selectedTypeFilter.value = "All"
        _selectedSortFilter.value = "Relevance"
        _selectedCategoryFilter.value = "All"
    }

    fun saveRecentSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        val currentList = _recentSearches.value.toMutableList()
        currentList.remove(trimmed)
        currentList.add(0, trimmed)
        if (currentList.size > 8) {
            currentList.removeAt(currentList.lastIndex)
        }
        _recentSearches.value = currentList
        prefs.edit().putString("recent", currentList.joinToString(",")).apply()
    }

    fun removeRecentSearch(query: String) {
        val currentList = _recentSearches.value.toMutableList()
        currentList.remove(query)
        _recentSearches.value = currentList
        prefs.edit().putString("recent", currentList.joinToString(",")).apply()
    }

    fun clearAllRecentSearches() {
        _recentSearches.value = emptyList()
        prefs.edit().remove("recent").apply()
    }

    fun onQueryChange(query: String) {
        _searchQuery.value = query
        if (query.isEmpty()) {
            _rawSearchResults.value = emptyList()
        }
    }

    private var searchJob: kotlinx.coroutines.Job? = null

    private fun performSearch(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _isLoading.value = true
            repository.searchMovies(query).onSuccess { movies ->
                _rawSearchResults.value = movies
            }
            _isLoading.value = false
        }
    }
}
