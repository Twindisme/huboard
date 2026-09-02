// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.graphics.drawable.toBitmap
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SearchScreen
import helium314.keyboard.settings.dialogs.ConfirmationDialog
import helium314.keyboard.theme.ResolvedVisualTheme
import helium314.keyboard.theme.ThemeAsset
import helium314.keyboard.theme.VisualThemeInstallAction
import helium314.keyboard.theme.VisualThemeManager
import helium314.keyboard.theme.VisualThemePackInstaller
import helium314.keyboard.theme.VisualThemeValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun VisualThemeBrowserScreen(
    onClickBack: () -> Unit,
    onOpenLab: (String) -> Unit,
) {
    val context = LocalContext.current
    val prefs = context.prefs()
    val scope = rememberCoroutineScope()
    var themes by remember { mutableStateOf(VisualThemeManager.availableThemes(context)) }
    var activeThemeId by remember {
        mutableStateOf(
            prefs.getString(Settings.PREF_VISUAL_THEME_PACK, Defaults.PREF_VISUAL_THEME_PACK)
                ?: Defaults.PREF_VISUAL_THEME_PACK,
        )
    }
    var expectedUpdateId by remember { mutableStateOf<String?>(null) }
    var deleteCandidate by remember { mutableStateOf<ResolvedVisualTheme?>(null) }

    fun reloadKeyboardTheme() {
        KeyboardIconsSet.needsReload = true
        KeyboardSwitcher.getInstance().setThemeNeedsReload()
    }

    fun applyTheme(themeId: String) {
        prefs.edit { putString(Settings.PREF_VISUAL_THEME_PACK, themeId) }
        activeThemeId = themeId
        reloadKeyboardTheme()
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val expectedId = expectedUpdateId
        expectedUpdateId = null
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val installed = context.contentResolver.openInputStream(uri)?.use {
                        VisualThemePackInstaller.installOrUpdate(context, it, expectedId)
                    } ?: error("Could not open the selected file")
                    installed to VisualThemeManager.availableThemes(context)
                }
            }
            result.onSuccess { (installed, refreshedThemes) ->
                themes = refreshedThemes
                if (expectedId == null) applyTheme(installed.manifest.id)
                else reloadKeyboardTheme()
                val message = if (installed.action == VisualThemeInstallAction.UPDATED) {
                    R.string.visual_theme_updated
                } else {
                    R.string.visual_theme_imported
                }
                Toast.makeText(
                    context,
                    context.getString(message, installed.manifest.displayName),
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

    SearchScreen(
        onClickBack = onClickBack,
        title = { Text(stringResource(R.string.visual_theme_browser)) },
        filteredItems = { query ->
            themes.filter { theme ->
                query.isBlank() || listOfNotNull(
                    theme.displayName,
                    theme.manifest.author,
                    theme.manifest.description,
                ).any { it.contains(query, ignoreCase = true) }
            }
        },
        itemContent = { theme ->
            VisualThemeCard(
                theme = theme,
                active = theme.id == activeThemeId,
                onApply = { applyTheme(theme.id) },
                onUpdate = {
                    expectedUpdateId = theme.id
                    importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                },
                onRemove = { deleteCandidate = theme },
                onOpenLab = { onOpenLab(theme.id) },
            )
        },
        additionalIcon = {
            IconButton(onClick = {
                expectedUpdateId = null
                importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
            }) {
                Icon(
                    painterResource(R.drawable.ic_plus),
                    stringResource(R.string.visual_theme_import_or_update),
                )
            }
        },
    )

    deleteCandidate?.let { theme ->
        ConfirmationDialog(
            onDismissRequest = { deleteCandidate = null },
            onConfirmed = {
                deleteCandidate = null
                scope.launch {
                    val (removed, refreshedThemes) = withContext(Dispatchers.IO) {
                        val didRemove = VisualThemePackInstaller.uninstall(context, theme.id)
                        didRemove to if (didRemove) {
                            VisualThemeManager.availableThemes(context)
                        } else {
                            emptyList()
                        }
                    }
                    if (!removed) return@launch
                    if (activeThemeId == theme.id) applyTheme(Defaults.PREF_VISUAL_THEME_PACK)
                    themes = refreshedThemes
                    Toast.makeText(
                        context,
                        context.getString(R.string.visual_theme_removed, theme.displayName),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            },
            title = { Text(stringResource(R.string.visual_theme_remove)) },
            content = {
                Text(stringResource(R.string.visual_theme_remove_confirm, theme.displayName))
            },
        )
    }
}

@Composable
private fun VisualThemeCard(
    theme: ResolvedVisualTheme,
    active: Boolean,
    onApply: () -> Unit,
    onUpdate: () -> Unit,
    onRemove: () -> Unit,
    onOpenLab: () -> Unit,
) {
    val context = LocalContext.current
    val preview = remember(theme.id, theme.manifest.versionCode) {
        runCatching {
            listOf(
                ThemeAsset.THEME_THUMBNAIL,
                ThemeAsset.KEYBOARD_BACKGROUND,
                ThemeAsset.TOOLBAR_BACKGROUND,
                ThemeAsset.KEY_NORMAL,
            ).firstNotNullOfOrNull { asset -> theme.drawable(context, asset) }
                ?.toBitmap(640, 240, Bitmap.Config.ARGB_8888)
                ?.asImageBitmap()
        }.getOrNull()
    }
    val fallbackColor = remember(theme.id, theme.manifest.versionCode) {
        theme.manifest.colors?.background?.let { color ->
            runCatching { Color(VisualThemeValidator.parseColor(color)) }.getOrNull()
        }
    } ?: MaterialTheme.colorScheme.surfaceVariant
    val cardColors = if (active) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    } else {
        CardDefaults.cardColors()
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        colors = cardColors,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(8f / 3f)
                    .background(fallbackColor, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
            ) {
                preview?.let {
                    Image(
                        bitmap = it,
                        contentDescription = theme.displayName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            Column(
                modifier = Modifier.padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = theme.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = stringResource(
                            if (theme.isBundled) R.string.visual_theme_bundled
                            else R.string.visual_theme_installed,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                theme.manifest.author?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    stringResource(
                        R.string.visual_theme_version,
                        theme.manifest.versionName ?: theme.manifest.versionCode.toString(),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                theme.manifest.description?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (theme.hasCustomKeyPreview) {
                    TextButton(onClick = onOpenLab) {
                        Text(stringResource(R.string.visual_theme_lab))
                    }
                }
                if (!theme.isBundled) {
                    TextButton(onClick = onRemove) {
                        Text(stringResource(R.string.visual_theme_remove))
                    }
                    TextButton(onClick = onUpdate) {
                        Text(stringResource(R.string.visual_theme_update))
                    }
                }
                if (active) {
                    Text(
                        text = stringResource(R.string.visual_theme_active),
                        modifier = Modifier.padding(horizontal = 12.dp),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                } else {
                    TextButton(onClick = onApply) {
                        Text(stringResource(R.string.visual_theme_apply))
                    }
                }
            }
        }
    }
}
