package com.abdownloadmanager.desktop.youtube

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abdownloadmanager.desktop.pages.addDownload.shared.DialogDropDown
import com.abdownloadmanager.shared.ui.widget.Text
import com.abdownloadmanager.shared.util.ui.WithContentAlpha
import com.abdownloadmanager.shared.util.ui.icon.MyIcons
import com.abdownloadmanager.shared.util.ui.myColors
import com.abdownloadmanager.shared.util.ui.theme.myShapes
import com.abdownloadmanager.shared.util.ui.theme.myTextSizes
import com.abdownloadmanager.shared.util.ui.widget.MyIcon
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.io.File

@Composable
fun YouTubeCatcherBanner(
    currentUrl: String,
    targetFolder: String = "",
    targetFileName: String = "",
    onFormatSelected: (format: YouTubeFormatOption, videoTitle: String) -> Unit,
    onDownloadReady: (((() -> Unit)?) -> Unit)? = null,
    onDownloadingStateChanged: ((Boolean) -> Unit)? = null,
    onCloseRequested: (() -> Unit)? = null,
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
    var downloadProgress by remember { mutableStateOf<YouTubeDownloadProgress?>(null) }
    var isDropdownOpen by remember { mutableStateOf(false) }

    // Auto-resolve when valid YouTube URL is detected
    LaunchedEffect(ytUrl) {
        isLoading = true
        errorMessage = null
        videoInfo = null
        selectedFormat = null
        downloadProgress = null
        onDownloadReady?.invoke(null)
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

    // Function to perform download
    val performDownload = remember(selectedFormat, videoInfo, targetFolder, targetFileName, ytUrl) {
        val fmt = selectedFormat
        val info = videoInfo
        if (fmt != null && info != null) {
            {
                scope.launch {
                    val destinationDir = if (targetFolder.isNotBlank()) File(targetFolder) else File(System.getProperty("user.home"), "Downloads")
                    val cleanTitle = info.title.replace(Regex("[\\\\/:*?\"<>|]"), " ").trim()
                    val resTag = fmt.resolutionLabel.split(" ").first()
                    val defaultFileName = "$cleanTitle [$resTag].${fmt.extension}"
                    val finalName = if (targetFileName.isNotBlank()) targetFileName else defaultFileName
                    val targetFile = File(destinationDir, finalName)

                    downloadProgress = YouTubeDownloadProgress(
                        isRunning = true,
                        statusText = "Menyiapkan download..."
                    )
                    onDownloadingStateChanged?.invoke(true)

                    val res = YouTubeVideoService.downloadVideoWithYtDlp(
                        videoUrl = ytUrl,
                        format = fmt,
                        targetFile = targetFile,
                        onProgress = { p ->
                            downloadProgress = p
                        }
                    )

                    onDownloadingStateChanged?.invoke(false)
                    res.onFailure { err ->
                        downloadProgress = YouTubeDownloadProgress(
                            isRunning = false,
                            isCompleted = false,
                            error = err.message ?: "Gagal mengunduh video"
                        )
                    }
                }
                Unit
            }
        } else {
            null
        }
    }

    LaunchedEffect(performDownload) {
        onDownloadReady?.invoke(performDownload)
    }

    val bannerShape = myShapes.defaultRounded
    val youtubeRed = Color(0xFFFF0000)
    val isDownloading = downloadProgress?.isRunning == true
    val isCompleted = downloadProgress?.isCompleted == true

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(bannerShape)
            .border(1.dp, youtubeRed.copy(alpha = 0.35f), bannerShape)
            .background(youtubeRed.copy(alpha = 0.06f))
            .padding(14.dp)
    ) {
        // Header Row: YouTube Catcher Badge & Channel
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(youtubeRed)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
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
            if (!isDownloading) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(myShapes.defaultRounded)
                        .clickable(enabled = !isLoading) {
                            scope.launch {
                                isLoading = true
                                errorMessage = null
                                downloadProgress = null
                                onDownloadReady?.invoke(null)
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
        }

        // Video Title & Format Selector
        videoInfo?.let { info ->
            Spacer(Modifier.height(10.dp))

            Text(
                text = info.title,
                fontSize = myTextSizes.base,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(10.dp))

            // Resolution Dropdown Selector
            Text(
                text = "Pilih Kualitas Video:",
                fontSize = myTextSizes.sm,
                fontWeight = FontWeight.Medium,
            )

            Spacer(Modifier.height(6.dp))

            DialogDropDown(
                selectedItem = selectedFormat,
                possibleItems = info.formats,
                onItemSelected = { fmt ->
                    selectedFormat = fmt
                    onFormatSelected(fmt, info.title)
                },
                enabled = !isDownloading,
                renderItem = { fmt ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = fmt.resolutionLabel,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = myColors.onBackground,
                        )
                        if (fmt.estimatedSizeBytes > 0) {
                            Spacer(Modifier.width(8.dp))
                            WithContentAlpha(0.6f) {
                                Text(
                                    text = "• ${formatByteSize(fmt.estimatedSizeBytes)}",
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }
                },
                dropdownOpen = isDropdownOpen,
                onRequestCloseDropDown = { isDropdownOpen = false },
                onRequestOpenDropDown = { isDropdownOpen = true },
                renderEmpty = {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text("Tidak ada format")
                    }
                },
                dropDownSize = DpSize(340.dp, 250.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            // Progress / Status UI (Only active when downloading or completed)
            if (isDownloading) {
                Spacer(Modifier.height(12.dp))
                val currentProgress = downloadProgress
                val animatedProgress by animateFloatAsState(
                    targetValue = ((currentProgress?.percent ?: 0f) / 100f).coerceIn(0f, 1f)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(myColors.surface)
                        .padding(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = currentProgress?.statusText?.ifBlank { "Mengunduh..." } ?: "Mengunduh...",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = youtubeRed,
                            modifier = Modifier.weight(1f)
                        )

                        Text(
                            text = String.format("%.1f%%", currentProgress?.percent ?: 0f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = myColors.onBackground
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    // Progress Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(myColors.onBackground.copy(alpha = 0.12f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(animatedProgress)
                                .background(youtubeRed)
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val stats = buildString {
                            if (!currentProgress?.sizeStr.isNullOrBlank()) append(currentProgress?.sizeStr)
                            if (!currentProgress?.speedStr.isNullOrBlank()) {
                                if (isNotEmpty()) append(" • ")
                                append(currentProgress?.speedStr)
                            }
                            if (!currentProgress?.etaStr.isNullOrBlank() && currentProgress?.etaStr != "Unknown") {
                                if (isNotEmpty()) append(" • ETA ")
                                append(currentProgress?.etaStr)
                            }
                        }

                        Text(
                            text = stats,
                            fontSize = 11.sp,
                            color = myColors.onBackground.copy(alpha = 0.7f),
                            modifier = Modifier.weight(1f)
                        )

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .border(1.dp, myColors.error.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                .clickable {
                                    YouTubeVideoService.cancelActiveDownload()
                                    downloadProgress = null
                                    onDownloadingStateChanged?.invoke(false)
                                }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Batal",
                                color = myColors.error,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            } else if (isCompleted) {
                Spacer(Modifier.height(12.dp))
                // Completed UI
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(myColors.surface)
                        .padding(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(myColors.success)
                        ) {
                            MyIcon(
                                icon = MyIcons.check,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = Color.White
                            )
                        }

                        Spacer(Modifier.width(8.dp))

                        Text(
                            text = "Download Selesai 100%!",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = myColors.success,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    downloadProgress?.outputFile?.let { file ->
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "${file.name} (${formatByteSize(file.length())})",
                            fontSize = 12.sp,
                            color = myColors.onBackground.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        downloadProgress?.outputFile?.let { file ->
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(youtubeRed)
                                    .clickable {
                                        openFileSafe(file)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    MyIcon(
                                        icon = MyIcons.fileOpen,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = Color.White
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "Buka File",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .border(1.dp, myColors.onBackground.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                                    .clickable {
                                        openFolderSafe(file)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    MyIcon(
                                        icon = MyIcons.folderOpen,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = myColors.onBackground
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "Buka Folder",
                                        color = myColors.onBackground,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.weight(1f))

                        onCloseRequested?.let { close ->
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { close() }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "Tutup",
                                    color = myColors.onBackground.copy(alpha = 0.7f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            } else {
                downloadProgress?.error?.let { err ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = err,
                        color = myColors.error,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

private fun openFileSafe(file: File) {
    try {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            Desktop.getDesktop().open(file)
        } else {
            ProcessBuilder("cmd", "/c", "start", "", file.absolutePath).start()
        }
    } catch (_: Exception) {
        try {
            ProcessBuilder("cmd", "/c", "start", "", file.absolutePath).start()
        } catch (_: Exception) {}
    }
}

private fun openFolderSafe(file: File) {
    try {
        ProcessBuilder("explorer.exe", "/select,", file.absolutePath).start()
    } catch (_: Exception) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(file.parentFile)
            }
        } catch (_: Exception) {}
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
