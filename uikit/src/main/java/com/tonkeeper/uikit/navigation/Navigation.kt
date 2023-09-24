package com.tonkeeper.uikit.navigation

import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import androidx.fragment.app.Fragment
import com.tonkeeper.uikit.base.fragment.BaseFragment

interface Navigation {

    companion object {

        fun from(context: Context): Navigation? {
            if (context is Navigation) {
                return context
            }
            if (context is ContextWrapper) {
                return from(context.baseContext)
            }
            return null
        }

        fun Context.nav(): Navigation? = from(this)

        fun Fragment.nav(): Navigation? {
            val context = context ?: return null
            return from(context)
        }

        fun Dialog.nav() = from(context)
    }

    fun replace(fragment: BaseFragment, addToBackStack: Boolean)

    fun add(fragment: BaseFragment)

    fun back()
}