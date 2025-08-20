package it.vfsfitvnm.vimusic.ui.screens.localplaylist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import it.vfsfitvnm.compose.persist.persistList
import it.vfsfitvnm.compose.reordering.animateItemPlacement
import it.vfsfitvnm.compose.reordering.draggedItem
import it.vfsfitvnm.compose.reordering.rememberReorderingState
import it.vfsfitvnm.core.data.enums.SongSortBy
import it.vfsfitvnm.core.data.enums.SortOrder
import it.vfsfitvnm.core.ui.Dimensions
import it.vfsfitvnm.core.ui.LocalAppearance
import it.vfsfitvnm.core.ui.utils.enumSaver
import it.vfsfitvnm.core.ui.utils.isLandscape
import it.vfsfitvnm.providers.innertube.Innertube
import it.vfsfitvnm.providers.innertube.models.bodies.BrowseBody
import it.vfsfitvnm.providers.innertube.requests.playlistPage
import it.vfsfitvnm.vimusic.Database
import it.vfsfitvnm.vimusic.LocalPlayerAwareWindowInsets
import it.vfsfitvnm.vimusic.LocalPlayerServiceBinder
import it.vfsfitvnm.vimusic.R
import it.vfsfitvnm.vimusic.models.Playlist
import it.vfsfitvnm.vimusic.models.Song
import it.vfsfitvnm.vimusic.models.SongPlaylistMap
import it.vfsfitvnm.vimusic.preferences.DataPreferences
import it.vfsfitvnm.vimusic.query
import it.vfsfitvnm.vimusic.transaction
import it.vfsfitvnm.vimusic.ui.components.LocalMenuState
import it.vfsfitvnm.vimusic.ui.components.themed.CircularProgressIndicator
import it.vfsfitvnm.vimusic.ui.components.themed.ConfirmationDialog
import it.vfsfitvnm.vimusic.ui.components.themed.Header
import it.vfsfitvnm.vimusic.ui.components.themed.HeaderIconButton
import it.vfsfitvnm.vimusic.ui.components.themed.InPlaylistMediaItemMenu
import it.vfsfitvnm.vimusic.ui.components.themed.LayoutWithAdaptiveThumbnail
import it.vfsfitvnm.vimusic.ui.components.themed.Menu
import it.vfsfitvnm.vimusic.ui.components.themed.MenuEntry
import it.vfsfitvnm.vimusic.ui.components.themed.ReorderHandle
import it.vfsfitvnm.vimusic.ui.components.themed.TextFieldDialog
import it.vfsfitvnm.vimusic.ui.items.SongItem
import it.vfsfitvnm.vimusic.ui.screens.home.HeaderSongSortBy
import it.vfsfitvnm.vimusic.utils.PlaylistDownloadIcon
import it.vfsfitvnm.vimusic.utils.asMediaItem
import it.vfsfitvnm.vimusic.utils.completed
import it.vfsfitvnm.vimusic.utils.enqueue
import it.vfsfitvnm.vimusic.utils.forcePlayAtIndex
import it.vfsfitvnm.vimusic.utils.forcePlayFromBeginning
import it.vfsfitvnm.vimusic.utils.launchYouTubeMusic
import it.vfsfitvnm.vimusic.utils.playingSong
import it.vfsfitvnm.vimusic.utils.toast
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun LocalPlaylistSongs(
    playlist: Playlist,
    onDelete: () -> Unit,
    thumbnailContent: @Composable () -> Unit,
    modifier: Modifier = Modifier
) = LayoutWithAdaptiveThumbnail(
    thumbnailContent = thumbnailContent,
    modifier = modifier
) {
    val (colorPalette, typography) = LocalAppearance.current
    val binder = LocalPlayerServiceBinder.current
    val menuState = LocalMenuState.current
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()

    var songs by persistList<Song>("localPlaylist/${playlist.id}/songs")

    // Search state
    var isSearching by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable(stateSaver = androidx.compose.runtime.saveable.Saver(
        save = { it.text },
        restore = { TextFieldValue(it) }
    )) { mutableStateOf(TextFieldValue("")) }

    val searchFocusRequester = remember { FocusRequester() }

    // Filter songs based on search query
    val filteredSongs by remember {
        derivedStateOf {
            if (searchQuery.text.isBlank()) {
                songs
            } else {
                songs.filter { song ->
                    song.title.contains(searchQuery.text, ignoreCase = true) ||
                        song.artistsText?.contains(searchQuery.text, ignoreCase = true) == true ||
                        song.album?.contains(searchQuery.text, ignoreCase = true) == true
                }
            }
        }
    }

    // Add state for sorting
    var sortBy by rememberSaveable(stateSaver = enumSaver()) { mutableStateOf(SongSortBy.Position) }
    var sortOrder by rememberSaveable(stateSaver = enumSaver()) { mutableStateOf(SortOrder.Ascending) }

    var loading by remember { mutableStateOf(false) }

    // Fetch songs and apply sorting
    LaunchedEffect(playlist.id, sortBy, sortOrder) {
        Database.instance
            .playlistSongs(playlist.id, sortBy, sortOrder) // Use the new DAO method
            .filterNotNull()
            .distinctUntilChanged()
            .collect { songs = it.toImmutableList() }
    }

    LaunchedEffect(Unit) {
        if (DataPreferences.autoSyncPlaylists) playlist.browseId?.let { browseId ->
            loading = true
            sync(playlist, browseId)
            loading = false
        }
    }

    // Focus search field when search mode is activated
    LaunchedEffect(isSearching) {
        if (isSearching) {
            searchFocusRequester.requestFocus()
        } else {
            keyboardController?.hide()
            searchQuery = TextFieldValue("")
        }
    }

    val reorderingState = rememberReorderingState(
        lazyListState = lazyListState,
        key = songs,
        onDragEnd = { fromIndex, toIndex ->
            transaction {
                Database.instance.move(playlist.id, fromIndex, toIndex)
            }
        },
        extraItemCount = 1
    )

    var isRenaming by rememberSaveable { mutableStateOf(false) }

    if (isRenaming) TextFieldDialog(
        hintText = stringResource(R.string.enter_playlist_name_prompt),
        initialTextInput = playlist.name,
        onDismiss = { isRenaming = false },
        onAccept = { text ->
            query {
                Database.instance.update(playlist.copy(name = text))
            }
        }
    )

    var isDeleting by rememberSaveable { mutableStateOf(false) }

    if (isDeleting) ConfirmationDialog(
        text = stringResource(R.string.confirm_delete_playlist),
        onDismiss = { isDeleting = false },
        onConfirm = {
            query {
                Database.instance.delete(playlist)
            }
            onDelete()
        }
    )

    val (currentMediaId, playing) = playingSong(binder)

    Box {
        LookaheadScope {
            LazyColumn(
                state = reorderingState.lazyListState,
                contentPadding = LocalPlayerAwareWindowInsets.current
                    .only(WindowInsetsSides.Vertical + WindowInsetsSides.End)
                    .asPaddingValues(),
                modifier = Modifier
                    .background(colorPalette.background0)
                    .fillMaxSize()
            ) {
                item(
                    key = "header",
                    contentType = 0
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AnimatedVisibility(
                            visible = !isSearching,
                            enter = fadeIn() + slideInHorizontally(),
                            exit = fadeOut() + slideOutHorizontally()
                        ) {
                            Header(
                                title = playlist.name,
                                modifier = Modifier.padding(bottom = 8.dp)
                            ) {
                                Spacer(modifier = Modifier.weight(1f))

                                AnimatedVisibility(loading) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                                }

                                PlaylistDownloadIcon(
                                    songs = filteredSongs.map { it.asMediaItem }.toImmutableList()
                                )

                                // Add the sorting component
                                if (playlist.sortable) {
                                    HeaderSongSortBy(
                                        sortBy = sortBy,
                                        setSortBy = { sortBy = it },
                                        sortOrder = sortOrder,
                                        setSortOrder = { sortOrder = it }
                                    )
                                }

                                // Search icon
                                HeaderIconButton(
                                    icon = R.drawable.search,
                                    color = if (isSearching) colorPalette.accent else colorPalette.text,
                                    onClick = {
                                        isSearching = true
                                    }
                                )

                                HeaderIconButton(
                                    icon = R.drawable.ellipsis_horizontal,
                                    color = colorPalette.text,
                                    onClick = {
                                        menuState.display {
                                            Menu {
                                                playlist.browseId?.let { browseId ->
                                                    MenuEntry(
                                                        icon = R.drawable.sync,
                                                        text = stringResource(R.string.sync),
                                                        enabled = !loading,
                                                        onClick = {
                                                            menuState.hide()
                                                            coroutineScope.launch {
                                                                loading = true
                                                                sync(playlist, browseId)
                                                                loading = false
                                                            }
                                                        }
                                                    )

                                                    songs.firstOrNull()?.id?.let { firstSongId ->
                                                        MenuEntry(
                                                            icon = R.drawable.play,
                                                            text = stringResource(R.string.watch_playlist_on_youtube),
                                                            onClick = {
                                                                menuState.hide()
                                                                binder?.player?.pause()
                                                                uriHandler.openUri(
                                                                    "https://youtube.com/watch?v=$firstSongId&list=${
                                                                        playlist.browseId.drop(2)
                                                                    }"
                                                                )
                                                            }
                                                        )

                                                        MenuEntry(
                                                            icon = R.drawable.musical_notes,
                                                            text = stringResource(R.string.open_in_youtube_music),
                                                            onClick = {
                                                                menuState.hide()
                                                                binder?.player?.pause()
                                                                if (
                                                                    !launchYouTubeMusic(
                                                                        context = context,
                                                                        endpoint = "watch?v=$firstSongId&list=${
                                                                            playlist.browseId.drop(2)
                                                                        }"
                                                                    )
                                                                ) context.toast(
                                                                    context.getString(R.string.youtube_music_not_installed)
                                                                )
                                                            }
                                                        )
                                                    }
                                                }

                                                MenuEntry(
                                                    icon = R.drawable.pencil,
                                                    text = stringResource(R.string.rename),
                                                    onClick = {
                                                        menuState.hide()
                                                        isRenaming = true
                                                    }
                                                )

                                                MenuEntry(
                                                    icon = R.drawable.trash,
                                                    text = stringResource(R.string.delete),
                                                    onClick = {
                                                        menuState.hide()
                                                        isDeleting = true
                                                    }
                                                )
                                            }
                                        }
                                    }
                                )
                            }
                        }

                        // Search bar with animation - FIXED VERSION
                        AnimatedVisibility(
                            visible = isSearching,
                            enter = fadeIn() + slideInHorizontally { -it },
                            exit = fadeOut() + slideOutHorizontally { -it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clipToBounds() // This prevents the animation overflow
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .background(
                                        color = colorPalette.background1,
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(50.dp)
                                    )
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    modifier = Modifier
                                        .weight(1f)
                                        .focusRequester(searchFocusRequester)
                                        .padding(vertical = 12.dp),
                                    textStyle = typography.l.copy(color = colorPalette.text),
                                    cursorBrush = SolidColor(colorPalette.accent),
                                    decorationBox = { innerTextField ->
                                        if (searchQuery.text.isEmpty()) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                androidx.compose.material3.Icon(
                                                    painter = androidx.compose.ui.res.painterResource(R.drawable.search),
                                                    contentDescription = null,
                                                    tint = colorPalette.textDisabled,
                                                    modifier = Modifier
                                                        .size(32.dp)
                                                        .padding(end = 8.dp)
                                                )
                                                androidx.compose.material3.Text(
                                                    text = stringResource(R.string.search_placeholder),
                                                    style = typography.l,
                                                    color = colorPalette.textDisabled
                                                )
                                            }
                                        }
                                        innerTextField()
                                    },
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(
                                        onSearch = { keyboardController?.hide() }
                                    ),
                                    singleLine = true
                                )

                                HeaderIconButton(
                                    icon = R.drawable.close,
                                    color = colorPalette.textSecondary,
                                    onClick = {
                                        isSearching = false
                                    },
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        if (!isLandscape) thumbnailContent()
                    }
                }

                itemsIndexed(
                    items = filteredSongs,
                    key = { index, song -> "${song.id}-$index" },
                    contentType = { _, song -> song }
                ) { index, song ->
                    val isSearchResult = isSearching && searchQuery.text.isNotBlank()

                    SongItem(
                        modifier = Modifier
                            .combinedClickable(
                                onLongClick = {
                                    menuState.display {
                                        InPlaylistMediaItemMenu(
                                            playlistId = playlist.id,
                                            song = song,
                                            onDismiss = menuState::hide
                                        )
                                    }
                                },
                                onClick = {
                                    binder?.stopRadio()
                                    binder?.player?.forcePlayAtIndex(
                                        items = filteredSongs.map { it.asMediaItem },
                                        index = index
                                    )
                                }
                            )
                            .animateItemPlacement(reorderingState)
                            .draggedItem(
                                reorderingState = reorderingState,
                                index = index
                            )
                            .background(
                                if (isSearchResult && (song.title.contains(searchQuery.text, ignoreCase = true) ||
                                        song.artistsText?.contains(searchQuery.text, ignoreCase = true) == true ||
                                        song.album?.contains(searchQuery.text, ignoreCase = true) == true))
                                    colorPalette.background1.copy(alpha = 0.3f)
                                else colorPalette.background0
                            ),
                        song = song,
                        thumbnailSize = Dimensions.thumbnails.song,
                        trailingContent = {
                            // Only show reorder handle when sorting by position and not searching
                            if (sortBy == SongSortBy.Position && !isSearching) {
                                ReorderHandle(
                                    reorderingState = reorderingState,
                                    index = index
                                )
                            }
                        },
                        clip = !reorderingState.isDragging,
                        isPlaying = playing && currentMediaId == song.id
                    )
                }
            }
        }

        // Custom floating action buttons using existing square rounded UI style
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = 16.dp,
                    bottom = 16.dp
                )
                .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues())
        ) {
            Column(
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.End
            ) {
                // Enqueue floating button (top)
                AnimatedVisibility(
                    visible = !reorderingState.isDragging && filteredSongs.isNotEmpty(),
                    enter = fadeIn() + androidx.compose.animation.scaleIn(),
                    exit = fadeOut() + androidx.compose.animation.scaleOut()
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = colorPalette.background2,
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                            )
                            .combinedClickable(
                                indication = null,
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                onClick = {
                                    binder?.player?.enqueue(filteredSongs.map { it.asMediaItem })
                                }
                            )
                            .padding(18.dp)
                            .size(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(R.drawable.enqueue),
                            contentDescription = null,
                            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(colorPalette.text),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Bottom row: Shuffle button and Scroll to top button
                Row(
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Scroll to top button (circular, appears when scrolled, left of shuffle)
                    AnimatedVisibility(
                        visible = remember {
                            derivedStateOf {
                                !reorderingState.isDragging &&
                                    (lazyListState.firstVisibleItemIndex > 0 ||
                                        lazyListState.firstVisibleItemScrollOffset > 0)
                            }
                        }.value,
                        enter = fadeIn() + androidx.compose.animation.scaleIn(),
                        exit = fadeOut() + androidx.compose.animation.scaleOut()
                    ) {
                        Box(
                            modifier = Modifier
                                .background(
                                    color = colorPalette.background2,
                                    shape = CircleShape
                                )
                                .combinedClickable(
                                    indication = null,
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    onClick = {
                                        coroutineScope.launch {
                                            lazyListState.animateScrollToItem(0)
                                        }
                                    }
                                )
                                .padding(18.dp)
                                .size(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.foundation.Image(
                                painter = androidx.compose.ui.res.painterResource(R.drawable.chevron_up),
                                contentDescription = null,
                                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(colorPalette.text),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Shuffle floating button (main button, always visible)
                    AnimatedVisibility(
                        visible = !reorderingState.isDragging,
                        enter = fadeIn() + androidx.compose.animation.scaleIn(),
                        exit = fadeOut() + androidx.compose.animation.scaleOut()
                    ) {
                        Box(
                            modifier = Modifier
                                .background(
                                    color = colorPalette.background2,
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                                )
                                .combinedClickable(
                                    indication = null,
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    onClick = {
                                        if (filteredSongs.isEmpty()) return@combinedClickable
                                        binder?.stopRadio()
                                        binder?.player?.forcePlayFromBeginning(
                                            filteredSongs.shuffled().map { it.asMediaItem }
                                        )
                                    }
                                )
                                .padding(18.dp)
                                .size(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.foundation.Image(
                                painter = androidx.compose.ui.res.painterResource(R.drawable.shuffle),
                                contentDescription = null,
                                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(colorPalette.text),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private suspend fun sync(
    playlist: Playlist,
    browseId: String
) = runCatching {
    Innertube.playlistPage(
        BrowseBody(browseId = browseId)
    )?.completed()?.getOrNull()?.let { remotePlaylist ->
        transaction {
            Database.instance.clearPlaylist(playlist.id)

            remotePlaylist.songsPage
                ?.items
                ?.map { it.asMediaItem }
                ?.onEach { Database.instance.insert(it) }
                ?.mapIndexed { position, mediaItem ->
                    SongPlaylistMap(
                        songId = mediaItem.mediaId,
                        playlistId = playlist.id,
                        position = position
                    )
                }
                ?.let(Database.instance::insertSongPlaylistMaps)
        }
    }
}.onFailure {
    if (it is CancellationException) throw it
    it.printStackTrace()
}
