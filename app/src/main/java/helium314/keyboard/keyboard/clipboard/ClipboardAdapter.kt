// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.keyboard.clipboard

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import helium314.keyboard.latin.ClipboardHistoryEntry
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.theme.VisualThemeClipboardStyler

class ClipboardAdapter(
    private val clipboardLayoutParams: ClipboardLayoutParams,
    private val keyEventListener: OnKeyEventListener,
) : ListAdapter<ClipboardHistoryEntry, ClipboardAdapter.ViewHolder>(DIFF_CALLBACK) {

    var pinnedIconResId = 0
    var itemBackgroundId = 0
    var itemTypeFace: Typeface? = null
    var itemTextColor = 0
    var itemTextSize = 0f
    var themedEntryStyle: VisualThemeClipboardStyler.EntryStyle? = null

    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.clipboard_entry_key, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.setContent(getItem(position))
    }

    override fun getItemId(position: Int) = getItem(position).id

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view),
        View.OnClickListener, View.OnTouchListener, View.OnLongClickListener {

        private val pinnedIconView: ImageView
        private val contentView: LinearLayout
        private val contentTextView: TextView
        private val contentImageView: ImageView

        init {
            view.apply {
                setOnClickListener(this@ViewHolder)
                setOnTouchListener(this@ViewHolder)
                setOnLongClickListener(this@ViewHolder)
                if (themedEntryStyle == null) setBackgroundResource(itemBackgroundId)
                isHapticFeedbackEnabled = false
            }
            if (themedEntryStyle == null) {
                Settings.getValues().mColors.setBackground(view, ColorType.KEY_BACKGROUND)
            }
            pinnedIconView = view.findViewById<ImageView>(R.id.clipboard_entry_pinned_icon).apply {
                visibility = View.GONE
                themedEntryStyle?.pinIcon()?.let {
                    setImageDrawable(it)
                    clearColorFilter()
                } ?: setImageResource(pinnedIconResId)
            }
            contentView = view.findViewById(R.id.clipboard_entry_content)
            contentTextView = view.findViewById<TextView>(R.id.clipboard_entry_text_content).apply {
                typeface = itemTypeFace
                setTextColor(itemTextColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, itemTextSize)
            }
            contentImageView = view.findViewById(R.id.clipboard_entry_image_content)
            clipboardLayoutParams.setItemProperties(view)
            if (themedEntryStyle == null) {
                Settings.getValues().mColors.setColor(pinnedIconView, ColorType.CLIPBOARD_PIN)
            }
        }

        fun setContent(historyEntry: ClipboardHistoryEntry) {
            itemView.tag = historyEntry.id
            themedEntryStyle?.let { style ->
                itemView.background = style.background(historyEntry.isPinned)
                contentView.setPaddingRelative(
                    contentView.paddingStart,
                    contentView.paddingTop,
                    if (historyEntry.isPinned) style.pinnedContentEndPadding else 0,
                    contentView.paddingBottom,
                )
            }
            contentTextView.text = null
            contentImageView.setImageDrawable(null)
            if (historyEntry.filename != null) {
                historyEntry.setImageAndDescription(contentImageView, contentTextView)
            } else {
                contentTextView.text = historyEntry.text?.take(1000)
            }
            pinnedIconView.visibility = if (historyEntry.isPinned) View.VISIBLE else View.GONE
            contentImageView.visibility = if (historyEntry.filename != null) View.VISIBLE else View.GONE
            contentTextView.visibility = if (contentTextView.text.isNullOrEmpty()) View.GONE else View.VISIBLE
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(view: View, event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                keyEventListener.onKeyDown(view.tag as Long)
            }
            return false
        }

        override fun onClick(view: View) {
            keyEventListener.onKeyUp(view.tag as Long)
        }

        override fun onLongClick(view: View): Boolean {
            keyEventListener.onTogglePinned(view.tag as Long)
            return true
        }
    }

    private companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<ClipboardHistoryEntry>() {
            override fun areItemsTheSame(
                oldItem: ClipboardHistoryEntry,
                newItem: ClipboardHistoryEntry,
            ) = oldItem.id == newItem.id

            override fun areContentsTheSame(
                oldItem: ClipboardHistoryEntry,
                newItem: ClipboardHistoryEntry,
            ) = oldItem == newItem
        }
    }
}
