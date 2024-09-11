package org.audienzz.mobile.rendering.models.openrtb.bidRequests

import org.json.JSONArray
import org.json.JSONObject
import org.prebid.mobile.rendering.models.openrtb.bidRequests.Ext

class AudienzzExt internal constructor(internal val prebidExt: Ext) {

    val jsonObject: JSONObject = prebidExt.jsonObject

    constructor() : this(Ext())

    fun put(key: String, value: String) {
        prebidExt.put(key, value)
    }

    fun put(key: String, value: Int) {
        prebidExt.put(key, value)
    }

    fun put(key: String, value: JSONObject) {
        prebidExt.put(key, value)
    }

    fun put(key: String, value: JSONArray) {
        prebidExt.put(key, value)
    }

    fun put(jsonObject: JSONObject) {
        prebidExt.put(jsonObject)
    }

    fun remove(key: String) {
        prebidExt.remove(key)
    }

    fun getMap(): Map<String, Any> = prebidExt.map
}
