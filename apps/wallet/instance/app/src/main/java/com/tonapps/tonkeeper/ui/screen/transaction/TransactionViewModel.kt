package com.tonapps.tonkeeper.ui.screen.transaction

import android.app.Application
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.tonapps.icu.Coins
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.events.EventsRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.settings.SpamTransactionState
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TransactionViewModel(
    app: Application,
    private val api: API,
    private val settingsRepository: SettingsRepository,
    private val eventsRepository: EventsRepository,
): BaseWalletVM(app) {

    fun getComment(txId: String): String? {
        return eventsRepository.getDecryptedComment(txId)
    }

    fun reportSpam(
        wallet: WalletEntity,
        txId: String,
        comment: String?,
        spam: Boolean,
        callback: () -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val state = if (spam) SpamTransactionState.SPAM else SpamTransactionState.NOT_SPAM
            settingsRepository.setSpamStateTransaction(wallet.id, txId, state)
            if (spam) {
                try {
                    api.reportTX(
                        txId = txId,
                        comment = comment,
                        recipient = wallet.accountId
                    )
                    eventsRepository.markAsSpam(wallet.accountId, wallet.testnet, txId)
                } catch (ignored: Throwable) {}
            } else {
                eventsRepository.removeSpam(wallet.accountId, wallet.testnet, txId)
            }
            withContext(Dispatchers.Main) {
                callback()
            }
        }
    }

}