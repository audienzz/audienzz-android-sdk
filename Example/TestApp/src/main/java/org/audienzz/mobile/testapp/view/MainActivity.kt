package org.audienzz.mobile.testapp.view

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import org.audienzz.mobile.testapp.DemoFeatureFlags
import org.audienzz.mobile.testapp.R
import org.audienzz.mobile.testapp.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        setupTabs()
        setupSmartRefreshV2Toggle()
    }

    /// Demo control: a Smart Refresh v2 switch. Persists the choice and restarts the app so the SDK
    /// picks up the new `smartRefreshV2Override` at launch (applied in App.onCreate()).
    private fun setupSmartRefreshV2Toggle() {
        binding.smartRefreshV2Switch.isChecked = DemoFeatureFlags.isSmartRefreshV2Enabled(this)
        binding.smartRefreshV2Switch.setOnCheckedChangeListener { _, isChecked ->
            DemoFeatureFlags.setSmartRefreshV2Enabled(this, isChecked)
            restartApp()
        }
    }

    private fun restartApp() {
        val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
        Runtime.getRuntime().exit(0)
    }

    private fun setupTabs() {
        val adapter = TabPagerAdapter(this)
        binding.viewPager.adapter = adapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = tabTitle(position)
        }.attach()
        // No page impression here. Every screen is reported by the app — there is no automatic
        // tracking — but the reporter for a tab is the TAB ITSELF: each fragment calls
        // `pageImpression(this)` from its own onResume, which ViewPager2 fires for the incoming
        // tab and for the one the app opens with.
        //
        // Reporting from here as well would be a second reporter for one transition, and a worse
        // one: it names the screen by title, while a banner inside the fragment resolves its host
        // to the Fragment object. The title report would release the very banners the tab had
        // just loaded.
    }

    private fun tabTitle(position: Int) = when (position) {
        0 -> "Remote Config"
        1 -> "Non-Remote"
        2 -> "Legacy (v0.0.13)"
        3 -> "Targeting"
        else -> "Tab ${position + 1}"
    }
}

/**
 * Tab order is the order these are worth looking at: the supported remote-config integration
 * first, then the non-remote original API, then the v0.0.13 copy kept for comparison, then
 * targeting.
 */
class TabPagerAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {
    override fun getItemCount(): Int = 4

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> RemoteConfigStickyFragment()
            1 -> NonRemoteBannersFragment()
            2 -> LegacyAdsPageFragment()
            3 -> TargetingPageFragment()
            else -> RemoteConfigStickyFragment()
        }
    }
}
