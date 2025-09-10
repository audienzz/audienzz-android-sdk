package org.audienzz.mobile.testapp.view

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import org.audienzz.mobile.testapp.R
import org.audienzz.mobile.testapp.adapter.AdsAdapter
import org.audienzz.mobile.testapp.databinding.AdsPageFragmentBinding

class SingleAdActivity : AppCompatActivity() {
    private lateinit var adapter: AdsAdapter
    private lateinit var binding: AdsPageFragmentBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView(this, R.layout.ads_page_fragment)

        adapter = AdsAdapter()
        binding.list.adapter = adapter

        val adType = intent.getIntExtra(AD_TYPE, 0)
        adapter.submitList(
            buildList {
                add(adType)
                addAll(List(50) { AdsAdapter.HOLDER_TYPE_DEFAULT })
                add(adType)
            },
        )
    }

    companion object {
        private const val AD_TYPE = "AD_TYPE"

        fun newIntent(context: Context, adType: Int) =
            Intent(context, SingleAdActivity::class.java).apply {
                putExtra(AD_TYPE, adType)
            }
    }
}
