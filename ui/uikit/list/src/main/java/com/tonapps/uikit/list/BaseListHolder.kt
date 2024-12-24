package com.tonapps.uikit.list

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.CallSuper
import androidx.annotation.ColorInt
import androidx.annotation.LayoutRes
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

abstract class BaseListHolder<I: BaseListItem>(
    view: View
): RecyclerView.ViewHolder(view) {

    val lifecycleScope: LifecycleCoroutineScope?
        get() = itemView.findViewTreeLifecycleOwner()?.lifecycleScope

    constructor(
        parent: ViewGroup,
        @LayoutRes resId: Int
    ) : this(
        LayoutInflater.from(parent.context).inflate(resId, parent, false)
    )

    val context: Context
        get() = itemView.context

    var item: I? = null
        private set

    fun <V : View> findViewById(id: Int): V = itemView.findViewById<V>(id)

    @CallSuper
    open fun bind(item: BaseListItem) {
        this.item = item as I
        onBind(item)
    }

    abstract fun onBind(item: I)

    fun unbind() {
        item = null
        onUnbind()
    }

    @CallSuper
    open fun onUnbind() {

    }

    fun getString(resId: Int): String = context.getString(resId)

    @ColorInt
    fun getColor(resId: Int): Int = context.getColor(resId)


    /*fun launch(
        context: CoroutineContext = EmptyCoroutineContext,
        start: CoroutineStart = CoroutineStart.DEFAULT,
        block: suspend CoroutineScope.() -> Unit
    ) {
        if (lifecycleScope == null) {
            itemView.doOnLayout { launch(context, start, block) }
            return
        }
        lifecycleScope?.launch(context, start, block)
    }*/
}
