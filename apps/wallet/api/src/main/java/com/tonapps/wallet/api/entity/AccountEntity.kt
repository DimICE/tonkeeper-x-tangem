package com.tonapps.wallet.api.entity

import android.net.Uri
import android.os.Parcelable
import com.tonapps.blockchain.ton.extensions.toAccountId
import com.tonapps.blockchain.ton.extensions.toRawAddress
import com.tonapps.blockchain.ton.extensions.toUserFriendly
import com.tonapps.blockchain.ton.extensions.toWalletAddress
import com.tonapps.extensions.short4
import io.tonapi.models.Account
import io.tonapi.models.AccountAddress
import kotlinx.parcelize.Parcelize
import org.ton.block.AddrStd

@Parcelize
data class AccountEntity(
    val address: String,
    val accountId: String,
    val name: String?,
    val iconUri: Uri?,
    val isWallet: Boolean,
    val isScam: Boolean
): Parcelable {

    val accountName: String
        get() {
            if (name.isNullOrBlank()) {
                return address.short4
            }
            return name
        }

    constructor(address: AddrStd, testnet: Boolean) : this(
        address = address.toWalletAddress(testnet),
        accountId = address.toAccountId(),
        name = null,
        iconUri = null,
        isWallet = true,
        isScam = false,
    )

    constructor(model: AccountAddress, testnet: Boolean): this(
        address = model.address.toUserFriendly(model.isWallet, testnet),
        accountId = model.address.toRawAddress(),
        name = model.name,
        iconUri = model.icon?.let { Uri.parse(it) },
        isWallet = model.isWallet,
        isScam = model.isScam
    )

    constructor(account: Account, testnet: Boolean) : this(
        address = account.address.toUserFriendly(account.isWallet, testnet),
        accountId = account.address.toRawAddress(),
        name = account.name,
        iconUri = account.icon?.let { Uri.parse(it) },
        isWallet = account.isWallet,
        isScam = account.isScam ?: false
    )
}
