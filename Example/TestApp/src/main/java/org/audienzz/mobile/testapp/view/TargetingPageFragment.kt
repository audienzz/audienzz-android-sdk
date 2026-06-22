package org.audienzz.mobile.testapp.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import org.audienzz.mobile.AudienzzPrebidMobile
import org.audienzz.mobile.AudienzzRemoteBannerView
import org.audienzz.mobile.AudienzzTargetingParams
import org.audienzz.mobile.testapp.R
import org.audienzz.mobile.testapp.databinding.TargetingPageFragmentBinding

class TargetingPageFragment : Fragment() {
    private lateinit var binding: TargetingPageFragmentBinding

    private var bannerView: AudienzzRemoteBannerView? = null

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

    override fun onResume() {
        super.onResume()
        if (AudienzzPrebidMobile.isSdkInitialized) {
            activity?.let { AudienzzPrebidMobile.onScreenResumed(it) }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        applyStoreUrlForTargeting()
        setupClickListeners()
    }

    private fun applyStoreUrlForTargeting() {
        AudienzzTargetingParams.storeUrl = STORE_URL
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
        bannerView?.destroy()

        val view = AudienzzRemoteBannerView(requireContext(), BANNER_CONFIG_ID)
        bannerView = view
        binding.adContainer.addView(view)
        view.loadAd()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bannerView?.destroy()
        bannerView = null
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val TAG = "TargetingPage"
        private const val BANNER_CONFIG_ID = "46"
        private val STORE_URL = "https://play.google.com/store/apps/details?id=com.example.testapp"
    }
}
