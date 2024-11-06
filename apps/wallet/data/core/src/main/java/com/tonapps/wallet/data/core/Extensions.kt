package com.tonapps.wallet.data.core

import android.content.Context
import androidx.biometric.BiometricManager
import com.google.firebase.crashlytics.FirebaseCrashlytics

fun accountId(accountId: String, testnet: Boolean): String {
    if (testnet) {
        return "testnet:$accountId"
    }
    return accountId
}

fun isAvailableBiometric(
    context: Context,
    authenticators: Int = BiometricManager.Authenticators.BIOMETRIC_STRONG
): Boolean {
    val authStatus = BiometricManager.from(context).canAuthenticate(authenticators)
    return authStatus == BiometricManager.BIOMETRIC_SUCCESS
}

fun recordException(e: Throwable) {
    FirebaseCrashlytics.getInstance().recordException(e)
}