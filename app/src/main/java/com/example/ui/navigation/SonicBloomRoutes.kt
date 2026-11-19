package com.example.ui.navigation

import com.example.data.model.SmartPlaylistType
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data object TracksRoute

@Serializable
data object BrowseRoute

@Serializable
data object PlaylistsRoute

@Serializable
data class AlbumDetailRoute(val albumKey: String)

@Serializable
data class ArtistDetailRoute(val artistName: String)

@Serializable
data class FolderDetailRoute(val folderName: String)

@Serializable
data class PlaylistDetailRoute(
    val playlistId: Long = -1L,
    val smartType: SmartPlaylistType? = null
)
