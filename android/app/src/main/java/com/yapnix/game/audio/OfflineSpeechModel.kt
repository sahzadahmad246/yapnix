package com.yapnix.game.audio

import android.content.Context
import org.vosk.Model
import java.io.File
import java.util.zip.ZipInputStream

object OfflineSpeechModel {
    @Volatile private var loaded: Model? = null
    @Synchronized fun load(context: Context): Model {
        loaded?.let { return it }
        val root = File(context.noBackupFilesDir, "speech-en-us-0.15")
        val complete = File(root, ".complete")
        if (!complete.exists()) {
            root.deleteRecursively(); check(root.mkdirs())
            ZipInputStream(context.assets.open("speech-model.zip")).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val relative = entry.name.substringAfter('/', "")
                    if (relative.isEmpty()) continue
                    val destination = File(root, relative).canonicalFile
                    require(destination.path.startsWith(root.canonicalPath + File.separator))
                    if (entry.isDirectory) destination.mkdirs() else {
                        destination.parentFile?.mkdirs()
                        destination.outputStream().use { zip.copyTo(it) }
                    }
                }
            }
            complete.writeText("vosk-model-small-en-us-0.15")
        }
        return Model(root.absolutePath).also { loaded = it }
    }
}
