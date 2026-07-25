package com.potflix.presentation.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.potflix.data.local.dao.LocalMovieDao
import com.potflix.data.local.entity.toLocalMovieEntity
import com.potflix.domain.model.Movie
import com.potflix.domain.model.Season
import com.potflix.domain.repository.MovieRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.potflix.util.DownloadHelper
import javax.inject.Inject

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val repository: MovieRepository,
    private val localMovieDao: com.potflix.data.local.dao.LocalMovieDao,
    private val localDownloadDao: com.potflix.data.local.dao.LocalDownloadDao,
    private val downloadHelper: DownloadHelper,
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val downloads = localDownloadDao.getAllDownloads().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val encodedJson: String = checkNotNull(savedStateHandle["movieJson"])
    private val movieJson: String = String(android.util.Base64.decode(encodedJson, android.util.Base64.URL_SAFE), Charsets.UTF_8)
    private val initialMovie: Movie = com.google.gson.Gson().fromJson(movieJson, Movie::class.java)

    private val _movie = MutableStateFlow<Movie?>(initialMovie)
    val movie = _movie.asStateFlow()

    private val _seasons = MutableStateFlow<List<Season>>(emptyList())
    val seasons = _seasons.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _streamError = MutableStateFlow<String?>(null)
    val streamError = _streamError.asStateFlow()

    private val prefs = context.getSharedPreferences("potflix_prefs", Context.MODE_PRIVATE)

    private val _selectedSeasonIndex = MutableStateFlow(0)
    val selectedSeasonIndex = _selectedSeasonIndex.asStateFlow()

    private val _lastPlayedEpisodeUrl = MutableStateFlow<String?>(prefs.getString("last_played_${initialMovie.url}", null))
    val lastPlayedEpisodeUrl = _lastPlayedEpisodeUrl.asStateFlow()

    fun setSeasonIndex(index: Int) {
        _selectedSeasonIndex.value = index
    }

    fun saveLastPlayedEpisode(episodeUrl: String) {
        prefs.edit().putString("last_played_${initialMovie.url}", episodeUrl).apply()
        _lastPlayedEpisodeUrl.value = episodeUrl
    }

    val isInWatchlist = localMovieDao.isInWatchlist(initialMovie.url).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    init {
        loadDetails()
    }

    private fun loadDetails() {
        viewModelScope.launch {
            _isLoading.value = true
            
            // Fetch TMDB metadata
            var currentMovie = initialMovie
            repository.getMovieDetails(initialMovie).onSuccess {
                currentMovie = it
                _movie.value = it
            }
            
            // Fetch seasons if it's a TV series
            if (currentMovie.type == "tv") {
                repository.getSeriesEpisodes(currentMovie.url).onSuccess { rawSeasons ->
                    val tmdbId = currentMovie.tmdbId
                    if (tmdbId != null) {
                        val mergedSeasons = rawSeasons.map { season ->
                            val tmdbSeasonResult = repository.getTmdbSeasonDetails(tmdbId, season.number)
                            if (tmdbSeasonResult.isSuccess) {
                                val tmdbSeason = tmdbSeasonResult.getOrNull()
                                val newEpisodes = season.episodes.map { rawEpisode ->
                                    val tmdbEpisode = tmdbSeason?.episodes?.find { it.episode_number == rawEpisode.number }
                                    if (tmdbEpisode != null) {
                                        rawEpisode.copy(
                                            title = tmdbEpisode.name ?: rawEpisode.title,
                                            overview = tmdbEpisode.overview,
                                            stillPath = tmdbEpisode.still_path?.let { path -> "https://image.tmdb.org/t/p/w300$path" }
                                        )
                                    } else {
                                        rawEpisode
                                    }
                                }
                                season.copy(episodes = newEpisodes)
                            } else {
                                season
                            }
                        }
                        _seasons.value = mergedSeasons
                        
                        val lastUrl = _lastPlayedEpisodeUrl.value
                        if (lastUrl != null) {
                            val seasonIdx = mergedSeasons.indexOfFirst { s -> s.episodes.any { it.url == lastUrl } }
                            if (seasonIdx != -1) {
                                _selectedSeasonIndex.value = seasonIdx
                            }
                        }
                    } else {
                        _seasons.value = rawSeasons
                        val lastUrl = _lastPlayedEpisodeUrl.value
                        if (lastUrl != null) {
                            val seasonIdx = rawSeasons.indexOfFirst { s -> s.episodes.any { it.url == lastUrl } }
                            if (seasonIdx != -1) {
                                _selectedSeasonIndex.value = seasonIdx
                            }
                        }
                    }
                }
            } else {
                // It's a movie, so initialMovie.url is likely a folder. We need to find the video file inside.
                repository.getMovieStreamUrl(currentMovie.url).onSuccess { streamUrl ->
                    _movie.value = _movie.value?.copy(url = streamUrl)
                }.onFailure { err ->
                    _streamError.value = err.message ?: "Unknown error loading stream"
                }
            }

            _isLoading.value = false
        }
    }

    fun toggleWatchlist() {
        viewModelScope.launch {
            if (isInWatchlist.value) {
                localMovieDao.removeByUrl(initialMovie.url)
            } else {
                _movie.value?.toLocalMovieEntity()?.copy(url = initialMovie.url)?.let { 
                    localMovieDao.addToWatchlist(it)
                }
            }
        }
    }

    fun startDownload(title: String, streamUrl: String, poster: String?) {
        downloadHelper.startDownload(title, streamUrl, poster)
    }
}
