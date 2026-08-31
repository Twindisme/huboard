/*
 * Copyright (C) 2011 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */
package helium314.keyboard.latin.suggestions

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.View.OnLongClickListener
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.doOnNextLayout
import androidx.core.view.isGone
import androidx.core.view.isVisible
import helium314.keyboard.event.HapticEvent
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.AudioAndHapticFeedbackManager
import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.R
import helium314.keyboard.latin.SuggestedWords
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.Colors
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.define.DebugFlags
import helium314.keyboard.latin.settings.DebugSettings
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.ToolbarKey
import helium314.keyboard.latin.utils.ToolbarMode
import helium314.keyboard.latin.utils.addPinnedKey
import helium314.keyboard.latin.utils.createToolbarKey
import helium314.keyboard.latin.utils.dpToPx
import helium314.keyboard.latin.utils.getCodeForToolbarKey
import helium314.keyboard.latin.utils.getCodeForToolbarKeyLongClick
import helium314.keyboard.latin.utils.getEnabledToolbarKeys
import helium314.keyboard.latin.utils.getPinnedToolbarKeys
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.utils.removeFirst
import helium314.keyboard.latin.utils.removePinnedKey
import helium314.keyboard.latin.utils.setToolbarButtonsActivatedStateOnPrefChange
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import helium314.keyboard.theme.ThemeAsset
import helium314.keyboard.theme.VisualThemeManager
import helium314.keyboard.theme.VisualThemeToolbarStyler

@SuppressLint("InflateParams")
class SuggestionStripView(context: Context, attrs: AttributeSet?, defStyle: Int) :
    RelativeLayout(context, attrs, defStyle), View.OnClickListener, OnLongClickListener, OnSharedPreferenceChangeListener {

    /** Construct a [SuggestionStripView] for showing suggestions to be picked by the user. */
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, R.attr.suggestionStripViewStyle)

    interface Listener {
        fun pickSuggestionManually(word: SuggestedWordInfo?)
        fun onCodeInput(primaryCode: Int, x: Int, y: Int, isKeyRepeat: Boolean)
        fun removeSuggestion(word: String?)
        fun removeExternalSuggestions()
        fun onSwipeDownOnToolbar()
    }

    private val moreSuggestionsContainer: View
    private val wordViews = ArrayList<TextView>()
    private val debugInfoViews = ArrayList<TextView>()
    private val dividerViews = ArrayList<View>()
    private val visualTheme = VisualThemeManager.activeTheme(context)
    private val hasDecoratedToolbar = visualTheme.hasCustomToolbar

    init {
        val inflater = LayoutInflater.from(context)
        inflater.inflate(R.layout.suggestions_strip, this)
        moreSuggestionsContainer = inflater.inflate(R.layout.more_suggestions, null)

        val colors = Settings.getValues().mColors
        if (hasDecoratedToolbar) {
            background = visualTheme.drawable(context, ThemeAsset.TOOLBAR_BACKGROUND)
        } else {
            colors.setBackground(this, ColorType.STRIP_BACKGROUND)
        }
        repeat(SuggestedWords.MAX_SUGGESTIONS) {
            val word = TextView(context, null, R.attr.suggestionWordStyle)
            word.contentDescription = resources.getString(R.string.spoken_empty_suggestion)
            word.setOnClickListener(this)
            word.setOnLongClickListener(this)
            if (hasDecoratedToolbar) word.setBackgroundColor(Color.TRANSPARENT)
            else colors.setBackground(word, ColorType.STRIP_BACKGROUND)
            wordViews.add(word)
            val divider = inflater.inflate(R.layout.suggestion_divider, null)
            dividerViews.add(divider)
            val info = TextView(context, null, R.attr.suggestionWordStyle)
            info.setTextColor(colors.get(ColorType.KEY_TEXT))
            info.setTextSize(TypedValue.COMPLEX_UNIT_DIP, DEBUG_INFO_TEXT_SIZE_IN_DIP)
            debugInfoViews.add(info)
        }

        DEBUG_SUGGESTIONS = context.prefs().getBoolean(DebugSettings.PREF_SHOW_SUGGESTION_INFOS, Defaults.PREF_SHOW_SUGGESTION_INFOS)
    }

    // toolbar views, drawables and setup
    private val toolbar: ViewGroup = findViewById(R.id.toolbar)
    private val toolbarContainer: View = findViewById(R.id.toolbar_container)
    private val pinnedKeys: ViewGroup = findViewById(R.id.pinned_keys)
    private val suggestionsStrip: ViewGroup = findViewById(R.id.suggestions_strip)
    private val toolbarStartContainer = findViewById<View>(R.id.visual_theme_toolbar_start_container)
    private val backpackKey = findViewById<ImageButton>(R.id.visual_theme_toolbar_start_key)
    private val toolbarEndArt = findViewById<ImageView>(R.id.visual_theme_toolbar_end_art)
    private val toolbarExpandKey = findViewById<ImageButton>(R.id.suggestions_strip_toolbar_key)
    private val themeSearchTag = Any()
    private val incognitoIcon = KeyboardIconsSet.instance.getNewDrawable(ToolbarKey.INCOGNITO.name, context)
    private val toolbarArrowIcon = KeyboardIconsSet.instance.getNewDrawable(KeyboardIconsSet.NAME_TOOLBAR_KEY, context)
    private var defaultToolbarBackground: Drawable = Color.TRANSPARENT.toDrawable()
    private val enabledToolKeyBackground = GradientDrawable()
    private var direction = 1 // 1 if LTR, -1 if RTL

    private val toolbarKeyLayoutParams = LinearLayout.LayoutParams(
        if (hasDecoratedToolbar) 56.dpToPx(resources)
        else resources.getDimensionPixelSize(R.dimen.config_suggestions_strip_edge_key_width),
        LinearLayout.LayoutParams.MATCH_PARENT
    )

    init {
        val colors = Settings.getValues().mColors

        if (hasDecoratedToolbar) {
            backpackKey.setBackgroundColor(Color.TRANSPARENT)
            backpackKey.setImageDrawable(VisualThemeToolbarStyler.stateDrawable(
                visualTheme.drawable(context, ThemeAsset.TOOLBAR_START),
                visualTheme.drawable(context, ThemeAsset.TOOLBAR_START_PRESSED),
            ))
            toolbarEndArt.setImageDrawable(
                visualTheme.drawable(context, ThemeAsset.TOOLBAR_END),
            )
            toolbarExpandKey.setBackgroundColor(Color.TRANSPARENT)
        } else {
            toolbarStartContainer.isGone = true
            toolbarEndArt.isGone = true
            val toolbarHeight =
                resources.getDimension(R.dimen.config_suggestions_strip_height).toInt()
            (toolbarExpandKey.parent as View).layoutParams.width = toolbarHeight
            toolbarExpandKey.layoutParams.height = toolbarHeight
            toolbarExpandKey.layoutParams.width = toolbarHeight
            toolbarExpandKey.translationY = 0f
            colors.setBackground(toolbarExpandKey, ColorType.STRIP_BACKGROUND)
            colors.setColor(toolbarExpandKey, ColorType.TOOL_BAR_EXPAND_KEY)
            colors.setColor(toolbarExpandKey.background, ColorType.TOOL_BAR_EXPAND_KEY_BACKGROUND)
            defaultToolbarBackground = toolbarExpandKey.background
        }

        // background indicator for pinned keys
        val color = colors.get(ColorType.TOOL_BAR_KEY_ENABLED_BACKGROUND) or -0x1000000 // ignore alpha (in Java this is more readable 0xFF000000)
        enabledToolKeyBackground.colors = intArrayOf(color, Color.TRANSPARENT)
        enabledToolKeyBackground.gradientType = GradientDrawable.RADIAL_GRADIENT
        enabledToolKeyBackground.gradientRadius = resources.getDimensionPixelSize(R.dimen.config_suggestions_strip_height) / 2.1f

        val mToolbarMode = if (isGone) ToolbarMode.HIDDEN else Settings.getValues().mToolbarMode
        if (mToolbarMode == ToolbarMode.TOOLBAR_KEYS ||
            (hasDecoratedToolbar && mToolbarMode == ToolbarMode.EXPANDABLE)
        ) {
            setToolbarVisibility(true)
        }

        val toolbarButtons = if (hasDecoratedToolbar) listOf(
            createThemedToolbarKey(ToolbarKey.SETTINGS, ThemeAsset.TOOLBAR_KEYBOARD),
            createThemedToolbarKey(ToolbarKey.SELECT_WORD, ThemeAsset.TOOLBAR_CURSOR),
            createThemedSearchKey(),
            createThemedToolbarKey(ToolbarKey.EMOJI, ThemeAsset.TOOLBAR_EMOJI),
        ) else getEnabledToolbarKeys(context.prefs()).map { createToolbarKey(context, it) }
        for (button in toolbarButtons) {
            button.layoutParams = toolbarKeyLayoutParams
            setupKey(button, colors)
            toolbar.addView(button)
        }
        for (pinnedKey in getPinnedToolbarKeys(context.prefs())) {
            val button = createToolbarKey(context, pinnedKey)
            button.layoutParams = toolbarKeyLayoutParams
            setupKey(button, colors)
            pinnedKeys.addView(button)
            val pinnedKeyInToolbar = toolbar.findViewWithTag<View>(pinnedKey)
            if (pinnedKeyInToolbar != null && Settings.getValues().mQuickPinToolbarKeys)
                pinnedKeyInToolbar.background = enabledToolKeyBackground
        }
        toolbarContainer.doOnNextLayout {
            // set min with of the toolbar so the weight of the toolbar keys actually does something
            // todo: results in requestLayout() improperly called by android.widget.LinearLayout during layout: running second layout pass
            toolbar.minimumWidth = toolbarContainer.width
        }

        updateKeys()
    }

    private lateinit var listener: Listener
    private var suggestedWords = SuggestedWords.getEmptyInstance()
    private var startIndexOfMoreSuggestions = 0
    private var isExternalSuggestionVisible = false // Required to disable the more suggestions if other suggestions are visible
    private val layoutHelper = SuggestionStripLayoutHelper(context, attrs, defStyle, wordViews, dividerViews, debugInfoViews)
    private val moreSuggestionsView = moreSuggestionsContainer.findViewById<MoreSuggestionsView>(R.id.more_suggestions_view).apply {
        val slidingListener = object : SimpleOnGestureListener() {
            override fun onScroll(down: MotionEvent?, me: MotionEvent, deltaX: Float, deltaY: Float): Boolean {
                if (down == null) return false
                val dy = me.y - down.y
                val dx = me.x - down.x

                if (Settings.getValues().mToolbarSwipeDownToHide && dy > 50.dpToPx(resources) && abs(dy) > abs(dx)) {
                    listener.onSwipeDownOnToolbar()
                    return true
                }

                return if (!isExternalSuggestionVisible && toolbarContainer.visibility != VISIBLE && deltaY > 0 && dy < (-10).dpToPx(resources)) showMoreSuggestions()
                else false
            }
        }
        gestureDetector = GestureDetector(context, slidingListener)
    }

    // public stuff

    val isShowingMoreSuggestionPanel get() = moreSuggestionsView.isShowingInParent

    /** A connection back to the input method. */
    fun setListener(newListener: Listener, inputView: View) {
        listener = newListener
        moreSuggestionsView.listener = newListener
        moreSuggestionsView.mainKeyboardView = inputView.findViewById(R.id.keyboard_view)
    }

    fun setRtl(isRtlLanguage: Boolean) {
        val newLayoutDirection: Int
        if (!Settings.getValues().mVarToolbarDirection)
            newLayoutDirection = LAYOUT_DIRECTION_LOCALE
        else {
            newLayoutDirection = if (isRtlLanguage) LAYOUT_DIRECTION_RTL else LAYOUT_DIRECTION_LTR
            direction = if (isRtlLanguage) -1 else 1
        }
        layoutDirection = newLayoutDirection
        suggestionsStrip.layoutDirection = newLayoutDirection
        if (!hasDecoratedToolbar) {
            toolbarExpandKey.scaleX =
                (if (toolbarContainer.visibility != VISIBLE) 1f else -1f) * direction
        }
    }

    fun setToolbarVisibility(toolbarVisible: Boolean) {
        pinnedKeys.isVisible = !toolbarVisible
        suggestionsStrip.isVisible = !toolbarVisible
        toolbarContainer.isVisible = toolbarVisible
        if (!hasDecoratedToolbar) {
            toolbarExpandKey.scaleX = (if (toolbarVisible) -1f else 1f) * direction
        }

        if (DEBUG_SUGGESTIONS) {
            for (view in debugInfoViews) {
                view.visibility = suggestionsStrip.visibility
            }
        }

    }

    fun setSuggestions(suggestions: SuggestedWords, isRtlLanguage: Boolean) {
        clear()
        setRtl(isRtlLanguage)
        suggestedWords = suggestions
        startIndexOfMoreSuggestions = layoutHelper.layoutAndReturnStartIndexOfMoreSuggestions(
            context, suggestedWords, suggestionsStrip, this
        )
        isExternalSuggestionVisible = false
        updateKeys()
    }

    fun setExternalSuggestionView(view: View?, addCloseButton: Boolean) {
        clear()
        isExternalSuggestionVisible = true

        if (addCloseButton) {
            val wrapper = LinearLayout(context)
            suggestionsStrip.doOnNextLayout {
                wrapper.layoutParams = LinearLayout.LayoutParams(suggestionsStrip.width - 30.dpToPx(resources), LayoutParams.MATCH_PARENT)
            }
            wrapper.addView(view)
            suggestionsStrip.addView(wrapper)

            val closeButton = createToolbarKey(context, ToolbarKey.CLOSE_HISTORY)
            closeButton.layoutParams = toolbarKeyLayoutParams
            setupKey(closeButton, Settings.getValues().mColors)
            closeButton.setOnClickListener {
                listener.removeExternalSuggestions()
            }
            suggestionsStrip.addView(closeButton)
        } else {
            suggestionsStrip.addView(view)
        }

        if (Settings.getValues().mAutoHideToolbar) setToolbarVisibility(false)
    }

    fun setMoreSuggestionsHeight(remainingHeight: Int) {
        layoutHelper.setMoreSuggestionsHeight(remainingHeight)
    }

    fun dismissMoreSuggestionsPanel() {
        moreSuggestionsView.dismissPopupKeysPanel()
    }

    // overrides: necessarily public, but not used from outside

    override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String?) {
        setToolbarButtonsActivatedStateOnPrefChange(pinnedKeys, key)
        setToolbarButtonsActivatedStateOnPrefChange(toolbar, key)
        if (key == Settings.PREF_ALWAYS_INCOGNITO_MODE)
            GlobalScope.launch { delay(10); withContext(Dispatchers.Main) { updateKeys() } }
    }

    override fun onVisibilityChanged(view: View, visibility: Int) {
        super.onVisibilityChanged(view, visibility)
        // workaround for a bug with inline suggestions views that just keep showing up otherwise, https://github.com/HeliBorg/HeliBoard/pull/386
        if (view === this) {
            suggestionsStrip.visibility = if (toolbarContainer.isVisible) GONE else visibility
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        dismissMoreSuggestionsPanel()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        // Called by the framework when the size is known. Show the important notice if applicable.
        // This may be overridden by showing suggestions later, if applicable.
    }

    override fun dispatchPopulateAccessibilityEvent(event: AccessibilityEvent): Boolean {
        // Don't populate accessibility event with suggested words and voice key.
        return true
    }

    override fun onInterceptTouchEvent(motionEvent: MotionEvent): Boolean {
        // Detecting sliding up finger to show MoreSuggestionsView.
        return moreSuggestionsView.shouldInterceptTouchEvent(motionEvent)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(motionEvent: MotionEvent): Boolean {
        moreSuggestionsView.touchEvent(motionEvent)
        return true
    }

    override fun onClick(view: View) {
        AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(KeyCode.NOT_SPECIFIED, this, HapticEvent.KEY_PRESS)
        val tag = view.tag
        if (tag === themeSearchTag) {
            listener.onCodeInput(Constants.CODE_ENTER, Constants.SUGGESTION_STRIP_COORDINATE, Constants.SUGGESTION_STRIP_COORDINATE, false)
            return
        }
        if (tag is ToolbarKey) {
            val code = getCodeForToolbarKey(tag)
            if (code != KeyCode.UNSPECIFIED) {
                Log.d(TAG, "click toolbar key $tag")
                listener.onCodeInput(code, Constants.SUGGESTION_STRIP_COORDINATE, Constants.SUGGESTION_STRIP_COORDINATE, false)
                return
            }
        }
        if (view === toolbarExpandKey) {
            setToolbarVisibility(toolbarContainer.visibility != VISIBLE)
        }

        // tag for word views is set in SuggestionStripLayoutHelper (setupWordViewsTextAndColor, layoutPunctuationSuggestions)
        if (tag is Int) {
            if (tag >= suggestedWords.size()) {
                return
            }
            val wordInfo = suggestedWords.getInfo(tag)
            listener.pickSuggestionManually(wordInfo)
        }
    }

    override fun onLongClick(view: View): Boolean {
        AudioAndHapticFeedbackManager.getInstance().performHapticFeedback(this, HapticEvent.KEY_LONG_PRESS)
        if (view.tag === themeSearchTag) return true
        if (view.tag is ToolbarKey) {
            onLongClickToolbarKey(view)
            return true
        }
        return if (view is TextView && wordViews.contains(view)) {
            onLongClickSuggestion(view)
        } else {
            showMoreSuggestions()
        }
    }

    // actually private stuff

    private fun onLongClickToolbarKey(view: View) {
        val tag = view.tag as? ToolbarKey ?: return
        if (!Settings.getValues().mQuickPinToolbarKeys || view.parent === pinnedKeys) {
            val longClickCode = getCodeForToolbarKeyLongClick(tag)
            if (longClickCode != KeyCode.UNSPECIFIED) {
                listener.onCodeInput(longClickCode, Constants.SUGGESTION_STRIP_COORDINATE, Constants.SUGGESTION_STRIP_COORDINATE, false)
            }
        } else if (view.parent === toolbar) {
            val pinnedKeyView = pinnedKeys.findViewWithTag<View>(tag)
            if (pinnedKeyView == null) {
                addKeyToPinnedKeys(tag)
                toolbar.findViewWithTag<View>(tag).background = enabledToolKeyBackground
                addPinnedKey(context.prefs(), tag)
            } else {
                removePinnedKey(context.prefs(), tag)
                toolbar.findViewWithTag<View>(tag).background = defaultToolbarBackground.constantState?.newDrawable(resources)
                pinnedKeys.removeView(pinnedKeyView)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility") // no need for View#performClick, we only return false mostly anyway
    private fun onLongClickSuggestion(wordView: TextView): Boolean {
        var showIcon = true
        if (wordView.tag is Int) {
            val index = wordView.tag as Int
            val type = suggestedWords.getInfo(index).mSourceDict
            if (type == Dictionary.DICTIONARY_USER_TYPED || type == Dictionary.DICTIONARY_HARDCODED)
                showIcon = false
        }
        if (showIcon) {
            val icon = KeyboardIconsSet.instance.getNewDrawable(KeyboardIconsSet.NAME_BIN, context)!!
            Settings.getValues().mColors.setColor(icon, ColorType.REMOVE_SUGGESTION_ICON)
            val w = icon.intrinsicWidth
            val h = icon.intrinsicHeight
            wordView.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null)
            wordView.ellipsize = TextUtils.TruncateAt.END
            val downOk = AtomicBoolean(false)
            wordView.setOnTouchListener { _, motionEvent ->
                if (motionEvent.action == MotionEvent.ACTION_UP && downOk.get()) {
                    val x = motionEvent.x
                    val y = motionEvent.y
                    if (0 < x && x < w && 0 < y && y < h) {
                        removeSuggestion(wordView)
                        wordView.cancelLongPress()
                        wordView.isPressed = false
                        return@setOnTouchListener true
                    }
                } else if (motionEvent.action == MotionEvent.ACTION_DOWN) {
                    val x = motionEvent.x
                    val y = motionEvent.y
                    if (0 < x && x < w && 0 < y && y < h) {
                        downOk.set(true)
                    }
                }
                false
            }
        }
        if (DebugFlags.DEBUG_ENABLED && (isShowingMoreSuggestionPanel || !showMoreSuggestions())) {
            showSourceDict(wordView)
            return true
        }
        return showMoreSuggestions()
    }

    private fun showMoreSuggestions(): Boolean {
        if (suggestedWords.size() <= startIndexOfMoreSuggestions) {
            return false
        }
        if (!moreSuggestionsView.show(
                suggestedWords, startIndexOfMoreSuggestions, moreSuggestionsContainer, layoutHelper, this
        ))
            return false
        for (i in 0..<startIndexOfMoreSuggestions) {
            wordViews[i].isPressed = false
        }
        return true
    }

    private fun showSourceDict(wordView: TextView) {
        val word = wordView.text.toString()
        val index = wordView.tag as? Int ?: return
        if (index >= suggestedWords.size()) return
        val info = suggestedWords.getInfo(index)
        if (info.word != word) return

        val text = info.mSourceDict.mDictType + ":" + info.mSourceDict.mLocale
        if (isShowingMoreSuggestionPanel) {
            moreSuggestionsView.dismissPopupKeysPanel()
        }
        KeyboardSwitcher.getInstance().showToast(text, true)
    }

    private fun removeSuggestion(wordView: TextView) {
        val word = wordView.text.toString()
        listener.removeSuggestion(word)
        moreSuggestionsView.dismissPopupKeysPanel()
        // show suggestions, but without the removed word
        val suggestedWordInfos = ArrayList<SuggestedWordInfo>()
        for (i in 0..<suggestedWords.size()) {
            val info = suggestedWords.getInfo(i)
            if (info.word != word) suggestedWordInfos.add(info)
        }
        suggestedWords.mRawSuggestions?.removeFirst { it.word == word }

        val newSuggestedWords = SuggestedWords(
            suggestedWordInfos, suggestedWords.mRawSuggestions, suggestedWords.typedWordInfo, suggestedWords.mTypedWordValid,
            suggestedWords.mWillAutoCorrect, suggestedWords.mIsObsoleteSuggestions, suggestedWords.mInputStyle, suggestedWords.mSequenceNumber
        )
        setSuggestions(newSuggestedWords, direction != 1)
        suggestionsStrip.isVisible = true

        // Show the toolbar if no suggestions are left and the "Auto show toolbar" setting is enabled
        if (this.suggestedWords.isEmpty && Settings.getValues().mAutoShowToolbar) {
            setToolbarVisibility(true)
        }
    }

    private fun clear() {
        suggestionsStrip.removeAllViews()
        if (DEBUG_SUGGESTIONS) removeAllDebugInfoViews()
        if (!toolbarContainer.isVisible)
            suggestionsStrip.isVisible = true
        dismissMoreSuggestionsPanel()
        for (word in wordViews) {
            word.setOnTouchListener(null)
        }
    }

    private fun removeAllDebugInfoViews() {
        for (debugInfoView in debugInfoViews) {
            val parent = debugInfoView.parent
            if (parent is ViewGroup) {
                parent.removeView(debugInfoView)
            }
        }
    }

    fun updateVoiceKey() {
        val show = Settings.getValues().mShowsVoiceInputKey
        toolbar.findViewWithTag<View>(ToolbarKey.VOICE)?.isVisible = show
        pinnedKeys.findViewWithTag<View>(ToolbarKey.VOICE)?.isVisible = show
    }

    private fun updateKeys() {
        updateVoiceKey()
        val settingsValues = Settings.getValues()

        val toolbarIsExpandable = settingsValues.mToolbarMode == ToolbarMode.EXPANDABLE
        if (hasDecoratedToolbar) {
            backpackKey.clearColorFilter()
            backpackKey.tag = ToolbarKey.CLIPBOARD
            backpackKey.setOnClickListener(this)
            toolbarExpandKey.setImageDrawable(
                visualTheme.drawable(context, ThemeAsset.TOOLBAR_EXPAND),
            )
            toolbarExpandKey.clearColorFilter()
            toolbarExpandKey.isVisible = toolbarIsExpandable
        } else if (settingsValues.mIncognitoModeEnabled) {
            toolbarExpandKey.setImageDrawable(incognitoIcon)
            toolbarExpandKey.isVisible = true
        } else {
            toolbarExpandKey.setImageDrawable(toolbarArrowIcon)
            toolbarExpandKey.isVisible = toolbarIsExpandable
        }

        toolbarExpandKey.setOnClickListener(if (!toolbarIsExpandable) null else this)
        pinnedKeys.visibility = suggestionsStrip.visibility
        isExternalSuggestionVisible = false
    }

    private fun addKeyToPinnedKeys(pinnedKey: ToolbarKey) {
        val original = toolbar.findViewWithTag<ImageButton>(pinnedKey) ?: return
        // copy the original key to a new ImageButton
        val copy = ImageButton(context, null, R.attr.suggestionWordStyle)
        copy.tag = pinnedKey
        copy.scaleType = original.scaleType
        copy.scaleX = original.scaleX
        copy.scaleY = original.scaleY
        copy.contentDescription = original.contentDescription
        copy.setImageDrawable(original.drawable)
        copy.layoutParams = original.layoutParams
        copy.isActivated = original.isActivated
        setupKey(copy, Settings.getValues().mColors)
        pinnedKeys.addView(copy)
    }

    private fun setupKey(view: ImageButton, colors: Colors) {
        view.setOnClickListener(this)
        view.setOnLongClickListener(this)
        (view.layoutParams as LinearLayout.LayoutParams).weight = 1f
        colors.setColor(view, ColorType.TOOL_BAR_KEY)
        if (hasDecoratedToolbar) view.setBackgroundColor(Color.TRANSPARENT)
        else colors.setBackground(view, ColorType.STRIP_BACKGROUND)
    }

    private fun createThemedToolbarKey(key: ToolbarKey, asset: String): ImageButton =
        createToolbarKey(context, key).apply {
            visualTheme.drawable(context, asset)?.let(::setImageDrawable)
        }

    private fun createThemedSearchKey() = ImageButton(context, null, R.attr.suggestionWordStyle).apply {
        scaleType = ImageView.ScaleType.CENTER
        tag = themeSearchTag
        contentDescription = resources.getString(R.string.label_search_key)
        setImageDrawable(
            visualTheme.drawable(context, ThemeAsset.TOOLBAR_SEARCH)
                ?: KeyboardIconsSet.instance.getNewDrawable(
                    KeyboardIconsSet.NAME_SEARCH_KEY,
                    context,
                ),
        )
    }

    companion object {
        @JvmField
        var DEBUG_SUGGESTIONS = false
        private const val DEBUG_INFO_TEXT_SIZE_IN_DIP = 6.5f
        private val TAG = SuggestionStripView::class.java.simpleName
    }
}
