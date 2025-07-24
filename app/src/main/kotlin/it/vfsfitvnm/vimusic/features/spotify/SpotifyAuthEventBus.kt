package it.vfsfitvnm.vimusic.features.spotify

import kotlinx.coroutines.flow.MutableSharedFlow

// A simple event bus to notify the UI about login completion
object SpotifyAuthEventBus {
    val events = MutableSharedFlow<Boolean>()
}
