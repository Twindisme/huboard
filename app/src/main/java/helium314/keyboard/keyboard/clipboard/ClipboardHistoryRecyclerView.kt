// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.keyboard.clipboard

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import helium314.keyboard.latin.ClipboardHistoryManager

class ClipboardHistoryRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : RecyclerView(context, attrs, defStyleAttr) {

    var placeholderView: View? = null
    var historyManager: ClipboardHistoryManager? = null

    @Suppress("unused")
    private val touchHelper = ItemTouchHelper(
        object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: ViewHolder,
                target: ViewHolder,
            ) = false

            override fun getSwipeDirs(
                recyclerView: RecyclerView,
                viewHolder: ViewHolder,
            ): Int {
                val clipId = viewHolder.itemId
                if (clipId == NO_ID || historyManager?.canRemove(clipId) != true) return 0
                return super.getSwipeDirs(recyclerView, viewHolder)
            }

            override fun onSwiped(viewHolder: ViewHolder, direction: Int) {
                val clipId = viewHolder.itemId
                if (clipId != NO_ID) historyManager?.removeEntry(clipId)
            }
        },
    ).attachToRecyclerView(this)

    private val adapterDataObserver = object : AdapterDataObserver() {
        override fun onChanged() = checkAdapterContentChange()
        override fun onItemRangeChanged(positionStart: Int, itemCount: Int) =
            checkAdapterContentChange()
        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) =
            checkAdapterContentChange()
        override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) =
            checkAdapterContentChange()
    }

    private fun checkAdapterContentChange() {
        val emptyView = placeholderView ?: return
        val adapterIsEmpty = adapter == null || adapter?.itemCount == 0
        if (isVisible && adapterIsEmpty) {
            emptyView.visibility = VISIBLE
            visibility = INVISIBLE
        } else if (isInvisible && !adapterIsEmpty) {
            emptyView.visibility = INVISIBLE
            visibility = VISIBLE
        }
    }

    override fun setAdapter(adapter: Adapter<*>?) {
        this.adapter?.unregisterAdapterDataObserver(adapterDataObserver)
        super.setAdapter(adapter)
        checkAdapterContentChange()
        adapter?.registerAdapterDataObserver(adapterDataObserver)
    }
}
