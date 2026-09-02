// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.theme

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** Deterministic host used only by the emulator visual-regression suite. */
class VisualThemeGoldenHostActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            isAppearanceLightNavigationBars = false
        }

        val density = resources.displayMetrics.density
        val editor = EditText(this).apply {
            setText(intent.getStringExtra(EXTRA_TEXT).orEmpty())
            setTextColor(Color.WHITE)
            setHintTextColor(0xFFAAAAAA.toInt())
            hint = "huBoard visual theme golden"
            textSize = 20f
            gravity = Gravity.TOP
            setPadding((24 * density).toInt(), (20 * density).toInt(), (24 * density).toInt(), 0)
            setBackgroundColor(Color.TRANSPARENT)
            isSingleLine = false
        }
        val label = TextView(this).apply {
            text = "huBoard • deterministic keyboard surface"
            setTextColor(0xFFBBBBBB.toInt())
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, (12 * density).toInt(), 0, (12 * density).toInt())
        }
        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(0xFF101014.toInt())
                addView(label, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
                addView(editor, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
            },
        )
        editor.requestFocus()
        editor.setSelection(editor.text.length)
        editor.postDelayed({
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT)
        }, 250L)
    }

    companion object {
        const val EXTRA_TEXT = "text"
        private const val MATCH_PARENT = LinearLayout.LayoutParams.MATCH_PARENT
        private const val WRAP_CONTENT = LinearLayout.LayoutParams.WRAP_CONTENT
    }
}
