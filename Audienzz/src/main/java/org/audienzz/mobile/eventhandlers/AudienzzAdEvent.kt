package org.audienzz.mobile.eventhandlers

import org.prebid.mobile.eventhandlers.AdEvent

enum class AudienzzAdEvent(internal val prebidAdEvent: AdEvent) {
    APP_EVENT_RECEIVED(AdEvent.APP_EVENT_RECEIVED),
    LOADED(AdEvent.LOADED),
    CLOSED(AdEvent.CLOSED),
    CLICKED(AdEvent.CLICKED),
    DISPLAYED(AdEvent.DISPLAYED),
    REWARD_EARNED(AdEvent.REWARD_EARNED),
    FAILED(AdEvent.FAILED),
}
