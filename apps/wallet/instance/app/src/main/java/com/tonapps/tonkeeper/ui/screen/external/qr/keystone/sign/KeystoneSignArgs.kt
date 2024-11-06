package com.tonapps.tonkeeper.ui.screen.external.qr.keystone.sign

import android.os.Bundle
import android.util.Log
import com.tonapps.extensions.getParcelableCompat
import com.tonapps.ur.UR
import com.tonapps.ur.registry.CryptoKeypath
import com.tonapps.ur.registry.TonSignRequest
import com.tonapps.ur.registry.pathcomponent.IndexPathComponent
import com.tonapps.ur.registry.pathcomponent.PathComponent
import com.tonapps.wallet.data.account.entities.WalletEntity
import org.ton.crypto.hex
import uikit.base.BaseArgs

data class KeystoneSignArgs(
    val requestId: String,
    val unsignedBody: String,
    val isTransaction: Boolean,
    val address: String,
    val keystone: WalletEntity.Keystone,
): BaseArgs() {

    private companion object {
        private const val ARG_REQUEST_ID = "requestId"
        private const val ARG_UNSIGNED_BODY = "unsignedBody"
        private const val ARG_IS_TRANSACTION = "isTransaction"
        private const val ARG_ADDRESS = "address"
        private const val ARG_KEYSTONE = "keystone"

        private fun buildPathList(path: String): List<PathComponent> {
            if (path.isBlank()) {
                return emptyList()
            }

            val components = mutableListOf<PathComponent>()
            for (component in path.split("/").drop(1)) {
                val hardened = component.endsWith("'")
                val value = if (hardened) {
                    component.dropLast(1)
                } else {
                    component
                }
                if (value.isBlank()) {
                    continue
                }
                val index = value.toIntOrNull() ?: continue
                components.add(IndexPathComponent(index, hardened))
            }
            return components.toList()
        }
    }

    val ur: UR by lazy {
        val pathList = buildPathList(keystone.path)
        val sourceFingerprint = hex(keystone.xfp)
        val isEmpty = 4 > sourceFingerprint.size
        val path = if (isEmpty) {
            null
        } else {
            CryptoKeypath(pathList, sourceFingerprint)
        }
        val request = TonSignRequest(requestId.toByteArray(), hex(unsignedBody), if (isTransaction) 1 else 2, path, address, "Tonkeeper")
        request.toUR()
    }

    constructor(bundle: Bundle) : this(
        requestId = bundle.getString(ARG_REQUEST_ID)!!,
        unsignedBody = bundle.getString(ARG_UNSIGNED_BODY)!!,
        isTransaction = bundle.getBoolean(ARG_IS_TRANSACTION),
        address = bundle.getString(ARG_ADDRESS)!!,
        keystone = bundle.getParcelableCompat(ARG_KEYSTONE)!!
    )

    override fun toBundle(): Bundle {
        val bundle = Bundle()
        bundle.putString(ARG_REQUEST_ID, requestId)
        bundle.putString(ARG_UNSIGNED_BODY, unsignedBody)
        bundle.putBoolean(ARG_IS_TRANSACTION, isTransaction)
        bundle.putString(ARG_ADDRESS, address)
        bundle.putParcelable(ARG_KEYSTONE, keystone)
        return bundle
    }

}
