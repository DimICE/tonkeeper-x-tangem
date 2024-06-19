package com.tonapps.tonkeeper.ui.screen.action

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tangem.TangemSdk
import com.tangem.common.CompletionResult
import com.tangem.crypto.hdWallet.DerivationPath
import com.tangem.sdk.extensions.init
import com.tonapps.blockchain.ton.extensions.base64
import com.tonapps.tonkeeper.password.PasscodeRepository
import com.tonapps.tonkeeper.ui.screen.send.SendException
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.Wallet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import org.ton.bitstring.BitString
import org.ton.cell.Cell
import uikit.extensions.activity
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class ActionViewModel(
    private val args: ActionArgs,
    private val accountRepository: AccountRepository,
    private val passcodeRepository: PasscodeRepository
): ViewModel() {

    private val _walletFlow = MutableStateFlow<WalletEntity?>(null)
    val walletFlow = _walletFlow.asStateFlow().filterNotNull()
    private fun wallet(context: Context) = walletFlow

    init {
        viewModelScope.launch {
            _walletFlow.value = accountRepository.getWalletById(args.walletId)
        }
    }

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

    fun sign(context: Context) = wallet(context).combine(walletFlow) { _, wallet ->
        val request = args.request
        val message: Cell
        val seqno = accountRepository.getSeqno(wallet)
        if (wallet.type === Wallet.Type.Tangem) {
            val msg = accountRepository.createMessage(
                wallet,
                seqno,
                request.validUntil,
                request.transfers
            )
            val unsignedBody = msg.body
            val hash = unsignedBody.hash()
            val signature = singWithTangem(context, hash, wallet)
            val transferBody = wallet.contract.signedBody(BitString(signature), unsignedBody)
            message = wallet.contract.createTransferMessageCell(
                address = wallet.contract.address,
                seqno = msg.seqno,
                transferBody = transferBody,
            )
        } else {
            val isValidPasscode = passcodeRepository.confirmation(context)
            if (!isValidPasscode) {
                throw SendException.WrongPasscode()
            }
            val secretKey = accountRepository.getPrivateKey(wallet.id)
            message = accountRepository.createSignedMessage(
                wallet,
                seqno,
                secretKey,
                request.validUntil,
                request.transfers
            )
        }
        message.base64()
    }.flowOn(Dispatchers.IO).take(1)

}