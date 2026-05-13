// This fragment is an exact copy of AdsPageFragment.kt from SDK tag v0.0.13.
// Used to verify backward compatibility — does not set initialPrefetchItemCount
// and does not include HOLDER_TYPE_LAZY_PREFETCH_BANNER in the ad list.
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
import org.audienzz.mobile.AudienzzPrebidMobile.setSchainObject
import org.audienzz.mobile.AudienzzTargetingParams
import org.audienzz.mobile.api.data.AudienzzInitializationStatus
import org.audienzz.mobile.testapp.App
import org.audienzz.mobile.testapp.BuildConfig
import org.audienzz.mobile.testapp.adapter.LegacyAdsAdapter
import org.audienzz.mobile.testapp.adapter.LegacyAdsAdapter.Companion.HOLDER_TYPE_DEFAULT
import org.audienzz.mobile.testapp.adapter.LegacyAdsAdapter.Companion.HOLDER_TYPE_HTML_BANNER_ADS
import org.audienzz.mobile.testapp.adapter.LegacyAdsAdapter.Companion.HOLDER_TYPE_INTERSTITIAL_ADS
import org.audienzz.mobile.testapp.adapter.LegacyAdsAdapter.Companion.HOLDER_TYPE_REWARDED_AD
import org.audienzz.mobile.testapp.adapter.LegacyAdsAdapter.Companion.HOLDER_TYPE_UNFILLED_AD
import org.audienzz.mobile.testapp.databinding.AdsPageFragmentBinding
import org.audienzz.mobile.util.remote.RemoteConfigManager

class LegacyAdsPageFragment : Fragment() {

    private lateinit var adapter: LegacyAdsAdapter
    private lateinit var binding: AdsPageFragmentBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
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

        adapter = LegacyAdsAdapter()
        binding.list.adapter = adapter

        // NOTE: initialPrefetchItemCount is NOT set here — this is the v0.0.13 behaviour.

        initSdk()

        binding.hideAllButton.setOnClickListener {
            adapter.submitList(emptyList()) {
                binding.list.scrollToPosition(0)
            }
        }

        binding.showAllButton.setOnClickListener {
            adapter.submitList(createMockData()) {
                binding.list.scrollToPosition(0)
            }
        }

        binding.versionTextView.text = BuildConfig.VERSION_NAME
    }

    private fun initSdk() {
        if (AudienzzPrebidMobile.isSdkInitialized) {
            AudienzzTargetingParams.isSubjectToGDPR = true
            binding.progressBar.isVisible = false
            adapter.submitList(createMockData())
            return
        }

        val useRemoteConfiguration = true

        if (useRemoteConfiguration) {
            RemoteConfigManager.initialize(
                publisherId = PUBLISHER_ID,
                remoteUrl = "https://api.adnz.co/api/ws-sdk-config/public/v1"
            )

            AudienzzPrebidMobile.isPbsDebug = true
            AudienzzPrebidMobile.initializeRemoteSdk(
                requireContext().applicationContext,
                PUBLISHER_ID,
                true,
            ) { status ->
                handleInitializationStatus(status)
            }
        } else {
            AudienzzPrebidMobile.initializeSdk(
                requireContext().applicationContext,
                "TestCompany",
                true,
            ) { status ->
                handleInitializationStatus(status)
            }
        }
    }

    private fun handleInitializationStatus(status: AudienzzInitializationStatus) {
        if (status == AudienzzInitializationStatus.SUCCEEDED) {
            AudienzzTargetingParams.isSubjectToGDPR = true
            adapter.submitList(createMockData())
            binding.progressBar.isVisible = false
            setSchainObject(
                """
                    { "source":
                        { "schain": {
                            "ver": "1.0",
                            "complete": 1,
                            "nodes": [
                                {
                                    "asi": "netpoint-media.de",
                                    "sid": "np-7255",
                                    "hp": 1
                                }
                              ]
                            }
                        }
                    }
                """.trimMargin(),
            )
            Log.d(App.TAG, "SDK was initialized successfully")
        } else {
            binding.progressBar.isVisible = false
            Log.e(App.TAG, "Error during SDK initialization: $status")
        }
    }

    // v0.0.13 ad list — no HOLDER_TYPE_LAZY_PREFETCH_BANNER
    private fun createMockData(): List<Int> = buildList {
        add(HOLDER_TYPE_HTML_BANNER_ADS)
        add(HOLDER_TYPE_INTERSTITIAL_ADS)
        add(HOLDER_TYPE_REWARDED_AD)
        add(HOLDER_TYPE_UNFILLED_AD)
        addAll(List(50) { HOLDER_TYPE_DEFAULT })
        add(HOLDER_TYPE_HTML_BANNER_ADS)
        add(HOLDER_TYPE_INTERSTITIAL_ADS)
        add(HOLDER_TYPE_REWARDED_AD)
        add(HOLDER_TYPE_UNFILLED_AD)
    }

    companion object {
        private const val PUBLISHER_ID = "35"
    }
}
