package com.tonapps.tonkeeper.core

import android.util.Log
import com.tangem.TangemSdk
import com.tangem.common.CompletionResult
import com.tangem.crypto.hdWallet.DerivationPath
import com.tangem.sdk.extensions.init
import com.tonapps.wallet.data.account.entities.WalletEntity
import uikit.navigation.NavigationActivity
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object TangemHelper {
    suspend fun singWithTangem(
        activity: NavigationActivity,
        wallet: WalletEntity,
        hash: ByteArray
    ) =
        suspendCoroutine<ByteArray?> { continuation ->
            try {
                activity.runOnUiThread {
                    val tangemSdk = TangemSdk.init(activity)
                    tangemSdk.sign(
                        hash = hash,
                        cardId = wallet.tangemCardId, // "AF30000000979194",
                        walletPublicKey = wallet.tangemPublicKey!!, // hex("23742cd43eb8aec7e2af8404bd257b21570aae17ad20cef98d258ebeb2be6c51"),
                        derivationPath = DerivationPath(rawPath = "m/44'/607'/0'") // TON
                    ) { result ->
                        when (result) {
                            is CompletionResult.Success -> {
                                continuation.resume(result.data.signature)
                            }

                            is CompletionResult.Failure -> {
                                continuation.resume(null)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.v("Tangem exception", "exception", e)
                continuation.resume(null)
            }
        }
}