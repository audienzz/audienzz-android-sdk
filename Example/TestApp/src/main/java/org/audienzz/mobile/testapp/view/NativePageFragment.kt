package org.audienzz.mobile.testapp.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import org.audienzz.mobile.AudienzzPrebidMobile
import org.audienzz.mobile.testapp.R
import org.audienzz.mobile.testapp.adapter.AdsAdapter
import org.audienzz.mobile.testapp.adapter.AdsAdapter.Companion.HOLDER_TYPE_IN_NATIVE_STYLES_AD
import org.audienzz.mobile.testapp.adapter.AdsAdapter.Companion.HOLDER_TYPE_RENDER_NATIVE_AD
import org.audienzz.mobile.testapp.databinding.NativePageFragmentBinding

class NativePageFragment : Fragment() {

    private lateinit var adapter: AdsAdapter
    private lateinit var binding: NativePageFragmentBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = DataBindingUtil.inflate(
            inflater,
            R.layout.native_page_fragment,
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

        adapter = AdsAdapter()
        binding.list.adapter = adapter

        adapter.submitList(listOf(HOLDER_TYPE_IN_NATIVE_STYLES_AD, HOLDER_TYPE_RENDER_NATIVE_AD))
    }
}
