// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.theme

import android.content.Context
import android.graphics.BitmapFactory
import java.io.File
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipInputStream

object VisualThemePackInstaller {
    private const val MAX_ARCHIVE_ENTRIES = 256
    private const val MAX_ENTRY_BYTES = 8L * 1024L * 1024L
    private const val MAX_EXTRACTED_BYTES = 30L * 1024L * 1024L
    private const val MAX_IMAGE_DIMENSION = 4_096
    private const val MAX_IMAGE_PIXELS = 8_000_000L
    private const val MAX_TOTAL_IMAGE_PIXELS = 40_000_000L

    fun install(context: Context, input: InputStream): VisualThemeManifest {
        val root = VisualThemeManager.installedThemesDirectory(context).apply { mkdirs() }
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
            validateExtractedAssets(context, staging, manifest)

            require(VisualThemeManager.availableThemes(context).none {
                it.id == manifest.id && it.isBundled
            }) { "A bundled theme already uses id '${manifest.id}'" }
            val destination = File(root, manifest.id)
            require(!destination.exists()) {
                "Theme '${manifest.displayName}' is already installed"
            }
            require(staging.renameTo(destination)) { "Could not finish installing the theme" }
            VisualThemeManager.clearCache()
            return manifest
        } finally {
            if (staging.exists()) staging.deleteRecursively()
        }
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
                                name.endsWith(".webp", ignoreCase = true)))
    }

    private fun validateExtractedAssets(
        context: Context,
        directory: File,
        manifest: VisualThemeManifest,
    ) {
        var totalImagePixels = 0L
        val validatedFiles = mutableSetOf<String>()
        manifest.assets.forEach { (asset, reference) ->
            if (reference.startsWith("res:")) {
                val name = reference.removePrefix("res:")
                @Suppress("DiscouragedApi")
                val id = context.resources.getIdentifier(name, "drawable", context.packageName)
                require(id != 0) { "Theme asset '$asset' references an unknown drawable" }
                return@forEach
            }
            val relativePath = reference.removePrefix("file:")
            val file = File(directory, relativePath)
            require(file.isFile && file.canonicalPath.startsWith(directory.canonicalPath + File.separator)) {
                "Theme asset '$asset' is missing"
            }
            if (!validatedFiles.add(file.canonicalPath)) return@forEach
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, options)
            val pixels = options.outWidth.toLong() * options.outHeight
            require(options.outWidth in 1..MAX_IMAGE_DIMENSION &&
                    options.outHeight in 1..MAX_IMAGE_DIMENSION &&
                    pixels <= MAX_IMAGE_PIXELS) {
                "Theme asset '$asset' is invalid or too large"
            }
            totalImagePixels += pixels
            require(totalImagePixels <= MAX_TOTAL_IMAGE_PIXELS) {
                "Theme images require too much decoded memory"
            }
        }
    }
}
