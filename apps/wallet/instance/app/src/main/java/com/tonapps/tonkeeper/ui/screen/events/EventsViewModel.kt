package com.tonapps.tonkeeper.ui.screen.events

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.network.NetworkMonitor
import com.tonapps.tonkeeper.core.history.HistoryHelper
import com.tonapps.tonkeeper.core.history.list.item.HistoryItem
import com.tonapps.tonkeeper.helper.DateHelper
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.core.ScreenCacheSource
import com.tonapps.wallet.data.events.EventsRepository
import com.tonapps.wallet.data.push.PushManager
import com.tonapps.wallet.data.push.entities.AppPushEntity
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.tonconnect.TonConnectRepository
import io.tonapi.models.AccountEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uikit.extensions.collectFlow

class EventsViewModel(
    private val application: Application,
    private val accountRepository: AccountRepository,
    private val eventsRepository: EventsRepository,
    private val networkMonitor: NetworkMonitor,
    private val tonConnectRepository: TonConnectRepository,
    private val pushManager: PushManager,
    private val historyHelper: HistoryHelper,
    private val screenCacheSource: ScreenCacheSource,
    private val settingsRepository: SettingsRepository
): AndroidViewModel(application) {

    private val _isUpdatingFlow = MutableStateFlow(false)
    val isUpdatingFlow = _isUpdatingFlow.asSharedFlow()

    private val _uiItemsFlow = MutableStateFlow<List<HistoryItem>?>(null)
    val uiItemsFlow = _uiItemsFlow.asStateFlow().filterNotNull()

    init {
        collectFlow(accountRepository.selectedWalletFlow.map { getCached(it) }.flowOn(Dispatchers.IO)) { items ->
            if (!items.isNullOrEmpty()) {
                _uiItemsFlow.value = items
            }
        }

        combine(
            accountRepository.selectedWalletFlow,
            networkMonitor.isOnlineFlow
        ) { wallet, isOnline ->
            loadEvents(wallet, isOnline)
        }.flowOn(Dispatchers.IO).launchIn(viewModelScope)

        collectFlow(accountRepository.realtimeEventsFlow.map { it.wallet }) { wallet ->
            loadEvents(wallet, true)
        }

        collectFlow(pushManager.dAppPushFlow.filterNotNull()) {
            val wallet = accountRepository.selectedWalletFlow.firstOrNull() ?: return@collectFlow
            loadEvents(wallet, true)
        }

        collectFlow(settingsRepository.hiddenBalancesFlow.drop(1)) {
            val wallet = accountRepository.selectedWalletFlow.firstOrNull() ?: return@collectFlow
            val uiItems = _uiItemsFlow.value?.map {
                if (it is HistoryItem.Event) {
                    it.copy(hiddenBalance = settingsRepository.hiddenBalances)
                } else {
                    it
                }
            } ?: return@collectFlow

            _uiItemsFlow.value = uiItems.toList()
            screenCacheSource.set(CACHE_NAME, wallet.id, uiItems)
        }
    }

    fun openQRCode() = accountRepository.selectedWalletFlow.take(1)

    fun update() {
        viewModelScope.launch(Dispatchers.IO) {
            val wallet = accountRepository.selectedWalletFlow.firstOrNull() ?: return@launch
            loadLast(wallet, true)
        }
    }

    private suspend fun loadLast(
        wallet: WalletEntity,
        inProgress: Boolean
    ) = withContext(Dispatchers.IO) {
        val events = eventsRepository.getLast(wallet.accountId, wallet.testnet)?.events?.filter {
            it.inProgress == inProgress
        } ?: return@withContext
        val items = mapping(wallet, events)
        val newItems = (_uiItemsFlow.value ?: emptyList()) + items
        setItems(wallet, newItems.distinctBy {
            it.uniqueId
        }, false)
    }

    private suspend fun getRemoteDAppEvents(wallet: WalletEntity): List<HistoryItem.App> {
        val events = pushManager.getRemoteDAppEvents(wallet)
        return dAppEventsMapping(wallet, events)
    }

    private suspend fun getLocalDAppEvents(wallet: WalletEntity): List<HistoryItem.App> {
        val events = pushManager.getLocalDAppEvents(wallet)
        return dAppEventsMapping(wallet, events)
    }

    private fun dAppEventsMapping(
        wallet: WalletEntity,
        events: List<AppPushEntity>
    ): List<HistoryItem.App> {
        val dappUrls = events.map { it.dappUrl }
        val apps = tonConnectRepository.getApps(dappUrls, wallet)

        val items = mutableListOf<HistoryItem.App>()
        for (event in events) {
            val app = apps.firstOrNull {
                it.url.startsWith(event.dappUrl) && it.accountId == event.account
            } ?: continue

            items.add(HistoryItem.App(
                iconUri = Uri.parse(app.manifest.iconUrl),
                title = app.manifest.name,
                body = event.message,
                date = DateHelper.formatTime(event.dateUnix),
                timestamp = event.dateUnix,
                deepLink = event.link,
                host = app.manifest.host
            ))
        }
        return items
    }

    private fun foundLastItem(): HistoryItem.Event? {
        return _uiItemsFlow.value?.lastOrNull { it is HistoryItem.Event } as? HistoryItem.Event
    }

    private fun lastLt(): Long? {
        val item = foundLastItem() ?: return null
        if (item.lt > 0) {
            return item.lt
        }
        return null
    }

    fun loadMore() {
        if (_isUpdatingFlow.value) {
            return
        }
        val lastLt = lastLt() ?: return
        withUpdating {
            val wallet = accountRepository.selectedWalletFlow.firstOrNull() ?: return@withUpdating
            val oldValues = _uiItemsFlow.value ?: emptyList()
            _uiItemsFlow.value = historyHelper.withLoadingItem(oldValues)
            loadRemote(wallet, lastLt)
        }
    }

    private fun loadEvents(
        wallet: WalletEntity,
        isOnline: Boolean
    ) {
        withUpdating {
            loadLocal(wallet)
            if (isOnline) {
                loadRemote(wallet)
            }
        }
    }

    private suspend fun loadLocal(wallet: WalletEntity) {
        val accountEvents = eventsRepository.getLocal(wallet.accountId, wallet.testnet) ?: return
        val walletEventItems = mapping(wallet, accountEvents.events)
        if (walletEventItems.isNotEmpty()) {
            setItems(wallet, walletEventItems + getLocalDAppEvents(wallet), false)
        }
    }

    private suspend fun loadRemote(wallet: WalletEntity, beforeLt: Long? = null) {
        val accountEvents = eventsRepository.getRemote(wallet.accountId, wallet.testnet, beforeLt) ?: return
        val walletEventItems = mapping(wallet, accountEvents.events)
        if (beforeLt == null) {
            setItems(wallet, walletEventItems + getRemoteDAppEvents(wallet), false)
        } else {
            val oldValues = _uiItemsFlow.value ?: emptyList()
            setItems(wallet, oldValues + walletEventItems, true)
        }
    }

    private suspend fun mapping(
        wallet: WalletEntity,
        events: List<AccountEvent>
    ): List<HistoryItem> {
        return historyHelper.mapping(
            wallet = wallet,
            events = events,
            removeDate = false,
            hiddenBalances = settingsRepository.hiddenBalances
        )
    }

    private fun setItems(
        wallet: WalletEntity,
        items: List<HistoryItem>,
        more: Boolean
    ) {
        val preparedItems = items.filter { it is HistoryItem.Event || it is HistoryItem.App }
            .sortedBy { it.timestampForSort }
            .reversed()

        val uiItems = mutableListOf<HistoryItem>()
        var currentDate: String? = null

        for (item in preparedItems) {
            val timestamp = item.timestampForSort
            val dateFormat = DateHelper.formatDate(application, timestamp)
            if (dateFormat != currentDate) {
                uiItems.add(HistoryItem.Header(dateFormat, item.timestampForSort))
                currentDate = dateFormat
            }
            uiItems.add(item)
        }

        _uiItemsFlow.value = uiItems.toList()
        if (!more) {
            screenCacheSource.set(CACHE_NAME, wallet.id, uiItems)
        }
    }

    private fun setUpdating(updating: Boolean) {
        _isUpdatingFlow.tryEmit(updating)
    }

    private fun withUpdating(action: suspend () -> Unit) {
        viewModelScope.launch {
            setUpdating(true)
            action()
            setUpdating(false)
        }
    }

    private fun getCached(wallet: WalletEntity): List<HistoryItem>? {
        val items: List<HistoryItem> = screenCacheSource.get(CACHE_NAME, wallet.id) {
            HistoryItem.createFromParcel(it)
        }
        if (items.isEmpty()) {
            return null
        }
        return items
    }

    private companion object {
        private const val CACHE_NAME = "events"
    }
}