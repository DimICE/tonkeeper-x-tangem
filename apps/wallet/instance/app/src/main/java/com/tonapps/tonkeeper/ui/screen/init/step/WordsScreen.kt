package com.tonapps.tonkeeper.ui.screen.init.step

import android.os.Bundle
import android.text.Editable
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import com.tonapps.blockchain.ton.TonMnemonic
import com.tonapps.tonkeeper.extensions.toast
import com.tonapps.tonkeeper.ui.component.WordEditText
import com.tonapps.tonkeeper.ui.screen.init.InitViewModel
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.iconPrimaryColor
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.ton.mnemonic.Mnemonic
import uikit.base.BaseFragment
import uikit.extensions.clear
import uikit.extensions.collectFlow
import uikit.extensions.doKeyboardAnimation
import uikit.extensions.getCurrentFocusEditText
import uikit.extensions.getViews
import uikit.extensions.hideKeyboard
import uikit.extensions.scrollDown
import uikit.extensions.scrollView
import uikit.extensions.withAlpha
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.ColumnLayout
import uikit.widget.LoaderView

class WordsScreen: BaseFragment(R.layout.fragment_init_words) {

    private val initViewModel: InitViewModel by viewModel(ownerProducer = { requireParentFragment() })

    override val secure: Boolean = true

    private lateinit var scrollView: NestedScrollView
    private lateinit var contentView: ColumnLayout
    private lateinit var button: Button
    private lateinit var loaderView: LoaderView

    private val wordInputs: List<WordEditText> by lazy {
        contentView.getViews().filterIsInstance<WordEditText>()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        scrollView = view.findViewById(R.id.scroll)
        scrollView.doKeyboardAnimation { offset, _, _ ->
            scrollView.updatePadding(bottom = offset)
        }

        contentView = view.findViewById(R.id.content)

        button = view.findViewById(R.id.button)
        button.setOnClickListener { next() }

        loaderView = view.findViewById(R.id.loader)
        loaderView.setTrackColor(requireContext().iconPrimaryColor.withAlpha(.32f))
        loaderView.setColor(requireContext().iconPrimaryColor)

        for ((index, wordInput) in wordInputs.withIndex()) {
            val isLast = index == wordInputs.size - 1
            wordInput.doOnTextChanged = { onTextChanged(index, it) }
            wordInput.imeOptions = if (isLast) EditorInfo.IME_ACTION_DONE else EditorInfo.IME_ACTION_NEXT
            wordInput.setOnEditorActionListener { _, actionId, _ ->
                if (isLast && actionId == EditorInfo.IME_ACTION_DONE) {
                    next()
                    true
                } else if (isLast && actionId == EditorInfo.IME_ACTION_NEXT) {
                    nextInput(index)
                    true
                } else {
                    false
                }
            }
        }

        collectFlow(initViewModel.uiTopOffset) {
            contentView.updatePadding(top = it)
        }
    }

    private fun nextInput(index: Int) {
        val nextView = wordInputs.getOrNull(index + 1) ?: return
        nextView.requestFocus()
        scrollView.scrollView(nextView, false)
    }

    private fun next() {
        lifecycleScope.launch {
            val words = getMnemonic()
            if (words.isEmpty() || !Mnemonic.isValid(words)) {
                if (TonMnemonic.isValidTONKeychain(words)) {
                    navigation?.toast(Localization.multi_account_secret_wrong)
                } else {
                    navigation?.toast(Localization.incorrect_phrase)
                }
                return@launch
            }
            setLoading()
            try {
                initViewModel.setMnemonic(words)
            } catch (e: Throwable) {
                setDefault()
            }
        }
    }

    private fun setLoading() {
        button.text = ""
        button.isEnabled = false
        loaderView.visibility = View.VISIBLE
        wordInputs.forEach { it.isEnabled = false }
    }

    private fun setDefault() {
        button.setText(Localization.continue_action)
        button.isEnabled = false
        loaderView.visibility = View.GONE
        wordInputs.forEach { it.isEnabled = true }
    }

    private fun onTextChanged(index: Int, editable: Editable) {
        if (index == 0) {
            val words = TonMnemonic.parseMnemonic(editable.toString())
            post {
                applyWords(words)
            }
        } else {
            post {
                checkWords()
            }
        }
    }

    private fun applyWords(words: List<String>) {
        if (words.size > 1) {
            wordInputs.first().clear()
            setWords(words)
        } else {
            checkWords()
        }
    }

    private fun checkWords(delay: Long = 0) {
        lifecycleScope.launch {
            if (delay > 0) {
                delay(delay)
            }
            button.isEnabled = getMnemonic().size == 24
        }
    }

    private suspend fun getMnemonic(): List<String> = withContext(Dispatchers.IO) {
        val words = wordInputs
            .map { it.text?.toString() }
            .filter { TonMnemonic.isValid(it) }
            .filterNotNull()

        if (words.size == wordInputs.size) {
            words
        } else {
            emptyList()
        }
    }

    private fun setWords(list: List<String>) {
        for (i in list.indices) {
            val wordInput = wordInputs.getOrNull(i) ?: break
            wordInput.setText(list[i])
        }
        if (list.size == wordInputs.size) {
            context?.getCurrentFocusEditText()?.hideKeyboard()
            scrollView.scrollDown(true)
            checkWords(500)
        }
    }

    companion object {

        private const val ARG_TESTNET = "testnet"

        fun newInstance(testnet: Boolean): WordsScreen {
            val fragment = WordsScreen()
            fragment.arguments = Bundle().apply {
                putBoolean(ARG_TESTNET, testnet)
            }
            return fragment
        }
    }
}