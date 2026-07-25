package com.potflix.presentation.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.potflix.data.local.dao.LocalMovieDao
import com.potflix.data.local.entity.toMovie
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class WatchlistViewModel @Inject constructor(
    localMovieDao: LocalMovieDao
) : ViewModel() {

    val watchlist = localMovieDao.getWatchlist().map { entities -> 
        entities.map { it.toMovie() }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
}
