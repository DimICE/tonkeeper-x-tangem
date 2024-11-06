package com.tonapps.tonkeeper.manager.widget

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tonapps.extensions.constructor
import com.tonapps.extensions.string

internal class WidgetSettings(context: Context) {

    private val prefs = context.getSharedPreferences("widget", Context.MODE_PRIVATE)

    fun setParams(widgetId: Int, params: WidgetParams) {
        params.save(keyParamsPrefix(widgetId), prefs)
    }

    fun setType(widgetId: Int, type: String) {
        prefs.string(keyType(widgetId), type)
    }

    fun getType(widgetId: Int): String? {
        return prefs.string(keyType(widgetId))
    }

     inline fun <reified T: WidgetParams> getParams(widgetId: Int): T? {
         return resolveParams<T>(widgetId)
     }

    private inline fun <reified T : WidgetParams> resolveParams(widgetId: Int): T? {
        return try {
            val constructor = T::class.constructor(String::class, SharedPreferences::class)
            constructor.newInstance(keyParamsPrefix(widgetId), prefs)
        } catch (e: Throwable) {
            FirebaseCrashlytics.getInstance().recordException(e)
            null
        }
    }

    private fun keyParamsPrefix(widgetId: Int): String {
        return "params.$widgetId"
    }

    private fun keyType(widgetId: Int): String {
        return keyParamsPrefix(widgetId) + ".type"
    }
}