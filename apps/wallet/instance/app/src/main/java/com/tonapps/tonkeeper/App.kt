package com.tonapps.tonkeeper

import android.app.Application
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.CameraXConfig
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.imagepipeline.core.ImagePipelineConfig
import com.facebook.imagepipeline.core.ImageTranscoderType
import com.facebook.imagepipeline.core.MemoryChunkType
import com.tonapps.tonkeeper.koin.koinModel
import com.tonapps.wallet.api.apiModule
import com.tonapps.wallet.data.account.accountModule
import com.tonapps.wallet.data.rates.ratesModule
import com.tonapps.wallet.data.token.tokenModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import com.tonapps.wallet.data.backup.backupModule
import com.tonapps.wallet.data.browser.browserModule
import com.tonapps.wallet.data.collectibles.collectiblesModule
import com.tonapps.wallet.data.core.Theme
import com.tonapps.wallet.data.core.dataModule
import com.tonapps.wallet.data.events.eventsModule
import com.tonapps.wallet.data.push.pushModule
import com.tonapps.wallet.data.rn.rnLegacyModule
import com.tonapps.wallet.data.tonconnect.tonConnectModule
import io.appmetrica.analytics.AppMetrica
import io.appmetrica.analytics.AppMetricaConfig
import org.koin.core.component.KoinComponent

class App: Application(), CameraXConfig.Provider, KoinComponent {

    companion object {

        lateinit var instance: App
    }

    override fun onCreate() {
        super.onCreate()
        Theme.add("blue", uikit.R.style.Theme_App_Blue)
        Theme.add("dark", uikit.R.style.Theme_App_Dark)
        Theme.add("light", uikit.R.style.Theme_App_Light, true)

        instance = this

        startKoin {
            androidContext(this@App)
            modules(koinModel, rnLegacyModule, backupModule, dataModule, browserModule, pushModule, tonConnectModule, apiModule, accountModule, ratesModule, tokenModule, eventsModule, collectiblesModule)
        }

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        initFresco()

        // Creating an extended library configuration.
        val config = AppMetricaConfig.newConfigBuilder("715217e0-8181-421c-ab93-904345d31a2a").build()
        // Initializing the AppMetrica SDK.
        AppMetrica.activate(this, config)
    }

    private fun initFresco() {
        val configBuilder = ImagePipelineConfig.newBuilder(this)
        configBuilder.setMemoryChunkType(MemoryChunkType.BUFFER_MEMORY)
        configBuilder.setImageTranscoderType(ImageTranscoderType.JAVA_TRANSCODER)
        configBuilder.experiment().setNativeCodeDisabled(true)
        configBuilder.setDownsampleEnabled(false)

        Fresco.initialize(this, configBuilder.build())
    }

    override fun getCameraXConfig(): CameraXConfig {
        return CameraXConfig.Builder
            .fromConfig(Camera2Config.defaultConfig())
            .setMinimumLoggingLevel(Log.ERROR).build()
    }

    fun isOriginalAppInstalled(): Boolean {
        val pm = packageManager
        return try {
            pm.getPackageInfo("com.ton_keeper", 0)
            true
        } catch (e: Exception) {
            false
        }
    }
}