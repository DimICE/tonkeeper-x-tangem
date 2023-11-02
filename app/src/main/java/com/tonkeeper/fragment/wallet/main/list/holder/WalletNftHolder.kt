package com.tonkeeper.fragment.wallet.main.list.holder

import android.net.Uri
import android.view.ViewGroup
import android.widget.TextView
import com.facebook.drawee.generic.RoundingParams
import com.facebook.drawee.view.SimpleDraweeView
import com.facebook.imagepipeline.common.ResizeOptions
import com.facebook.imagepipeline.request.ImageRequestBuilder
import com.tonkeeper.R
import com.tonkeeper.api.collectionName
import com.tonkeeper.fragment.nft.NftScreen
import com.tonkeeper.fragment.wallet.main.list.item.WalletNftItem
import uikit.extensions.getDimension

class WalletNftHolder(
    parent: ViewGroup
): WalletHolder<WalletNftItem>(parent, R.layout.view_nft) {

    private val radius = context.getDimension(uikit.R.dimen.cornerMedium)

    private val previewView = findViewById<SimpleDraweeView>(R.id.preview)
    private val titleView = findViewById<TextView>(R.id.title)
    private val collectionView = findViewById<TextView>(R.id.collection)

    init {
        previewView.hierarchy.roundingParams = RoundingParams.fromCornersRadii(radius, radius, 0f, 0f)
    }

    override fun onBind(item: WalletNftItem) {
        itemView.setOnClickListener {
            nav?.add(NftScreen.newInstance(item.nftAddress))
        }

        loadImage(item.imageURI)
        titleView.text = item.title
        collectionView.text = item.collectionName
    }

    private fun loadImage(uri: Uri) {
        val builder = ImageRequestBuilder.newBuilderWithSource(uri)
        builder.resizeOptions = ResizeOptions.forSquareSize(192)
        previewView.setImageRequest(builder.build())
    }
}