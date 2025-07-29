package it.vfsfitvnm.vimusic.ui.screens.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.media3.common.Player
import it.vfsfitvnm.core.ui.LocalAppearance
import it.vfsfitvnm.core.ui.favoritesIcon
import it.vfsfitvnm.core.ui.utils.px
import it.vfsfitvnm.core.ui.utils.roundedShape
import it.vfsfitvnm.vimusic.Database
import it.vfsfitvnm.vimusic.R
import it.vfsfitvnm.vimusic.models.Info
import it.vfsfitvnm.vimusic.models.ui.UiMedia
import it.vfsfitvnm.vimusic.preferences.PlayerPreferences
import it.vfsfitvnm.vimusic.service.PlayerService
import it.vfsfitvnm.vimusic.ui.components.FadingRow
import it.vfsfitvnm.vimusic.ui.components.LocalMenuState
import it.vfsfitvnm.vimusic.ui.components.SeekBar
import it.vfsfitvnm.vimusic.ui.components.themed.IconButton
import it.vfsfitvnm.vimusic.ui.screens.artistRoute
import it.vfsfitvnm.vimusic.utils.bold
import it.vfsfitvnm.vimusic.utils.forceSeekToNext
import it.vfsfitvnm.vimusic.utils.forceSeekToPrevious
import it.vfsfitvnm.vimusic.utils.secondary
import it.vfsfitvnm.vimusic.utils.semiBold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun Controls(
    media: UiMedia?,
    binder: PlayerService.Binder?,
    likedAt: Long?,
    setLikedAt: (Long?) -> Unit,
    shouldBePlaying: Boolean,
    position: Long,
    modifier: Modifier = Modifier,
) {
    val shouldBePlayingTransition = updateTransition(
        targetState = shouldBePlaying,
        label = "shouldBePlaying"
    )

    val playButtonRadius by shouldBePlayingTransition.animateDp(
        transitionSpec = { tween(durationMillis = 100, easing = LinearEasing) },
        label = "playPauseRoundness",
        targetValueByState = { if (it) 16.dp else 32.dp }
    )

    if (media != null && binder != null) {
        ClassicControls(
            media = media,
            binder = binder,
            shouldBePlaying = shouldBePlaying,
            position = position,
            likedAt = likedAt,
            setLikedAt = setLikedAt,
            playButtonRadius = playButtonRadius,
            modifier = modifier
        )
    }
}

@Composable
private fun ClassicControls(
    media: UiMedia,
    binder: PlayerService.Binder,
    shouldBePlaying: Boolean,
    position: Long,
    likedAt: Long?,
    setLikedAt: (Long?) -> Unit,
    playButtonRadius: Dp,
    modifier: Modifier = Modifier
) {
    val (colorPalette) = LocalAppearance.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
    ) {
        Spacer(modifier = Modifier.weight(1f))

        MediaInfo(
            media = media,
            binder = binder,
        )

        Spacer(modifier = Modifier.weight(1f))
        SeekBar(
            binder = binder,
            position = position,
            media = media,
            alwaysShowDuration = true
        )
        Spacer(modifier = Modifier.weight(1f))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(
                icon = if (likedAt == null) R.drawable.heart_outline else R.drawable.heart,
                color = colorPalette.favoritesIcon,
                onClick = {
                    setLikedAt(if (likedAt == null) System.currentTimeMillis() else null)
                },
                modifier = Modifier
                    .weight(1f)
                    .size(24.dp)
            )

            IconButton(
                icon = R.drawable.play_skip_back,
                color = colorPalette.text,
                onClick = binder.player::forceSeekToPrevious,
                modifier = Modifier
                    .weight(1f)
                    .size(24.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .clip(playButtonRadius.roundedShape)
                    .clickable {
                        if (shouldBePlaying) binder.player.pause()
                        else {
                            if (binder.player.playbackState == Player.STATE_IDLE) binder.player.prepare()
                            binder.player.play()
                        }
                    }
                    .background(colorPalette.background2)
                    .size(64.dp)
            ) {
                AnimatedPlayPauseButton(
                    playing = shouldBePlaying,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(32.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                icon = R.drawable.play_skip_forward,
                color = colorPalette.text,
                onClick = binder.player::forceSeekToNext,
                modifier = Modifier
                    .weight(1f)
                    .size(24.dp)
            )

            IconButton(
                icon = R.drawable.infinite,
                enabled = PlayerPreferences.trackLoopEnabled,
                onClick = { PlayerPreferences.trackLoopEnabled = !PlayerPreferences.trackLoopEnabled },
                modifier = Modifier
                    .weight(1f)
                    .size(24.dp)
            )
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun MediaInfo(
    media: UiMedia,
    binder: PlayerService.Binder
) {
    val menuState = LocalMenuState.current
    var mediaItem by remember { mutableStateOf(binder.player.currentMediaItem) }
    val (colorPalette, typography) = LocalAppearance.current

    var artistInfo: List<Info>? by remember { mutableStateOf(null) }
    var maxHeight by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(media) {
        withContext(Dispatchers.IO) {
            artistInfo = Database.instance
                .songArtistInfo(media.id)
                .takeIf { it.isNotEmpty() }
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(36.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.size(24.dp))

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = media.title,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "SongTitle"
                ) { title ->
                    // Swapped back to FadingRow to get the fade effect.
                    // The modifier makes it narrower, as you wanted.
                    FadingRow(
                        modifier = Modifier.fillMaxWidth(0.7f)
                    ) {
                        // The parent Box will center this content.
                        BasicText(
                            text = title,
                            style = typography.l.bold,
                            maxLines = 1
                        )
                    }
                }
            }

            IconButton(
                icon = R.drawable.more,
                color = colorPalette.text,
                onClick = {
                    menuState.display {
                        PlayerMenu(
                            binder = binder,
                            mediaItem = mediaItem!!,
                            onDismiss = menuState::hide,
                            onShowSpeedDialog = { },
                            onShowNormalizationDialog = { },
                        )
                    }
                },
                modifier = Modifier.size(24.dp)
            )
        }

        // Artist info section (no changes)
        AnimatedContent(
            targetState = media to artistInfo,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "ArtistInfo"
        ) { (media, state) ->
            state?.let { artists ->
                FadingRow(
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .heightIn(maxHeight.px.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        artists.fastForEachIndexed { i, artist ->
                            if (i == artists.lastIndex && artists.size > 1) BasicText(
                                text = " & ",
                                style = typography.s.semiBold.secondary
                            )
                            BasicText(
                                text = artist.name.orEmpty(),
                                style = typography.s.bold.secondary,
                                modifier = Modifier.clickable { artistRoute.global(artist.id) }
                            )
                            if (i != artists.lastIndex && i + 1 != artists.lastIndex) BasicText(
                                text = ", ",
                                style = typography.s.semiBold.secondary
                            )
                        }
                        if (media.explicit) {
                            Spacer(Modifier.width(4.dp))
                            Image(
                                painter = painterResource(R.drawable.explicit),
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(colorPalette.text),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            } ?: FadingRow(
                modifier = Modifier.fillMaxWidth(0.75f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicText(
                        text = media.artist,
                        style = typography.s.semiBold.secondary,
                        maxLines = 1,
                        modifier = Modifier.onGloballyPositioned { maxHeight = it.size.height }
                    )
                    if (media.explicit) {
                        Spacer(Modifier.width(4.dp))
                        Image(
                            painter = painterResource(R.drawable.explicit),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(colorPalette.text),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}
