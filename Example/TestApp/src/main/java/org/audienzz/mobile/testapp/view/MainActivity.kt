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
            tab.text = when (position) {
                0 -> "Ads page"
                1 -> "Targeting page"
                2 -> "Remote Config"
                3 -> "Legacy (v0.0.13)"
                4 -> "Non-Remote"
                else -> "Tab ${position + 1}"
            }
        }.attach()
    }
}

class TabPagerAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {
    override fun getItemCount(): Int = 5

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> AdsPageFragment()
            1 -> TargetingPageFragment()
            2 -> RemoteConfigStickyFragment()
            3 -> LegacyAdsPageFragment()
            4 -> NonRemoteBannersFragment()
            else -> AdsPageFragment()
        }
    }
}
