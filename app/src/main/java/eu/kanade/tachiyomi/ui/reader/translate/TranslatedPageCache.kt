package eu.kanade.tachiyomi.ui.reader.translate

import java.io.File
import java.security.MessageDigest

/**
 * Disk cache for translated page images, independent of the chapter cache that holds the originals.
 * Entries are named by [key]; the least recently used files are deleted when [maxBytes] is exceeded.
 */
class TranslatedPageCache(
    private val directory: File,
    private val maxBytes: () -> Long,
) {

    fun get(key: String): ByteArray? {
        val file = File(directory, key)
        if (!file.isFile) return null
        return try {
            file.setLastModified(System.currentTimeMillis())
            file.readBytes()
        } catch (_: Exception) {
            null
        }
    }

    fun put(key: String, bytes: ByteArray) {
        directory.mkdirs()
        val tmp = File(directory, "$key.tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(File(directory, key))) tmp.delete()
        trim()
    }

    fun sizeBytes(): Long = directory.listFiles()?.sumOf { it.length() } ?: 0L

    fun clear() {
        directory.listFiles()?.forEach { it.delete() }
    }

    private fun trim() {
        val limit = maxBytes()
        val files = directory.listFiles()?.filter { it.isFile }?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        for (file in files) {
            if (total <= limit) break
            total -= file.length()
            file.delete()
        }
    }

    companion object {
        /**
         * Identity of a translated page: image content, OCR engine and version, languages,
         * provider and its non-secret settings, and the renderer settings and version.
         */
        fun key(
            imageHash: String,
            ocrId: String,
            source: String,
            target: String,
            providerSignature: String,
            renderVersion: Int,
            style: RenderStyle,
        ): String = sha256(
            listOf(
                imageHash,
                ocrId,
                source,
                target,
                providerSignature,
                "r$renderVersion",
                "fs${style.fontScale}",
                style.textColor.id,
            ).joinToString("\n"),
        )

        fun sha256(text: String): String = hex(MessageDigest.getInstance("SHA-256").digest(text.toByteArray()))

        fun sha256(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(bytes))

        private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
    }
}
