/*
 * This file was mentioned as modified in the NOTICE file by Apache 2.0 License.
 *
 * Changelog:
 * - Removed all keyword const for R.id values
 * - Removed all variables related to ads of types in-app, max and adMob
 * - Removed all function addSdkTestingExamples related to ads of types in-app, max and adMob
 * - Removed all function formPbsDemoList related to ads of types in-app, max and adMob
 */

/*
 *    Copyright 2018-2021 Prebid.org, Inc.
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

package org.prebid.mobile.renderingtestapp.utils

import android.content.Context
import android.os.Bundle
import org.prebid.mobile.renderingtestapp.AdFragment
import org.prebid.mobile.renderingtestapp.R
import org.prebid.mobile.renderingtestapp.data.DemoItem
import org.prebid.mobile.renderingtestapp.data.Tag
import org.prebid.mobile.renderingtestapp.plugplay.bidding.gam.rendering.GamNativeFragment

class DemoItemProvider private constructor() {

    companion object {
        private var context: Context? = null
        private val demoList = mutableListOf<DemoItem>()

        private const val MIN_WIDTH_PERC = 30
        private const val MIN_HEIGHT_PERC = 30

        private val gamBannerAction = R.id.action_header_bidding_to_gam_banner
        private val gamBannerOriginalAction = R.id.action_header_bidding_to_gam_original_banner
        private val gamBannerOriginalMultiformatAction =
            R.id.action_header_bidding_to_gam_original_banner_multiformat
        private val gamInterstitialAction = R.id.action_header_bidding_to_gam_interstitial
        private val gamInterstitialMultiformatAction =
            R.id.action_header_bidding_to_gam_interstitial_multiformat
        private val gamRewardedAction = R.id.action_header_bidding_to_gam_video_rewarded
        private val gamMultiformatOriginalAction =
            R.id.action_header_bidding_to_gam_original_multiformat
        private val gamMultiformatOriginalNativeStylesAction =
            R.id.action_header_bidding_to_gam_original_multiformat_native_styles
        private val gamMultiformatInterstitialOriginalAction =
            R.id.action_header_bidding_to_gam_original_multiformat_interstitial
        private val gamMultiformatRewardedOriginalAction =
            R.id.action_header_bidding_to_gam_original_multiformat_rewarded

        fun init(context: Context) {
            if (demoList.isNotEmpty()) {
                return
            }
            Companion.context = context

            formPbsDemoList()

            Companion.context = null
        }

        fun getDemoList() = demoList

        private fun getString(resId: Int): String {
            return context!!.getString(resId)
        }

        private fun addSdkTestingExamples() {
            demoList.addAll(
                arrayListOf(
                    DemoItem(
                        getString(R.string.demo_bidding_sdk_testing_memory_leak_original_api),
                        R.id.action_header_bidding_to_gam_original_banner_memory_leak_testing,
                        listOf(Tag.ALL, Tag.ORIGINAL, Tag.BANNER, Tag.REMOTE),
                        createBannerBundle(
                            R.string.imp_prebid_id_banner_320x50,
                            R.string.adunit_gam_banner_320_50_original,
                            320,
                            50
                        )
                    )
                )
            )
        }

        private fun formPbsDemoList() {
            addGamOriginalExamples()
            addGamPbsExamples()
            addSdkTestingExamples()
        }

        private fun addGamOriginalExamples() {
            val gamBannerTagList = listOf(Tag.ALL, Tag.ORIGINAL, Tag.BANNER, Tag.REMOTE)
            val gamInterstitialTagList = listOf(Tag.ALL, Tag.ORIGINAL, Tag.INTERSTITIAL, Tag.REMOTE)
            val gamVideoTagList = listOf(Tag.ALL, Tag.ORIGINAL, Tag.VIDEO, Tag.REMOTE)
            val gamNativeTagList = listOf(Tag.ALL, Tag.ORIGINAL, Tag.NATIVE, Tag.REMOTE)

            demoList.add(
                DemoItem(
                    getString(R.string.demo_bidding_gam_banner_320_50_original),
                    gamBannerOriginalAction,
                    gamBannerTagList,
                    createBannerBundle(
                        R.string.imp_prebid_id_banner_320x50,
                        R.string.adunit_gam_banner_320_50_original,
                        320,
                        50
                    )
                )
            )

            demoList.add(
                DemoItem(
                    getString(R.string.demo_bidding_gam_banner_300_250_original),
                    gamBannerOriginalAction,
                    gamBannerTagList,
                    createBannerBundle(
                        R.string.imp_prebid_id_banner_300x250,
                        R.string.adunit_gam_banner_300_250_original,
                        300,
                        250
                    )
                )
            )
            demoList.add(
                DemoItem(
                    getString(R.string.demo_bidding_gam_banner_728_90_original),
                    gamBannerOriginalAction,
                    gamBannerTagList,
                    createBannerBundle(
                        R.string.imp_prebid_id_banner_728x90,
                        R.string.adunit_gam_banner_728_90_original,
                        728,
                        90
                    )
                )
            )
            demoList.add(
                DemoItem(
                    getString(R.string.demo_bidding_gam_banner_multisize_original),
                    gamBannerOriginalAction,
                    gamBannerTagList,
                    createBannerBundle(
                        R.string.imp_prebid_id_banner_multisize,
                        R.string.adunit_gam_banner_multisize_original,
                        320,
                        50
                    )
                )
            )
            demoList.add(
                DemoItem(
                    getString(R.string.demo_bidding_gam_banner_300_250_multiformat),
                    gamBannerOriginalMultiformatAction,
                    gamBannerTagList,
                    createBannerBundle(
                        R.string.imp_prebid_dynamic,
                        R.string.adunit_gam_banner_multiformat_original,
                        300,
                        250
                    )
                )
            )

            demoList.add(
                DemoItem(
                    getString(R.string.demo_bidding_gam_interstitial_320_480_original),
                    R.id.action_header_bidding_to_gam_original_interstitial,
                    gamInterstitialTagList,
                    createBannerBundle(
                        R.string.imp_prebid_id_interstitial_320_480,
                        R.string.adunit_gam_interstitial_320_480_original,
                        320,
                        480
                    )
                )
            )
            demoList.add(
                DemoItem(
                    getString(R.string.demo_bidding_gam_interstitial_320_480_original_multiformat),
                    R.id.action_header_bidding_to_gam_original_interstitial_multiformat,
                    gamInterstitialTagList,
                    createBannerBundle(
                        R.string.imp_prebid_dynamic,
                        R.string.adunit_gam_interstitial_320_480_original_multiformat,
                        320,
                        480
                    )
                )
            )

            demoList.add(
                DemoItem(
                    getString(R.string.demo_bidding_gam_video_oustream_original),
                    R.id.action_header_bidding_to_gam_video_outstream_original,
                    gamVideoTagList,
                    createBannerBundle(
                        R.string.imp_prebid_id_video_outstream_original_api,
                        R.string.adunit_gam_video_300_250_original,
                        300,
                        250
                    )
                )
            )
            demoList.add(
                DemoItem(
                    getString(R.string.demo_bidding_gam_video_oustream_original_new_api),
                    R.id.action_header_bidding_to_gam_video_outstream_original_new_api,
                    gamVideoTagList,
                    createBannerBundle(
                        R.string.imp_prebid_id_video_outstream_original_api,
                        R.string.adunit_gam_video_300_250_original,
                        300,
                        250
                    )
                )
            )
            demoList.add(
                DemoItem(
                    getString(R.string.demo_bidding_gam_video_instream_original),
                    R.id.action_header_bidding_to_gam_original_instream,
                    gamVideoTagList,
                    createBannerBundle(
                        R.string.imp_prebid_id_video_instream,
                        R.string.adunit_gam_video_instream,
                        640,
                        480
                    )
                )
            )
            demoList.add(
                DemoItem(
                    getString(R.string.demo_bidding_gam_video_instream_original_new_api),
                    R.id.action_header_bidding_to_gam_original_instream_new_api,
                    gamVideoTagList,
                    createBannerBundle(
                        R.string.imp_prebid_id_video_instream,
                        R.string.adunit_gam_video_instream,
                        640,
                        480
                    )
                )
            )
            demoList.add(
                DemoItem(
                    getString(R.string.demo_bidding_gam_interstitial_video_320_480_original),
                    R.id.action_header_bidding_to_gam_original_interstitial,
                    gamVideoTagList,
                    createBannerBundle(
                        R.string.imp_prebid_id_video_interstitial_320_480_original_api,
                        R.string.adunit_gam_interstitial_video_320_480_original,
                        320,
                        480
                    )
                )
            )
            demoList.add(
                DemoItem(
                    getString(R.string.demo_bidding_gam_video_rewarded_end_card_320_480_original),
                    R.id.action_header_bidding_to_gam_original_rewarded_video,
                    gamVideoTagList,
                    createBannerBundle(
                        R.string.imp_prebid_id_video_rewarded_end_card_320_480_original_api,
                        R.string.adunit_gam_interstitial_video_320_480_original,
                        MIN_WIDTH_PERC,
                        MIN_HEIGHT_PERC
                    )
                )
            )
            demoList.add(
                DemoItem(
                    getString(R.string.demo_bidding_gam_native_in_app_original),
                    R.id.action_header_bidding_to_gam_original_native_in_app,
                    gamNativeTagList,
                    createBannerBundle(
                        R.string.imp_prebid_id_native_styles,
                        adUnitIdRes = R.string.adunit_gam_native_custom_template
                    )
                )
            )
            demoList.add(
                DemoItem(
                    getString(R.string.demo_bidding_gam_native_banner_original),
                    R.id.action_header_bidding_to_gam_original_native_banner,
                    gamNativeTagList,
                    createBannerBundle(
                        R.string.imp_prebid_id_native_styles,
                        adUnitIdRes = R.string.adunit_gam_native_styles
                    )
                )
            )

            demoList.add(
                DemoItem(
                    getString(R.string.demo_bidding_gam_multiformat),
                    gamMultiformatOriginalAction,
                    gamBannerTagList,
                    createBannerBundle(
                        R.string.imp_prebid_dynamic,
                        R.string.adunit_dynamic,
                    )
                )
            )
            demoList.add(
                DemoItem(
                    getString(R.string.demo_bidding_gam_multiformat_native_styles),
                    gamMultiformatOriginalNativeStylesAction,
                    gamBannerTagList,
                    createBannerBundle(
                        R.string.imp_prebid_dynamic,
                        R.string.adunit_dynamic,
                    )
                )
            )
            demoList.add(
                DemoItem(
                    getString(R.string.demo_bidding_gam_multiformat_interstitial),
                    gamMultiformatInterstitialOriginalAction,
                    gamInterstitialTagList,
                    createBannerBundle(
                        R.string.imp_prebid_dynamic,
                        R.string.adunit_gam_interstitial_320_480_original_multiformat,
                        320,
                        480
                    )
                )
            )
            demoList.add(
                DemoItem(
                    getString(R.string.demo_bidding_gam_multiformat_rewarded),
                    gamMultiformatRewardedOriginalAction,
                    gamInterstitialTagList,
                    createBannerBundle(
                        R.string.imp_prebid_id_video_rewarded_end_card_320_480_original_api,
                        R.string.adunit_gam_interstitial_video_320_480_original,
                        320,
                        480
                    )
                )
            )
        }

        private fun addGamPbsExamples() {
            val gamBannerTagList = listOf(Tag.ALL, Tag.GAM, Tag.BANNER, Tag.REMOTE)
            val gamInterstitialTagList = listOf(Tag.ALL, Tag.GAM, Tag.INTERSTITIAL, Tag.REMOTE)
            val gamMraidTagList = listOf(Tag.ALL, Tag.GAM, Tag.MRAID, Tag.REMOTE)
            val gamVideoTagList = listOf(Tag.ALL, Tag.GAM, Tag.VIDEO, Tag.REMOTE)
            val gamNativeTagList = listOf(Tag.ALL, Tag.GAM, Tag.NATIVE, Tag.REMOTE)

            /// GAM Banner
            demoList.add(
                DemoItem(
                    getString(R.string.demo_bidding_gam_banner_320_50_app_event),
                    gamBannerAction,
                    gamBannerTagList,
                    createBannerBundle(
                        R.string.imp_prebid_id_banner_320x50,
                        R.string.adunit_gam_banner_320_50_app_event,
                        320,
                        50
                    )
                )
            )
            demoList.add(
                DemoItem(
                    getString(R.string.demo_bidding_gam_banner_320_50_no_bids),
                    gamBannerAction,
                    gamBannerTagList,
                    createBannerBundle(
                        R.string.imp_prebid_id_no_bids,
                        null,
                        320,
                        50
                    )
                )
            )
            demoList.add(
                DemoItem(
                    getString(R.string.demo_bidding_gam_banner_320_50_gam_ad),
                    gamBannerAction,
                    gamBannerTagList,
                    createBannerBundle(
                        R.string.imp_prebid_id_banner_320x50,
                        R.string.adunit_gam_banner_320_50_gam_ad,
                        320,
                        50
                    )
                )
            )
            demoList.add(
                DemoItem(
                    getString(R.string.demo_bidding_gam_banner_320_50_random),
                    gamBannerAction,
                    gamBannerTagList,
                    createBannerBundle(
                        R.string.imp_prebid_id_banner_320x50,
                        R.string.adunit_gam_banner_320_50_random,
                        320,
                        50
                    )
                )
            )
            demoList.add(
                DemoItem(
                    getString(R.string.demo_bidding_gam_banner_320_50_app_event_with_events_url),
                    gamBannerAction,
                    gamBannerTagList,
                    createBannerBundle(
                        R.string.imp_prebid_id_banner_320x50,
                        R.string.adunit_gam_banner_320_50_app_event,
                        320,
                        50
                    ).apply {
                        putString(
                            AdFragment.ARGUMENT_ACCOUNT_ID,
                            getString(R.string.prebid_account_id_prod_enabled_events)
                        )
                    }
                )
            )
            demoList.add(
                DemoItem(
                    getString(R.string.demo_bidding_gam_banner_300_250),
                    gamBannerAction,
                    gamBannerTagList,
                    createBannerBundle(
                        R.string.imp_prebid_id_banner_300x250,
                        R.string.adunit_gam_banner_300_250,
                        300,
                        250
                    )
                )
            )
            demoList.add(
                DemoItem(
                    getString(R.string.demo_bidding_gam_banner_728_90),
                    gamBannerAction,
                    gamBannerTagList,
                    createBannerBundle(
                        R.string.imp_prebid_id_banner_728x90,
                        R.string.adunit_gam_banner_728_90,
                        728,
                        90
                    )
                )
            )
            demoList.add(
                DemoItem(
                    getString(R.string.demo_bidding_gam_banner_multisize),
                    R.id.action_header_bidding_to_gam_multisize_banner,
                    gamBannerTagList,
                    createBannerBundle(
                        R.string.imp_prebid_id_banner_multisize,
                        R.string.adunit_gam_banner_multisize,
                        320,
                        50
                    )
                )
            )
            demoList.add(
                DemoItem(
                    getString(R.string.demo_bidding_gam_banners_and_interstitial),
                    R.id.action_header_bidding_to_gam_banners_and_interstitial,
                    gamBannerTagList,
                    createBannerBundle(null, null, 0, 0)
                )
            )
            demoList.add(
                DemoItem(
                    getString(R.string.demo_bidding_gam_mraid_expand),
                    gamBannerAction,
                    gamMraidTagList,
                    createBannerBundle(
                        R.string.imp_prebid_id_mraid_expand,
                        R.string.adunit_gam_banner_320_50_app_event,
                        320,
                        50
                    )
                )
            )
            demoList.add(
                DemoItem(
                    getString(R.string.demo_bidding_gam_mraid_resize),
                    gamBannerAction,
                    gamMraidTagList,
                    createBannerBundle(
                        R.string.imp_prebid_id_mraid_resize,
                        R.string.adunit_gam_banner_320_50_app_event,
                        320,
                        50
                    )
                )
            )
            demoList.add(
                DemoItem(
                    getString(R.string.demo_bidding_gam_mraid_fullscreen_video),
                    gamInterstitialAction,
                    gamMraidTagList,
                    createBannerBundle(
                        R.string.imp_prebid_id_mraid_video_interstitial,
                        R.string.adunit_gam_interstitial_320_480_app_event,
                        MIN_WIDTH_PERC,
                        MIN_HEIGHT_PERC
                    )
                )
            )

            // GAM Interstitial
            demoList.add(
                DemoItem(
                    getString(R.string.demo_bidding_gam_interstitial_320_480_app_event),
                    gamInterstitialAction,
                    gamInterstitialTagList,
                    createBannerBundle(
                        R.string.imp_prebid_id_interstitial_320_480,
                        R.string.adunit_gam_interstitial_320_480_app_event,
                        MIN_WIDTH_PERC,
                        MIN_HEIGHT_PERC
                    )
                )
            )
            demoList.add(
                DemoItem(
                    getString(R.string.demo_bidding_gam_interstitial_320_480_random),
                    gamInterstitialAction,
                    gamInterstitialTagList,
                    createBannerBundle(
                        R.string.imp_prebid_id_interstitial_320_480,
                        R.string.adunit_gam_interstitial_320_480_random,
                        MIN_WIDTH_PERC,
                        MIN_HEIGHT_PERC
                    )
                )
            )

            demoList.add(
                DemoItem(
                    getString(R.string.demo_bidding_gam_interstitial_320_480_no_bids),
                    gamInterstitialAction,
                    gamInterstitialTagList,
                    createBannerBundle(
                        R.string.imp_prebid_id_no_bids,
                        R.string.adunit_gam_interstitial_320_480_no_bids,
                        MIN_WIDTH_PERC,
                        MIN_HEIGHT_PERC
                    )
                )
            )

            ///GAM Video
            demoList.add(
                DemoItem(
                    getString(R.string.demo_bidding_gam_interstitial_video_320_480_app_event),
                    gamInterstitialAction,
                    gamVideoTagList,
                    createBannerBundle(
                        R.string.imp_prebid_id_video_interstitial_320_480,
                        R.string.adunit_gam_interstitial_video_320_480_app_event,
                        MIN_WIDTH_PERC,
                        MIN_HEIGHT_PERC
                    )
                )
            )
            demoList.add(
                DemoItem(
                    getString(R.string.demo_bidding_gam_interstitial_video_320_480_random),
                    gamInterstitialAction,
                    gamVideoTagList,
                    createBannerBundle(
                        R.string.imp_prebid_id_video_interstitial_320_480,
                        R.string.adunit_gam_interstitial_video_320_480_random,
                        MIN_WIDTH_PERC,
                        MIN_HEIGHT_PERC
                    )
                )
            )
            demoList.add(
                DemoItem(
                    getString(R.string.demo_bidding_gam_interstitial_video_320_480_no_bids),
                    gamInterstitialAction,
                    gamVideoTagList,
                    createBannerBundle(
                        R.string.imp_prebid_id_no_bids,
                        R.string.adunit_gam_interstitial_video_320_480_no_bids,
                        MIN_WIDTH_PERC,
                        MIN_HEIGHT_PERC
                    )
                )
            )
            demoList.add(
                DemoItem(
                    getString(R.string.demo_bidding_gam_interstitial_video_320_480_app_event_configuration),
                    gamInterstitialAction,
                    gamVideoTagList,
                    createBannerBundle(
                        R.string.imp_prebid_id_video_interstitial_320_480,
                        R.string.adunit_gam_interstitial_video_320_480_app_event,
                        MIN_WIDTH_PERC,
                        MIN_HEIGHT_PERC
                    )
                )
            )
            demoList.add(
                DemoItem(
                    getString(R.string.demo_bidding_gam_interstitial_video_320_480_app_event_endcard_configuration),
                    gamInterstitialAction,
                    gamVideoTagList,
                    createBannerBundle(
                        R.string.imp_prebid_id_video_interstitial_320_480_with_end_card_with_ad_configuration,
                        R.string.adunit_gam_interstitial_video_320_480_app_event,
                        MIN_WIDTH_PERC,
                        MIN_HEIGHT_PERC
                    )
                )
            )
            demoList.add(
                DemoItem(
                    getString(R.string.demo_bidding_gam_interstitial_320_480_multiformat),
                    gamInterstitialMultiformatAction,
                    gamVideoTagList,
                    createBannerBundle(
                        R.string.imp_prebid_dynamic,
                        R.string.adunit_gam_interstitial_video_320_480_app_event,
                        MIN_WIDTH_PERC,
                        MIN_HEIGHT_PERC
                    )
                )
            )
            demoList.add(
                DemoItem(
                    getString(R.string.demo_bidding_gam_video_rewarded_320_480_metadata),
                    gamRewardedAction,
                    gamVideoTagList,
                    createBannerBundle(
                        R.string.imp_prebid_id_video_rewarded_320_480,
                        R.string.adunit_gam_video_rewarded_320_480_metadata,
                        MIN_WIDTH_PERC,
                        MIN_HEIGHT_PERC
                    )
                )
            )
            demoList.add(
                DemoItem(
                    getString(R.string.demo_bidding_gam_video_rewarded_end_card_320_480_metadata),
                    gamRewardedAction,
                    gamVideoTagList,
                    createBannerBundle(
                        R.string.imp_prebid_id_video_rewarded_end_card_320_480,
                        R.string.adunit_gam_video_rewarded_320_480_metadata,
                        MIN_WIDTH_PERC,
                        MIN_HEIGHT_PERC
                    )
                )
            )
            demoList.add(
                DemoItem(
                    getString(R.string.demo_bidding_gam_video_rewarded_end_card_320_480_no_bids),
                    gamRewardedAction,
                    gamVideoTagList,
                    createBannerBundle(
                        R.string.imp_prebid_id_no_bids,
                        R.string.adunit_gam_video_rewarded_320_480,
                        MIN_WIDTH_PERC,
                        MIN_HEIGHT_PERC
                    )
                )
            )
            demoList.add(
                DemoItem(
                    getString(R.string.demo_bidding_gam_video_rewarded_end_card_320_480_random),
                    gamRewardedAction,
                    gamVideoTagList,
                    createBannerBundle(
                        R.string.imp_prebid_id_video_rewarded_end_card_320_480,
                        R.string.adunit_gam_video_rewarded_320_480_random,
                        MIN_WIDTH_PERC,
                        MIN_HEIGHT_PERC
                    )
                )
            )
            demoList.add(
                DemoItem(
                    getString(R.string.demo_bidding_gam_video_rewarded_end_card_320_480_metadata_configuration),
                    gamRewardedAction,
                    gamVideoTagList,
                    createBannerBundle(
                        R.string.imp_prebid_id_video_rewarded_end_card_320_480_with_ad_configuration,
                        R.string.adunit_gam_video_rewarded_320_480_metadata,
                        MIN_WIDTH_PERC,
                        MIN_HEIGHT_PERC
                    )
                )
            )
            demoList.add(
                DemoItem(
                    getString(R.string.demo_bidding_gam_video_oustream_app_event),
                    R.id.action_header_bidding_to_gam_banner_video,
                    gamVideoTagList,
                    createBannerBundle(
                        R.string.imp_prebid_id_video_outstream,
                        R.string.adunit_gam_banner_300_250,
                        300,
                        250
                    )
                )
            )
            demoList.add(
                DemoItem(
                    getString(R.string.demo_bidding_gam_video_outstream_no_bids),
                    R.id.action_header_bidding_to_gam_banner_video,
                    gamVideoTagList,
                    createBannerBundle(
                        R.string.imp_prebid_id_no_bids,
                        R.string.adunit_gam_video_300_250,
                        300,
                        250
                    )
                )
            )
            demoList.add(
                DemoItem(
                    getString(R.string.demo_bidding_gam_video_outstream_random),
                    R.id.action_header_bidding_to_gam_banner_video,
                    gamVideoTagList,
                    createBannerBundle(
                        R.string.imp_prebid_id_video_outstream,
                        R.string.adunit_gam_video_300_250_random,
                        300,
                        250
                    )
                )
            )
            demoList.add(
                DemoItem(
                    getString(R.string.demo_bidding_gam_video_outstream_feed),
                    R.id.action_header_bidding_to_gam_banner_video_feed,
                    gamVideoTagList,
                    createBannerBundle(
                        R.string.imp_prebid_id_video_outstream,
                        R.string.adunit_gam_video_300_250_random,
                        300,
                        250
                    )
                )
            )

            // Native
            var gamNativeBundle = createBannerBundle(
                R.string.imp_prebid_id_native_styles,
                R.string.adunit_gam_native_custom_template
            )
            gamNativeBundle.putString(GamNativeFragment.ARG_CUSTOM_FORMAT_ID, "11934135")
            demoList.add(
                DemoItem(
                    getString(R.string.demo_bidding_gam_native_custom_templates),
                    R.id.action_header_bidding_to_gam_native,
                    gamNativeTagList,
                    gamNativeBundle
                )
            )

            gamNativeBundle = createBannerBundle(
                R.string.imp_prebid_id_native_styles,
                R.string.adunit_gam_native_custom_template
            )
            gamNativeBundle.putString(GamNativeFragment.ARG_CUSTOM_FORMAT_ID, "11982639")
            demoList.add(
                DemoItem(
                    getString(R.string.demo_bidding_gam_native_custom_templates_prebid_ok),
                    R.id.action_header_bidding_to_gam_native,
                    gamNativeTagList,
                    gamNativeBundle
                )
            )

            demoList.add(
                DemoItem(
                    getString(R.string.demo_bidding_gam_native_custom_templates_events),
                    R.id.action_header_bidding_to_gam_native,
                    gamNativeTagList,
                    createBannerBundle(
                        R.string.imp_prebid_id_native_styles,
                        R.string.adunit_gam_native_custom_template
                    ).apply {
                        putString(GamNativeFragment.ARG_CUSTOM_FORMAT_ID, "11934135")
                        putString(
                            AdFragment.ARGUMENT_ACCOUNT_ID,
                            getString(R.string.prebid_account_id_prod_enabled_events)
                        )
                    }
                )
            )

            gamNativeBundle = createBannerBundle(
                R.string.imp_prebid_id_no_bids,
                R.string.adunit_gam_native_custom_template
            )
            gamNativeBundle.putString(GamNativeFragment.ARG_CUSTOM_FORMAT_ID, "11982639")
            demoList.add(
                DemoItem(
                    getString(R.string.demo_bidding_gam_native_custom_templates_no_bids),
                    R.id.action_header_bidding_to_gam_native,
                    gamNativeTagList,
                    gamNativeBundle
                )
            )

            demoList.add(
                DemoItem(
                    getString(R.string.demo_bidding_gam_native_unified_ads),
                    R.id.action_header_bidding_to_gam_native,
                    gamNativeTagList,
                    createBannerBundle(
                        R.string.imp_prebid_id_native_styles,
                        R.string.adunit_gam_native_unified
                    )
                )
            )
            demoList.add(
                DemoItem(
                    getString(R.string.demo_bidding_gam_native_unified_ads_prebid_ok),
                    R.id.action_header_bidding_to_gam_native,
                    gamNativeTagList,
                    createBannerBundle(
                        R.string.imp_prebid_id_native_styles,
                        R.string.adunit_gam_native_unified_static
                    )
                )
            )
            demoList.add(
                DemoItem(
                    getString(R.string.demo_bidding_gam_native_unified_ads_no_bids),
                    R.id.action_header_bidding_to_gam_native,
                    gamNativeTagList,
                    createBannerBundle(
                        R.string.imp_prebid_id_no_bids,
                        R.string.adunit_gam_native_unified_static
                    )
                )
            )

            gamNativeBundle = createBannerBundle(
                R.string.imp_prebid_id_native_styles,
                R.string.adunit_gam_native_custom_template
            )
            gamNativeBundle.putString(GamNativeFragment.ARG_CUSTOM_FORMAT_ID, "11934135")
            demoList.add(
                DemoItem(
                    getString(R.string.demo_bidding_gam_native_feed),
                    R.id.action_header_bidding_to_gam_native_feed,
                    gamNativeTagList, gamNativeBundle
                )
            )
        }

        private fun createBannerBundle(
            configIdRes: Int?,
            adUnitIdRes: Int? = null,
            width: Int = 0,
            height: Int = 0
        ): Bundle {
            return Bundle().apply {
                if (configIdRes != null) {
                    putString(getString(R.string.key_bid_config_id), getString(configIdRes))
                }
                if (adUnitIdRes != null) {
                    putString(getString(R.string.key_ad_unit), getString(adUnitIdRes))
                }
                putInt(getString(R.string.key_width), width)
                putInt(getString(R.string.key_height), height)
            }
        }
    }

}