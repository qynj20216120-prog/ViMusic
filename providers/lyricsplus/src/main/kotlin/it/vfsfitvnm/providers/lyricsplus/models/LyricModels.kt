package it.vfsfitvnm.providers.lyricsplus.models

data class LyricWord(
    val text: String,
    val startTimeMs: Long,
    val durationMs: Long
)

data class LyricLine(
    val fullText: String,
    val startTimeMs: Long,
    val durationMs: Long,
    val words: List<LyricWord>
)
