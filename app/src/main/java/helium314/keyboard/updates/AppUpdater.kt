// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.updates

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

data class UpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
)

object AppUpdater {
    private const val TAG = "AppUpdater"
    private const val MANIFEST_URL =
        "https://github.com/Twindisme/huboard/releases/latest/download/update.json"
    private const val RELEASE_PATH_PREFIX = "/Twindisme/huboard/releases/download/"
    private const val PREFS_NAME = "huboard_updates"
    private const val KEY_LAST_ATTEMPT = "last_attempt"
    private const val KEY_LAST_SUCCESS = "last_success"
    private const val KEY_VERSION_CODE = "version_code"
    private const val KEY_VERSION_NAME = "version_name"
    private const val KEY_APK_URL = "apk_url"
    private const val KEY_SHA256 = "sha256"
    private const val CHANNEL_ID = "huboard_updates"
    private const val NOTIFICATION_ID = 0x4842
    private const val SUCCESS_INTERVAL_MS = 24L * 60L * 60L * 1000L
    private const val RETRY_INTERVAL_MS = 60L * 60L * 1000L
    private const val MAX_MANIFEST_BYTES = 64 * 1024
    private const val MAX_APK_BYTES = 100L * 1024L * 1024L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val checking = AtomicBoolean(false)

    @JvmStatic
    fun checkForUpdates(context: Context, force: Boolean = false) {
        val appContext = context.applicationContext
        if (!isUserUnlocked(appContext)) return
        if (!force && !appContext.prefs().getBoolean(
                Settings.PREF_AUTOMATIC_UPDATE_CHECKS,
                Defaults.PREF_AUTOMATIC_UPDATE_CHECKS,
            )
        ) return
        getAvailableUpdate(appContext)?.let { showUpdateNotification(appContext, it) }

        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (!force) {
            if (now - prefs.getLong(KEY_LAST_SUCCESS, 0L) < SUCCESS_INTERVAL_MS) return
            if (now - prefs.getLong(KEY_LAST_ATTEMPT, 0L) < RETRY_INTERVAL_MS) return
        }
        if (!checking.compareAndSet(false, true)) return
        prefs.edit { putLong(KEY_LAST_ATTEMPT, now) }

        scope.launch {
            try {
                val update = fetchAndCacheUpdate(appContext)
                if (update.versionCode > BuildConfig.VERSION_CODE) {
                    showUpdateNotification(appContext, update)
                } else {
                    NotificationManagerCompat.from(appContext).cancel(NOTIFICATION_ID)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Update check failed", e)
            } finally {
                checking.set(false)
            }
        }
    }

    suspend fun checkNow(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        check(isUserUnlocked(appContext)) { "Unlock the device before checking for updates" }
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putLong(KEY_LAST_ATTEMPT, System.currentTimeMillis()) }
        val update = fetchAndCacheUpdate(appContext)
        update.takeIf { it.versionCode > BuildConfig.VERSION_CODE }.also {
            if (it == null) NotificationManagerCompat.from(appContext).cancel(NOTIFICATION_ID)
        }
    }

    fun getAvailableUpdate(context: Context): UpdateInfo? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val versionCode = prefs.getLong(KEY_VERSION_CODE, 0L)
        if (versionCode <= BuildConfig.VERSION_CODE) return null
        val versionName = prefs.getString(KEY_VERSION_NAME, null) ?: return null
        val apkUrl = prefs.getString(KEY_APK_URL, null) ?: return null
        val sha256 = prefs.getString(KEY_SHA256, null) ?: return null
        return UpdateInfo(versionCode, versionName, apkUrl, sha256)
    }

    fun cancelUpdateNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    fun downloadAndVerify(context: Context, update: UpdateInfo): File {
        require(isTrustedApkUrl(update.apkUrl)) { "Untrusted update URL" }
        val updateDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val partialFile = File(updateDir, "huBoard-update.apk.part")
        val apkFile = File(updateDir, "huBoard-update.apk")
        partialFile.delete()
        apkFile.delete()

        val digest = MessageDigest.getInstance("SHA-256")
        val connection = URL(update.apkUrl).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 15_000
        connection.readTimeout = 60_000
        connection.setRequestProperty("User-Agent", userAgent())
        connection.setRequestProperty("Accept", "application/vnd.android.package-archive")

        try {
            val responseCode = connection.responseCode
            require(responseCode in 200..299) { "Download returned HTTP $responseCode" }
            val declaredLength = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                connection.contentLengthLong
            } else {
                connection.contentLength.toLong()
            }
            require(declaredLength <= 0L || declaredLength <= MAX_APK_BYTES) { "Update is too large" }

            var totalBytes = 0L
            connection.inputStream.use { input ->
                FileOutputStream(partialFile).buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        totalBytes += count
                        require(totalBytes <= MAX_APK_BYTES) { "Update is too large" }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                }
            }
            require(totalBytes > 0L) { "Downloaded update is empty" }
            val actualSha256 = digest.digest().joinToString("") { "%02x".format(it) }
            require(actualSha256.equals(update.sha256, ignoreCase = true)) {
                "Downloaded file failed verification"
            }
            require(partialFile.renameTo(apkFile)) { "Could not finalize the downloaded update" }
            return apkFile
        } catch (e: Exception) {
            partialFile.delete()
            apkFile.delete()
            throw e
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchAndCacheUpdate(context: Context): UpdateInfo {
        val update = fetchUpdateManifest()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putLong(KEY_LAST_SUCCESS, System.currentTimeMillis())
            putLong(KEY_VERSION_CODE, update.versionCode)
            putString(KEY_VERSION_NAME, update.versionName)
            putString(KEY_APK_URL, update.apkUrl)
            putString(KEY_SHA256, update.sha256)
        }
        return update
    }

    private fun fetchUpdateManifest(): UpdateInfo {
        val connection = URL(MANIFEST_URL).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("User-Agent", userAgent())
        connection.setRequestProperty("Accept", "application/json")
        try {
            val responseCode = connection.responseCode
            require(responseCode in 200..299) { "Manifest returned HTTP $responseCode" }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            require(body.toByteArray().size <= MAX_MANIFEST_BYTES) { "Update manifest is too large" }
            val json = JSONObject(body)
            val update = UpdateInfo(
                versionCode = json.getLong("versionCode"),
                versionName = json.getString("versionName"),
                apkUrl = json.getString("apkUrl"),
                sha256 = json.getString("sha256").lowercase(Locale.ROOT),
            )
            require(update.versionCode > 0L) { "Invalid update version" }
            require(update.versionName.isNotBlank()) { "Invalid update name" }
            require(update.sha256.matches(Regex("[0-9a-f]{64}"))) { "Invalid update checksum" }
            require(isTrustedApkUrl(update.apkUrl)) { "Untrusted update URL" }
            return update
        } finally {
            connection.disconnect()
        }
    }

    private fun isTrustedApkUrl(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" &&
            uri.host.equals("github.com", ignoreCase = true) &&
            uri.path.startsWith(RELEASE_PATH_PREFIX) &&
            uri.path.endsWith(".apk")
    }.getOrDefault(false)

    private fun isUserUnlocked(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return true
        return (context.getSystemService(Context.USER_SERVICE) as UserManager).isUserUnlocked
    }

    private fun userAgent() = "huBoard/${BuildConfig.VERSION_NAME}"

    @SuppressLint("MissingPermission")
    private fun showUpdateNotification(context: Context, update: UpdateInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.app_update_channel),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }
        val intent = Intent(context, UpdateActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_update_available)
            .setContentTitle(context.getString(R.string.app_update_available_title))
            .setContentText(context.getString(R.string.app_update_available_text, update.versionName))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}
