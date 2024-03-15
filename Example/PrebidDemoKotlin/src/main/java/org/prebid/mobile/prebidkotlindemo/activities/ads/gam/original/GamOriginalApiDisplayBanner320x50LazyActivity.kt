/*
 *    Copyright 2018-2019 Prebid.org, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.prebid.mobile.prebidkotlindemo.activities.ads.gam.original

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import org.prebid.mobile.prebidkotlindemo.R
import org.prebid.mobile.prebidkotlindemo.databinding.ActivityLazyBinding
import org.prebid.mobile.prebidkotlindemo.view.adapter.BannerOriginalLazyAdapter

class GamOriginalApiDisplayBanner320x50LazyActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding: ActivityLazyBinding =
            DataBindingUtil.setContentView(this, R.layout.activity_lazy)
        val adapter = BannerOriginalLazyAdapter()
        binding.list.setAdapter(adapter)

        adapter.submitList(createMockData())
    }

    private fun createMockData(): List<String> {
        val list: MutableList<String> = ArrayList()
        for (i in 0..199) {
            list.add(i.toString())
        }
        return list
    }
}
