package az.gesture.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import az.gesture.app.R
import az.gesture.app.data.Repo
import az.gesture.app.update.AppReleaseInfo
import az.gesture.app.update.AppUpdateChecker
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

private enum class UpdatePromptStep {
    MAIN,
    CONFIRM_DISMISS
}

private sealed interface UpdateDownloadState {
    data object Idle : UpdateDownloadState
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long) : UpdateDownloadState
    data class NeedInstallPermission(val apkFile: File) : UpdateDownloadState
    data object Error : UpdateDownloadState
}

@Composable
fun UpdateDialogHost(
    repo: Repo,
    release: AppReleaseInfo,
    currentVersion: String,
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var step by remember(release.versionName) { mutableStateOf(UpdatePromptStep.MAIN) }
    var dontShowAgainChecked by remember(release.versionName) { mutableStateOf(false) }
    var downloadState by remember(release.versionName) {
        mutableStateOf<UpdateDownloadState>(UpdateDownloadState.Idle)
    }

    DisposableEffect(lifecycleOwner, downloadState) {
        val observer = LifecycleEventObserver { _, event ->
            val currentState = downloadState
            if (event == Lifecycle.Event.ON_RESUME &&
                currentState is UpdateDownloadState.NeedInstallPermission &&
                AppUpdateChecker.canInstallPackages(context)
            ) {
                AppUpdateChecker.launchApkInstall(context, currentState.apkFile)
                onFinished()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun startDownload() {
        if (downloadState is UpdateDownloadState.Downloading) return
        downloadState = UpdateDownloadState.Downloading(0L, release.apkSizeBytes)
        scope.launch {
            try {
                val apkFile = AppUpdateChecker.downloadApk(context, release) { downloaded, total ->
                    downloadState = UpdateDownloadState.Downloading(downloaded, total)
                }
                if (AppUpdateChecker.canInstallPackages(context)) {
                    AppUpdateChecker.launchApkInstall(context, apkFile)
                    onFinished()
                } else {
                    downloadState = UpdateDownloadState.NeedInstallPermission(apkFile)
                }
            } catch (_: Throwable) {
                downloadState = UpdateDownloadState.Error
            }
        }
    }

    when (step) {
        UpdatePromptStep.MAIN -> {
            val isDownloading = downloadState is UpdateDownloadState.Downloading
            val needPermission = downloadState is UpdateDownloadState.NeedInstallPermission

            AlertDialog(
                onDismissRequest = {
                    if (!isDownloading) {
                        dontShowAgainChecked = false
                        step = UpdatePromptStep.CONFIRM_DISMISS
                    }
                },
                shape = RoundedCornerShape(26.dp),
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.update_available_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "v$currentVersion → v${release.versionName}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.update_whats_new),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 64.dp, max = 210.dp)
                        ) {
                            val notesText = release.releaseNotes.ifBlank {
                                stringResource(R.string.update_no_notes)
                            }
                            Text(
                                text = notesText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .verticalScroll(rememberScrollState())
                                    .padding(14.dp)
                            )
                        }

                        when (val st = downloadState) {
                            is UpdateDownloadState.Downloading -> {
                                Spacer(Modifier.height(2.dp))
                                if (st.totalBytes > 0L) {
                                    val progress = (st.downloadedBytes.toFloat() / st.totalBytes.toFloat())
                                        .coerceIn(0f, 1f)
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    val dlMb = String.format(Locale.US, "%.1f", st.downloadedBytes / (1024f * 1024f))
                                    val totMb = String.format(Locale.US, "%.1f", st.totalBytes / (1024f * 1024f))
                                    val pct = (progress * 100f).toInt()
                                    Text(
                                        text = "${stringResource(R.string.update_downloading)} $pct% ($dlMb / $totMb MB)",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                    Text(
                                        text = stringResource(R.string.update_downloading),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            is UpdateDownloadState.NeedInstallPermission -> {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f))
                                ) {
                                    Text(
                                        text = stringResource(R.string.update_install_permission_hint),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                            is UpdateDownloadState.Error -> {
                                Text(
                                    text = stringResource(R.string.update_download_failed),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            UpdateDownloadState.Idle -> Unit
                        }
                    }
                },
                dismissButton = {
                    if (!isDownloading) {
                        TextButton(
                            onClick = {
                                dontShowAgainChecked = false
                                step = UpdatePromptStep.CONFIRM_DISMISS
                            }
                        ) {
                            Text(stringResource(R.string.update_dismiss))
                        }
                    }
                },
                confirmButton = {
                    when {
                        isDownloading -> Unit
                        needPermission -> {
                            Button(
                                onClick = { AppUpdateChecker.openInstallPermissionSettings(context) },
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Text(stringResource(R.string.update_open_install_settings))
                            }
                        }
                        else -> {
                            Button(
                                onClick = { startDownload() },
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Text(
                                    stringResource(
                                        if (downloadState is UpdateDownloadState.Error) {
                                            R.string.update_retry
                                        } else {
                                            R.string.update_download
                                        }
                                    )
                                )
                            }
                        }
                    }
                }
            )
        }

        UpdatePromptStep.CONFIRM_DISMISS -> {
            AlertDialog(
                onDismissRequest = { step = UpdatePromptStep.MAIN },
                shape = RoundedCornerShape(26.dp),
                title = {
                    Text(
                        text = stringResource(R.string.update_dismiss_confirm_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = stringResource(R.string.update_dismiss_confirm_message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(0.7.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { dontShowAgainChecked = !dontShowAgainChecked }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = dontShowAgainChecked,
                                    onCheckedChange = { dontShowAgainChecked = it }
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.update_dont_show_again),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { step = UpdatePromptStep.MAIN }) {
                        Text(stringResource(R.string.update_back))
                    }
                },
                confirmButton = {
                    FilledTonalButton(
                        onClick = {
                            if (dontShowAgainChecked) {
                                repo.setSkippedUpdateVersion(release.versionName)
                            }
                            onFinished()
                        },
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(stringResource(R.string.update_dismiss))
                    }
                }
            )
        }
    }
}
