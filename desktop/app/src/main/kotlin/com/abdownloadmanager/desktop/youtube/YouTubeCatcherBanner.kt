package com.abdownloadmanager.desktop.youtube

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abdownloadmanager.shared.ui.widget.Text
import com.abdownloadmanager.shared.util.ui.WithContentAlpha
import com.abdownloadmanager.shared.util.ui.WithContentColor
import com.abdownloadmanager.shared.util.ui.icon.MyIcons
import com.abdownloadmanager.shared.util.ui.myColors
import com.abdownloadmanager.shared.util.ui.theme.myShapes
import com.abdownloadmanager.shared.util.ui.theme.myTextSizes
import com.abdownloadmanager.shared.util.ui.widget.MyIcon
import kotlinx.coroutines.launch

@Composable
fun YouTubeCatcherBanner(
    currentUrl: String,
    onFormatSelected: (format: YouTubeFormatOption, videoTitle: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var activeYouTubeUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(currentUrl) {
        if (YouTubeVideoService.isYouTubeUrl(currentUrl)) {
            activeYouTubeUrl = currentUrl
        }
    }

    val ytUrl = activeYouTubeUrl ?: if (YouTubeVideoService.isYouTubeUrl(currentUrl)) currentUrl else null
    if (ytUrl == null) return

    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var videoInfo by remember { mutableStateOf<YouTubeVideoInfo?>(null) }
    var selectedFormat by remember { mutableStateOf<YouTubeFormatOption?>(null) }

    // Auto-resolve when valid YouTube URL is detected
    LaunchedEffect(ytUrl) {
        isLoading = true
        errorMessage = null
        videoInfo = null
        selectedFormat = null
        val result = YouTubeVideoService.resolveVideo(ytUrl)
        isLoading = false
        result.onSuccess { info ->
            videoInfo = info
            val defaultFmt = info.defaultFormat
            selectedFormat = defaultFmt
            if (defaultFmt != null) {
                onFormatSelected(defaultFmt, info.title)
            }
        }.onFailure { err ->
            errorMessage = err.message ?: "Gagal mengambil info YouTube"
        }
    }

    val bannerShape = myShapes.defaultRounded
    val youtubeRed = Color(0xFFFF0000)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(bannerShape)
            .border(1.dp, youtubeRed.copy(alpha = 0.35f), bannerShape)
            .background(youtubeRed.copy(alpha = 0.06f))
            .padding(12.dp)
    ) {
        // Header Row: YouTube Catcher Badge & Status
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // YouTube Icon Badge
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(youtubeRed)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "YouTube Catcher",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.width(8.dp))

            if (isLoading) {
                WithContentAlpha(0.7f) {
                    Text(
                        text = "Mendeteksi format video...",
                        fontSize = myTextSizes.sm,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else if (videoInfo != null) {
                Text(
                    text = videoInfo!!.channel,
                    fontSize = myTextSizes.sm,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            } else if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = myColors.error,
                    fontSize = myTextSizes.sm,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Spacer(Modifier.weight(1f))
            }

            // Retry / Refresh button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(myShapes.defaultRounded)
                    .clickable(enabled = !isLoading) {
                        scope.launch {
                            isLoading = true
                            errorMessage = null
                            val res = YouTubeVideoService.resolveVideo(ytUrl)
                            isLoading = false
                            res.onSuccess { info ->
                                videoInfo = info
                                val defaultFmt = info.defaultFormat
                                selectedFormat = defaultFmt
                                if (defaultFmt != null) {
                                    onFormatSelected(defaultFmt, info.title)
                                }
                            }.onFailure { err ->
                                errorMessage = err.message
                            }
                        }
                    }
                    .padding(4.dp)
            ) {
                MyIcon(
                    icon = MyIcons.refresh,
                    contentDescription = "Refresh YouTube",
                    modifier = Modifier.size(16.dp),
                    tint = myColors.onBackground
                )
            }
        }

        // Video Title & Format Selector
        videoInfo?.let { info ->
            Spacer(Modifier.height(8.dp))

            Text(
                text = info.title,
                fontSize = myTextSizes.base,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(8.dp))

            // Formats Chips Row
            Text(
                text = "Pilih Resolusi / Kualitas:",
                fontSize = myTextSizes.sm,
                fontWeight = FontWeight.Medium,
            )

            Spacer(Modifier.height(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                for (fmt in info.formats) {
                    val isSelected = selectedFormat == fmt
                    val chipShape = RoundedCornerShape(6.dp)

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .clip(chipShape)
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) youtubeRed else myColors.onBackground.copy(alpha = 0.2f),
                                shape = chipShape
                            )
                            .background(
                                if (isSelected) youtubeRed.copy(alpha = 0.15f) else Color.Transparent
                            )
                            .clickable {
                                selectedFormat = fmt
                                onFormatSelected(fmt, info.title)
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = fmt.resolutionLabel,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) youtubeRed else myColors.onBackground
                            )
                            if (fmt.estimatedSizeBytes > 0) {
                                Spacer(Modifier.width(4.dp))
                                WithContentAlpha(0.6f) {
                                    Text(
                                        text = "(${formatByteSize(fmt.estimatedSizeBytes)})",
                                        fontSize = 10.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatByteSize(bytes: Long): String {
    if (bytes <= 0) return ""
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format("%.1f GB", gb)
        mb >= 1.0 -> String.format("%.1f MB", mb)
        kb >= 1.0 -> String.format("%.0f KB", kb)
        else -> "$bytes B"
    }
}
