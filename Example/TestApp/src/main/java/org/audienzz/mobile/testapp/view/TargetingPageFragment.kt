package org.audienzz.mobile.testapp.view

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.admanager.AdManagerAdView
import org.audienzz.mobile.AudienzzBannerAdUnit
import org.audienzz.mobile.AudienzzBannerParameters
import org.audienzz.mobile.AudienzzSignals
import org.audienzz.mobile.AudienzzTargetingParams
import org.audienzz.mobile.addentum.AudienzzAdViewUtils
import org.audienzz.mobile.original.AudienzzAdViewHandler
import org.audienzz.mobile.testapp.R
import org.audienzz.mobile.testapp.databinding.TargetingPageFragmentBinding

class TargetingPageFragment : Fragment() {

    private lateinit var binding: TargetingPageFragmentBinding
    private var adUnit = AudienzzBannerAdUnit(CONFIG_ID, AD_SIZE.width, AD_SIZE.height)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        binding = DataBindingUtil.inflate(
            inflater,
            R.layout.targeting_page_fragment,
            container,
            false,
        )
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.submitButton.setOnClickListener {
            val key = binding.keyInputEditText.text?.toString()?.trim()
            val value = binding.valueInputEditText.text?.toString()?.trim()

            if (key.isNullOrEmpty()) {
                showToast("Please enter a key")
                return@setOnClickListener
            }

            if (value.isNullOrEmpty()) {
                showToast("Please enter a value")
                return@setOnClickListener
            }

            if (value.contains(",")) {
                val valueArray = value.split(",")
                AudienzzTargetingParams.addGlobalTargeting(key = key, value = valueArray.toSet())
            } else {
                AudienzzTargetingParams.addGlobalTargeting(key = key, value = value)
            }

            binding.keyInputEditText.text?.clear()
            binding.valueInputEditText.text?.clear()

            showToast("Added: $key = $value")
        }

        binding.removeButton.setOnClickListener {
            val keyToRemove = binding.removeKeyEditText.text?.toString()?.trim()

            if (keyToRemove.isNullOrEmpty()) {
                showToast("Please enter a key to remove")
                return@setOnClickListener
            }

            AudienzzTargetingParams.removeGlobalTargeting(keyToRemove)
            binding.removeKeyEditText.text?.clear()
            showToast("Removed key: $keyToRemove")
        }

        binding.clearAllButton.setOnClickListener {
            AudienzzTargetingParams.clearGlobalTargeting()
            showToast("All key-value pairs cleared")
        }

        binding.requestAdButton.setOnClickListener {
            requestAd()
        }
    }

    private fun requestAd() {
        binding.adContainer.removeAllViews()
        val adView = AdManagerAdView(requireContext()).apply {
            adUnitId = AD_UNIT_ID
            setAdSizes(AD_SIZE)
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    super.onAdLoaded()
                    Log.d(LOG_TAG_NAME, "onAdLoaded")
                    AudienzzAdViewUtils.hideScrollBar(this@apply)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    super.onAdFailedToLoad(error)
                    Log.d(LOG_TAG_NAME, "onAdFailedToLoad $error")
                }
            }
        }

        binding.adContainer.addView(adView)

        adUnit.bannerParameters = AudienzzBannerParameters().apply {
            api = listOf(AudienzzSignals.Api.MRAID_3, AudienzzSignals.Api.OMID_1)
        }

        AudienzzAdViewHandler(
            adView = adView,
            adUnit = adUnit,
        ).load(
            callback = { request, _ ->
                adView.loadAd(request)
            },
        )
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val CONFIG_ID = "15624474"
        private const val AD_UNIT_ID = "/96628199/testapp_publisher/medium_rectangle_banner"

        private const val LOG_TAG_NAME = "[TargetingPage]"

        private val AD_SIZE = AdSize(300, 250)
    }
}
