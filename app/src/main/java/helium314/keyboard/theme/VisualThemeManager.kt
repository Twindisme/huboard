// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.theme

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BitmapFactory
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import kotlinx.serialization.json.Json
import java.io.File

object VisualThemeManager {
    private const val BUNDLED_THEME_PATH = "visual-themes"
    private const val INSTALLED_THEME_DIRECTORY = "visual_themes"
    private val json = Json {
        ignoreUnknownKeys = false
        explicitNulls = false
    }

    @Volatile
    private var bundledThemes: List<ResolvedVisualTheme>? = null

    @JvmStatic
    fun availableThemes(context: Context): List<ResolvedVisualTheme> =
        bundledThemes ?: synchronized(this) {
            bundledThemes ?: loadBundledThemes(context.applicationContext).also {
                bundledThemes = it
            }
        }

    @JvmStatic
    fun activeTheme(context: Context): ResolvedVisualTheme {
        val themes = availableThemes(context)
        val selectedId = context.prefs().getString(
            Settings.PREF_VISUAL_THEME_PACK,
            Defaults.PREF_VISUAL_THEME_PACK,
        )
        return themes.firstOrNull { it.id == selectedId }
            ?: themes.firstOrNull { it.id == Defaults.PREF_VISUAL_THEME_PACK }
            ?: themes.firstOrNull { it.id == "classic" }
            ?: error("No valid bundled visual themes")
    }

    fun clearCache() {
        bundledThemes = null
    }

    private fun loadBundledThemes(context: Context): List<ResolvedVisualTheme> {
        val themeDirectories = context.assets
            .open("$BUNDLED_THEME_PATH/index.json")
            .bufferedReader()
            .use { json.decodeFromString<List<String>>(it.readText()) }
        val bundled = themeDirectories.mapNotNull { directory ->
            runCatching { loadBundledTheme(context, directory) }.getOrNull()
        }
        val installedRoot = File(context.filesDir, INSTALLED_THEME_DIRECTORY)
        VisualThemePackInstaller.recoverInterruptedUpdates(context)
        val installed = installedRoot.listFiles()
            .orEmpty()
            .filter { it.isDirectory && !it.name.startsWith('.') }
            .sortedBy { it.name }
            .mapNotNull { directory ->
                runCatching { loadInstalledTheme(context, installedRoot, directory) }.getOrNull()
            }
        return (bundled + installed)
            .distinctBy(ResolvedVisualTheme::id)
            .also { themes ->
                require(themes.any { it.id == "classic" }) { "Classic visual theme is missing" }
            }
    }

    @SuppressLint("DiscouragedApi")
    private fun loadBundledTheme(context: Context, directory: String): ResolvedVisualTheme {
        val manifestBytes = context.assets
            .open("$BUNDLED_THEME_PATH/$directory/manifest.json")
            .use { it.readBytes() }
        require(manifestBytes.size <= VisualThemeValidator.MAX_MANIFEST_BYTES) {
            "Visual theme manifest is too large"
        }
        val manifest = json.decodeFromString<VisualThemeManifest>(manifestBytes.decodeToString())
        VisualThemeValidator.validate(manifest)
        require(manifest.id == directory) { "Theme directory does not match its id" }

        var totalPixels = 0L
        val resources = manifest.assets.mapValues { (asset, reference) ->
            when {
                reference.startsWith("res:") -> {
                    val name = reference.removePrefix("res:")
                    val id = context.resources.getIdentifier(name, "drawable", context.packageName).also { id ->
                        require(id != 0) { "Missing bundled drawable '$name'" }
                    }
                    if (manifest.keyPressAnimation?.spriteAtlas?.asset == asset) {
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeResource(context.resources, id, options)
                        VisualThemeValidator.validateSpriteAtlasDimensions(
                            manifest,
                            asset,
                            options.outWidth,
                            options.outHeight,
                        )
                    }
                    ResolvedThemeAsset(resourceId = id)
                }
                reference.startsWith("file:") -> {
                    val relativePath = reference.removePrefix("file:")
                    val assetPath = "$BUNDLED_THEME_PATH/$directory/$relativePath"
                    val pixels = context.assets.open(assetPath).use { input ->
                        when {
                            assetPath.endsWith(".svg", ignoreCase = true) ->
                                VisualThemeSvg.parse(input).pixels
                            assetPath.endsWith(".json", ignoreCase = true) -> {
                                VisualThemeLottie.parse(input)
                                0L
                            }
                            assetPath.endsWith(".luau", ignoreCase = true) -> {
                                val source = input.readBytes()
                                require(source.size <= VisualThemeValidator.MAX_SCRIPT_BYTES) {
                                    "Bundled huBoard Motion script '$asset' is too large"
                                }
                                source.decodeToString(throwOnInvalidSequence = true)
                                0L
                            }
                            else -> {
                                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                BitmapFactory.decodeStream(input, null, options)
                                val imagePixels = options.outWidth.toLong() * options.outHeight
                                require(options.outWidth in 1..VisualThemeSvg.MAX_DIMENSION &&
                                        options.outHeight in 1..VisualThemeSvg.MAX_DIMENSION &&
                                        imagePixels <= VisualThemeSvg.MAX_PIXELS) {
                                    "Bundled theme asset '$asset' is invalid or too large"
                                }
                                VisualThemeValidator.validateSpriteAtlasDimensions(
                                    manifest,
                                    asset,
                                    options.outWidth,
                                    options.outHeight,
                                )
                                imagePixels
                            }
                        }
                    }
                    totalPixels += pixels
                    require(totalPixels <= VisualThemeSvg.MAX_TOTAL_PIXELS) {
                        "Bundled theme images require too much decoded memory"
                    }
                    ResolvedThemeAsset(bundledPath = assetPath)
                }
                else -> error("Unsupported bundled theme asset reference for $asset")
            }
        }
        return ResolvedVisualTheme(manifest, resources, isBundled = true)
    }

    @SuppressLint("DiscouragedApi")
    private fun loadInstalledTheme(
        context: Context,
        installedRoot: File,
        directory: File,
    ): ResolvedVisualTheme {
        require(directory.canonicalFile.parentFile == installedRoot.canonicalFile) {
            "Theme escaped the installed theme directory"
        }
        val manifestFile = File(directory, "manifest.json")
        require(manifestFile.isFile && manifestFile.length() <= VisualThemeValidator.MAX_MANIFEST_BYTES) {
            "Installed visual theme manifest is missing or too large"
        }
        val manifest = decodeManifest(manifestFile.readText())
        require(manifest.id == directory.name) { "Theme directory does not match its id" }
        val resources = manifest.assets.mapValues { (asset, reference) ->
            when {
                reference.startsWith("res:") -> {
                    val name = reference.removePrefix("res:")
                    val id = context.resources.getIdentifier(name, "drawable", context.packageName)
                    require(id != 0) { "Missing drawable '$name' for $asset" }
                    ResolvedThemeAsset(resourceId = id)
                }
                reference.startsWith("file:") -> {
                    val file = File(directory, reference.removePrefix("file:"))
                    require(file.isFile && file.canonicalPath.startsWith(directory.canonicalPath + File.separator)) {
                        "Missing or unsafe file for $asset"
                    }
                    ResolvedThemeAsset(file = file)
                }
                else -> error("Unsupported asset reference for $asset")
            }
        }
        return ResolvedVisualTheme(manifest, resources, isBundled = false)
    }

    internal fun decodeManifest(text: String): VisualThemeManifest =
        json.decodeFromString<VisualThemeManifest>(text).also(VisualThemeValidator::validate)

    internal fun installedThemesDirectory(context: Context): File =
        File(context.filesDir, INSTALLED_THEME_DIRECTORY)
}
