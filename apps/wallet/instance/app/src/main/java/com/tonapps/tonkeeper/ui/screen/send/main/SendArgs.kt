package com.tonapps.tonkeeper.ui.screen.send.main

import android.os.Bundle
import com.tonapps.blockchain.ton.extensions.base64
import com.tonapps.blockchain.ton.extensions.parseCell
import com.tonapps.blockchain.ton.extensions.toRawAddress
import org.ton.cell.Cell
import uikit.base.BaseArgs

data class SendArgs(
    val targetAddress: String?,
    val tokenAddress: String,
    val amountNano: Long,
    val text: String?,
    val nftAddress: String,
    val bin: Cell?
): BaseArgs() {

    private companion object {
        private const val ARG_TARGET_ADDRESS = "target_address"
        private const val ARG_TOKEN_ADDRESS = "token_address"
        private const val ARG_AMOUNT_NANO = "amount_nano"
        private const val ARG_TEXT = "text"
        private const val ARG_NFT_ADDRESS = "nft_address"
        private const val ARG_BIN = "bin"

        private fun normalizeTokenAddress(address: String?): String {
            return if (address.isNullOrBlank()) "TON" else address.toRawAddress()
        }

        private fun normalizeAmount(amount: Long): Long {
            return if (amount < 0) 0 else amount
        }
    }

    val isNft: Boolean
        get() = nftAddress.isNotEmpty()

    constructor(bundle: Bundle) : this(
        targetAddress = bundle.getString(ARG_TARGET_ADDRESS),
        tokenAddress = normalizeTokenAddress(bundle.getString(ARG_TOKEN_ADDRESS)),
        amountNano = normalizeAmount(bundle.getLong(ARG_AMOUNT_NANO)),
        text = bundle.getString(ARG_TEXT),
        nftAddress = bundle.getString(ARG_NFT_ADDRESS) ?: "",
        bin = bundle.getString(ARG_BIN)?.parseCell()
    )

    override fun toBundle(): Bundle {
        val bundle = Bundle()
        targetAddress?.let { bundle.putString(ARG_TARGET_ADDRESS, it) }
        bundle.putString(ARG_TOKEN_ADDRESS, tokenAddress)
        if (amountNano > 0) {
            bundle.putLong(ARG_AMOUNT_NANO, amountNano)
        }
        text?.let { bundle.putString(ARG_TEXT, it) }
        bundle.putString(ARG_NFT_ADDRESS, nftAddress)
        bin?.let { bundle.putString(ARG_BIN, it.base64()) }
        return bundle
    }
}