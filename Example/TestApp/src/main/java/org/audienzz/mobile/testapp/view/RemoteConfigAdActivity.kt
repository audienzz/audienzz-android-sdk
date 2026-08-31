package org.audienzz.mobile.testapp.view

import android.os.Bundle
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import org.audienzz.mobile.AudienzzRemoteBannerView
import org.audienzz.mobile.testapp.R

/**
 * A separate screen with a remote-config banner, opened from the Remote Config tab. Navigating here
 * and back exercises screen-navigation pause/resume/reload and ad↔screen matching (screen tracking
 * is automatic — no onScreenResumed calls here).
 */
class RemoteConfigAdActivity : AppCompatActivity() {

    private var banner: AudienzzRemoteBannerView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_remote_config_ad)
        title = "Remote Config Ad Screen"

        val container = findViewById<FrameLayout>(R.id.bannerContainer)
        val b = AudienzzRemoteBannerView(this, BANNER_CONFIG_ID)
        banner = b
        container.addView(
            b,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        b.loadAd()
    }

    override fun onDestroy() {
        banner?.destroy()
        banner = null
        super.onDestroy()
    }

    companion object {
        private const val BANNER_CONFIG_ID = "46"
    }
}
