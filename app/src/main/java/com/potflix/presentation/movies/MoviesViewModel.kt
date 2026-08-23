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

    private val _genres = MutableStateFlow<List<com.potflix.domain.model.Genre>>(emptyList())
    val genres = _genres.asStateFlow()

    private val _trendingMovies = MutableStateFlow<List<Movie>>(emptyList())
    val trendingMovies = _trendingMovies.asStateFlow()

    private val _genreMovies = MutableStateFlow<Map<String, List<Movie>>>(emptyMap())
    val genreMovies = _genreMovies.asStateFlow()

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
            repository.getGenres().onSuccess { dbGenres ->
                val dynamicGenres = mutableListOf<com.potflix.domain.model.Genre>()
                val dynamicGenreMovies = mutableMapOf<String, List<Movie>>()
                
                val selectedGenres = dbGenres.shuffled().take(7)
                
                selectedGenres.forEach { genre ->
                    repository.getMoviesByGenre(genre.id, "movie").onSuccess { moviesInGenre ->
                        if (moviesInGenre.isNotEmpty()) {
                            val items = moviesInGenre.take(15)
                            dynamicGenres.add(genre)
                            dynamicGenreMovies[genre.id.toString()] = items
                            
                            items.forEach { movie ->
                                launch {
                                    repository.getMovieDetails(movie).onSuccess { detailedMovie ->
                                        if (detailedMovie.poster != null) {
                                            val currentMap = _genreMovies.value.toMutableMap()
                                            val currentList = currentMap[genre.id.toString()]?.toMutableList() ?: return@launch
                                            val index = currentList.indexOfFirst { it.url == movie.url }
                                            if (index != -1) {
                                                currentList[index] = detailedMovie
                                                currentMap[genre.id.toString()] = currentList
                                                _genreMovies.value = currentMap
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                _genres.value = dynamicGenres
                _genreMovies.value = dynamicGenreMovies
            }
            _isLoading.value = false
        }
    }
}
