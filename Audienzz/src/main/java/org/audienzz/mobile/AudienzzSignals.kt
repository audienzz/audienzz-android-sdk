package org.audienzz.mobile

import org.prebid.mobile.Signals

object AudienzzSignals {

    enum class Api(internal val prebidApi: Signals.Api) {
        VPAID_1(Signals.Api.VPAID_1),
        VPAID_2(Signals.Api.VPAID_2),
        MRAID_1(Signals.Api.MRAID_1),
        ORMMA(Signals.Api.ORMMA),
        MRAID_2(Signals.Api.MRAID_2),
        MRAID_3(Signals.Api.MRAID_3),
        OMID_1(Signals.Api.OMID_1), ;

        companion object {

            @JvmStatic
            internal fun fromPrebidApi(prebidApi: Signals.Api) =
                Api.entries.find { it.prebidApi == prebidApi } ?: VPAID_1
        }
    }

    enum class Placement(internal val prebidPlacement: Signals.Placement) {
        InStream(Signals.Placement.InStream),
        InBanner(Signals.Placement.InBanner),
        InArticle(Signals.Placement.InArticle),
        InFeed(Signals.Placement.InFeed),
        Interstitial(Signals.Placement.Interstitial),
        Slider(Signals.Placement.Slider),
        Floating(Signals.Placement.Floating), ;

        companion object {

            @JvmStatic
            internal fun fromPrebidPlacement(prebidPlacement: Signals.Placement) =
                Placement.entries.find { it.prebidPlacement == prebidPlacement } ?: InStream
        }
    }

    enum class PlaybackMethod(internal val prebidPlaybackMethod: Signals.PlaybackMethod) {
        AutoPlaySoundOn(Signals.PlaybackMethod.AutoPlaySoundOn),
        AutoPlaySoundOff(Signals.PlaybackMethod.AutoPlaySoundOff),
        ClickToPlay(Signals.PlaybackMethod.ClickToPlay),
        MouseOver(Signals.PlaybackMethod.MouseOver),
        EnterSoundOn(Signals.PlaybackMethod.EnterSoundOn),
        EnterSoundOff(Signals.PlaybackMethod.EnterSoundOff), ;

        companion object {

            @JvmStatic
            internal fun fromPrebidPlaybackMethod(prebidPlaybackMethod: Signals.PlaybackMethod) =
                PlaybackMethod.entries.find { it.prebidPlaybackMethod == prebidPlaybackMethod }
                    ?: AutoPlaySoundOn
        }
    }

    enum class StartDelay(internal val prebidStartDelay: Signals.StartDelay) {
        PreRoll(Signals.StartDelay.PreRoll),
        GenericMidRoll(Signals.StartDelay.GenericMidRoll),
        GenericPostRoll(Signals.StartDelay.GenericPostRoll), ;

        companion object {

            @JvmStatic
            internal fun fromPrebidStartDelay(prebidStartDelay: Signals.StartDelay) =
                StartDelay.entries.find { it.prebidStartDelay == prebidStartDelay } ?: PreRoll
        }
    }

    enum class Protocols(internal val prebidProtocols: Signals.Protocols) {
        VAST_1_0(Signals.Protocols.VAST_1_0),
        VAST_2_0(Signals.Protocols.VAST_2_0),
        VAST_3_0(Signals.Protocols.VAST_3_0),
        VAST_1_0_WRAPPER(Signals.Protocols.VAST_1_0_Wrapper),
        VAST_2_0_WRAPPER(Signals.Protocols.VAST_2_0_Wrapper),
        VAST_3_0_WRAPPER(Signals.Protocols.VAST_3_0_Wrapper),
        VAST_4_0(Signals.Protocols.VAST_4_0),
        VAST_4_0_WRAPPER(Signals.Protocols.VAST_4_0_Wrapper),
        DAAST_1_0(Signals.Protocols.DAAST_1_0),
        DAAST_1_0_WRAPPER(Signals.Protocols.DAAST_1_0_WRAPPER), ;

        companion object {

            @JvmStatic
            internal fun fromPrebidProtocols(prebidProtocols: Signals.Protocols) =
                Protocols.entries.find { it.prebidProtocols == prebidProtocols } ?: VAST_1_0
        }
    }
}
