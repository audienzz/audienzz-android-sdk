package org.audienzz.mobile.testapp.view

import android.os.Bundle
import android.widget.Button
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import org.audienzz.mobile.AudienzzPrebidMobile
import org.audienzz.mobile.AudienzzRemoteBannerView
import org.audienzz.mobile.testapp.R

/**
 * A separate screen with a remote-config banner, opened from the Remote Config tab. Navigating here
 * and back exercises screen-navigation pause/resume/reload and ad↔screen matching. Reports itself via
 * pageImpression(this) — the SDK derives the screen name from the Activity.
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

        // Same transition as the system back gesture, just discoverable. Finishing is what makes
        // the Remote Config tab current again, which is the half of this test that matters.
        findViewById<Button>(R.id.btnClose).setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        AudienzzPrebidMobile.pageImpression(this)
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
