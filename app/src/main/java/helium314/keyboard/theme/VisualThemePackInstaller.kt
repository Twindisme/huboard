// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.theme

import android.content.Context
import android.graphics.BitmapFactory
import java.io.File
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipInputStream

enum class VisualThemeInstallAction {
    INSTALLED,
    UPDATED,
}

data class VisualThemeInstallResult(
    val manifest: VisualThemeManifest,
    val action: VisualThemeInstallAction,
)

object VisualThemePackInstaller {
    private const val MAX_ARCHIVE_ENTRIES = 256
    private const val MAX_ENTRY_BYTES = 8L * 1024L * 1024L
    private const val MAX_EXTRACTED_BYTES = 30L * 1024L * 1024L
    private const val BACKUP_PREFIX = ".backup-"
    private val safeThemeId = Regex("^[a-z][a-z0-9_]{0,47}$")

    fun install(context: Context, input: InputStream): VisualThemeManifest =
        installOrUpdate(context, input).manifest

    fun installOrUpdate(
        context: Context,
        input: InputStream,
        expectedThemeId: String? = null,
    ): VisualThemeInstallResult {
        val root = VisualThemeManager.installedThemesDirectory(context).apply { mkdirs() }
        recoverInterruptedUpdates(root)
        val staging = File(root, ".import-${UUID.randomUUID()}")
        require(staging.mkdir()) { "Could not create theme import directory" }

        try {
            extract(input, staging)
            val manifestFile = File(staging, "manifest.json")
            require(manifestFile.isFile) { "Theme pack has no manifest.json" }
            require(manifestFile.length() <= VisualThemeValidator.MAX_MANIFEST_BYTES) {
                "Theme manifest is too large"
            }
            val manifest = VisualThemeManager.decodeManifest(manifestFile.readText())
            validateExtractedAssets(staging, manifest)
            require(expectedThemeId == null || manifest.id == expectedThemeId) {
                "Selected pack is '${manifest.displayName}', not the requested theme"
            }

            require(VisualThemeManager.availableThemes(context).none {
                it.id == manifest.id && it.isBundled
            }) { "A bundled theme already uses id '${manifest.id}'" }
            val destination = File(root, manifest.id)
            val action = if (destination.exists()) {
                val existingManifestFile = File(destination, "manifest.json")
                require(existingManifestFile.isFile &&
                        existingManifestFile.length() <= VisualThemeValidator.MAX_MANIFEST_BYTES) {
                    "Installed theme '${manifest.id}' is damaged"
                }
                val existingManifest = VisualThemeManager.decodeManifest(
                    existingManifestFile.readText(),
                )
                require(manifest.versionCode > existingManifest.versionCode) {
                    "Theme ${manifest.displayName} ${manifest.versionName ?: manifest.versionCode} " +
                        "is not newer than the installed version"
                }
                replaceInstalledTheme(root, destination, staging, manifest.id)
                VisualThemeInstallAction.UPDATED
            } else {
                require(staging.renameTo(destination)) { "Could not finish installing the theme" }
                VisualThemeInstallAction.INSTALLED
            }
            VisualThemeManager.clearCache()
            return VisualThemeInstallResult(manifest, action)
        } finally {
            if (staging.exists()) staging.deleteRecursively()
        }
    }

    internal fun recoverInterruptedUpdates(context: Context) {
        recoverInterruptedUpdates(VisualThemeManager.installedThemesDirectory(context))
    }

    private fun recoverInterruptedUpdates(root: File) {
        if (!root.isDirectory) return
        root.listFiles().orEmpty()
            .filter { it.isDirectory && it.name.startsWith(BACKUP_PREFIX) }
            .forEach { backup ->
                val themeId = backup.name.removePrefix(BACKUP_PREFIX)
                if (!safeThemeId.matches(themeId)) return@forEach
                val destination = File(root, themeId)
                if (destination.exists()) backup.deleteRecursively()
                else backup.renameTo(destination)
            }
    }

    private fun replaceInstalledTheme(
        root: File,
        destination: File,
        staging: File,
        themeId: String,
    ) {
        val backup = File(root, "$BACKUP_PREFIX$themeId")
        require(!backup.exists()) { "Could not prepare theme update backup" }
        require(destination.renameTo(backup)) { "Could not back up the installed theme" }
        if (!staging.renameTo(destination)) {
            backup.renameTo(destination)
            error("Could not finish updating the theme")
        }
        backup.deleteRecursively()
    }

    fun uninstall(context: Context, themeId: String): Boolean {
        val theme = VisualThemeManager.availableThemes(context)
            .firstOrNull { it.id == themeId && !it.isBundled }
            ?: return false
        val root = VisualThemeManager.installedThemesDirectory(context).canonicalFile
        val directory = File(root, theme.id).canonicalFile
        require(directory.parentFile == root) { "Unsafe installed theme path" }
        val deleted = directory.deleteRecursively()
        if (deleted) VisualThemeManager.clearCache()
        return deleted
    }

    private fun extract(input: InputStream, destination: File) {
        var entries = 0
        var totalBytes = 0L
        val seenEntries = mutableSetOf<String>()
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries++
                require(entries <= MAX_ARCHIVE_ENTRIES) { "Theme archive has too many files" }
                val normalizedName = entry.name.replace('\\', '/')
                require(seenEntries.add(normalizedName)) { "Theme archive contains duplicate files" }
                require(isAllowedEntry(normalizedName, entry.isDirectory)) {
                    "Theme archive contains an unsupported path"
                }
                val output = File(destination, normalizedName)
                require(output.canonicalPath.startsWith(destination.canonicalPath + File.separator)) {
                    "Theme archive contains an unsafe path"
                }
                if (entry.isDirectory) {
                    require(output.mkdirs() || output.isDirectory) { "Could not create theme directory" }
                } else {
                    val parent = output.parentFile
                    require(parent == null || parent.isDirectory || parent.mkdirs()) {
                        "Could not create theme directory"
                    }
                    var entryBytes = 0L
                    output.outputStream().buffered().use { sink ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = zip.read(buffer)
                            if (count < 0) break
                            entryBytes += count
                            totalBytes += count
                            require(entryBytes <= MAX_ENTRY_BYTES) { "A theme file is too large" }
                            require(totalBytes <= MAX_EXTRACTED_BYTES) { "Theme archive is too large" }
                            sink.write(buffer, 0, count)
                        }
                    }
                }
                zip.closeEntry()
            }
        }
    }

    private fun isAllowedEntry(name: String, directory: Boolean): Boolean {
        if (!VisualThemeValidator.isSafeRelativePath(name.removeSuffix("/"))) return false
        if (directory) return name == "assets" || name.startsWith("assets/")
        return name == "manifest.json" ||
                (name.startsWith("assets/") &&
                        (name.endsWith(".png", ignoreCase = true) ||
                                name.endsWith(".webp", ignoreCase = true) ||
                                name.endsWith(".svg", ignoreCase = true) ||
                                name.endsWith(".json", ignoreCase = true) ||
                                name.endsWith(".luau", ignoreCase = true)))
    }

    private fun validateExtractedAssets(
        directory: File,
        manifest: VisualThemeManifest,
    ) {
        var totalImagePixels = 0L
        val validatedFiles = mutableSetOf<String>()
        manifest.assets.forEach { (asset, reference) ->
            require(reference.startsWith("file:")) {
                "Imported theme asset '$asset' must use a file: reference"
            }
            val relativePath = reference.removePrefix("file:")
            val file = File(directory, relativePath)
            require(file.isFile && file.canonicalPath.startsWith(directory.canonicalPath + File.separator)) {
                "Theme asset '$asset' is missing"
            }
            if (!validatedFiles.add(file.canonicalPath)) return@forEach
            val pixels = when {
                file.extension.equals("svg", ignoreCase = true) ->
                    file.inputStream().use(VisualThemeSvg::parse).pixels
                file.extension.equals("json", ignoreCase = true) -> {
                    require(file.length() <= VisualThemeLottie.MAX_BYTES) {
                        "Lottie animation '$asset' is too large"
                    }
                    file.inputStream().use(VisualThemeLottie::parse)
                    0L
                }
                file.extension.equals("luau", ignoreCase = true) -> {
                    require(file.length() <= VisualThemeValidator.MAX_SCRIPT_BYTES) {
                        "huBoard Motion script '$asset' is too large"
                    }
                    file.readBytes().decodeToString(throwOnInvalidSequence = true)
                    0L
                }
                else -> {
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(file.path, options)
                    val imagePixels = options.outWidth.toLong() * options.outHeight
                    require(options.outWidth in 1..VisualThemeSvg.MAX_DIMENSION &&
                            options.outHeight in 1..VisualThemeSvg.MAX_DIMENSION &&
                            imagePixels <= VisualThemeSvg.MAX_PIXELS) {
                        "Theme asset '$asset' is invalid or too large"
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
            totalImagePixels += pixels
            require(totalImagePixels <= VisualThemeSvg.MAX_TOTAL_PIXELS) {
                "Theme images require too much decoded memory"
            }
        }
    }
}
