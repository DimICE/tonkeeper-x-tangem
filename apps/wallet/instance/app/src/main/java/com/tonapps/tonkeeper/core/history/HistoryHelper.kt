package com.tonapps.tonkeeper.core.history

import com.tonapps.icu.Coins
import com.tonapps.blockchain.ton.extensions.toUserFriendly
import com.tonapps.extensions.withMinus
import com.tonapps.extensions.withPlus
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.tonkeeper.api.amount
import com.tonapps.tonkeeper.api.fee
import com.tonapps.tonkeeper.api.getNameOrAddress
import com.tonapps.tonkeeper.api.iconURL
import com.tonapps.tonkeeper.api.jettonPreview
import com.tonapps.tonkeeper.api.parsedAmount
import com.tonapps.tonkeeper.api.shortAddress
import com.tonapps.tonkeeper.api.title
import com.tonapps.tonkeeper.api.ton
import com.tonapps.tonkeeper.core.history.list.item.HistoryItem
import io.tonapi.models.AccountAddress
import io.tonapi.models.AccountEvent
import io.tonapi.models.Action
import io.tonapi.models.ActionSimplePreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.tonapps.tonkeeper.api.totalFees
import com.tonapps.tonkeeper.helper.DateHelper
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.collectibles.CollectiblesRepository
import com.tonapps.wallet.data.core.WalletCurrency
import com.tonapps.wallet.data.events.EventsRepository
import com.tonapps.wallet.data.rates.RatesRepository
import com.tonapps.wallet.data.rates.entity.RatesEntity
import com.tonapps.wallet.data.settings.SettingsRepository
import io.tonapi.models.MessageConsequences

// TODO request refactoring
class HistoryHelper(
    private val ratesRepository: RatesRepository,
    private val collectiblesRepository: CollectiblesRepository,
    private val settingsRepository: SettingsRepository,
    private val eventsRepository: EventsRepository
) {

    private val currency: WalletCurrency
        get() = settingsRepository.currency

    companion object {
        const val EVENT_LIMIT = 20

        const val MINUS_SYMBOL = "-"
        const val PLUS_SYMBOL = "+"
    }

    data class Details(
        val accountId: String,
        val items: List<HistoryItem>,
        val fee: Coins,
        val feeFormat: CharSequence,
        val feeFiat: Coins,
        val feeFiatFormat: CharSequence
    )

    suspend fun create(
        wallet: WalletEntity,
        response: MessageConsequences,
        rates: RatesEntity,
    ): Details {
        val items = mapping(wallet, response.event, true)
        val fee = Coins.of(response.totalFees)
        val feeFormat = CurrencyFormatter.format("TON", fee)
        val feeFiat = rates.convert("TON", fee)
        val feeFiatFormat = CurrencyFormatter.formatFiat(rates.currency.code, feeFiat)
        return Details(
            accountId = wallet.accountId,
            items = items,
            fee = fee,
            feeFormat = feeFormat,
            feeFiat = feeFiat,
            feeFiatFormat = feeFiatFormat
        )
    }

    fun withLoadingItem(items: List<HistoryItem>): List<HistoryItem> {
        val last = items.lastOrNull()
        if (last is HistoryItem.Loader) {
            return items
        }

        val newItems = items.toMutableList()
        newItems.add(HistoryItem.Loader(newItems.size, System.currentTimeMillis() / 1000))
        return newItems
    }

    fun removeLoadingItem(items: List<HistoryItem>): List<HistoryItem> {
        val last = items.lastOrNull()
        if (last is HistoryItem.Loader) {
            val newItems = items.toMutableList()
            newItems.removeAt(newItems.size - 1)
            return newItems
        }
        return items
    }


    fun formatDate(timestamp: Long): String {
        if (timestamp == 0L) {
            return ""
        }
        return DateHelper.formatTime(timestamp)
    }

    suspend fun mapping(
        wallet: WalletEntity,
        event: AccountEvent,
        removeDate: Boolean = false,
        hiddenBalances: Boolean = false
    ): List<HistoryItem> {
        return mapping(wallet, listOf(event), removeDate, hiddenBalances)
    }

    suspend fun mapping(
        wallet: WalletEntity,
        events: List<AccountEvent>,
        removeDate: Boolean = false,
        hiddenBalances: Boolean = false
    ): List<HistoryItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<HistoryItem>()

        for ((index, event) in events.withIndex()) {

            val pending = event.inProgress
            val prevEvent = events.getOrNull(index - 1)

            val actions = event.actions
            val fee = Coins.of(event.fee)
            val currency = settingsRepository.currency

            val rates = ratesRepository.getRates(currency, TokenEntity.TON.symbol)
            val feeInCurrency = rates.convert(TokenEntity.TON.symbol, fee)

            val chunkItems = mutableListOf<HistoryItem>()
            for ((actionIndex, action) in actions.withIndex()) {
                val timestamp = if (removeDate) 0 else event.timestamp
                val item = action(actionIndex, event.eventId, wallet, action, timestamp)
                chunkItems.add(item.copy(
                    pending = pending,
                    position = ListCell.getPosition(actions.size, actionIndex),
                    fee = CurrencyFormatter.format(TokenEntity.TON.symbol, fee, TokenEntity.TON.decimals),
                    feeInCurrency = CurrencyFormatter.formatFiat(currency.code, feeInCurrency),
                    lt = event.lt,
                    hiddenBalance = hiddenBalances
                ))
            }

            if (chunkItems.size > 0) {
                items.addAll(chunkItems)
            }
        }

        return@withContext items
    }

    private fun action(
        index: Int,
        txId: String,
        wallet: WalletEntity,
        action: Action,
        timestamp: Long
    ): HistoryItem.Event {

        val simplePreview = action.simplePreview
        val date = formatDate(timestamp)

        if (action.jettonSwap != null) {
            val jettonSwap = action.jettonSwap!!
            val jettonPreview = jettonSwap.jettonPreview!!
            val token = jettonSwap.jettonPreview!!.address
            val symbol = jettonPreview.symbol
            val amount = Coins.of(jettonSwap.amount, jettonPreview.decimals)
            val tonFromJetton = Coins.of(jettonSwap.ton)

            val isOut = jettonSwap.amountOut != ""
            val value: CharSequence
            val value2: CharSequence
            val tokenCode: String

            if (!isOut) {
                tokenCode = TokenEntity.TON.symbol
                value = CurrencyFormatter.format(tokenCode, tonFromJetton)
                value2 = CurrencyFormatter.format(symbol, amount)
            } else {
                tokenCode = symbol
                value = CurrencyFormatter.format(symbol, amount)
                value2 = CurrencyFormatter.format(TokenEntity.TON.symbol, tonFromJetton)
            }

            val rates = ratesRepository.getRates(currency, token)
            val inCurrency = rates.convert(token, amount)

            return HistoryItem.Event(
                index = index,
                txId = txId,
                iconURL = "",
                action = ActionType.Swap,
                title = simplePreview.name,
                subtitle = jettonSwap.router.getNameOrAddress(wallet.testnet),
                comment = "",
                value = value.withPlus,
                value2 = value2.withMinus,
                tokenCode = tokenCode,
                coinIconUrl = jettonPreview.image,
                timestamp = timestamp,
                date = date,
                isOut = isOut,
                currency = CurrencyFormatter.formatFiat(currency.code, inCurrency),
                failed = action.status == Action.Status.failed,
            )
        } else if (action.jettonTransfer != null) {
            val jettonTransfer = action.jettonTransfer!!
            val token = jettonTransfer.jetton.address
            val symbol = jettonTransfer.jetton.symbol
            val isOut = !wallet.isMyAddress(jettonTransfer.recipient?.address ?: "")

            val amount = Coins.of(jettonTransfer.amount, jettonTransfer.jetton.decimals)
            var value = CurrencyFormatter.format(symbol, amount, jettonTransfer.jetton.decimals)

            val itemAction: ActionType
            val accountAddress: AccountAddress?

            if (isOut) {
                itemAction = ActionType.Send
                accountAddress = jettonTransfer.recipient
                value = value.withMinus
            } else {
                itemAction = ActionType.Received
                accountAddress = jettonTransfer.sender
                value = value.withPlus
            }

            val rates = ratesRepository.getRates(currency, token)
            val inCurrency = rates.convert(token, amount)

            return HistoryItem.Event(
                index = index,
                txId = txId,
                iconURL = accountAddress?.iconURL ?: "",
                action = itemAction,
                title = simplePreview.name,
                subtitle = accountAddress?.getNameOrAddress(wallet.testnet) ?: "",
                comment = jettonTransfer.comment,
                value = value,
                tokenCode = "",
                coinIconUrl = jettonTransfer.jetton.image,
                timestamp = timestamp,
                date = date,
                isOut = isOut,
                address = accountAddress?.address?.toUserFriendly(
                    wallet = accountAddress.isWallet,
                    testnet = wallet.testnet
                ),
                addressName = accountAddress?.name,
                currency = CurrencyFormatter.format(currency.code, inCurrency),
                failed = action.status == Action.Status.failed,
                cipherText = action.tonTransfer?.encryptedComment?.cipherText
            )
        } else if (action.tonTransfer != null) {
            val tonTransfer = action.tonTransfer!!

            val isOut = !wallet.isMyAddress(tonTransfer.recipient.address)

            val itemAction: ActionType
            val accountAddress: AccountAddress

            val amount = Coins.of(tonTransfer.amount)
            var value = CurrencyFormatter.format("TON", amount, TokenEntity.TON.decimals)

            if (isOut) {
                itemAction = ActionType.Send
                accountAddress = tonTransfer.recipient
                value = value.withMinus
            } else {
                itemAction = ActionType.Received
                accountAddress = tonTransfer.sender
                value = value.withPlus
            }

            val rates = ratesRepository.getRates(currency, TokenEntity.TON.symbol)
            val inCurrency = rates.convert(TokenEntity.TON.symbol, amount)

            return HistoryItem.Event(
                index = index,
                txId = txId,
                iconURL = accountAddress.iconURL,
                action = itemAction,
                title = simplePreview.name,
                subtitle = accountAddress.getNameOrAddress(wallet.testnet),
                comment = tonTransfer.comment,
                value = value,
                tokenCode = "TON",
                coinIconUrl = TokenEntity.TON.imageUri.toString(),
                timestamp = timestamp,
                date = date,
                isOut = isOut,
                address = accountAddress.address.toUserFriendly(
                    wallet = accountAddress.isWallet,
                    testnet = wallet.testnet
                ),
                addressName = accountAddress.name,
                currency = CurrencyFormatter.formatFiat(currency.code, inCurrency),
                failed = action.status == Action.Status.failed,
                cipherText = action.tonTransfer?.encryptedComment?.cipherText
            )
        } else if (action.smartContractExec != null) {
            val smartContractExec = action.smartContractExec!!
            val executor = smartContractExec.executor

            val amount = Coins.of(smartContractExec.tonAttached)
            val value = CurrencyFormatter.format("TON", amount)

            return HistoryItem.Event(
                index = index,
                txId = txId,
                iconURL = executor.iconURL,
                action = ActionType.CallContract,
                title = simplePreview.name,
                subtitle = executor.getNameOrAddress(wallet.testnet),
                value = value.withMinus,
                tokenCode = "TON",
                timestamp = timestamp,
                date = date,
                isOut = true,
                failed = action.status == Action.Status.failed,
            )
        } else if (action.nftItemTransfer != null) {
            val nftItemTransfer = action.nftItemTransfer!!
            val isOut = !wallet.isMyAddress(nftItemTransfer.recipient?.address ?: "-")

            val itemAction: ActionType
            val iconURL: String?
            val subtitle: String

            if (isOut) {
                itemAction = ActionType.NftSend
                iconURL = nftItemTransfer.recipient?.iconURL
                subtitle = nftItemTransfer.recipient?.getNameOrAddress(wallet.testnet) ?: ""
            } else {
                itemAction = ActionType.NftReceived
                iconURL = nftItemTransfer.sender?.iconURL
                subtitle = nftItemTransfer.sender?.getNameOrAddress(wallet.testnet) ?: ""
            }

            val nftItem = collectiblesRepository.getNft(
                accountId = wallet.accountId,
                testnet = wallet.testnet,
                address = nftItemTransfer.nft
            )

            return HistoryItem.Event(
                index = index,
                txId = txId,
                iconURL = iconURL,
                action = itemAction,
                title = simplePreview.name,
                subtitle = subtitle,
                value = "NFT",
                nft = nftItem,
                tokenCode = "NFT",
                timestamp = timestamp,
                date = date,
                isOut = isOut,
                failed = action.status == Action.Status.failed,
            )
        } else if (action.contractDeploy != null) {
            return HistoryItem.Event(
                index = index,
                txId = txId,
                iconURL = "",
                action = ActionType.DeployContract,
                title = simplePreview.name,
                subtitle = wallet.address.shortAddress,
                value = MINUS_SYMBOL,
                tokenCode = "TON",
                timestamp = timestamp,
                date = date,
                isOut = false,
                failed = action.status == Action.Status.failed,
            )
        } else if (action.depositStake != null) {
            val depositStake = action.depositStake!!

            val amount = Coins.of(depositStake.amount)
            val value = CurrencyFormatter.format("TON", amount)

            return HistoryItem.Event(
                index = index,
                iconURL = depositStake.implementation.iconURL,
                txId = txId,
                action = ActionType.DepositStake,
                title = simplePreview.name,
                subtitle = depositStake.pool.getNameOrAddress(wallet.testnet),
                value = value.withMinus,
                tokenCode = "TON",
                timestamp = timestamp,
                coinIconUrl = depositStake.implementation.iconURL,
                date = date,
                isOut = false,
                failed = action.status == Action.Status.failed,
            )
        } else if (action.jettonMint != null) {
            val jettonMint = action.jettonMint!!

            val amount = jettonMint.parsedAmount

            val value = CurrencyFormatter.format(jettonMint.jetton.symbol, amount)

            return HistoryItem.Event(
                index = index,
                txId = txId,
                action = ActionType.JettonMint,
                title = simplePreview.name,
                subtitle = jettonMint.jetton.name,
                value = value.withPlus,
                tokenCode = jettonMint.jetton.symbol,
                timestamp = timestamp,
                coinIconUrl = jettonMint.jetton.image,
                date = date,
                isOut = false,
                failed = action.status == Action.Status.failed,
            )
        } else if (action.withdrawStakeRequest != null) {
            val withdrawStakeRequest = action.withdrawStakeRequest!!

            val amount = Coins.of(withdrawStakeRequest.amount ?: 0L)
            val value = CurrencyFormatter.format("TON", amount)

            return HistoryItem.Event(
                index = index,
                iconURL = withdrawStakeRequest.implementation.iconURL,
                txId = txId,
                action = ActionType.WithdrawStakeRequest,
                title = simplePreview.name,
                subtitle = withdrawStakeRequest.pool.getNameOrAddress(wallet.testnet),
                value = value,
                tokenCode = "TON",
                timestamp = timestamp,
                coinIconUrl = withdrawStakeRequest.implementation.iconURL,
                date = date,
                isOut = false,
                failed = action.status == Action.Status.failed,
            )
        } else if (action.domainRenew != null) {
            val domainRenew = action.domainRenew!!

            return HistoryItem.Event(
                index = index,
                txId = txId,
                action = ActionType.DomainRenewal,
                title = simplePreview.name,
                subtitle = domainRenew.domain,
                value = MINUS_SYMBOL,
                tokenCode = "",
                timestamp = timestamp,
                date = date,
                isOut = false,
                failed = action.status == Action.Status.failed,
            )
        } else if (action.auctionBid != null) {
            val auctionBid = action.auctionBid!!
            val subtitle = auctionBid.nft?.title ?: auctionBid.bidder.getNameOrAddress(wallet.testnet)

            val amount = Coins.of(auctionBid.amount.value)
            val tokenCode = auctionBid.amount.tokenName

            val value = CurrencyFormatter.format(auctionBid.amount.tokenName, amount)

            return HistoryItem.Event(
                index = index,
                txId = txId,
                action = ActionType.AuctionBid,
                title = simplePreview.name,
                subtitle = subtitle,
                value = value.withMinus,
                tokenCode = tokenCode,
                timestamp = timestamp,
                date = date,
                isOut = false,
                failed = action.status == Action.Status.failed,
            )
        } else if (action.type == Action.Type.unknown) {
            return createUnknown(index, txId, action, date, timestamp, simplePreview)
        } else if (action.withdrawStake != null) {
            val withdrawStake = action.withdrawStake!!

            val amount = Coins.of(withdrawStake.amount)
            val value = CurrencyFormatter.format("TON", amount)

            return HistoryItem.Event(
                index = index,
                txId = txId,
                iconURL = withdrawStake.implementation.iconURL,
                action = ActionType.WithdrawStake,
                title = simplePreview.name,
                subtitle = withdrawStake.pool.getNameOrAddress(wallet.testnet),
                value = value.withPlus,
                tokenCode = "TON",
                timestamp = timestamp,
                coinIconUrl = withdrawStake.implementation.iconURL,
                date = date,
                isOut = false,
                failed = action.status == Action.Status.failed,
            )
        } else if (action.nftPurchase != null) {
            val nftPurchase = action.nftPurchase!!

            val amount = Coins.of(nftPurchase.amount.value.toLong())
            val value = CurrencyFormatter.format(nftPurchase.amount.tokenName, amount)

            val nftItem = collectiblesRepository.getNft(
                accountId = wallet.accountId,
                testnet = wallet.testnet,
                address = nftPurchase.nft.address
            )

            return HistoryItem.Event(
                index = index,
                txId = txId,
                action = ActionType.NftPurchase,
                title = simplePreview.name,
                subtitle = nftPurchase.buyer.getNameOrAddress(wallet.testnet),
                value = value.withMinus,
                tokenCode = "TON",
                timestamp = timestamp,
                nft = nftItem,
                date = date,
                isOut = false,
                failed = action.status == Action.Status.failed,
            )
        } else if (action.jettonBurn != null) {
            val jettonBurn = action.jettonBurn!!

            val amount = jettonBurn.parsedAmount
            val value = CurrencyFormatter.format(jettonBurn.jetton.symbol, amount)

            return HistoryItem.Event(
                index = index,
                txId = txId,
                action = ActionType.JettonBurn,
                title = simplePreview.name,
                subtitle = jettonBurn.sender.getNameOrAddress(wallet.testnet),
                value = value.withMinus,
                tokenCode = jettonBurn.jetton.symbol,
                timestamp = timestamp,
                coinIconUrl = jettonBurn.jetton.image,
                date = date,
                isOut = false,
                failed = action.status == Action.Status.failed,
            )
        } else if (action.unSubscribe != null) {
            val unsubscribe = action.unSubscribe!!

            return HistoryItem.Event(
                index = index,
                txId = txId,
                action = ActionType.UnSubscribe,
                title = simplePreview.name,
                subtitle = unsubscribe.beneficiary.getNameOrAddress(wallet.testnet),
                value = MINUS_SYMBOL,
                tokenCode = "TON",
                timestamp = timestamp,
                coinIconUrl = unsubscribe.beneficiary.iconURL ?: "",
                date = date,
                isOut = false,
                failed = action.status == Action.Status.failed,
            )
        } else if (action.subscribe != null) {
            val subscribe = action.subscribe!!

            val amount = Coins.of(subscribe.amount)
            val value = CurrencyFormatter.format("TON", amount)

            return HistoryItem.Event(
                index = index,
                txId = txId,
                action = ActionType.Subscribe,
                title = simplePreview.name,
                subtitle = subscribe.beneficiary.getNameOrAddress(wallet.testnet),
                value = value.withMinus,
                tokenCode = "TON",
                timestamp = timestamp,
                coinIconUrl = subscribe.beneficiary.iconURL ?: "",
                date = date,
                isOut = false,
                failed = action.status == Action.Status.failed,
            )
        } else {
            return createUnknown(index, txId, action, date, timestamp, simplePreview)
        }
    }

    private fun createUnknown(
        index: Int,
        txId: String,
        action: Action,
        date: String,
        timestamp: Long,
        simplePreview: ActionSimplePreview,
    ) = HistoryItem.Event(
        index = index,
        txId = txId,
        action = ActionType.Unknown,
        title = simplePreview.name,
        subtitle = action.simplePreview.description,
        value = MINUS_SYMBOL,
        tokenCode = "TON",
        timestamp = timestamp,
        date = date,
        isOut = false,
        failed = action.status == Action.Status.failed,
    )
}