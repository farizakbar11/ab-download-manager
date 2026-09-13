package com.abdownloadmanager.desktop.youtube

import ir.amirab.util.logger.appLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStreamReader
import java.net.URI

object YouTubeVideoService {
    private val logger = appLogger.withTag("YouTubeVideoService")

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Checks whether the provided URL belongs to YouTube.
     */
    fun isYouTubeUrl(url: String): Boolean {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return false
        return try {
            val uri = URI(trimmed)
            val host = uri.host?.lowercase() ?: return false
            host == "youtube.com" ||
                    host.endsWith(".youtube.com") ||
                    host == "youtu.be"
        } catch (_: Exception) {
            trimmed.contains("youtube.com/") || trimmed.contains("youtu.be/")
        }
    }

    /**
     * Checks if yt-dlp executable is present in system PATH or current directory.
     */
    fun isYtDlpAvailable(): Boolean {
        return try {
            val process = ProcessBuilder("yt-dlp", "--version").start()
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Checks if ffmpeg executable is present in system PATH.
     */
    fun isFfmpegAvailable(): Boolean {
        return try {
            val process = ProcessBuilder("ffmpeg", "-version").start()
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Fetches video information and available formats for the given YouTube URL.
     */
    suspend fun resolveVideo(url: String): Result<YouTubeVideoInfo> = withContext(Dispatchers.IO) {
        runCatching {
            logger.d { "Resolving YouTube video info for: $url" }
            val pb = ProcessBuilder(
                "yt-dlp",
                "--dump-json",
                "--no-playlist",
                "--no-warnings",
                url
            )
            pb.redirectErrorStream(false)
            val process = pb.start()

            val stdoutReader = InputStreamReader(process.inputStream, Charsets.UTF_8)
            val jsonOutput = stdoutReader.readText()
            val stderr = process.errorStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            if (exitCode != 0 || jsonOutput.isBlank()) {
                val msg = stderr.ifBlank { "yt-dlp exited with code $exitCode" }
                logger.e { "Failed to resolve video: $msg" }
                error("Gagal membaca info video: $msg")
            }

            val raw = json.decodeFromString<YtDlpRawVideo>(jsonOutput)

            // Find best audio stream for DASH pairing (prefer m4a/aac)
            val audioStreams = raw.formats.filter {
                it.acodec != null && it.acodec != "none" && (it.vcodec == null || it.vcodec == "none")
            }
            val bestAudio = audioStreams.maxByOrNull { it.tbr ?: (it.filesize?.toDouble() ?: 0.0) }

            val options = mutableListOf<YouTubeFormatOption>()

            // Group video formats by height (e.g. 2160, 1440, 1080, 720, 480, 360)
            val videoStreams = raw.formats.filter {
                (it.height?.toInt() ?: 0) >= 240 && it.url.isNotBlank()
            }

            val heights = listOf(2160, 1440, 1080, 720, 480, 360)
            for (h in heights) {
                // Find matching video format, prefer mp4
                val matching = videoStreams
                    .filter { it.height?.toInt() == h }
                    .sortedWith(compareByDescending<YtDlpRawFormat> { it.ext == "mp4" }.thenByDescending { it.tbr ?: 0.0 })
                    .firstOrNull() ?: continue

                val isSeparateAudio = matching.acodec == null || matching.acodec == "none"
                val audioUrl = if (isSeparateAudio) bestAudio?.url else null
                val matchingSize = (matching.filesize ?: matching.filesizeApprox ?: 0.0).toLong()
                val audioSize = (bestAudio?.filesize ?: bestAudio?.filesizeApprox ?: 0.0).toLong()
                val totalSize = matchingSize + (if (isSeparateAudio) audioSize else 0L)

                val label = when (h) {
                    2160 -> "4K (2160p)"
                    1440 -> "2K (1440p)"
                    1080 -> "1080p (Full HD)"
                    720 -> "720p (HD)"
                    480 -> "480p (SD)"
                    else -> "${h}p"
                }

                options.add(
                    YouTubeFormatOption(
                        formatId = matching.formatId,
                        resolutionLabel = label,
                        extension = "mp4",
                        videoUrl = matching.url,
                        audioUrl = audioUrl,
                        estimatedSizeBytes = totalSize,
                        isDASH = isSeparateAudio,
                        isAudioOnly = false,
                    )
                )
            }

            // Audio only option
            if (bestAudio != null && bestAudio.url.isNotBlank()) {
                val audioSize = (bestAudio.filesize ?: bestAudio.filesizeApprox ?: 0.0).toLong()
                options.add(
                    YouTubeFormatOption(
                        formatId = bestAudio.formatId,
                        resolutionLabel = "Audio Only (${bestAudio.ext.uppercase()})",
                        extension = bestAudio.ext.ifBlank { "m4a" },
                        videoUrl = bestAudio.url,
                        audioUrl = null,
                        estimatedSizeBytes = audioSize,
                        isDASH = false,
                        isAudioOnly = true,
                    )
                )
            }

            val defaultOpt = options.firstOrNull { it.resolutionLabel.startsWith("720p") }
                ?: options.firstOrNull { it.resolutionLabel.startsWith("1080p") }
                ?: options.firstOrNull()

            YouTubeVideoInfo(
                id = raw.id,
                title = sanitizeFileName(raw.title),
                channel = raw.channel ?: raw.uploader ?: "YouTube",
                durationSeconds = raw.duration?.toLong() ?: 0L,
                thumbnailUrl = raw.thumbnail,
                formats = options,
                defaultFormat = defaultOpt,
            )
        }
    }

    /**
     * Muxes downloaded video and audio files into a single output file using ffmpeg.
     */
    suspend fun mergeVideoAndAudio(
        videoFile: File,
        audioFile: File,
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            logger.d { "Muxing video (${videoFile.name}) and audio (${audioFile.name}) into ${outputFile.name}" }
            val pb = ProcessBuilder(
                "ffmpeg",
                "-y",
                "-i", videoFile.absolutePath,
                "-i", audioFile.absolutePath,
                "-c", "copy",
                outputFile.absolutePath
            )
            pb.redirectErrorStream(true)
            val process = pb.start()
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                logger.e { "FFmpeg mux error: $output" }
                error("FFmpeg muxing gagal: $output")
            }
            outputFile
        }
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), " ").trim()
    }
}
