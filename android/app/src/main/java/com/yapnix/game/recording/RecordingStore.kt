package com.yapnix.game.recording

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

class RecordingStore(private val context: Context) {
    private val directory = File(context.filesDir, "recordings").apply { mkdirs() }
    fun newFile(): File = File(directory, "Yapnix-${System.currentTimeMillis()}-${UUID.randomUUID()}.pending.mp4")
    fun finish(file: File): Boolean = checked(file).renameTo(File(directory, file.name.removeSuffix(".pending.mp4") + ".mp4"))
    fun list(): List<File> = directory.listFiles()?.filter { it.extension == "mp4" && !it.name.endsWith(".pending.mp4") && it.length() > 0 }
        ?.sortedByDescending { it.lastModified() }.orEmpty()
    fun uri(file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.recordings", checked(file))
    fun delete(file: File): Boolean = checked(file).delete()
    fun export(file: File, destination: Uri) {
        val source = checked(file)
        val output = context.contentResolver.openOutputStream(destination) ?: error("Could not open the selected destination")
        output.use { stream -> source.inputStream().use { it.copyTo(stream) } }
    }
    private fun checked(file: File): File {
        require(file.canonicalFile.parentFile == directory.canonicalFile && file.extension == "mp4") { "Invalid recording" }
        return file
    }
}
