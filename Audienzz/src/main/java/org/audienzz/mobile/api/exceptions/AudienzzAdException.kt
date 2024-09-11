package org.audienzz.mobile.api.exceptions

import org.prebid.mobile.api.exceptions.AdException

/**
 * Base error. Maintaining error description.
 */
open class AudienzzAdException internal constructor(
    internal val prebidAdException: AdException,
) : Exception() {

    override val message: String? = prebidAdException.message

    constructor(type: String, message: String) : this(AdException(type, message))

    fun setMessage(msg: String) {
        prebidAdException.setMessage(msg)
    }

    companion object {

        const val INTERNAL_ERROR = "SDK internal error"
        const val INIT_ERROR = "Initialization failed"
        const val SERVER_ERROR = "Server error"
        const val THIRD_PARTY = "Third Party SDK"
    }
}
