package com.cyopstd.game

import com.cyopstd.game.audio.ChiptuneComposer
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import org.junit.Test

/** Not an assertion: the audio a human listens to. */
class RenderTracks {
    @Test
    fun render() {
        File("build/previews").mkdirs()
        for (track in ChiptuneComposer.Track.entries) {
            val file = File("build/previews/${track.name.lowercase()}.wav")
            BufferedOutputStream(FileOutputStream(file), 1 shl 16).use {
                ChiptuneComposer.writeWav(it, track)
            }
            println(
                "RENDER ${track.name} ${ChiptuneComposer.trackSeconds(track)}s " +
                    "${file.length()} bytes bpm=${track.bpm}"
            )
        }
    }
}
