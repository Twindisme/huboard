// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.preferences

import android.widget.Toast
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.dialogs.ListPickerDialog
import helium314.keyboard.theme.VisualThemeManager
import helium314.keyboard.theme.VisualThemePackInstaller

@Composable
fun VisualThemeDeletePreference(setting: Setting) {
    val context = LocalContext.current
    var showDialog by rememberSaveable { mutableStateOf(false) }
    var installedThemes by remember {
        mutableStateOf(VisualThemeManager.availableThemes(context).filterNot { it.isBundled })
    }
    Preference(
        name = setting.title,
        description = if (installedThemes.isEmpty()) {
            stringResource(R.string.visual_theme_remove_none)
        } else setting.description,
        onClick = { if (installedThemes.isNotEmpty()) showDialog = true },
    )
    if (showDialog) {
        ListPickerDialog(
            onDismissRequest = { showDialog = false },
            items = installedThemes,
            onItemSelected = { theme ->
                if (VisualThemePackInstaller.uninstall(context, theme.id)) {
                    if (context.prefs().getString(Settings.PREF_VISUAL_THEME_PACK, null) == theme.id) {
                        context.prefs().edit {
                            putString(Settings.PREF_VISUAL_THEME_PACK, "classic")
                        }
                    }
                    installedThemes = VisualThemeManager.availableThemes(context)
                        .filterNot { it.isBundled }
                    KeyboardIconsSet.needsReload = true
                    KeyboardSwitcher.getInstance().setThemeNeedsReload()
                    Toast.makeText(
                        context,
                        context.getString(R.string.visual_theme_removed, theme.displayName),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            },
            title = { Text(setting.title) },
            getItemName = { it.displayName },
            showRadioButtons = false,
        )
    }
}
