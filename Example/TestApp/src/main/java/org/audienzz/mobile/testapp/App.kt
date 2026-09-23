package org.audienzz.mobile.testapp

import android.app.Application
import android.util.Log
import org.audienzz.mobile.AudienzzPrebidMobile
import org.audienzz.mobile.AudienzzTargetingParams
import org.audienzz.mobile.util.remote.RemoteConfigManager
import org.audienzz.mobile.api.data.AudienzzInitializationStatus

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        // Demo: apply the persisted Smart Refresh v2 toggle (see the switch on the home screen).
        // The local override wins over the backend flag, so this forces the model on/off for the app.
        // One greppable AUDZ line per slot decision. Capture with `adb logcat -s AUDZ`.
        // On by default HERE because this app exists to be tested and have its log read back; in
        // a real app it is off unless you ask for it. Set it BEFORE initializing, so the very
        // first page impression is in the log.
        AudienzzPrebidMobile.diagnosticsEnabled = true
        AudienzzPrebidMobile.smartRefreshV2Override = DemoFeatureFlags.isSmartRefreshV2Enabled(this)
        // Demo: blank the slot (same size) during a screen-change reload so it's obvious it refreshed.
        AudienzzPrebidMobile.blankOnScreenReload = true

        initializeSdk()
    }

    /**
     * Initialize once, here, before any screen exists.
     *
     * This used to live in whichever fragment happened to be the first tab, which meant the SDK was
     * only initialized if you happened to open that tab — reordering the tabs was enough to leave
     * `MainComponent` unbuilt, so every remote banner logged "Remote config not found" and the app
     * showed no ads at all. An Application is the only place that is guaranteed to run before the
     * first ad, and it is what a real integration should do too.
     *
     * Ads created before the config arrives are fine: `RemoteConfigManager.getAdUnitConfig` awaits
     * the in-flight fetch. What is *not* fine is creating them before this runs at all.
     */
    private fun initializeSdk() {
        RemoteConfigManager.initialize(
            publisherId = PUBLISHER_ID,
            remoteUrl = REMOTE_CONFIG_URL,
        )
        AudienzzPrebidMobile.isPbsDebug = true
        AudienzzPrebidMobile.initializeRemoteSdk(this, PUBLISHER_ID) { status ->
            if (status == AudienzzInitializationStatus.SUCCEEDED) {
                // Override the bundle/storeUrl the remote config carries (com.example.app) with this
                // app's real package, or the bid request describes an app that does not exist.
                AudienzzTargetingParams.bundleName = packageName
                AudienzzTargetingParams.storeUrl =
                    "https://play.google.com/store/apps/details?id=$packageName"
                Log.d(TAG, "SDK initialized")
            } else {
                Log.e(TAG, "SDK init failed: $status")
            }
            isSdkReady = true
            pending.forEach { it() }
            pending.clear()
        }
    }

    companion object {
        const val TAG = "TestApp"
        const val PUBLISHER_ID = "35"
        private const val REMOTE_CONFIG_URL = "https://api.adnz.co/api/ws-sdk-config/public/v1"

        /** Main thread only — every caller is a fragment callback. */
        private var isSdkReady = false
        private val pending = mutableListOf<() -> Unit>()

        /**
         * Run [action] once the SDK has finished initializing, or immediately if it already has.
         *
         * Screens that only create ads do not need this — those await the config themselves. It is
         * for the ones that want to show a spinner until init settles.
         */
        fun whenSdkReady(action: () -> Unit) {
            if (isSdkReady) action() else pending.add(action)
        }
    }
}
