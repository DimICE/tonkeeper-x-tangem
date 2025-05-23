package uikit.navigation

import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.fragment.app.Fragment
import com.tonapps.uikit.color.UIKitColor
import com.tonapps.uikit.color.backgroundContentTintColor
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.suspendCancellableCoroutine
import uikit.base.BaseFragment
import kotlin.coroutines.resume

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

        val Context.navigation: Navigation?
            get() = from(this)

        val Fragment.navigation: Navigation?
            get() {
                val context = context ?: return null
                return from(context)
            }

        val Dialog.navigation: Navigation?
            get() = from(context)
    }

    fun setFragmentResult(requestKey: String, result: Bundle = Bundle())

    fun setFragmentResultListener(
        requestKey: String,
        listener: ((bundle: Bundle) -> Unit)
    )

    fun resetFragmentResult(requestKey: String)

    fun add(fragment: BaseFragment)

    fun addForResult(fragment: BaseFragment, callback: (Bundle) -> Unit)

    fun remove(fragment: Fragment)

    fun removeByClass(runnable: Runnable, vararg clazz: Class<out Fragment>)

    fun openURL(url: String)

    fun toast(message: String, loading: Boolean, color: Int)

    fun run(fragment: BaseFragment) {

    }

    fun migrationLoader(show: Boolean) {

    }
}
