package org.audienzz.mobile.testapp.view

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import org.audienzz.mobile.AudienzzPrebidMobile
import org.audienzz.mobile.api.data.AudienzzInitializationStatus
import org.audienzz.mobile.testapp.AdPreferences
import org.audienzz.mobile.testapp.App
import org.audienzz.mobile.testapp.BuildConfig
import org.audienzz.mobile.testapp.adapter.BannerOriginalLazyAdapter
import org.audienzz.mobile.testapp.adapter.BannerOriginalLazyAdapter.Companion.HOLDER_TYPE_DEFAULT
import org.audienzz.mobile.testapp.databinding.AdsPageFragmentBinding

class AdsPageFragment : Fragment() {

    private lateinit var adapter: BannerOriginalLazyAdapter
    private lateinit var binding: AdsPageFragmentBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        binding = DataBindingUtil.inflate(
            inflater,
            org.audienzz.mobile.testapp.R.layout.ads_page_fragment,
            container,
            false,
        )
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.progressBar.isVisible = true

        adapter = BannerOriginalLazyAdapter()
        binding.list.adapter = adapter

        initSdk()

        binding.hideAllButton.setOnClickListener {
            AdPreferences.setAllAdTypesEnabled(requireContext(), false)
            adapter.submitList(AdPreferences.getAllAdTypes()) {
                binding.list.scrollToPosition(0)
            }
            adapter.notifyDataSetChanged()
        }

        binding.showAllButton.setOnClickListener {
            AdPreferences.setAllAdTypesEnabled(requireContext(), true)
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
        AudienzzPrebidMobile.initializeSdk(
            requireContext().applicationContext,
            "TestCompany",
        ) { status ->
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
        val enabledAdTypes = AdPreferences.getEnabledAdTypes(requireContext())
        return buildList {
            addAll(enabledAdTypes)
            addAll(List(50) { HOLDER_TYPE_DEFAULT })
            addAll(enabledAdTypes)
        }
    }
}
