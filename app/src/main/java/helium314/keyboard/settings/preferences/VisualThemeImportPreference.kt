// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.preferences

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.Setting
import helium314.keyboard.theme.VisualThemePackInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun VisualThemeImportPreference(setting: Setting) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use {
                        VisualThemePackInstaller.install(context, it)
                    } ?: error("Could not open the selected file")
                }
            }
            result.onSuccess { manifest ->
                context.prefs().edit {
                    putString(Settings.PREF_VISUAL_THEME_PACK, manifest.id)
                }
                KeyboardIconsSet.needsReload = true
                KeyboardSwitcher.getInstance().setThemeNeedsReload()
                Toast.makeText(
                    context,
                    context.getString(R.string.visual_theme_imported, manifest.displayName),
                    Toast.LENGTH_LONG,
                ).show()
            }.onFailure { error ->
                Toast.makeText(
                    context,
                    context.getString(
                        R.string.visual_theme_import_failed,
                        error.message ?: context.getString(R.string.visual_theme_unknown_error),
                    ),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    Preference(
        name = setting.title,
        description = setting.description,
        onClick = {
            launcher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
        },
    )
}
