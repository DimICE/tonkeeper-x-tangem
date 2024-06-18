package com.tonapps.tonkeeper.fragment.tonconnect.auth

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tangem.TangemSdk
import com.tangem.common.CompletionResult
import com.tangem.crypto.hdWallet.DerivationPath
import com.tangem.sdk.extensions.init
import com.tonapps.tonkeeper.core.tonconnect.models.TCData
import com.tonapps.tonkeeper.password.PasscodeRepository
import com.tonapps.tonkeeper.ui.screen.send.SendException
import com.tonapps.wallet.data.push.GooglePushService
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.Wallet
import com.tonapps.wallet.data.account.WalletProof.prefixMessage
import com.tonapps.wallet.data.tonconnect.TonConnectRepository
import com.tonapps.wallet.data.tonconnect.entities.DAppRequestEntity
import com.tonapps.wallet.data.tonconnect.entities.reply.DAppEventSuccessEntity
import com.tonapps.wallet.data.tonconnect.entities.reply.DAppProofItemReplySuccess
import com.tonapps.wallet.data.tonconnect.entities.reply.DAppReply
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import org.ton.bitstring.BitString
import org.ton.crypto.base64
import org.ton.crypto.digest.sha256
import uikit.extensions.activity
import uikit.extensions.collectFlow
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class TCAuthViewModel(
    private val request: DAppRequestEntity,
    private val accountRepository: AccountRepository,
    private val passcodeRepository: PasscodeRepository,
    private val tonConnectRepository: TonConnectRepository,
): ViewModel() {

    private val _dataState = MutableStateFlow<TCData?>(null)
    val dataState = _dataState.asStateFlow().filterNotNull()

    init {
        collectFlow(accountRepository.selectedWalletFlow, ::requestData)
    }

    private fun requestData(wallet: WalletEntity) {
        viewModelScope.launch {
            val manifest = tonConnectRepository.getManifest(request.payload.manifestUrl) ?: return@launch

            val data = TCData(
                manifest = manifest,
                accountId = wallet.accountId,
                clientId = request.id,
                items = request.payload.items,
                testnet = wallet.testnet,
            )

            _dataState.tryEmit(data)
        }
    }

    private fun passcode(context: Context) = passcodeRepository.confirmationFlow(context)

    private fun wallet(context: Context) = accountRepository.selectedWalletFlow.take(1)

    private suspend fun singWithTangem(context: Context, hash: ByteArray, wallet: WalletEntity) = suspendCoroutine<ByteArray> { continuation ->
        viewModelScope.launch (Dispatchers.Main) {
            val tangemSdk = TangemSdk.init(context.activity!!)
            tangemSdk.sign(
                hash = hash,
                cardId = wallet.tangemCardId,
                walletPublicKey = wallet.tangemPublicKey!!,
                derivationPath = DerivationPath(rawPath = "m/44'/607'/0'") // TON
            ) { result ->
                when (result) {
                    is CompletionResult.Success -> {
                        continuation.resume(result.data.signature)
                    }

                    is CompletionResult.Failure -> {
                    }
                }
            }
        }
    }

    fun connect(
        context: Context,
        allowPush: Boolean
    ): Flow<DAppEventSuccessEntity> = wallet(context).combine(dataState) { wallet, data ->
        val firebaseToken = if (allowPush) {
            GooglePushService.requestToken()
        } else {
            null
        }

        if (wallet.type !== Wallet.Type.Tangem) {
            val isValidPasscode = passcodeRepository.confirmation(context)
            if (!isValidPasscode) {
                throw SendException.WrongPasscode()
            }
        }

        val enablePush = firebaseToken != null
        val app = tonConnectRepository.newApp(data.manifest, wallet.accountId, wallet.testnet, data.clientId, wallet.id, enablePush)

        val items = tonConnectRepository.createItems(app, wallet, data.items)
        val privateKey = accountRepository.getPrivateKey(wallet.id)
        items.forEach {
            if (it is DAppProofItemReplySuccess) {
                val body = sha256(prefixMessage + it.proof.signatureMessage)
                if (wallet.type === Wallet.Type.Tangem) {
                    it.proof.signature = base64(singWithTangem(context, body, wallet))
                } else {
                    it.proof.signature = base64(privateKey.sign(body))
                }
            }
        }
        tonConnectRepository.connect(wallet, app, items, firebaseToken)

    }.take(1).flowOn(Dispatchers.IO)


}