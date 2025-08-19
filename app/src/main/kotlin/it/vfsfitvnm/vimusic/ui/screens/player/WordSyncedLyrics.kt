package it.vfsfitvnm.vimusic.ui.screens.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import it.vfsfitvnm.core.ui.LocalAppearance
import it.vfsfitvnm.providers.lyricsplus.LyricsPlusSyncManager
import it.vfsfitvnm.vimusic.ui.modifiers.verticalFadingEdge
import it.vfsfitvnm.vimusic.utils.center
import it.vfsfitvnm.vimusic.utils.color
import it.vfsfitvnm.vimusic.utils.medium
import kotlinx.collections.immutable.toImmutableList

@Composable
fun WordSyncedLyrics(manager: LyricsPlusSyncManager, modifier: Modifier = Modifier) {
    val (colorPalette, typography) = LocalAppearance.current

    val lazyListState = rememberLazyListState()
    val lyrics = manager.getLyrics()

    // Collect the current line index and player position as state
    val currentLineIndex by manager.currentLineIndex.collectAsState()
    val currentPosition by manager.currentPosition.collectAsState()

    // This effect triggers when the current line changes, scrolling it to the center
    LaunchedEffect(currentLineIndex) {
        if (currentLineIndex >= 0) {
            // Calculate the necessary offset to center the item in the viewport
            val viewHeight = lazyListState.layoutInfo.viewportSize.height
            // Find the specific item's height, default to 0 if not visible
            val itemHeight = lazyListState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.index == currentLineIndex + 1 }?.size ?: 0
            val centerOffset = (viewHeight - itemHeight) / 2

            // Animate the scroll to the target item, applying the offset
            // We add 1 to the index to account for the initial Spacer item
            lazyListState.animateScrollToItem(index = currentLineIndex + 1, scrollOffset = -centerOffset)
        }
    }

    LazyColumn(
        state = lazyListState,
        userScrollEnabled = false,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .verticalFadingEdge()
            .fillMaxWidth()
    ) {
        // A dynamic spacer that takes up half the screen height
        // This allows the first few lines of the lyrics to be centered properly
        item {
            Spacer(modifier = Modifier.height(lazyListState.layoutInfo.viewportSize.height.dp / 2))
        }

        itemsIndexed(lyrics.toImmutableList()) { lineIndex, line ->
            val isActiveLine = lineIndex == currentLineIndex

            // Build an annotated string for the entire line to allow for text wrapping
            val annotatedString = remember(line, currentLineIndex, currentPosition) {
                buildAnnotatedString {
                    line.words.forEach { word ->
                        val isWordActive = currentPosition in word.startTimeMs..(word.startTimeMs + word.durationMs)

                        // Determine the color of each word based on its state
                        val targetColor = when {
                            // Any currently sung word in the active line
                            isActiveLine && isWordActive -> Color.White
                            // Words that have already been sung in the active line
                            isActiveLine && currentPosition > (word.startTimeMs + word.durationMs) -> Color.White.copy(alpha = 0.8f)
                            // Upcoming words in the active line
                            isActiveLine -> Color.White.copy(alpha = 0.5f)
                            // Words in any other line (past or future)
                            else -> colorPalette.textDisabled
                        }

                        withStyle(style = SpanStyle(color = targetColor)) {
                            append(word.text)
                        }
                    }
                }
            }

            BasicText(
                text = annotatedString,
                style = typography.xs.center.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp, horizontal = 32.dp)
            )
        }

        // A dynamic spacer for the bottom, allowing the last lines to be centered
        item {
            Spacer(modifier = Modifier.height(lazyListState.layoutInfo.viewportSize.height.dp / 2))
        }
    }
}
