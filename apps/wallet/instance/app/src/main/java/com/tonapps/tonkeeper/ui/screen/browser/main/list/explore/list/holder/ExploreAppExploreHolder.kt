package com.tonapps.tonkeeper.ui.screen.browser.main.list.explore.list.holder

import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.facebook.imagepipeline.common.ResizeOptions
import com.tonapps.tonkeeper.helper.BrowserHelper
import com.tonapps.tonkeeper.ui.screen.browser.main.list.explore.list.ExploreItem
import com.tonapps.tonkeeper.ui.screen.browser.dapp.DAppScreen
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.backgroundContentTintColor
import uikit.extensions.drawable
import uikit.navigation.Navigation
import uikit.widget.FrescoView

class ExploreAppExploreHolder(parent: ViewGroup): ExploreHolder<ExploreItem.App>(parent, R.layout.view_browser_app) {

    private val iconView = findViewById<FrescoView>(R.id.icon)
    private val nameView = findViewById<AppCompatTextView>(R.id.name)

    init {
        val placeholderDrawable = context.drawable(uikit.R.drawable.bg_content_tint_16)
        placeholderDrawable.setTint(context.backgroundContentTintColor)
        iconView.setPlaceholder(placeholderDrawable)
    }

    override fun onBind(item: ExploreItem.App) {
        itemView.setOnClickListener {
            if (item.app.useCustomTabs) {
                BrowserHelper.open(context, item.url.toString())
            } else {
                Navigation.from(context)?.add(DAppScreen.newInstance(
                    wallet = item.wallet,
                    title = item.name,
                    url = item.url,
                    source = "browser"
                ))
            }
        }
        iconView.setImageURIWithResize(item.icon, ResizeOptions.forSquareSize(172)!!)
        nameView.text = item.name
        if (item.singleLine) {
            nameView.isSingleLine = true
            nameView.maxLines = 1
        } else {
            nameView.isSingleLine = false
            nameView.maxLines = 2
        }
    }
}