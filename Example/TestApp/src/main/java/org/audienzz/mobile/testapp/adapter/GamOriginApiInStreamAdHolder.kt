package org.audienzz.mobile.testapp.adapter

import android.net.Uri
import android.view.ViewGroup
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.ext.ima.ImaAdsLoader
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.source.ads.AdsMediaSource
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DataSpec
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import org.audienzz.mobile.AudienzzAdSize
import org.audienzz.mobile.AudienzzInStreamVideoAdUnit
import org.audienzz.mobile.AudienzzSignals
import org.audienzz.mobile.AudienzzUtil
import org.audienzz.mobile.AudienzzVideoParameters
import org.audienzz.mobile.api.data.AudienzzBidInfo
import org.audienzz.mobile.testapp.R

class GamOriginApiInStreamAdHolder(parent: ViewGroup) : AdHolder(parent) {

    override val titleRes = R.string.gam_original_instream_video_title

    private var adUnit: AudienzzInStreamVideoAdUnit? = null
    private var player: SimpleExoPlayer? = null
    private var adsUri: Uri? = null
    private var adsLoader: ImaAdsLoader? = null
    private var playerView: PlayerView? = null

    override fun createAds() {
        adUnit = AudienzzInStreamVideoAdUnit(CONFIG_ID, WIDTH, HEIGHT)
        adUnit?.videoParameters = configureVideoParameters()

        playerView = PlayerView(adContainer.context)
        val params = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 600)
        adContainer.addView(playerView, params)

        adUnit?.fetchDemand { bidInfo: AudienzzBidInfo ->
            showFetchErrorDialog(adContainer.context, bidInfo.resultCode)
            val sizes = HashSet<AudienzzAdSize>()
            sizes.add(AudienzzAdSize(WIDTH, HEIGHT))
            val prebidURL = AudienzzUtil.generateInstreamUriForGam(
                AD_UNIT_ID,
                sizes,
                bidInfo.targetingKeywords,
            )
            adsUri = Uri.parse(prebidURL)
            initializePlayer()
        }
    }

    private fun configureVideoParameters(): AudienzzVideoParameters {
        return AudienzzVideoParameters(listOf("video/x-flv", "video/mp4")).apply {
            placement = AudienzzSignals.Placement.InStream
            api = listOf(AudienzzSignals.Api.VPAID_1, AudienzzSignals.Api.VPAID_2)
            maxBitrate = 1500
            minBitrate = 300
            maxDuration = 30
            minDuration = 5
            playbackMethod = listOf(AudienzzSignals.PlaybackMethod.AutoPlaySoundOn)
            protocols = listOf(AudienzzSignals.Protocols.VAST_2_0)
        }
    }

    private fun initializePlayer() {
        adsLoader = ImaAdsLoader.Builder(adContainer.context).build()

        val playerBuilder = SimpleExoPlayer.Builder(adContainer.context)
        player = playerBuilder.build()
        playerView?.player = player
        adsLoader?.setPlayer(player)

        val uri = Uri.parse(VIDEO_URL)

        val mediaItem = MediaItem.fromUri(uri)
        val dataSourceFactory: DataSource.Factory = DefaultDataSourceFactory(
            adContainer.context,
            adContainer.resources.getString(R.string.app_name),
        )
        val mediaSourceFactory = ProgressiveMediaSource.Factory(dataSourceFactory)
        val mediaSource: MediaSource = mediaSourceFactory.createMediaSource(mediaItem)
        val dataSpec = DataSpec(adsUri!!)
        val adsMediaSource = AdsMediaSource(
            mediaSource,
            dataSpec,
            "ad",
            mediaSourceFactory,
            adsLoader!!,
            playerView!!,
        )
        player?.setMediaSource(adsMediaSource)
        player?.playWhenReady = true
        player?.prepare()
    }

    override fun onAttach() {
        adUnit?.resumeAutoRefresh()
    }

    override fun onDetach() {
        adUnit?.stopAutoRefresh()
        adsLoader?.setPlayer(null)
        adsLoader?.release()
        player?.release()
    }

    companion object {

        private const val AD_UNIT_ID = "/21808260008/prebid_demo_app_instream"
        private const val CONFIG_ID = "prebid-demo-video-interstitial-320-480-original-api"

        private const val WIDTH = 640
        private const val HEIGHT = 480

        private const val VIDEO_URL =
            "https://storage.googleapis.com/gvabox/media/samples/stock.mp4"
    }
}
