package org.audienzz.mobile.testapp

import android.app.Application
import org.audienzz.mobile.AudienzzPrebidMobile

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        // Demo: apply the persisted Smart Refresh v2 toggle (see the switch on the home screen).
        // The local override wins over the backend flag, so this forces the model on/off for the app.
        AudienzzPrebidMobile.smartRefreshV2Override = DemoFeatureFlags.isSmartRefreshV2Enabled(this)
    }

    companion object {
        const val TAG = "TestApp"
    }
}
