package org.audienzz.mobile.testapp.view

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import org.audienzz.mobile.testapp.R
import org.audienzz.mobile.testapp.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        setupTabs()
    }

    private fun setupTabs() {
        val adapter = TabPagerAdapter(this)
        binding.viewPager.adapter = adapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Ads page"
                1 -> "Targeting page"
                2 -> "Remote Config"
                3 -> "Sticky Ad"
                else -> "Tab ${position + 1}"
            }
        }.attach()
    }
}

class TabPagerAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {
    override fun getItemCount(): Int = 4

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> AdsPageFragment()
            1 -> TargetingPageFragment()
            2 -> RemoteConfigFragment()
            3 -> StickyAdFragment()
//            4 -> NativePageFragment()
            else -> AdsPageFragment()
        }
    }
}
