package com.potflix.presentation.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.potflix.data.repository.WatchlistRepository
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
import kotlinx.coroutines.Dispatchers
import com.potflix.util.DownloadHelper
import javax.inject.Inject

import android.content.Context
import android.app.DownloadManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val repository: MovieRepository,
    private val watchlistRepository: WatchlistRepository,
    private val localDownloadDao: com.potflix.data.local.dao.LocalDownloadDao,
    private val downloadHelper: DownloadHelper,
    private val firebaseSyncManager: com.potflix.data.remote.FirebaseSyncManager,
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

    fun onPlayStarted() {
        viewModelScope.launch {
            repository.addToWatchHistory(initialMovie)
        }
    }

    fun saveLastPlayedEpisode(episodeUrl: String) {
        prefs.edit().putString("last_played_${initialMovie.url}", episodeUrl).apply()
        _lastPlayedEpisodeUrl.value = episodeUrl
    }

    val isInWatchlist = watchlistRepository.isInWatchlist(initialMovie.url).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    init {
        loadDetails()
    }

    fun reload() {
        loadDetails()
    }

    private fun loadDetails() {
        _isLoading.value = true
        _streamError.value = null
        
        viewModelScope.launch {
            try {
                // 1. Fetch play URLs / Episodes to unblock the UI
                if (initialMovie.type == "tv") {
                    repository.getSeriesEpisodes(initialMovie.url).onSuccess { rawSeasons ->
                        _seasons.value = rawSeasons
                        val lastUrl = _lastPlayedEpisodeUrl.value
                        if (lastUrl != null) {
                            val seasonIdx = rawSeasons.indexOfFirst { s -> s.episodes.any { it.url == lastUrl } }
                            if (seasonIdx != -1) {
                                _selectedSeasonIndex.value = seasonIdx
                            }
                        }
                    }.onFailure { err ->
                        _streamError.value = err.message
                    }
                } else {
                    // It's a movie, load stream URL
                    repository.getMovieStreamUrl(initialMovie.url).onSuccess { streamUrl ->
                        _movie.value = _movie.value?.copy(url = streamUrl)
                    }.onFailure { err ->
                        _streamError.value = err.message ?: "Unknown error loading stream"
                    }
                }

                // 2. Fetch TMDB metadata / sync with updated Room DB
                val currentM = _movie.value ?: initialMovie
                repository.getMovieDetails(currentM).onSuccess { enrichedMovie ->
                    val currentStreamUrl = _movie.value?.url ?: enrichedMovie.url
                    _movie.value = enrichedMovie.copy(url = currentStreamUrl)
                    
                    if (enrichedMovie.type == "tv" && enrichedMovie.tmdbId != null) {
                        enrichEpisodes(enrichedMovie.tmdbId)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("DetailViewModel", "Error loading details", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun enrichEpisodes(tmdbId: Int) {
        val currentSeasons = _seasons.value
        if (currentSeasons.isEmpty()) return

        val enrichedSeasons = currentSeasons.toMutableList()
        for ((index, season) in enrichedSeasons.withIndex()) {
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
                enrichedSeasons[index] = season.copy(episodes = newEpisodes)
            }
        }
        _seasons.value = enrichedSeasons.toList()
    }

    fun searchTmdb(query: String, type: String, onResult: (List<com.potflix.data.remote.TmdbMovieDto>) -> Unit) {
        viewModelScope.launch {
            repository.searchTmdb(query, type).onSuccess { results ->
                onResult(results)
            }.onFailure {
                onResult(emptyList())
            }
        }
    }

    fun applyTmdbCorrection(tmdbId: Long, type: String, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            _isLoading.value = true
            val detailResult = repository.getTmdbDetail(tmdbId.toInt(), type)
            if (detailResult.isSuccess) {
                val detail = detailResult.getOrNull()!!
                val newTitle = detail.title ?: detail.name ?: initialMovie.title
                val newPoster = detail.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" } ?: movie.value?.poster
                val newBackdrop = detail.backdrop_path?.let { "https://image.tmdb.org/t/p/w780$it" } ?: movie.value?.backdrop
                val newOverview = detail.overview ?: movie.value?.overview
                val newRating = detail.vote_average ?: movie.value?.rating

                val currentMovie = movie.value ?: initialMovie
                val updatedMovie = repository.updateMovieTmdbMatch(
                    originalMovie = currentMovie,
                    newTmdbId = tmdbId,
                    type = type,
                    newTitle = newTitle,
                    newOverview = newOverview,
                    newPoster = newPoster,
                    newBackdrop = newBackdrop,
                    newRating = newRating
                ).getOrDefault(currentMovie.copy(
                    title = newTitle,
                    tmdbId = tmdbId.toInt(),
                    type = type,
                    poster = newPoster,
                    backdrop = newBackdrop,
                    overview = newOverview,
                    rating = newRating
                ))

                _movie.value = updatedMovie

                // Upload community correction to Firestore centrally
                firebaseSyncManager.suggestTmdbCorrection(
                    originalTitle = initialMovie.title,
                    url = initialMovie.url,
                    correctedTmdbId = tmdbId,
                    correctedTitle = newTitle,
                    type = type
                )

                // If TV, enrich episodes with new TMDB ID
                if (type == "tv" || updatedMovie.type == "tv") {
                    enrichEpisodes(tmdbId.toInt())
                }

                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    android.widget.Toast.makeText(context, "TMDB matched to \"$newTitle\"! Community suggestion submitted.", android.widget.Toast.LENGTH_LONG).show()
                }
            } else {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    android.widget.Toast.makeText(context, "Failed to load TMDB info for ID: $tmdbId", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            _isLoading.value = false
            onComplete()
        }
    }

    fun toggleWatchlist() {
        viewModelScope.launch {
            if (isInWatchlist.value) {
                watchlistRepository.removeByUrl(initialMovie.url)
            } else {
                movie.value?.toLocalMovieEntity()?.copy(url = initialMovie.url)?.let {
                    watchlistRepository.addToWatchlist(it)
                }
            }
        }
    }

    fun startDownload(title: String, streamUrl: String, poster: String?) {
        downloadHelper.startDownload(title, streamUrl, poster)
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            android.widget.Toast.makeText(context, "Download started: $title", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
