package it.vfsfitvnm.vimusic.features.import

import java.io.InputStream

class CsvPlaylistParser {
    fun getHeader(inputStream: InputStream): List<String> {
        return inputStream.bufferedReader().useLines { lines ->
            lines.firstOrNull()?.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)".toRegex())
                ?.map { it.trim().removeSurrounding("\"") } ?: emptyList()
        }
    }

    fun parse(
        inputStream: InputStream,
        titleColumnIndex: Int,
        artistColumnIndex: Int
    ): List<SongImportInfo> {
        val songList = mutableListOf<SongImportInfo>()
        inputStream.bufferedReader().useLines { lines ->
            lines.drop(1).forEach { line ->
                val columns = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)".toRegex())
                    .map { it.trim().removeSurrounding("\"") }

                if (columns.size > titleColumnIndex && columns.size > artistColumnIndex) {
                    val title = columns[titleColumnIndex]
                    val artist = columns[artistColumnIndex]
                    if (title.isNotBlank() && artist.isNotBlank()) {
                        songList.add(SongImportInfo(title = title, artist = artist))
                    }
                }
            }
        }
        return songList
    }
}
