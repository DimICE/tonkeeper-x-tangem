package com.tonapps.signer.deeplink

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.tonapps.blockchain.ton.extensions.base64
import com.tonapps.blockchain.ton.extensions.hex
import com.tonapps.security.hex
import com.tonapps.signer.Key
import org.ton.api.pub.PublicKeyEd25519

object TKDeepLink {

    private const val STORE_LINK = "https://play.google.com/store/apps/details?id=com.ton_keeper"
    private const val APP_SCHEME = "tonkeeper"

    fun buildPublishUri(signature: ByteArray): Uri {
        val baseUri = Uri.parse("${APP_SCHEME}://publish")
        val builder = baseUri.buildUpon()
        builder.appendQueryParameter(Key.SIGN, hex(signature))
        return builder.build()
    }

    fun buildLinkUri(
        publicKey: PublicKeyEd25519,
        name: String,
        local: Boolean
    ): Uri {
        val baseUri = Uri.parse("${APP_SCHEME}://signer/link")
        val builder = baseUri.buildUpon()
        builder.appendQueryParameter("pk", publicKey.hex())
        builder.appendQueryParameter("name", name)
        if (local) {
            builder.appendQueryParameter("local", "true")
        }
        return builder.build()
    }

    fun buildLinkUriWeb(publicKey: PublicKeyEd25519, name: String): Uri {
        val baseUri = Uri.parse("https://wallet.tonkeeper.com/signer/link")
        val builder = baseUri.buildUpon()
        builder.appendQueryParameter("pk", publicKey.hex())
        builder.appendQueryParameter("name", name)
        builder.appendQueryParameter("local", "true")
        return builder.build()
    }

    fun openOrInstall(context: Context, uri: Uri) {
        try {
            openUri(context, uri)
        } catch (e: Throwable) {
            openStore(context)
        }
    }

    private fun openUri(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW, uri)
        intent.setPackage("com.ton_keeper")
        open(context, intent)
    }

    private fun openStore(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(STORE_LINK))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        open(context, intent)
    }

    private fun open(context: Context, intent: Intent) {
        context.startActivity(intent)
    }
}