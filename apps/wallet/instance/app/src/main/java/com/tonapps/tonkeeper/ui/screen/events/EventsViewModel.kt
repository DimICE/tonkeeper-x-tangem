package com.tonapps.tonkeeper.ui.screen.events

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.tonapps.tonkeeper.api.AccountEventWrap
import com.tonapps.tonkeeper.core.history.HistoryHelper
import com.tonapps.tonkeeper.core.history.list.item.HistoryItem
import com.tonapps.tonkeeper.manager.tx.TransactionManager
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.events.filters.FilterItem
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.core.ScreenCacheSource
import com.tonapps.wallet.data.dapps.DAppsRepository
import com.tonapps.wallet.data.dapps.entities.AppEntity
import com.tonapps.wallet.data.dapps.entities.AppPushEntity
import com.tonapps.wallet.data.events.EventsRepository
import com.tonapps.wallet.data.events.isOutTransfer
import com.tonapps.wallet.data.settings.SettingsRepository
import io.tonapi.models.AccountEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

// TODO: Refactor this class
class EventsViewModel(
    app: Application,
    private val wallet: WalletEntity,
    private val accountsRepository: AccountRepository,
    private val eventsRepository: EventsRepository,
    private val historyHelper: HistoryHelper,
    private val screenCacheSource: ScreenCacheSource,
    private val settingsRepository: SettingsRepository,
    private val transactionManager: TransactionManager,
    private val dAppsRepository: DAppsRepository,
): BaseWalletVM(app) {

    private var autoRefreshJob: Job? = null
    private var events: Array<AccountEventWrap>? = null
    private var pushes: Array<AppPushEntity>? = null
    private val isLoading: AtomicBoolean = AtomicBoolean(true)
    private val txFilter = AtomicInteger(TX_FILTER_NONE)

    private val _selectedFilter = MutableStateFlow<FilterItem?>(null)
    private val selectedFilter = _selectedFilter.asStateFlow()

    private val _filterAppsFlow = MutableStateFlow<List<AppEntity>>(emptyList())
    private val filterAppsFlow = _filterAppsFlow.asStateFlow()

    val uiFilterItemsFlow: Flow<List<FilterItem>> = combine(
        selectedFilter,
        filterAppsFlow,
    ) { selected, apps ->
        val uiFilterItems = mutableListOf<FilterItem>()
        apps.forEach { app ->
            uiFilterItems.add(FilterItem.App(selected?.id == app.id, app))
        }
        uiFilterItems.add(FilterItem.Send(selected?.type == FilterItem.TYPE_SEND))
        uiFilterItems.add(FilterItem.Receive(selected?.type == FilterItem.TYPE_RECEIVE))
        uiFilterItems.toList()
    }


    private val _uiStateFlow = MutableStateFlow(EventsUiState())
    val uiStateFlow = _uiStateFlow.stateIn(viewModelScope, SharingStarted.Eagerly, EventsUiState())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            setUiItems(getCached())
            submitEvents(cache(), true)

            val eventsDeferred = async { load() }
            val dappEventsDeferred = async { loadDAppEvents() }

            submitEvents(eventsDeferred.await(), loading = false, updateState = false)
            submitPushes(dappEventsDeferred.await(), updateState = false)
            updateState()
        }

        eventsRepository.decryptedCommentFlow.collectFlow {
            updateState()
        }

        settingsRepository.hiddenBalancesFlow.drop(1).collectFlow {
            updateState()
        }

        transactionManager.eventsFlow(wallet).collectFlow { event ->
            /*if (event.pending) {
                appendEvent(AccountEventWrap(event.body))
            }*/
            refresh()
        }

        autoRefreshJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                checkAutoRefresh()
                delay(35.seconds)
            }
        }

        selectedFilter.collectFlow { filter ->
            val txFilterValue = if (filter == null) {
                TX_FILTER_NONE
            } else {
                when (filter.id) {
                    FilterItem.SEND_ID -> TX_FILTER_SENT
                    FilterItem.RECEIVE_ID -> TX_FILTER_RECEIVED
                    else -> TX_FILTER_APP
                }
            }

            txFilter.set(txFilterValue)

            if (txFilterValue == TX_FILTER_APP) {
                submitEvents(emptyList(), false)
            } else {
                submitEvents(emptyList(), true)
                submitEvents(load(), false)
            }
        }
    }

    fun clickFilter(filter: FilterItem) {
        if (_selectedFilter.value?.id == filter.id) {
            _selectedFilter.value = null
        } else {
            _selectedFilter.value = filter
        }
    }

    private suspend fun loadDAppEvents(): List<AppPushEntity> {
        if (!wallet.isTonConnectSupported) {
            return emptyList()
        }

        val tonProof = accountsRepository.requestTonProofToken(wallet) ?: return emptyList()
        return dAppsRepository.getPushes(tonProof, wallet.accountId)
    }

    private suspend fun checkAutoRefresh() {
        if (hasPendingEvents()) {
            setLoading()
            requestRefresh()
        }
    }

    fun refresh() {
        if (isLoading.get()) {
            return
        }
        setLoading()
        viewModelScope.launch(Dispatchers.IO) {
            requestRefresh()
        }
    }

    private suspend fun requestRefresh() = withContext(Dispatchers.IO) {
        submitPushes(loadDAppEvents())
        submitEvents(load(), false)
    }

    fun loadMore() {
        if (isLoading.get()) {
            return
        }

        val lastLt = getLastLt() ?: return

        setLoading()
        viewModelScope.launch(Dispatchers.IO) {
            val events = load(lastLt)
            appendEvents(events)
        }
    }

    private fun setLoading() {
        isLoading.set(true)

        _uiStateFlow.update {
            it.copy(
                items = historyHelper.withLoadingItem(it.items),
                isLoading = true
            )
        }
    }

    private fun setUiItems(uiItems: List<HistoryItem>) {
        val loading = isLoading.get()
        _uiStateFlow.value = EventsUiState(
            items = if (loading && uiItems.isNotEmpty()) {
                historyHelper.withLoadingItem(uiItems)
            } else {
                uiItems
            },
            isLoading = loading
        )
    }

    private suspend fun mapping(events: List<AccountEvent>, pushes: List<AppPushEntity>): List<HistoryItem> {
        val txFilterValue = txFilter.get()

        if (txFilterValue == TX_FILTER_APP) {
            val selectedFilter = selectedFilter.value as? FilterItem.App ?: return emptyList()

            val pushItems = pushes.map {
                HistoryItem.App(context, wallet, it)
            }.filter { it.url == selectedFilter.url }
            return historyHelper.groupByDate(pushItems)
        }

        val eventItems = historyHelper.mapping(
            wallet = wallet,
            events = events.map { it.copy() },
            removeDate = false,
            hiddenBalances = settingsRepository.hiddenBalances
        )

        if (txFilterValue == TX_FILTER_SENT || txFilterValue == TX_FILTER_RECEIVED) {
            return historyHelper.groupByDate(eventItems)
        }

        val firstTimestamp = events.firstOrNull()?.timestamp ?: 0
        val lastTimestamp = events.lastOrNull()?.timestamp ?: 0
        val eventsTimestampRange = firstTimestamp..lastTimestamp

        val pushItems = pushes.map {
            HistoryItem.App(context, wallet, it)
        }.filter { it.timestamp in eventsTimestampRange }


        return historyHelper.groupByDate(eventItems + pushItems)
    }

    private fun getCached(): List<HistoryItem> {
        return screenCacheSource.get(CACHE_NAME, wallet.id) {
            HistoryItem.createFromParcel(it)
        }
    }

    private suspend fun updateState() = withContext(Dispatchers.IO) {
        val events = getEvents()
        val uiItems = mapping(events.map { it.event }, pushes?.toList() ?: emptyList())
        setUiItems(uiItems)
        if (txFilter.get() == TX_FILTER_NONE) {
            screenCacheSource.set(CACHE_NAME, wallet.id, uiItems)
        }
    }

    private suspend fun submitPushes(
        newPushes: List<AppPushEntity>,
        updateState: Boolean = true
    ) {
        pushes = newPushes.sortedByDescending { it.timestamp }.toTypedArray()
        _filterAppsFlow.value = newPushes.map { it.from }.distinctBy { it.id }
        if (updateState) {
            updateState()
        }
    }

    private suspend fun submitEvents(
        newEvents: List<AccountEventWrap>,
        loading: Boolean,
        updateState: Boolean = true
    ) = withContext(Dispatchers.IO) {
        events = newEvents.distinctBy { it.eventId }
            .sortedByDescending { it.timestamp }
            .toTypedArray()

        isLoading.set(loading)
        if (updateState) {
            updateState()
        }
    }

    private suspend fun getEvents(): MutableList<AccountEventWrap> = withContext(Dispatchers.IO) {
        events?.map { it.copy() }?.toMutableList() ?: mutableListOf()
    }

    private suspend fun appendEvents(newEvents: List<AccountEventWrap>) {
        val list = getEvents() + newEvents.map { it.copy() }
        submitEvents(list, false)
    }

    private suspend fun appendEvent(event: AccountEventWrap) {
        appendEvents(listOf(event.copy()))
    }

    private fun getLastLt(): Long? {
        val lt = events?.lastOrNull { !it.inProgress }?.lt ?: return null
        if (0 >= lt) {
            return null
        }
        return lt
    }

    private fun hasPendingEvents(): Boolean {
        return events?.firstOrNull { it.inProgress } != null
    }

    private suspend fun loadWithFilters(onlySent: Boolean, beforeLt: Long? = null): List<AccountEventWrap> {
        return loadUntil(onlySent = onlySent, beforeLt = beforeLt).map(::AccountEventWrap)
    }

    private suspend fun loadUntil(
        max: Int = 25,
        onlySent: Boolean,
        beforeLt: Long? = null
    ): List<AccountEvent> {
        val list = mutableListOf<AccountEvent>()
        var nextBeforeLt = beforeLt
        do {
            val events = loadEvents(beforeLt = nextBeforeLt) ?: break
            list.addAll(events.filter { event ->
                event.isOutTransfer(wallet.accountId) == onlySent
            })
            if (list.size >= max) break
            nextBeforeLt = events.last().lt
        } while (true)
        return list.toList()
    }

    private suspend fun loadEvents(limit: Int = 50, beforeLt: Long? = null): List<AccountEvent>? {
        val events = eventsRepository.getRemote(
            accountId = wallet.accountId,
            testnet = wallet.testnet,
            limit = limit,
            beforeLt = beforeLt
        )?.events
        if (events.isNullOrEmpty()) {
            return null
        }
        return events
    }

    private suspend fun load(beforeLt: Long? = null): List<AccountEventWrap> {
        val txFilterValue = txFilter.get()
        if (txFilterValue == TX_FILTER_SENT) {
            return loadSent(beforeLt)
        } else if (txFilterValue == TX_FILTER_RECEIVED) {
            return loadReceived(beforeLt)
        } else if (txFilterValue == TX_FILTER_APP) {
            return emptyList()
        }
        return loadDefault(beforeLt)
    }


    private suspend fun loadDefault(beforeLt: Long?): List<AccountEventWrap> {
        val list = eventsRepository.getRemote(
            accountId = wallet.accountId,
            testnet = wallet.testnet,
            beforeLt = beforeLt
        )?.events?.map(::AccountEventWrap)
        return list ?: emptyList()
    }

    private suspend fun loadSent(beforeLt: Long?): List<AccountEventWrap> {
        return loadWithFilters(true, beforeLt)
    }

    private suspend fun loadReceived(beforeLt: Long?): List<AccountEventWrap> {
        return loadWithFilters(false, beforeLt)
    }

    private suspend fun cache(): List<AccountEventWrap> {
        val list = eventsRepository.getLocal(
            accountId = wallet.accountId,
            testnet = wallet.testnet
        )?.events?.map { AccountEventWrap.cached(it)}
        return list ?: emptyList()
    }

    override fun onCleared() {
        super.onCleared()
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

    private companion object {
        private const val CACHE_NAME = "events"

        private const val TX_FILTER_NONE = 0
        private const val TX_FILTER_SENT = 1
        private const val TX_FILTER_RECEIVED = 2
        private const val TX_FILTER_APP = 3
    }
}