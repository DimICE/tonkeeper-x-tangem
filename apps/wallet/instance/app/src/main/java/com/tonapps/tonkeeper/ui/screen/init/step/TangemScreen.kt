package com.tonapps.tonkeeper.ui.screen.init.step

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import com.tangem.TangemSdk
import com.tangem.common.CompletionResult
import com.tangem.common.card.Card
import com.tangem.common.core.CardSession
import com.tangem.common.core.CardSessionRunnable
import com.tangem.common.core.CompletionCallback
import com.tangem.common.core.TangemSdkError
import com.tangem.crypto.hdWallet.DerivationPath
import com.tangem.crypto.hdWallet.bip32.ExtendedPublicKey
import com.tangem.operations.ScanTask
import com.tangem.operations.derivation.DeriveWalletPublicKeyTask
import com.tangem.sdk.extensions.init
import com.tonapps.tonkeeper.ui.screen.init.InitViewModel
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.iconPrimaryColor
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.ton.api.pub.PublicKeyEd25519
import org.ton.crypto.hex
import uikit.base.BaseFragment
import uikit.dialog.alert.AlertDialog
import uikit.extensions.collectFlow
import uikit.extensions.doKeyboardAnimation
import uikit.extensions.withAlpha
import uikit.widget.ColumnLayout
import uikit.widget.LoaderView
import kotlin.coroutines.resume

class TangemScreen: BaseFragment(R.layout.fragment_init_tangem) {

    private val initViewModel: InitViewModel by viewModel(ownerProducer = { requireParentFragment() })

    private lateinit var scrollView: NestedScrollView
    private lateinit var contentView: ColumnLayout
    private lateinit var button: Button
    private lateinit var loaderView: LoaderView

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

        setDefault()

        collectFlow(initViewModel.uiTopOffset) {
            contentView.updatePadding(top = it)
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun next() {
        lifecycleScope.launch {
            setLoading()
            try {
                val tangemSdk = TangemSdk.init(requireActivity())
                val task = object : CardSessionRunnable<Triple<String,ByteArray,ExtendedPublicKey>> {
                    override fun run(session: CardSession, callback: CompletionCallback<Triple<String,ByteArray,ExtendedPublicKey>>) {
                        ScanTask().run(session) { scanResult ->
                            when (scanResult) {
                                is CompletionResult.Success -> {
                                    val cardId = scanResult.data.cardId
                                    val walletPublicKey = scanResult.data.wallets[4].publicKey
                                    val command = DeriveWalletPublicKeyTask(walletPublicKey, DerivationPath(rawPath = "m/44'/607'/0'"))
                                    command.run(session) { deriveResult ->
                                        when (deriveResult) {
                                            is CompletionResult.Success -> {
                                                callback(CompletionResult.Success(Triple(cardId, walletPublicKey, deriveResult.data)))
                                            }

                                            is CompletionResult.Failure -> {
                                                callback(CompletionResult.Failure(deriveResult.error))
                                            }
                                        }
                                    }
                                }

                                is CompletionResult.Failure -> {
                                    callback(CompletionResult.Failure(scanResult.error))
                                }
                            }
                        }
                    }
                }
                tangemSdk.startSessionWithRunnable(
                    runnable = task,
                ) { result ->
                    when (result) {
                        is CompletionResult.Success -> {
                            var (cardId, walletPublicKey, extendedPublicKey) = result.data
                            val hex = extendedPublicKey.publicKey.toHexString()
                            val publicKey = PublicKeyEd25519(hex(hex))
                            lifecycleScope.launch {
                                initViewModel.setTangemPublicKey(tangemCardId = cardId, tangemPublicKey = walletPublicKey, publicKey = publicKey)
                            }
                        }
                        is CompletionResult.Failure -> {
                            requireActivity().runOnUiThread {
                                setDefault()
                            }
                        }
                    }
                }
            } catch (e: Throwable) {
                setDefault()
            }
        }
    }

    private fun setLoading() {
        button.text = ""
        button.isEnabled = false
        loaderView.visibility = View.VISIBLE
    }

    private fun setDefault() {
        button.setText(Localization.continue_action)
        button.isEnabled = true
        loaderView.visibility = View.GONE
    }

    companion object {

        private const val ARG_TESTNET = "testnet"

        fun newInstance(testnet: Boolean): TangemScreen {
            val fragment = TangemScreen()
            fragment.arguments = Bundle().apply {
                putBoolean(ARG_TESTNET, testnet)
            }
            return fragment
        }
    }
}