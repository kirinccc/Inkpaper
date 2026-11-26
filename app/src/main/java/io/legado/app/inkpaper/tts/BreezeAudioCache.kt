package io.legado.app.inkpaper.tts

import splitties.init.appCtx
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

class BreezeAudioCache(
    private val cacheDirProvider: () -> File,
) {
    private val publishLock = Any()

    private fun dir(): File {
        val folder = cacheDirProvider()
        if (!folder.isDirectory) {
            folder.mkdirs()
        }
        return folder
    }

    fun cacheKey(
        modelId: String,
        voiceId: String,
        instruction: String,
        text: String,
    ): String {
        val raw = buildString {
            append(modelId).append('\u0000')
            append(voiceId).append('\u0000')
            append(instruction).append('\u0000')
            append(text)
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(32)
    }

    fun fileForKey(key: String): File = File(dir(), "$key.mp3")

    fun getReusableFile(key: String): File? {
        val file = fileForKey(key)
        if (file.isFile && file.length() > 0L) {
            file.setLastModified(System.currentTimeMillis())
            return file
        }
        return null
    }

    fun writeStream(
        key: String,
        input: InputStream,
        checkActive: () -> Unit,
    ): File {
        checkActive()
        getReusableFile(key)?.let { return it }
        val destination = fileForKey(key).absoluteFile
        val folder = destination.parentFile ?: throw IOException("Cache file has no parent")
        if (!folder.isDirectory && !folder.mkdirs() && !folder.isDirectory) {
            throw IOException("Cannot create audio cache directory")
        }
        val temporary = File.createTempFile("breeze-", ".part", folder)
        try {
            temporary.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    checkActive()
                    val count = input.read(buffer)
                    checkActive()
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
            }
            if (temporary.length() == 0L) {
                throw IOException("Breeze TTS server returned empty audio")
            }
            synchronized(publishLock) {
                checkActive()
                if (!destination.isFile || destination.length() == 0L) {
                    if (destination.exists() && !destination.delete()) {
                        throw IOException("Cannot replace invalid audio cache")
                    }
                    if (!temporary.renameTo(destination)) {
                        throw IOException("Cannot publish downloaded audio cache")
                    }
                }
            }
            return destination
        } finally {
            temporary.delete()
        }
    }

    fun getOrCreateSilenceFile(durationMs: Int, wavBytes: ByteArray): File {
        val file = File(dir(), "pause_${durationMs}.wav")
        if (!file.isFile || file.length() == 0L) {
            synchronized(publishLock) {
                if (!file.isFile || file.length() == 0L) {
                    file.writeBytes(wavBytes)
                }
            }
        }
        return file
    }

    fun trimToMaxBytes(maxBytes: Long = DEFAULT_MAX_CACHE_BYTES) {
        val folder = cacheDirProvider()
        if (!folder.isDirectory) return
        val files = folder.listFiles()
            ?.filter { it.isFile && (it.extension == "mp3" || it.extension == "wav") }
            ?: return
        var total = files.sumOf { it.length() }
        if (total <= maxBytes) return
        val sortedOldestFirst = files.sortedBy { it.lastModified() }
        for (file in sortedOldestFirst) {
            if (total <= maxBytes) break
            val len = file.length()
            if (file.delete()) {
                total -= len
            }
        }
    }

    fun stats(): Pair<Int, Long> {
        val folder = cacheDirProvider()
        if (!folder.isDirectory) return 0 to 0L
        val files = folder.listFiles()?.filter { it.isFile && it.extension == "mp3" } ?: emptyList()
        return files.size to files.sumOf { it.length() }
    }

    fun clearAll() {
        val folder = cacheDirProvider()
        if (!folder.isDirectory) return
        folder.listFiles()?.forEach { file ->
            if (file.isFile) file.delete()
        }
    }

    companion object {
        const val DEFAULT_MAX_CACHE_BYTES = 500L * 1024L * 1024L

        val instance: BreezeAudioCache by lazy {
            BreezeAudioCache { File(appCtx.filesDir, "breeze_audio_cache") }
        }
    }
}
