package com.tonapps.tonkeeper.ui.screen.settings.language

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.tonapps.tonkeeper.App
import com.tonapps.tonkeeper.extensions.capitalized
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.settings.language.list.Item
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.localization.Language
import com.tonapps.wallet.localization.SupportedLanguages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

class LanguageViewModel(
    app: Application,
    private val settingsRepository: SettingsRepository
): BaseWalletVM(app) {

    private val _uiItemsFlow = MutableStateFlow<List<Item>>(emptyList())
    val uiItemsFlow = _uiItemsFlow.asStateFlow().filter { it.isNotEmpty() }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _uiItemsFlow.value = getItems()
        }
    }

    fun setLanguage(code: String) {
        settingsRepository.language = Language(code)

        _uiItemsFlow.value = _uiItemsFlow.value.map { item ->
            item.copy(selected = item.code == code)
        }
    }

    private fun getItems(): List<Item> {
        val items = mutableListOf<Item>()
        for ((index, language) in SupportedLanguages.withIndex()) {
            val position = ListCell.getPosition(SupportedLanguages.size, index)
            items.add(getLangItem(language, position))
        }
        return items.toList()
    }

    private fun getLangItem(language: Language, position: ListCell.Position): Item {
        val checked = language == settingsRepository.language
        return Item(
            name = language.name.capitalized,
            nameLocalized = language.nameLocalized.capitalized,
            selected = checked,
            code = language.code,
            position = position
        )
    }
}