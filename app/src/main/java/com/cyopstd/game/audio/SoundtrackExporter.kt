package com.cyopstd.game.audio

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.util.Log

/**
 * Saves the bought soundtrack (`Sku.SOUNDTRACK`) to the phone's Music folder,
 * under "Music/CyOps TD", as "CyOps TD - Level X (Y).mp3" (owner,
 * 2026-10-01). The files already carry that title, the album "CyOps TD
 * Soundtrack" and their track numbers, so any music app lists them properly.
 *
 * Android 10 and newer only: from there an app may add to the shared Music
 * collection with no storage permission at all. Supporting older phones would
 * mean asking every player for WRITE_EXTERNAL_STORAGE, for one feature.
 */
class SoundtrackExporter(private val context: Context) {

    sealed interface Result {
        /** [saved] new files; [alreadyThere] were kept as they were. */
        data class Done(val saved: Int, val alreadyThere: Int) : Result
        object NeedsNewerAndroid : Result
        data class Failed(val saved: Int) : Result
    }

    /** Blocking file work: call it off the main thread. */
    fun saveAll(): Result {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return Result.NeedsNewerAndroid
        val resolver = context.contentResolver
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        var saved = 0
        var already = 0
        for (track in MusicLibrary.tracks) {
            try {
                if (exists(track.fileName)) {
                    already++
                    continue
                }
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, track.fileName)
                    put(MediaStore.Audio.Media.TITLE, track.title)
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
                    put(MediaStore.Audio.Media.RELATIVE_PATH, RELATIVE_PATH)
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(collection, values) ?: return Result.Failed(saved)
                resolver.openOutputStream(uri)?.use { out ->
                    context.resources.openRawResource(track.resId).use { it.copyTo(out) }
                } ?: return Result.Failed(saved)
                values.clear()
                values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                saved++
            } catch (error: Exception) {
                Log.w(TAG, "Could not save ${track.fileName}", error)
                return Result.Failed(saved)
            }
        }
        return Result.Done(saved, already)
    }

    private fun exists(name: String): Boolean {
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        return context.contentResolver.query(
            collection,
            arrayOf(MediaStore.Audio.Media._ID),
            "${MediaStore.Audio.Media.DISPLAY_NAME}=? AND ${MediaStore.Audio.Media.RELATIVE_PATH}=?",
            arrayOf(name, RELATIVE_PATH),
            null
        )?.use { it.count > 0 } ?: false
    }

    companion object {
        const val RELATIVE_PATH = "Music/CyOps TD/"
        private const val TAG = "CyOpsSoundtrack"
    }
}
