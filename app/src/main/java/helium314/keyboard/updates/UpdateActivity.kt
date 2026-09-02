// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.updates

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.Theme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class UpdateStage {
    CHECKING,
    READY,
    DOWNLOADING,
    ALLOW_INSTALLS,
    OPENING_INSTALLER,
    CHECK_ERROR,
    DOWNLOAD_ERROR,
}

class UpdateActivity : ComponentActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var stage by mutableStateOf(UpdateStage.READY)
    private var update by mutableStateOf<UpdateInfo?>(null)
    private var errorMessage by mutableStateOf<String?>(null)
    private var pendingInstall: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        update = AppUpdater.getAvailableUpdate(this)
        setContent {
            Theme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    UpdateScreen(
                        update = update,
                        stage = stage,
                        errorMessage = errorMessage,
                        onBack = ::finish,
                        onCheck = ::checkNow,
                        onDownload = ::downloadAndInstall,
                    )
                }
            }
        }
        if (intent.getBooleanExtra(EXTRA_CHECK_NOW, false)) checkNow()
    }

    override fun onResume() {
        super.onResume()
        val file = pendingInstall ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || packageManager.canRequestPackageInstalls()) {
            pendingInstall = null
            launchInstaller(file)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun checkNow() {
        errorMessage = null
        stage = UpdateStage.CHECKING
        scope.launch {
            runCatching { AppUpdater.checkNow(this@UpdateActivity) }
                .onSuccess {
                    update = it
                    stage = UpdateStage.READY
                }
                .onFailure {
                    errorMessage = it.message ?: it.javaClass.simpleName
                    stage = UpdateStage.CHECK_ERROR
                }
        }
    }

    private fun downloadAndInstall() {
        val update = update ?: return
        errorMessage = null
        stage = UpdateStage.DOWNLOADING
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    AppUpdater.downloadAndVerify(this@UpdateActivity, update)
                }
            }.onSuccess(::requestInstall)
                .onFailure {
                    errorMessage = it.message ?: it.javaClass.simpleName
                    stage = UpdateStage.DOWNLOAD_ERROR
                }
        }
    }

    private fun requestInstall(file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            pendingInstall = file
            stage = UpdateStage.ALLOW_INSTALLS
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    "package:$packageName".toUri(),
                ),
            )
            return
        }
        launchInstaller(file)
    }

    private fun launchInstaller(file: File) {
        stage = UpdateStage.OPENING_INSTALLER
        runCatching {
            val uri = FileProvider.getUriForFile(this, "$packageName.updates", file)
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.clipData = ClipData.newRawUri("huBoard update", uri)
            startActivity(intent)
        }.onFailure {
            errorMessage = it.message ?: it.javaClass.simpleName
            stage = UpdateStage.DOWNLOAD_ERROR
        }
    }

    companion object {
        private const val EXTRA_CHECK_NOW = "check_now"

        fun createCheckIntent(context: Context) =
            Intent(context, UpdateActivity::class.java).putExtra(EXTRA_CHECK_NOW, true)
    }
}

@Composable
private fun UpdateScreen(
    update: UpdateInfo?,
    stage: UpdateStage,
    errorMessage: String?,
    onBack: () -> Unit,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) {
                Text("‹", style = MaterialTheme.typography.headlineLarge)
            }
            Text(
                text = stringResource(R.string.app_update_screen_title),
                style = MaterialTheme.typography.titleLarge,
            )
        }
        Spacer(Modifier.height(32.dp))
        Surface(
            shape = RoundedCornerShape(18.dp),
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_update_available),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp),
                )
                Spacer(Modifier.height(20.dp))
                when {
                    stage == UpdateStage.CHECKING -> UpdateProgress(R.string.app_update_checking)
                    stage == UpdateStage.CHECK_ERROR -> {
                        UpdateError(errorMessage)
                        UpdateButton(R.string.app_update_retry, onCheck)
                    }
                    update == null -> {
                        Text(
                            text = stringResource(R.string.app_update_up_to_date),
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                        )
                        VersionText()
                        Spacer(Modifier.height(24.dp))
                        UpdateButton(R.string.app_update_check_again, onCheck)
                    }
                    else -> {
                        Text(
                            text = stringResource(R.string.app_update_ready),
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.app_update_version, update.versionName),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                        )
                        VersionText()
                        Spacer(Modifier.height(24.dp))
                        when (stage) {
                            UpdateStage.READY -> UpdateButton(R.string.app_update_download, onDownload)
                            UpdateStage.DOWNLOAD_ERROR -> {
                                UpdateError(errorMessage)
                                UpdateButton(R.string.app_update_retry, onDownload)
                            }
                            UpdateStage.DOWNLOADING -> UpdateProgress(R.string.app_update_downloading)
                            UpdateStage.ALLOW_INSTALLS -> UpdateProgress(R.string.app_update_allow_installs)
                            UpdateStage.OPENING_INSTALLER -> UpdateProgress(R.string.app_update_opening_installer)
                            UpdateStage.CHECKING, UpdateStage.CHECK_ERROR -> Unit
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VersionText() {
    Text(
        text = stringResource(R.string.app_update_current_version, BuildConfig.VERSION_NAME),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun UpdateError(errorMessage: String?) {
    if (errorMessage == null) return
    Text(
        text = stringResource(R.string.app_update_failed, errorMessage),
        color = MaterialTheme.colorScheme.error,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(bottom = 14.dp),
    )
}

@Composable
private fun UpdateButton(text: Int, onClick: () -> Unit) {
    Button(onClick = onClick) {
        Text(text = stringResource(text))
    }
}

@Composable
private fun UpdateProgress(message: Int) {
    CircularProgressIndicator()
    Spacer(Modifier.height(16.dp))
    Text(text = stringResource(message), textAlign = TextAlign.Center)
}
