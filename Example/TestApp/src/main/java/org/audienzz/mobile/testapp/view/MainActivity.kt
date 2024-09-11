package org.audienzz.mobile.testapp.view

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import org.audienzz.mobile.AudienzzPrebidMobile
import org.audienzz.mobile.api.data.AudienzzInitializationStatus
import org.audienzz.mobile.testapp.AdPreferences
import org.audienzz.mobile.testapp.App
import org.audienzz.mobile.testapp.BuildConfig
import org.audienzz.mobile.testapp.R
import org.audienzz.mobile.testapp.adapter.BannerOriginalLazyAdapter
import org.audienzz.mobile.testapp.adapter.BannerOriginalLazyAdapter.Companion.HOLDER_TYPE_DEFAULT
import org.audienzz.mobile.testapp.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var adapter: BannerOriginalLazyAdapter

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        binding.progressBar.isVisible = true

        adapter = BannerOriginalLazyAdapter()
        binding.list.adapter = adapter

        initSdk()

        binding.hideAllButton.setOnClickListener {
            AdPreferences.setAllAdTypesEnabled(this, false)
            adapter.submitList(AdPreferences.getAllAdTypes()) {
                binding.list.scrollToPosition(0)
            }
            adapter.notifyDataSetChanged()
        }

        binding.showAllButton.setOnClickListener {
            AdPreferences.setAllAdTypesEnabled(this, true)
            adapter.submitList(createMockData()) {
                binding.list.scrollToPosition(0)
            }
            adapter.notifyDataSetChanged()
        }

        binding.versionTextView.text = BuildConfig.VERSION_NAME
    }

    private fun initSdk() {
        if (AudienzzPrebidMobile.isSdkInitialized) {
            binding.progressBar.isVisible = false
            adapter.submitList(createMockData())
            return
        }
        AudienzzPrebidMobile.initializeSdk(applicationContext, "TestCompany") { status ->
            if (status == AudienzzInitializationStatus.SUCCEEDED) {
                adapter.submitList(createMockData())
                binding.progressBar.isVisible = false
                Log.d(App.TAG, "SDK was initialized successfully")
            } else {
                binding.progressBar.isVisible = false
                Log.e(App.TAG, "Error during SDK initialization: $status")
            }
        }
    }

    private fun createMockData(): List<Int> {
        val enabledAdTypes = AdPreferences.getEnabledAdTypes(this)
        return buildList {
            addAll(enabledAdTypes)
            addAll(List(50) { HOLDER_TYPE_DEFAULT })
            addAll(enabledAdTypes)
        }
    }
}
