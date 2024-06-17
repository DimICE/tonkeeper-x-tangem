package com.tonapps.tonkeeper.ui.screen.init

import androidx.lifecycle.SavedStateHandle
import com.tonapps.blockchain.ton.extensions.base64
import com.tonapps.blockchain.ton.extensions.safePublicKey
import com.tonapps.tonkeeper.ui.screen.init.list.AccountItem
import com.tonapps.wallet.api.entity.AccountDetailsEntity
import com.tonapps.wallet.data.account.Wallet
import org.ton.api.pub.PublicKeyEd25519

class InitModelState(private val savedStateHandle: SavedStateHandle) {

    companion object {
        private const val PASSCODE_KEY = "passcode"
        private const val LABEL_KEY = "label"
        private const val WATCH_ACCOUNT_KEY = "watch_account"
        private const val MNEMONIC_KEY = "mnemonic"
        private const val ACCOUNTS = "accounts"
        private const val PUBLIC_KEY = "public_key"
        private const val TANGEM_CARD_ID = "tangem_card_id"
        private const val TANGEM_PUBLIC_KEY = "tangem_public_key"
    }

    var passcode: String?
        get() = savedStateHandle[PASSCODE_KEY]
        set(value) = savedStateHandle.set(PASSCODE_KEY, value)

    var label: Wallet.Label?
        get() = savedStateHandle[LABEL_KEY]
        set(value) = savedStateHandle.set(LABEL_KEY, value)

    var watchAccount: AccountDetailsEntity?
        get() = savedStateHandle[WATCH_ACCOUNT_KEY]
        set(value) = savedStateHandle.set(WATCH_ACCOUNT_KEY, value)

    var mnemonic: List<String>?
        get() = savedStateHandle[MNEMONIC_KEY]
        set(value) = savedStateHandle.set(MNEMONIC_KEY, value)

    var accounts: List<AccountItem>?
        get() = savedStateHandle[ACCOUNTS]
        set(value) = savedStateHandle.set(ACCOUNTS, value)

    var publicKey: PublicKeyEd25519?
        get() {
            val value = savedStateHandle.get<String>(PUBLIC_KEY) ?: return null
            return value.safePublicKey()
        }
        set(value) = savedStateHandle.set(PUBLIC_KEY, value?.base64())

    var tangemCardId: String?
        get() = savedStateHandle[TANGEM_CARD_ID]
        set(value) = savedStateHandle.set(TANGEM_CARD_ID, value)

    var tangemPublicKey: ByteArray?
        get() = savedStateHandle[TANGEM_PUBLIC_KEY]
        set(value) = savedStateHandle.set(TANGEM_PUBLIC_KEY, value)
}