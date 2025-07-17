package it.vfsfitvnm.vimusic.ui.screens.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import it.vfsfitvnm.core.ui.LocalAppearance
import it.vfsfitvnm.providers.lyricsplus.LyricsPlusSyncManager
import it.vfsfitvnm.providers.lyricsplus.models.LyricLine
import it.vfsfitvnm.vimusic.ui.modifiers.verticalFadingEdge
import it.vfsfitvnm.vimusic.utils.center
import it.vfsfitvnm.vimusic.utils.medium
import kotlinx.coroutines.launch

@Composable
fun WordSyncedLyrics(
    manager: LyricsPlusSyncManager,
    modifier: Modifier = Modifier
) {
    val (colorPalette, typography) = LocalAppearance.current
    val currentLineIndex by manager.currentLineIndex.collectAsState()
    val currentWordIndex by manager.currentWordIndex.collectAsState()

    val lyrics = manager.getLyrics()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(currentLineIndex) {
        if (currentLineIndex >= 0) {
            coroutineScope.launch {
                // Add padding items count to the actual index
                val targetIndex = currentLineIndex + 1 // +1 for header spacer

                // Get viewport height
                val viewportHeight = listState.layoutInfo.viewportSize.height

                // Calculate proper offset to center the item
                val offset = -(viewportHeight / 2)

                // Use animateScrollToItem with proper offset handling
                listState.animateScrollToItem(
                    index = targetIndex,
                    scrollOffset = offset
                )
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .verticalFadingEdge()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Add substantial padding at the top to allow centering of first items
        item(key = "header", contentType = 0) {
            Spacer(modifier = Modifier.height(300.dp)) // Increased height for proper centering
        }

        items(lyrics) { line ->
            val lineIndex = lyrics.indexOf(line)
            val isActiveLine = lineIndex == currentLineIndex
            val wordIndex = if (isActiveLine) currentWordIndex else -1

            WordSyncedLyricsLine(
                line = line,
                isActive = isActiveLine,
                currentWordIndex = wordIndex
            )
        }

        // Add substantial padding at the bottom to allow centering of last items
        item(key = "footer", contentType = 0) {
            Spacer(modifier = Modifier.height(300.dp)) // Increased height for proper centering
        }
    }
}

@Composable
private fun WordSyncedLyricsLine(
    line: LyricLine,
    isActive: Boolean,
    currentWordIndex: Int
) {
    val (colorPalette, typography) = LocalAppearance.current

    val annotated = buildAnnotatedString {
        line.words.forEachIndexed { index, word ->
            val isActiveWord = index == currentWordIndex && isActive
            val color by animateColorAsState(
                targetValue = if (isActiveWord || (isActive && currentWordIndex == -1)) Color.White
                else colorPalette.textDisabled,
                label = "word_color"
            )

            val weight = when {
                isActiveWord -> FontWeight.Bold
                isActive -> FontWeight.Medium
                else -> FontWeight.Normal
            }

            withStyle(style = SpanStyle(color = color, fontWeight = weight)) {
                append(word.text)
                if (index < line.words.lastIndex) append(" ") // Added space between words
            }
        }
    }

    BasicText(
        text = annotated,
        style = typography.xs.center.medium,
        modifier = Modifier.fillMaxWidth()
            .padding(vertical = 4.dp)
    )
}
