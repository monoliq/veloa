package az.gesture.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class AppReleaseInfo(
    val versionName: String,
    val tagName: String,
    val releaseNotes: String,
    val apkDownloadUrl: String,
    val apkSizeBytes: Long
)

object AppUpdateChecker {

    const val LATEST_RELEASE_API_URL =
        "https://api.github.com/repos/monoliq/veloa/releases/latest"

    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 8_000
    private const val DOWNLOAD_TIMEOUT_MS = 20_000
    private const val MAX_REDIRECTS = 5

    fun normalizeVersion(raw: String): String {
        return raw.trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore('-')
            .substringBefore('+')
            .trim()
    }

    fun parseSemVer(raw: String): List<Int> {
        val norm = normalizeVersion(raw)
        if (norm.isEmpty()) return emptyList()
        return norm.split('.').map { part ->
            part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
        }
    }

    fun isNewerVersion(remoteVersion: String, currentVersion: String): Boolean {
        val remoteParts = parseSemVer(remoteVersion)
        val currentParts = parseSemVer(currentVersion)
        if (remoteParts.isEmpty() || currentParts.isEmpty()) return false
        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r != c) return r > c
        }
        return false
    }

    fun hasPendingUpdate(remoteVersion: String?, currentVersion: String): Boolean {
        if (remoteVersion.isNullOrBlank()) return false
        return isNewerVersion(remoteVersion, currentVersion)
    }

    fun shouldPromptUpdate(
        remoteVersion: String,
        currentVersion: String,
        skippedVersion: String?
    ): Boolean {
        if (!isNewerVersion(remoteVersion, currentVersion)) return false
        if (skippedVersion.isNullOrBlank()) return true
        return normalizeVersion(skippedVersion) != normalizeVersion(remoteVersion)
    }

    fun formatReleaseNotes(rawMarkdown: String): String {
        if (rawMarkdown.isBlank()) return ""
        val lines = rawMarkdown.replace("\r\n", "\n").lines()
        val kept = mutableListOf<String>()
        for (rawLine in lines) {
            val trimmed = rawLine.trim()
            if (trimmed.matches(Regex("^#{1,6}\\s*Downloads\\b.*", RegexOption.IGNORE_CASE))) {
                break
            }
            if (trimmed.matches(Regex("^#{1,3}\\s*Veloa\\s+v?[0-9].*", RegexOption.IGNORE_CASE))) {
                continue
            }
            if (trimmed == "---" || trimmed == "***") {
                continue
            }
            var cleaned = trimmed
                .replace(Regex("^#{1,6}\\s*"), "")
                .replace("**", "")
                .replace("__", "")
                .replace("`", "")
            cleaned = when {
                cleaned.startsWith("- ") -> "• " + cleaned.removePrefix("- ").trim()
                cleaned.startsWith("* ") -> "• " + cleaned.removePrefix("* ").trim()
                else -> cleaned
            }
            if (cleaned.isEmpty()) {
                if (kept.isNotEmpty() && kept.last().isNotEmpty()) {
                    kept.add("")
                }
            } else {
                kept.add(cleaned)
            }
        }
        while (kept.isNotEmpty() && kept.last().isEmpty()) {
            kept.removeAt(kept.lastIndex)
        }
        return kept.joinToString("\n")
    }

    fun parseLatestReleaseJson(json: String): AppReleaseInfo? {
        return try {
            val obj = JSONObject(json)
            if (obj.optBoolean("draft", false) || obj.optBoolean("prerelease", false)) {
                return null
            }
            val tag = obj.optString("tag_name", "").trim()
            val version = normalizeVersion(tag)
            if (version.isEmpty()) return null

            val assets = obj.optJSONArray("assets") ?: return null
            var apkUrl = ""
            var apkSize = 0L
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name", "")
                val url = asset.optString("browser_download_url", "")
                if (name.endsWith(".apk", ignoreCase = true) && url.startsWith("https://")) {
                    apkUrl = url
                    apkSize = asset.optLong("size", 0L)
                    break
                }
            }
            if (apkUrl.isEmpty()) return null

            val body = formatReleaseNotes(obj.optString("body", ""))
            AppReleaseInfo(
                versionName = version,
                tagName = tag,
                releaseNotes = body,
                apkDownloadUrl = apkUrl,
                apkSizeBytes = apkSize
            )
        } catch (_: Throwable) {
            null
        }
    }

    suspend fun fetchLatestRelease(): AppReleaseInfo? = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(LATEST_RELEASE_API_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Veloa-Android")
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            parseLatestReleaseJson(body)
        } catch (_: Throwable) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    suspend fun downloadApk(
        context: Context,
        release: AppReleaseInfo,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val finalFile = File(updatesDir, "Veloa_${release.versionName}.apk")
        val partFile = File(updatesDir, "Veloa_${release.versionName}.apk.part")
        if (partFile.exists()) partFile.delete()

        var currentUrl = release.apkDownloadUrl
        var redirects = 0
        var conn: HttpURLConnection? = null

        try {
            while (true) {
                conn?.disconnect()
                conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = DOWNLOAD_TIMEOUT_MS
                    instanceFollowRedirects = false
                    setRequestProperty("User-Agent", "Veloa-Android")
                    setRequestProperty("Accept", "application/octet-stream")
                }
                val code = conn.responseCode
                if (code in 300..308 && code != HttpURLConnection.HTTP_NOT_MODIFIED) {
                    val location = conn.getHeaderField("Location")
                        ?: throw IllegalStateException("Missing redirect location")
                    if (++redirects > MAX_REDIRECTS) {
                        throw IllegalStateException("Too many redirects")
                    }
                    currentUrl = URL(URL(currentUrl), location).toString()
                    continue
                }
                if (code !in 200..299) {
                    throw IllegalStateException("HTTP $code")
                }
                break
            }

            val total = conn!!.contentLengthLong.takeIf { it > 0L } ?: release.apkSizeBytes
            onProgress(0L, total)

            conn.inputStream.use { input ->
                FileOutputStream(partFile).use { output ->
                    val buf = ByteArray(16 * 1024)
                    var downloaded = 0L
                    var lastReport = 0L
                    while (true) {
                        val read = input.read(buf)
                        if (read == -1) break
                        output.write(buf, 0, read)
                        downloaded += read
                        val now = System.currentTimeMillis()
                        if (now - lastReport >= 80L || downloaded == total) {
                            lastReport = now
                            onProgress(downloaded, total)
                        }
                    }
                    output.fd.sync()
                }
            }
            if (finalFile.exists()) finalFile.delete()
            if (!partFile.renameTo(finalFile)) {
                partFile.copyTo(finalFile, overwrite = true)
                partFile.delete()
            }
            finalFile
        } catch (t: Throwable) {
            partFile.delete()
            throw t
        } finally {
            conn?.disconnect()
        }
    }

    fun cleanupOldUpdateApks(context: Context, currentVersion: String) {
        try {
            val updatesDir = File(context.cacheDir, "updates")
            if (!updatesDir.isDirectory) return
            val files = updatesDir.listFiles() ?: return
            for (file in files) {
                if (file.name.endsWith(".part")) {
                    file.delete()
                    continue
                }
                val versionPart = file.name
                    .removePrefix("Veloa_")
                    .removeSuffix(".apk")
                if (!isNewerVersion(versionPart, currentVersion)) {
                    file.delete()
                }
            }
        } catch (_: Throwable) {
        }
    }

    fun canInstallPackages(context: Context): Boolean {
        return try {
            context.packageManager.canRequestPackageInstalls()
        } catch (_: Throwable) {
            false
        }
    }

    fun openInstallPermissionSettings(context: Context) {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Throwable) {
        }
    }

    fun launchApkInstall(context: Context, apkFile: File): Boolean {
        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun getInstalledVersionName(context: Context): String {
        return try {
            val pm = context.packageManager
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(
                    context.packageName,
                    android.content.pm.PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, 0)
            }
            info.versionName ?: "1.0.0"
        } catch (_: Throwable) {
            "1.0.0"
        }
    }
}
