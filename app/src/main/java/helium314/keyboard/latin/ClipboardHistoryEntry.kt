// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.latin

import android.annotation.SuppressLint
import android.content.ClipDescription
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.FileProvider
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Log
import androidx.core.view.inputmethod.InputContentInfoCompat
import helium314.keyboard.latin.database.ClipboardDao
import java.io.File

data class ClipboardHistoryEntry(
    val id: Long,
    val timeStamp: Long,
    val isPinned: Boolean,
    val text: String?,
    val filename: String?,
    val mimeTypes: List<String>?
) : Comparable<ClipboardHistoryEntry> {
    // for display order
    override fun compareTo(other: ClipboardHistoryEntry) =
        comparator(Settings.getValues()?.mClipboardHistoryPinnedFirst != false).compare(this, other)

    fun getContentInfo(context: Context): InputContentInfoCompat =
        InputContentInfoCompat(getContentUri(context)!!, ClipDescription(text, mimeTypes?.toTypedArray()), null)

    fun getContentUri(context: Context) = filename?.let { FileProvider.getUriForFile(
        context,
        context.getString(R.string.clipboard_provider_authority),
        File(ClipboardDao.clipFilesDir, it)
    ) }

    // todo: if slow we could decode images it in a coroutine, or use cached preview images
    @SuppressLint("SetTextI18n")
    fun setImageAndDescription(imageView: ImageView, textView: TextView) {
        if (mimeTypes == null || filename == null) return // should never happen
        try {
            val path = File(ClipboardDao.clipFilesDir, filename).absolutePath
            val opt = BitmapFactory.Options()
            opt.inJustDecodeBounds = true
            BitmapFactory.decodeFile(path, opt)
            // reduce size of images larger than the screen, only needs to fit half screen width
            val scale = opt.outWidth / (imageView.resources.displayMetrics.widthPixels * 2)
            opt.inSampleSize = scale
            opt.inJustDecodeBounds = false
            val bitmap = BitmapFactory.decodeFile(path, opt)
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap)
                textView.text = null
                return
            }
        } catch (e: Exception) {
            Log.w("ClipboardHistoryEntry", "could not load image for clip $id", e)
        }
        val description = if (text.isNullOrBlank()) ""
            else "\n" + textView.context.getString(R.string.item_description, text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val info = imageView.context.contentResolver.getTypeInfo(mimeTypes[0])
            info.icon.setTint(Settings.getValues().mColors.get(ColorType.EMOJI_CATEGORY))
            imageView.setImageIcon(info.icon)
            textView.text = textView.context.getString(R.string.item_type, info.label.toString()) + description
            return
        }
        imageView.setImageResource(R.drawable.ic_dictionary)
        Settings.getValues().mColors.setColor(imageView, ColorType.EMOJI_CATEGORY)
        textView.text = textView.context.getString(R.string.item_type, mimeTypes.first()) + description
    }

    companion object {
        /** A deterministic order keeps DiffUtil and staggered-grid span assignment in sync. */
        fun comparator(pinnedFirst: Boolean) = Comparator<ClipboardHistoryEntry> { first, second ->
            val pinOrder = second.isPinned.compareTo(first.isPinned)
            if (pinOrder != 0) {
                if (pinnedFirst) pinOrder else -pinOrder
            } else {
                val timeOrder = second.timeStamp.compareTo(first.timeStamp)
                if (timeOrder != 0) timeOrder else second.id.compareTo(first.id)
            }
        }
    }
}
