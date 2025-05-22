package org.audienzz.mobile

import org.prebid.mobile.NativeEventTracker

data class AudienzzNativeEventTracker internal constructor(
    internal val prebidNativeEventTracker: NativeEventTracker,
) {

    var ext: Any?
        get() = prebidNativeEventTracker.extObject
        set(value) {
            prebidNativeEventTracker.setExt(value)
        }

    val event: EventType = EventType.fromPrebidEventType(prebidNativeEventTracker.event)
    val methods: List<EventTrackingMethod> = prebidNativeEventTracker.methods.map {
        EventTrackingMethod.fromPrebidEventTrackingMethod(it)
    }

    constructor(
        event: EventType,
        methods: List<EventTrackingMethod>,
    ) : this(
        NativeEventTracker(
            event.prebidNativeEventTracker,
            arrayListOf<NativeEventTracker.EVENT_TRACKING_METHOD>().apply {
                addAll(methods.map { it.prebidEventTrackingMethod })
            },
        ),
    )

    enum class EventType(internal val prebidNativeEventTracker: NativeEventTracker.EVENT_TYPE) {
        IMPRESSION(NativeEventTracker.EVENT_TYPE.IMPRESSION),
        VIEWABLE_MRC50(NativeEventTracker.EVENT_TYPE.VIEWABLE_MRC50),
        VIEWABLE_MRC100(NativeEventTracker.EVENT_TYPE.VIEWABLE_MRC100),
        VIEWABLE_VIDEO50(NativeEventTracker.EVENT_TYPE.VIEWABLE_VIDEO50),
        CUSTOM(NativeEventTracker.EVENT_TYPE.CUSTOM), ;

        companion object {

            @JvmStatic
            fun fromPrebidEventType(eventType: NativeEventTracker.EVENT_TYPE) =
                EventType.entries.find { it.prebidNativeEventTracker == eventType } ?: IMPRESSION
        }
    }

    enum class EventTrackingMethod(
        internal val prebidEventTrackingMethod: NativeEventTracker.EVENT_TRACKING_METHOD,
    ) {
        IMAGE(NativeEventTracker.EVENT_TRACKING_METHOD.IMAGE),
        JS(NativeEventTracker.EVENT_TRACKING_METHOD.JS),
        CUSTOM(NativeEventTracker.EVENT_TRACKING_METHOD.CUSTOM), ;

        companion object {

            @JvmStatic
            fun fromPrebidEventTrackingMethod(eventType: NativeEventTracker.EVENT_TRACKING_METHOD) =
                EventTrackingMethod.entries.find { it.prebidEventTrackingMethod == eventType }
                    ?: IMAGE
        }
    }
}
