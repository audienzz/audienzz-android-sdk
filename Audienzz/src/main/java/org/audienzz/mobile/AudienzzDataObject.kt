package org.audienzz.mobile

import org.audienzz.mobile.rendering.models.openrtb.bidRequests.AudienzzExt
import org.json.JSONObject
import org.prebid.mobile.DataObject
import org.prebid.mobile.DataObject.SegmentObject

class AudienzzDataObject constructor(internal val prebidDataObject: DataObject) {

    /**
     * Exchange-specific ID for the data provider.
     */
    var id: String?
        get() = prebidDataObject.id
        set(value) {
            prebidDataObject.id = value
        }

    /**
     * Exchange-specific name for the data provider.
     */
    var name: String?
        get() = prebidDataObject.name
        set(value) {
            prebidDataObject.name = value
        }

    constructor() : this(DataObject())

    fun setExt(ext: AudienzzExt) {
        prebidDataObject.setExt(ext.prebidExt)
    }

    fun getJsonObject(): JSONObject = prebidDataObject.jsonObject

    fun getSegments(): List<AudienzzSegmentObject> =
        prebidDataObject.segments.map { AudienzzSegmentObject(it) }

    fun addSegment(segmentObject: AudienzzSegmentObject) {
        prebidDataObject.addSegment(segmentObject.prebidSegmentObject)
    }

    fun setSegments(segments: ArrayList<AudienzzSegmentObject>) {
        prebidDataObject.segments = arrayListOf<SegmentObject>().apply {
            addAll(segments.map { it.prebidSegmentObject })
        }
    }

    class AudienzzSegmentObject internal constructor(
        internal val prebidSegmentObject: SegmentObject,
    ) {

        /**
         * ID of the data segment specific to the data provider.
         */
        var id: String?
            get() = prebidSegmentObject.id
            set(value) {
                prebidSegmentObject.value = value
            }

        /**
         * Name of the data segment specific to the data provider.
         */
        var name: String?
            get() = prebidSegmentObject.name
            set(value) {
                prebidSegmentObject.value = value
            }

        /**
         * String representation of the data segment value.
         */
        var value: String?
            get() = prebidSegmentObject.value
            set(value) {
                prebidSegmentObject.value = value
            }

        constructor() : this(SegmentObject())

        fun getJsonObject(): JSONObject? = prebidSegmentObject.jsonObject
    }
}
