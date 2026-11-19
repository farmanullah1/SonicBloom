package com.example.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.R
import com.example.data.model.AudioTrack
import com.example.data.model.SmartPlaylistType
import com.example.ui.components.AddToPlaylistDialog
import com.example.ui.components.EqualizerBottomSheet
import com.example.ui.components.MiniPlayerBar
import com.example.ui.components.MultiTrackPickerDialog
import com.example.ui.components.PlaybackSpeedDialog
import com.example.ui.components.QueueBottomSheet
import com.example.ui.components.SleepTimerDialog
import com.example.ui.components.StudioSettingsSheet
import com.example.ui.components.TrackDetailsDialog
import com.example.ui.components.TrackOptionsBottomSheet
import com.example.ui.navigation.AlbumDetailRoute
import com.example.ui.navigation.ArtistDetailRoute
import com.example.ui.navigation.BrowseRoute
import com.example.ui.navigation.FolderDetailRoute
import com.example.ui.navigation.HomeRoute
import com.example.ui.navigation.PlaylistDetailRoute
import com.example.ui.navigation.PlaylistsRoute
import com.example.ui.navigation.TracksRoute
import com.example.ui.screens.BrowseScreen
import com.example.ui.screens.CollectionDetailScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.NowPlayingScreen
import com.example.ui.screens.PlaylistsScreen
import com.example.ui.screens.TracksScreen
import com.example.ui.viewmodel.SonicBloomViewModel
import com.example.ui.viewmodel.UiEvent

private data class TopNavItem<T : Any>(
    val route: T,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@Composable
fun SonicBloomApp(
    viewModel: SonicBloomViewModel,
    incomingAudioUri: Uri? = null,
    onConsumedIncomingUri: () -> Unit = {}
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val libraryState by viewModel.libraryState.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val equalizerState by viewModel.equalizerState.collectAsStateWithLifecycle()

    var showNowPlaying by remember { mutableStateOf(false) }
    var showEqualizer by remember { mutableStateOf(false) }
    var showSleepTimer by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }

    var sheetTrack by remember { mutableStateOf<AudioTrack?>(null) }
    var sheetPlaylistId by remember { mutableStateOf<Long?>(null) }
    var addToPlaylistTrack by remember { mutableStateOf<AudioTrack?>(null) }
    var detailsTrack by remember { mutableStateOf<AudioTrack?>(null) }
    var batchPickerPlaylistId by remember { mutableStateOf<Long?>(null) }

    val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { }
    )

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            viewModel.onPermissionUpdated(granted, userInitiated = true)
            if (granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val hasNotif = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!hasNotif) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    )

    val safImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                viewModel.importAudioFiles(uris, playImmediately = playbackState.currentTrack == null)
            }
        }
    )

    LaunchedEffect(Unit) {
        val hasAudio = ContextCompat.checkSelfPermission(
            context,
            audioPermission
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.onPermissionUpdated(hasAudio, userInitiated = false)
    }

    LaunchedEffect(incomingAudioUri) {
        if (incomingAudioUri != null) {
            viewModel.importAudioFiles(listOf(incomingAudioUri), playImmediately = true)
            showNowPlaying = true
            onConsumedIncomingUri()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.ShowMessage -> {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(
                        message = event.message,
                        actionLabel = event.actionLabel,
                        duration = SnackbarDuration.Short
                    )
                }
            }
        }
    }

    LaunchedEffect(playbackState.errorMessage) {
        val err = playbackState.errorMessage
        if (err != null && !showNowPlaying) {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(
                message = err,
                duration = SnackbarDuration.Short
            )
        }
    }

    val topLevelItems = remember {
        listOf(
            TopNavItem(
                route = HomeRoute,
                labelRes = R.string.nav_home,
                selectedIcon = Icons.Filled.Home,
                unselectedIcon = Icons.Outlined.Home,
                testTag = "nav_home"
            ),
            TopNavItem(
                route = TracksRoute,
                labelRes = R.string.nav_tracks,
                selectedIcon = Icons.Filled.MusicNote,
                unselectedIcon = Icons.Outlined.MusicNote,
                testTag = "nav_tracks"
            ),
            TopNavItem(
                route = BrowseRoute,
                labelRes = R.string.nav_browse,
                selectedIcon = Icons.Filled.Album,
                unselectedIcon = Icons.Outlined.Album,
                testTag = "nav_browse"
            ),
            TopNavItem(
                route = PlaylistsRoute,
                labelRes = R.string.nav_playlists,
                selectedIcon = Icons.AutoMirrored.Filled.QueueMusic,
                unselectedIcon = Icons.AutoMirrored.Outlined.QueueMusic,
                testTag = "nav_playlists"
            )
        )
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    fun navigateTopLevel(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val launchImport = {
        runCatching { safImportLauncher.launch(arrayOf("audio/*")) }
            .onFailure {
                viewModel.rescanLibrary(showFeedback = false)
            }
    }
    val launchPermission = {
        audioPermissionLauncher.launch(audioPermission)
    }
    val openAppSettings = {
        runCatching {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState) { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        actionColor = MaterialTheme.colorScheme.primary
                    )
                }
            },
            bottomBar = {
                if (!useRail) {
                    Column {
                        MiniPlayerBar(
                            playbackState = playbackState,
                            onOpenNowPlaying = { showNowPlaying = true },
                            onTogglePlayPause = viewModel::togglePlayPause,
                            onSkipNext = viewModel::skipNext,
                            onToggleFavorite = viewModel::toggleFavorite
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                            thickness = 1.dp
                        )
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            windowInsets = WindowInsets.navigationBars,
                            tonalElevation = 0.dp
                        ) {
                            topLevelItems.forEach { item ->
                                val selected = currentDestination?.hierarchy?.any {
                                    it.hasRoute(item.route::class)
                                } == true
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateTopLevel(item.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = stringResource(item.labelRes)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = stringResource(item.labelRes),
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                    ),
                                    modifier = Modifier.testTag(item.testTag)
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        topLevelItems.forEach { item ->
                            val selected = currentDestination?.hierarchy?.any {
                                it.hasRoute(item.route::class)
                            } == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopLevel(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = stringResource(item.labelRes)
                                    )
                                },
                                label = {
                                    Text(
                                        text = stringResource(item.labelRes),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = HomeRoute,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        composable<HomeRoute> {
                            HomeScreen(
                                libraryState = libraryState,
                                currentTrackId = playbackState.currentTrack?.id,
                                isPlaying = playbackState.isPlaying,
                                sleepTimer = playbackState.sleepTimer,
                                onCycleTheme = viewModel::cycleThemeMode,
                                onOpenSettings = { showSettingsSheet = true },
                                onRequestAudioPermission = launchPermission,
                                onOpenAppSettings = { openAppSettings() },
                                onImportAudioFiles = { launchImport() },
                                onRescanLibrary = { viewModel.rescanLibrary(showFeedback = true) },
                                onDismissError = viewModel::dismissLibraryError,
                                onShuffleAll = viewModel::shuffleAll,
                                onOpenEqualizer = { showEqualizer = true },
                                onOpenSleepTimer = { showSleepTimer = true },
                                onPlayTracks = viewModel::playTracks,
                                onToggleFavorite = viewModel::toggleFavorite,
                                onTrackMoreClick = { track ->
                                    sheetTrack = track
                                    sheetPlaylistId = null
                                },
                                onOpenFolder = { folder ->
                                    navController.navigate(FolderDetailRoute(folder))
                                },
                                onNavigateToTracks = { navigateTopLevel(TracksRoute) }
                            )
                        }

                        composable<TracksRoute> {
                            TracksScreen(
                                libraryState = libraryState,
                                currentTrackId = playbackState.currentTrack?.id,
                                isPlaying = playbackState.isPlaying,
                                onCycleTheme = viewModel::cycleThemeMode,
                                onOpenSettings = { showSettingsSheet = true },
                                onSearchQueryChange = viewModel::updateSearchQuery,
                                onFilterOptionChange = viewModel::updateFilterOption,
                                onSortOptionChange = viewModel::updateSortOption,
                                onToggleSortDirection = viewModel::toggleSortDirection,
                                onClearFilters = viewModel::clearTrackFilters,
                                onRescanLibrary = { viewModel.rescanLibrary(showFeedback = true) },
                                onDismissError = viewModel::dismissLibraryError,
                                onImportAudioFiles = { launchImport() },
                                onRequestAudioPermission = launchPermission,
                                onPlayTracks = viewModel::playTracks,
                                onToggleFavorite = viewModel::toggleFavorite,
                                onTrackMoreClick = { track ->
                                    sheetTrack = track
                                    sheetPlaylistId = null
                                }
                            )
                        }

                        composable<BrowseRoute> {
                            BrowseScreen(
                                libraryState = libraryState,
                                onCycleTheme = viewModel::cycleThemeMode,
                                onOpenSettings = { showSettingsSheet = true },
                                onBrowseSearchChange = viewModel::updateBrowseSearchQuery,
                                onSelectTab = viewModel::updateBrowseTab,
                                onOpenAlbum = { albumKey ->
                                    navController.navigate(AlbumDetailRoute(albumKey))
                                },
                                onOpenArtist = { artistName ->
                                    navController.navigate(ArtistDetailRoute(artistName))
                                },
                                onOpenFolder = { folderName ->
                                    navController.navigate(FolderDetailRoute(folderName))
                                }
                            )
                        }

                        composable<PlaylistsRoute> {
                            PlaylistsScreen(
                                libraryState = libraryState,
                                onCycleTheme = viewModel::cycleThemeMode,
                                onOpenSettings = { showSettingsSheet = true },
                                onOpenSmartPlaylist = { smartType ->
                                    navController.navigate(PlaylistDetailRoute(smartType = smartType))
                                },
                                onOpenPlaylist = { playlistId ->
                                    navController.navigate(PlaylistDetailRoute(playlistId = playlistId))
                                },
                                isDuplicatePlaylistName = viewModel::isPlaylistNameDuplicate,
                                onCreatePlaylist = { name -> viewModel.createPlaylist(name) },
                                onRenamePlaylist = viewModel::renamePlaylist,
                                onDeletePlaylist = viewModel::deletePlaylist
                            )
                        }

                        composable<AlbumDetailRoute> { entry ->
                            val route = entry.toRoute<AlbumDetailRoute>()
                            val tracks = viewModel.getTracksForAlbum(route.albumKey)
                            val album = libraryState.albums.find { it.key == route.albumKey }
                            CollectionDetailScreen(
                                title = album?.title ?: "Album",
                                subtitle = album?.artist ?: "Unknown Artist",
                                coverArtUri = album?.albumArtUri,
                                tracks = tracks,
                                currentTrackId = playbackState.currentTrack?.id,
                                isPlaying = playbackState.isPlaying,
                                onNavigateBack = { navController.popBackStack() },
                                onPlayTracks = viewModel::playTracks,
                                onToggleFavorite = viewModel::toggleFavorite,
                                onTrackMoreClick = { track, pid ->
                                    sheetTrack = track
                                    sheetPlaylistId = pid
                                }
                            )
                        }

                        composable<ArtistDetailRoute> { entry ->
                            val route = entry.toRoute<ArtistDetailRoute>()
                            val tracks = viewModel.getTracksForArtist(route.artistName)
                            CollectionDetailScreen(
                                title = route.artistName,
                                subtitle = "Artist Collection",
                                coverArtUri = tracks.firstNotNullOfOrNull { it.albumArtUri },
                                tracks = tracks,
                                currentTrackId = playbackState.currentTrack?.id,
                                isPlaying = playbackState.isPlaying,
                                onNavigateBack = { navController.popBackStack() },
                                onPlayTracks = viewModel::playTracks,
                                onToggleFavorite = viewModel::toggleFavorite,
                                onTrackMoreClick = { track, pid ->
                                    sheetTrack = track
                                    sheetPlaylistId = pid
                                }
                            )
                        }

                        composable<FolderDetailRoute> { entry ->
                            val route = entry.toRoute<FolderDetailRoute>()
                            val tracks = viewModel.getTracksForFolder(route.folderName)
                            CollectionDetailScreen(
                                title = route.folderName,
                                subtitle = "Device Folder",
                                coverArtUri = tracks.firstNotNullOfOrNull { it.albumArtUri },
                                tracks = tracks,
                                currentTrackId = playbackState.currentTrack?.id,
                                isPlaying = playbackState.isPlaying,
                                onNavigateBack = { navController.popBackStack() },
                                onPlayTracks = viewModel::playTracks,
                                onToggleFavorite = viewModel::toggleFavorite,
                                onTrackMoreClick = { track, pid ->
                                    sheetTrack = track
                                    sheetPlaylistId = pid
                                }
                            )
                        }

                        composable<PlaylistDetailRoute> { entry ->
                            val route = entry.toRoute<PlaylistDetailRoute>()
                            val smart = route.smartType
                            val title: String
                            val subtitle: String
                            val tracks: List<AudioTrack>
                            val contextPlaylistId: Long?

                            if (smart != null) {
                                contextPlaylistId = null
                                tracks = viewModel.getTracksForSmartPlaylist(smart)
                                title = when (smart) {
                                    SmartPlaylistType.LIKED -> stringResource(R.string.playlist_liked_songs)
                                    SmartPlaylistType.MOST_PLAYED -> stringResource(R.string.playlist_most_played)
                                    SmartPlaylistType.RECENTLY_ADDED -> stringResource(R.string.playlist_recently_added)
                                }
                                subtitle = "Smart Mix"
                            } else {
                                val playlist = libraryState.playlists.find { it.id == route.playlistId }
                                contextPlaylistId = route.playlistId
                                tracks = viewModel.getTracksForPlaylist(route.playlistId)
                                title = playlist?.name ?: "Playlist"
                                subtitle = "Custom Playlist"
                            }

                            CollectionDetailScreen(
                                title = title,
                                subtitle = subtitle,
                                coverArtUri = tracks.firstNotNullOfOrNull { it.albumArtUri },
                                tracks = tracks,
                                currentTrackId = playbackState.currentTrack?.id,
                                isPlaying = playbackState.isPlaying,
                                playlistContextId = contextPlaylistId,
                                onNavigateBack = { navController.popBackStack() },
                                onOpenBatchTrackPicker = contextPlaylistId?.let { pid ->
                                    { batchPickerPlaylistId = pid }
                                },
                                onPlayTracks = viewModel::playTracks,
                                onToggleFavorite = viewModel::toggleFavorite,
                                onTrackMoreClick = { track, pid ->
                                    sheetTrack = track
                                    sheetPlaylistId = pid
                                }
                            )
                        }
                    }

                    if (useRail) {
                        MiniPlayerBar(
                            playbackState = playbackState,
                            onOpenNowPlaying = { showNowPlaying = true },
                            onTogglePlayPause = viewModel::togglePlayPause,
                            onSkipNext = viewModel::skipNext,
                            onToggleFavorite = viewModel::toggleFavorite,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showNowPlaying && playbackState.currentTrack != null,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            NowPlayingScreen(
                playbackState = playbackState,
                onClose = { showNowPlaying = false },
                onTogglePlayPause = viewModel::togglePlayPause,
                onSeekTo = viewModel::seekTo,
                onSeekRelative = viewModel::seekRelative,
                onSkipNext = viewModel::skipNext,
                onSkipPrevious = viewModel::skipPrevious,
                onToggleShuffle = viewModel::toggleShuffle,
                onCycleRepeat = viewModel::cycleRepeatMode,
                onToggleFavorite = viewModel::toggleFavorite,
                onOpenEqualizer = { showEqualizer = true },
                onOpenSleepTimer = { showSleepTimer = true },
                onOpenSpeedDialog = { showSpeedDialog = true },
                onOpenQueue = { showQueueSheet = true },
                onMoreOptions = { track ->
                    sheetTrack = track
                    sheetPlaylistId = null
                },
                onClearError = viewModel::clearPlaybackError
            )
        }
    }

    sheetTrack?.let { track ->
        TrackOptionsBottomSheet(
            track = track,
            playlistContextId = sheetPlaylistId,
            onDismiss = {
                sheetTrack = null
                sheetPlaylistId = null
            },
            onPlayNext = { viewModel.playNext(track) },
            onAddToQueue = { viewModel.addToQueue(track) },
            onToggleFavorite = { viewModel.toggleFavorite(track) },
            onOpenAddToPlaylist = { addToPlaylistTrack = track },
            onShowDetails = { detailsTrack = track },
            onRemoveFromPlaylist = sheetPlaylistId?.let { pid ->
                { viewModel.removeTrackFromPlaylist(pid, track.id) }
            }
        )
    }

    addToPlaylistTrack?.let { track ->
        AddToPlaylistDialog(
            track = track,
            playlists = libraryState.playlists,
            isDuplicateName = { name -> viewModel.isPlaylistNameDuplicate(name) },
            onDismiss = { addToPlaylistTrack = null },
            onSelectPlaylist = { pid -> viewModel.addTrackToPlaylist(pid, track.id) },
            onCreateAndAdd = { newName -> viewModel.createPlaylist(newName, track.id) }
        )
    }

    batchPickerPlaylistId?.let { pid ->
        val existingIds = libraryState.playlists.find { it.id == pid }?.trackIds?.toSet().orEmpty()
        MultiTrackPickerDialog(
            availableTracks = libraryState.allTracks,
            existingTrackIds = existingIds,
            onDismiss = { batchPickerPlaylistId = null },
            onAddSelected = { selected -> viewModel.addTracksToPlaylist(pid, selected) }
        )
    }

    detailsTrack?.let { track ->
        TrackDetailsDialog(
            track = track,
            onDismiss = { detailsTrack = null }
        )
    }

    if (showSettingsSheet) {
        StudioSettingsSheet(
            libraryState = libraryState,
            onDismiss = { showSettingsSheet = false },
            onSelectThemeMode = viewModel::setThemeMode,
            onOpenEqualizer = { showEqualizer = true },
            onRescanLibrary = { viewModel.rescanLibrary(showFeedback = true) },
            onImportAudioFiles = { launchImport() }
        )
    }

    if (showEqualizer) {
        EqualizerBottomSheet(
            equalizerState = equalizerState,
            presets = viewModel.equalizerPresets,
            onDismiss = { showEqualizer = false },
            onToggleEnabled = viewModel::setEqEnabled,
            onSelectPreset = viewModel::applyEqPreset,
            onBandLevelChange = viewModel::setEqBandLevel,
            onBassBoostChange = viewModel::setBassBoost
        )
    }

    if (showSleepTimer) {
        SleepTimerDialog(
            sleepTimer = playbackState.sleepTimer,
            onDismiss = { showSleepTimer = false },
            onSelectMinutes = viewModel::startSleepTimerMinutes,
            onSelectEndOfTrack = viewModel::startSleepAfterCurrentTrack,
            onCancelTimer = viewModel::cancelSleepTimer
        )
    }

    if (showSpeedDialog) {
        PlaybackSpeedDialog(
            currentSpeed = playbackState.playbackSpeed,
            onDismiss = { showSpeedDialog = false },
            onSelectSpeed = viewModel::setPlaybackSpeed
        )
    }

    if (showQueueSheet) {
        QueueBottomSheet(
            queue = playbackState.queue,
            currentIndex = playbackState.currentIndex,
            isPlaying = playbackState.isPlaying,
            onDismiss = { showQueueSheet = false },
            onJumpToIndex = viewModel::jumpToQueueIndex,
            onMoveItem = viewModel::moveQueueItem,
            onRemoveIndex = viewModel::removeFromQueue,
            onClearQueue = viewModel::clearQueue
        )
    }
}
