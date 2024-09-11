package org.audienzz.mobile.testapp

import android.app.Application
import org.audienzz.mobile.AudienzzTargetingParams

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        preInitSdk()
    }

    private fun preInitSdk() {
        AudienzzTargetingParams.isSubjectToGDPR = true
    }

    companion object {

        const val TAG = "TEST_APP"
    }
}
