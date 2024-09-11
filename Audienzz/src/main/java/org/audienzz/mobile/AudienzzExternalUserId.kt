package org.audienzz.mobile

import org.json.JSONObject
import org.prebid.mobile.ExternalUserId

/**
 * Defines the User Id Object from an External Third Party Source
 */
data class AudienzzExternalUserId internal constructor(
    internal val prebidExternalUserId: ExternalUserId,
) {

    var source: String
        get() = prebidExternalUserId.source
        set(value) {
            prebidExternalUserId.source = value
        }

    var identifier: String
        get() = prebidExternalUserId.identifier
        set(value) {
            prebidExternalUserId.identifier = value
        }

    var atype: Int?
        get() = prebidExternalUserId.atype
        set(value) {
            prebidExternalUserId.atype = value
        }

    var ext: Map<String, Any>?
        get() = prebidExternalUserId.ext
        set(value) {
            prebidExternalUserId.ext = value
        }

    val json: JSONObject? = prebidExternalUserId.json

    /**
     * Initialize ExternalUserId Class
     * - Parameter source: Source of the External User Id String.
     * - Parameter identifier: String of the External User Id.
     * - Parameter atype: (Optional) Integer of the External User Id.
     * - Parameter ext: (Optional) Map of the External User Id.
     */
    constructor(
        source: String,
        identifier: String,
        atype: Int?,
        ext: Map<String, Any>?,
    ) : this(
        ExternalUserId(
            source,
            identifier,
            atype,
            ext,
        ),
    )

    override fun toString(): String = prebidExternalUserId.toString()
}
