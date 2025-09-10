package org.audienzz.mobile.testapp.adapter.original

import android.net.Uri
import android.view.ViewGroup
import androidx.core.net.toUri
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
import org.audienzz.mobile.testapp.R
import org.audienzz.mobile.testapp.adapter.BaseAdHolder
import org.audienzz.mobile.testapp.constants.SizeConstants
import org.audienzz.mobile.testapp.constants.VideoConstants

class OriginalApiInStreamAdHolder(parent: ViewGroup) : BaseAdHolder(parent) {

    override val titleRes = R.string.original_api_instream_video_title

    private var adUnit: AudienzzInStreamVideoAdUnit? = null
    private var player: SimpleExoPlayer? = null
    private var adsUri: Uri? = null
    private var adsLoader: ImaAdsLoader? = null
    private var playerView: PlayerView? = null

    override fun createAds() {
        adUnit = AudienzzInStreamVideoAdUnit(
            CONFIG_ID,
            SizeConstants.INSTREAM_AD_WIDTH,
            SizeConstants.INSTREAM_AD_HEIGHT,
        )
        adUnit?.videoParameters = configureVideoParameters()

        playerView = PlayerView(adContainer.context)
        val params = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, CONTAINER_HEIGHT)
        adContainer.addView(playerView, params)

        adUnit?.fetchDemand { bidInfo ->
            showFetchErrorDialog(adContainer.context, bidInfo.resultCode)
            val sizes = HashSet<AudienzzAdSize>()
            sizes.add(
                AudienzzAdSize(
                    SizeConstants.INSTREAM_AD_WIDTH,
                    SizeConstants.INSTREAM_AD_HEIGHT,
                ),
            )
            val prebidURL = AudienzzUtil.generateInstreamUriForGam(
                AD_UNIT_ID,
                sizes,
                bidInfo.targetingKeywords,
            )
            adsUri = prebidURL.toUri()
            initializePlayer()
        }
    }

    private fun configureVideoParameters(): AudienzzVideoParameters {
        return AudienzzVideoParameters(listOf("video/x-flv", "video/mp4")).apply {
            placement = AudienzzSignals.Placement.InStream
            api = listOf(AudienzzSignals.Api.VPAID_1, AudienzzSignals.Api.VPAID_2)
            maxBitrate = VideoConstants.MAX_BITRATE
            minBitrate = VideoConstants.MIN_BITRATE
            maxDuration = VideoConstants.MAX_DURATION
            minDuration = VideoConstants.MIN_DURATION
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

        val uri = VIDEO_URL.toUri()

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
        private const val CONTAINER_HEIGHT = 600
        private const val VIDEO_URL =
            "https://storage.googleapis.com/gvabox/media/samples/stock.mp4"
    }
}
