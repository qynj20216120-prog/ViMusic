package it.vfsfitvnm.vimusic.ui.screens.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.vfsfitvnm.providers.lyricsplus.LyricsPlusSyncManager
import it.vfsfitvnm.providers.lyricsplus.models.LyricLine
import kotlinx.coroutines.launch

@Composable
fun WordSyncedLyrics(
    manager: LyricsPlusSyncManager,
    modifier: Modifier = Modifier,
    activeLineColor: Color = MaterialTheme.colorScheme.primary,
    activeWordColor: Color = MaterialTheme.colorScheme.secondary,
    inactiveColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
    fontSize: Int = 16
) {
    val currentLineIndex by manager.currentLineIndex.collectAsState()
    val currentWordIndex by manager.currentWordIndex.collectAsState()
    val currentPosition by manager.currentPosition.collectAsState()

    val lyrics = manager.getLyrics()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(currentLineIndex) {
        if (currentLineIndex >= 0) {
            coroutineScope.launch {
                listState.animateScrollToItem(currentLineIndex)
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        items(lyrics) { line ->
            val lineIndex = lyrics.indexOf(line)
            val isActiveLine = lineIndex == currentLineIndex
            val wordIndex = if (isActiveLine) currentWordIndex else -1

            WordSyncedLyricsLine(
                line = line,
                isActive = isActiveLine,
                currentWordIndex = wordIndex,
                activeLineColor = activeLineColor,
                activeWordColor = activeWordColor,
                inactiveColor = inactiveColor,
                fontSize = fontSize
            )
        }
    }
}

@Composable
private fun WordSyncedLyricsLine(
    line: LyricLine,
    isActive: Boolean,
    currentWordIndex: Int,
    activeLineColor: Color,
    activeWordColor: Color,
    inactiveColor: Color,
    fontSize: Int
) {
    val annotated = buildAnnotatedString {
        line.words.forEachIndexed { index, word ->
            val color = when {
                index == currentWordIndex -> activeWordColor
                isActive -> activeLineColor
                else -> inactiveColor
            }

            val weight = when {
                index == currentWordIndex -> FontWeight.Bold
                isActive -> FontWeight.Medium
                else -> FontWeight.Normal
            }

            withStyle(style = SpanStyle(color = color, fontWeight = weight)) {
                append(word.text)
                append(" ")
            }
        }
    }

    Text(
        text = annotated,
        fontSize = fontSize.sp,
        modifier = Modifier.fillMaxWidth()
    )
}
