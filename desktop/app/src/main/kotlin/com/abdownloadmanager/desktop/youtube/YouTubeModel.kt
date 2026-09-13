package com.abdownloadmanager.desktop.youtube

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class YouTubeFormatOption(
    val formatId: String,
    val resolutionLabel: String, // e.g. "1080p (Full HD)", "720p (HD)", "Audio (MP3/M4A)"
    val extension: String,       // e.g. "mp4", "m4a"
    val videoUrl: String,
    val audioUrl: String? = null,
    val estimatedSizeBytes: Long = 0L,
    val isDASH: Boolean = false, // If true, requires ffmpeg muxing of videoUrl + audioUrl
    val isAudioOnly: Boolean = false,
)

data class YouTubeVideoInfo(
    val id: String,
    val title: String,
    val channel: String,
    val durationSeconds: Long,
    val thumbnailUrl: String?,
    val formats: List<YouTubeFormatOption>,
    val defaultFormat: YouTubeFormatOption?,
)

@Serializable
data class YtDlpRawFormat(
    @SerialName("format_id") val formatId: String = "",
    @SerialName("format_note") val formatNote: String? = null,
    @SerialName("ext") val ext: String = "",
    @SerialName("vcodec") val vcodec: String? = null,
    @SerialName("acodec") val acodec: String? = null,
    @SerialName("height") val height: Int? = null,
    @SerialName("width") val width: Int? = null,
    @SerialName("fps") val fps: Int? = null,
    @SerialName("filesize") val filesize: Long? = null,
    @SerialName("filesize_approx") val filesizeApprox: Long? = null,
    @SerialName("tbr") val tbr: Double? = null,
    @SerialName("url") val url: String = "",
)

@Serializable
data class YtDlpRawVideo(
    @SerialName("id") val id: String = "",
    @SerialName("title") val title: String = "",
    @SerialName("uploader") val uploader: String? = null,
    @SerialName("channel") val channel: String? = null,
    @SerialName("duration") val duration: Double? = null,
    @SerialName("thumbnail") val thumbnail: String? = null,
    @SerialName("formats") val formats: List<YtDlpRawFormat> = emptyList(),
)
