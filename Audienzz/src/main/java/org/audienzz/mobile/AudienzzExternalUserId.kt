package org.audienzz.mobile

import org.json.JSONObject
import org.prebid.mobile.ExternalUserId
import org.prebid.mobile.ExternalUserId.UniqueId

/**
 * Defines the User Id Object from an External Third Party Source
 */
data class AudienzzExternalUserId internal constructor(
    internal val prebidExternalUserId: ExternalUserId,
    internal val uniqueIds: List<AudienzzUniqueId>,
) {

    val source: String
        get() = prebidExternalUserId.source

    var ext: Map<String, Any>?
        get() = prebidExternalUserId.ext
        set(value) {
            prebidExternalUserId.setExt(value)
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
        uniqueIds: List<AudienzzUniqueId>,
    ) : this(
        ExternalUserId(
            source,
            uniqueIds.map {
                UniqueId(it.id, it.atype).apply {
                    setExt(it.ext)
                }
            },
        ),
        uniqueIds,
    )

    data class AudienzzUniqueId(
        val id: String,
        val atype: Int,
        var ext: MutableMap<String?, Any?>? = null,
    )

    override fun toString(): String = prebidExternalUserId.toString()
}
