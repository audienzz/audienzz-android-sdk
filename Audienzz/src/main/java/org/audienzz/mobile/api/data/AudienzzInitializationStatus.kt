package org.audienzz.mobile.api.data

import org.prebid.mobile.api.data.InitializationStatus

enum class AudienzzInitializationStatus(
    internal val prebidInitializationStatus: InitializationStatus,
) {

    SUCCEEDED(InitializationStatus.SUCCEEDED),
    SERVER_STATUS_WARNING(InitializationStatus.SERVER_STATUS_WARNING),
    FAILED(InitializationStatus.FAILED), ;

    var description: String?
        get() = prebidInitializationStatus.description
        set(value) {
            prebidInitializationStatus.description = value
        }

    companion object {

        @JvmStatic
        fun fromPrebidInitializationStatus(initializationStatus: InitializationStatus) =
            values().find { it.prebidInitializationStatus == initializationStatus } ?: SUCCEEDED
    }
}
