// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.keyboard.clipboard

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import helium314.keyboard.event.HapticEvent
import helium314.keyboard.keyboard.KeyboardActionListener
import helium314.keyboard.keyboard.KeyboardElement
import helium314.keyboard.keyboard.KeyboardLayoutSet
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.KeyboardTypeface
import helium314.keyboard.keyboard.MainKeyboardView
import helium314.keyboard.keyboard.PointerTracker
import helium314.keyboard.keyboard.internal.KeyDrawParams
import helium314.keyboard.keyboard.internal.KeyVisualAttributes
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.AudioAndHapticFeedbackManager
import helium314.keyboard.latin.ClipboardHistoryEntry
import helium314.keyboard.latin.ClipboardHistoryManager
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.database.ClipboardDao
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ResourceUtils
import helium314.keyboard.latin.utils.ToolbarKey
import helium314.keyboard.latin.utils.createToolbarKey
import helium314.keyboard.latin.utils.getCodeForToolbarKey
import helium314.keyboard.latin.utils.getCodeForToolbarKeyLongClick
import helium314.keyboard.latin.utils.getEnabledClipboardToolbarKeys
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.utils.setToolbarButtonsActivatedStateOnPrefChange
import helium314.keyboard.theme.VisualThemeClipboardStyler
import helium314.keyboard.theme.VisualThemeManager
import helium314.keyboard.theme.VisualThemeToolbarStyler

@SuppressLint("CustomViewStyleable")
class ClipboardHistoryView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet?,
    defStyle: Int = R.attr.clipboardHistoryViewStyle,
) : LinearLayout(context, attrs, defStyle), View.OnClickListener,
    ClipboardDao.Listener, OnKeyEventListener, View.OnLongClickListener,
    SharedPreferences.OnSharedPreferenceChangeListener {

    private val clipboardLayoutParams = ClipboardLayoutParams(context)
    private val pinIconId: Int
    private val keyBackgroundId: Int
    private val toolbarKeys = mutableListOf<ImageButton>()
    private val visualTheme = VisualThemeManager.activeTheme(context)

    private lateinit var clipboardRecyclerView: ClipboardHistoryRecyclerView
    private lateinit var placeholderView: TextView
    private lateinit var clipboardAdapter: ClipboardAdapter
    private lateinit var clipboardHistoryManager: ClipboardHistoryManager
    lateinit var keyboardActionListener: KeyboardActionListener
    private var pinRefreshPending = false
    private var pinRefreshAnchor: ScrollAnchor? = null
    private var pinRefreshItemAnimator: RecyclerView.ItemAnimator? = null

    init {
        val clipboardViewAttr = context.obtainStyledAttributes(
            attrs,
            R.styleable.ClipboardHistoryView,
            defStyle,
            R.style.ClipboardHistoryView,
        )
        pinIconId = clipboardViewAttr.getResourceId(
            R.styleable.ClipboardHistoryView_iconPinnedClip,
            0,
        )
        clipboardViewAttr.recycle()
        @SuppressLint("UseKtx")
        val keyboardViewAttr = context.obtainStyledAttributes(
            attrs,
            R.styleable.KeyboardView,
            defStyle,
            R.style.KeyboardView,
        )
        keyBackgroundId = keyboardViewAttr.getResourceId(
            R.styleable.KeyboardView_keyBackground,
            0,
        )
        keyboardViewAttr.recycle()
        if (Settings.getValues().mSecondaryStripVisible) {
            getEnabledClipboardToolbarKeys(context.prefs())
                .forEach { key ->
                    toolbarKeys.add(createToolbarKey(context, key).apply {
                        if (visualTheme.hasCustomToolbar) {
                            setImageDrawable(
                                VisualThemeToolbarStyler.themedToolbarIcon(
                                    context,
                                    visualTheme,
                                    key,
                                ),
                            )
                        }
                    })
                }
        }
        fitsSystemWindows = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val res = context.resources
        val width = ResourceUtils.getKeyboardWidth(context, Settings.getValues()) +
            paddingLeft + paddingRight
        val height = ResourceUtils.getSecondaryKeyboardHeight(res, Settings.getValues()) +
            paddingTop + paddingBottom
        setMeasuredDimension(width, height)
    }

    private fun initialize() {
        if (::clipboardAdapter.isInitialized) return
        clipboardAdapter = ClipboardAdapter(clipboardLayoutParams, this).apply {
            itemBackgroundId = keyBackgroundId
            pinnedIconResId = pinIconId
            themedEntryStyle = VisualThemeClipboardStyler.entryStyle(
                context,
                VisualThemeManager.activeTheme(context),
                clipboardLayoutParams.bottomRowKeyboardHeight,
            )
        }
        placeholderView = findViewById(R.id.clipboard_empty_view)
        clipboardRecyclerView = findViewById<ClipboardHistoryRecyclerView>(R.id.clipboard_list).apply {
            val columnCount = resources.getInteger(R.integer.config_clipboard_keyboard_col_count)
            layoutManager = StaggeredGridLayoutManager(
                columnCount,
                StaggeredGridLayoutManager.VERTICAL,
            )
            preserveFocusAfterLayout = false
            @Suppress("deprecation")
            persistentDrawingCache = PERSISTENT_NO_CACHE
            clipboardLayoutParams.setListProperties(this)
            placeholderView = this@ClipboardHistoryView.placeholderView
        }
        val clipboardStrip = KeyboardSwitcher.getInstance().clipboardStrip
        rootView.findViewById<ImageButton>(R.id.clipboard_toolbar_backpack_key)
            .setOnClickListener { sendToolbarCode(KeyCode.ALPHA) }
        rootView.findViewById<ImageButton>(R.id.clipboard_toolbar_return_key)
            .setOnClickListener { sendToolbarCode(KeyCode.ALPHA) }
        val colors = Settings.getValues().mColors
        toolbarKeys.forEach {
            clipboardStrip.addView(it)
            it.setOnClickListener(this@ClipboardHistoryView)
            it.setOnLongClickListener(this@ClipboardHistoryView)
            colors.setColor(it, ColorType.TOOL_BAR_KEY)
            it.setBackgroundColor(Color.TRANSPARENT)
            VisualThemeToolbarStyler.styleToolbarButton(it, visualTheme)
        }
    }

    private fun setupClipKey(params: KeyDrawParams) {
        clipboardAdapter.apply {
            itemBackgroundId = keyBackgroundId
            itemTypeFace = params.mTypeface
            itemTextColor = params.mTextColor
            itemTextSize = params.mLabelSize.toFloat()
        }
    }

    private fun setupToolbarKeys() {
        val toolbarKeyLayoutParams = LayoutParams(
            VisualThemeToolbarStyler.toolbarKeyWidthPx(
                context,
                visualTheme,
                resources.getDimensionPixelSize(R.dimen.config_suggestions_strip_edge_key_width),
            ),
            LayoutParams.MATCH_PARENT,
        )
        toolbarKeys.forEach { it.layoutParams = toolbarKeyLayoutParams }
    }

    private fun setupBottomRowKeyboard(
        editorInfo: EditorInfo,
        listener: KeyboardActionListener,
    ) {
        val keyboardView = findViewById<MainKeyboardView>(R.id.bottom_row_keyboard)
        keyboardView.setKeyboardActionListener(listener)
        PointerTracker.switchTo(keyboardView)
        val kls = KeyboardLayoutSet.Builder.buildEmojiClipBottomRow(context, editorInfo)
        keyboardView.setKeyboard(kls.getKeyboard(KeyboardElement.CLIPBOARD_BOTTOM_ROW))
    }

    fun setHardwareAcceleratedDrawingEnabled(enabled: Boolean) {
        if (enabled) setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun startClipboardHistory(
        historyManager: ClipboardHistoryManager,
        keyVisualAttr: KeyVisualAttributes?,
        editorInfo: EditorInfo,
        keyboardActionListener: KeyboardActionListener,
    ) {
        clipboardHistoryManager = historyManager
        this.keyboardActionListener = keyboardActionListener
        initialize()
        setupToolbarKeys()
        historyManager.prepareClipboardHistory()
        historyManager.setHistoryChangeListener(this)
        clipboardRecyclerView.historyManager = historyManager
        clipboardAdapter.submitList(historyManager.getHistoryEntries())

        val params = KeyDrawParams()
        params.updateParams(clipboardLayoutParams.bottomRowKeyboardHeight, keyVisualAttr)
        KeyboardTypeface.customTypeface()?.let { params.mTypeface = it }
        setupClipKey(params)
        setupBottomRowKeyboard(editorInfo, keyboardActionListener)

        placeholderView.apply {
            KeyboardTypeface.applyToTextView(this)
            setTextColor(params.mTextColor)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, params.mLabelSize.toFloat() * 2)
        }
        val settings = Settings.getInstance()
        clipboardRecyclerView.apply {
            adapter = clipboardAdapter
            val keyboardWidth = ResourceUtils.getKeyboardWidth(context, settings.current)
            layoutParams.width = keyboardWidth
            ClipboardLayoutParams(context).setListProperties(this)
            val keyboardAttr = context.obtainStyledAttributes(
                null,
                R.styleable.Keyboard,
                R.attr.keyboardStyle,
                R.style.Keyboard,
            )
            val leftPadding = (keyboardAttr.getFraction(
                R.styleable.Keyboard_keyboardLeftPadding,
                keyboardWidth,
                keyboardWidth,
                0f,
            ) * settings.current.mSidePaddingScale).toInt()
            val rightPadding = (keyboardAttr.getFraction(
                R.styleable.Keyboard_keyboardRightPadding,
                keyboardWidth,
                keyboardWidth,
                0f,
            ) * settings.current.mSidePaddingScale).toInt()
            keyboardAttr.recycle()
            setPadding(leftPadding, paddingTop, rightPadding, paddingBottom)
        }
        toolbarKeys.forEach { it.isEnabled = false; it.isEnabled = true }
    }

    fun stopClipboardHistory() {
        if (!::clipboardAdapter.isInitialized) return
        if (pinRefreshPending) {
            clipboardRecyclerView.itemAnimator = pinRefreshItemAnimator
            pinRefreshPending = false
            pinRefreshAnchor = null
            pinRefreshItemAnimator = null
        }
        clipboardRecyclerView.adapter = null
        clipboardRecyclerView.historyManager = null
        clipboardHistoryManager.setHistoryChangeListener(null)
    }

    override fun onClick(view: View) {
        val tag = view.tag
        if (tag !is ToolbarKey) return
        AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(
            KeyCode.NOT_SPECIFIED,
            this,
            HapticEvent.KEY_PRESS,
        )
        val code = getCodeForToolbarKey(tag)
        if (code != KeyCode.UNSPECIFIED) {
            keyboardActionListener.onCodeInput(
                code,
                Constants.NOT_A_COORDINATE,
                Constants.NOT_A_COORDINATE,
                false,
            )
        }
    }

    private fun sendToolbarCode(code: Int) {
        AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(
            KeyCode.NOT_SPECIFIED,
            this,
            HapticEvent.KEY_PRESS,
        )
        keyboardActionListener.onCodeInput(
            code,
            Constants.NOT_A_COORDINATE,
            Constants.NOT_A_COORDINATE,
            false,
        )
    }

    override fun onLongClick(view: View): Boolean {
        val tag = view.tag
        if (tag !is ToolbarKey) return false
        AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(
            KeyCode.NOT_SPECIFIED,
            this,
            HapticEvent.KEY_LONG_PRESS,
        )
        val longClickCode = getCodeForToolbarKeyLongClick(tag)
        if (longClickCode != KeyCode.UNSPECIFIED) {
            keyboardActionListener.onCodeInput(
                longClickCode,
                Constants.NOT_A_COORDINATE,
                Constants.NOT_A_COORDINATE,
                false,
            )
        }
        return true
    }

    override fun onKeyDown(clipId: Long) {
        keyboardActionListener.onPressKey(
            KeyCode.NOT_SPECIFIED,
            0,
            true,
            HapticEvent.KEY_PRESS,
        )
    }

    override fun onKeyUp(clipId: Long) {
        val clipContent = clipboardHistoryManager.getHistoryEntryContent(clipId)
        if (clipContent?.filename != null) {
            keyboardActionListener.onContent(clipContent.getContentInfo(context))
        } else {
            keyboardActionListener.onTextInput(clipContent?.text)
        }
        keyboardActionListener.onReleaseKey(KeyCode.NOT_SPECIFIED, false)
        if (Settings.getValues().mAlphaAfterClipHistoryEntry) {
            keyboardActionListener.onCodeInput(
                KeyCode.ALPHA,
                Constants.NOT_A_COORDINATE,
                Constants.NOT_A_COORDINATE,
                false,
            )
        }
    }

    override fun onTogglePinned(clipId: Long) {
        clipboardHistoryManager.toggleClipPinned(clipId)
    }

    override fun onClipboardChanged(
        entries: List<ClipboardHistoryEntry>,
        focusedEntryId: Long?,
    ) {
        val oldFocusedEntry = clipboardAdapter.currentList
            .firstOrNull { it.id == focusedEntryId }
        val newFocusedEntry = entries.firstOrNull { it.id == focusedEntryId }
        val pinStateChanged = oldFocusedEntry != null && newFocusedEntry != null &&
            oldFocusedEntry.isPinned != newFocusedEntry.isPinned
        if (pinStateChanged) preparePinRefresh(focusedEntryId)
        clipboardAdapter.submitList(entries) { finishPendingPinRefresh() }
    }

    private fun preparePinRefresh(movingEntryId: Long?) {
        if (pinRefreshPending) return
        pinRefreshItemAnimator = clipboardRecyclerView.itemAnimator
        clipboardRecyclerView.itemAnimator = null
        pinRefreshPending = true
        pinRefreshAnchor = captureScrollAnchor(excludingId = movingEntryId)
    }

    private fun captureScrollAnchor(excludingId: Long?): ScrollAnchor? {
        val layoutManager = clipboardRecyclerView.layoutManager
            as? StaggeredGridLayoutManager ?: return null
        return (0 until clipboardRecyclerView.childCount)
            .asSequence()
            .map { clipboardRecyclerView.getChildAt(it) }
            .map { child ->
                val holder = clipboardRecyclerView.getChildViewHolder(child)
                ScrollAnchor(
                    entryId = holder.itemId,
                    topOffset = layoutManager.getDecoratedTop(child) -
                        clipboardRecyclerView.paddingTop,
                    left = layoutManager.getDecoratedLeft(child),
                )
            }
            .filter { it.entryId != RecyclerView.NO_ID && it.entryId != excludingId }
            .minWithOrNull(compareBy(ScrollAnchor::topOffset, ScrollAnchor::left))
    }

    private fun finishPendingPinRefresh() {
        if (!pinRefreshPending) return
        val anchor = pinRefreshAnchor
        val itemAnimator = pinRefreshItemAnimator
        pinRefreshPending = false
        pinRefreshAnchor = null
        pinRefreshItemAnimator = null

        if (clipboardRecyclerView.adapter !== clipboardAdapter) {
            clipboardRecyclerView.itemAnimator = itemAnimator
            return
        }
        val layoutManager = clipboardRecyclerView.layoutManager
            as? StaggeredGridLayoutManager
        val anchorPosition = anchor?.let { captured ->
            clipboardAdapter.currentList.indexOfFirst { it.id == captured.entryId }
        } ?: RecyclerView.NO_POSITION

        // Detaching guarantees that no old ViewHolder or span assignment can survive the move.
        clipboardRecyclerView.adapter = null
        clipboardRecyclerView.recycledViewPool.clear()
        layoutManager?.invalidateSpanAssignments()
        clipboardRecyclerView.adapter = clipboardAdapter
        if (anchorPosition != RecyclerView.NO_POSITION && anchor != null) {
            layoutManager?.scrollToPositionWithOffset(anchorPosition, anchor.topOffset)
        }
        clipboardRecyclerView.itemAnimator = itemAnimator
        clipboardRecyclerView.requestLayout()
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
        setToolbarButtonsActivatedStateOnPrefChange(
            KeyboardSwitcher.getInstance().clipboardStrip,
            key,
        )
        if (
            ::clipboardHistoryManager.isInitialized &&
            key == Settings.PREF_CLIPBOARD_HISTORY_PINNED_FIRST
        ) {
            Settings.getInstance().onSharedPreferenceChanged(prefs, key)
            clipboardHistoryManager.sortHistoryEntries()
        }
    }

    private data class ScrollAnchor(
        val entryId: Long,
        val topOffset: Int,
        val left: Int,
    )
}
