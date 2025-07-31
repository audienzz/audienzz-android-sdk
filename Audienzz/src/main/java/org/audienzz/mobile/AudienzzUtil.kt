package org.audienzz.mobile

import android.util.Log
import org.json.JSONException
import org.json.JSONObject
import org.prebid.mobile.Util

object AudienzzUtil {

    @JvmStatic val HTTP_CONNECTION_TIMEOUT = Util.HTTP_CONNECTION_TIMEOUT

    @JvmStatic val HTTP_SOCKET_TIMEOUT = Util.HTTP_SOCKET_TIMEOUT

    @JvmStatic val NATIVE_AD_VISIBLE_PERIOD_MILLIS = Util.NATIVE_AD_VISIBLE_PERIOD_MILLIS

    @JvmStatic
    fun apply(bids: HashMap<String, String>, adObj: Any?) {
        Util.apply(bids, adObj)
    }

    @JvmStatic
    fun saveCacheId(cacheId: String?, adObject: Any?) {
        Util.saveCacheId(cacheId, adObject)
    }

    /**
     * Generate ad tag url for Google's IMA SDK to fetch ads
     *
     * @param adUnit         GAM ad unit id
     * @param sizes          a set of ad sizes, only 640x480 and 400x300 are valid
     * @param prebidKeywords prebid keywords
     * @return ad tag url
     */
    @JvmStatic
    fun generateInstreamUriForGam(
        adUnit: String?,
        sizes: HashSet<AudienzzAdSize>?,
        prebidKeywords: Map<String, String>?,
    ): String = Util.generateInstreamUriForGam(
        adUnit,
        sizes?.map { it.adSize }?.toHashSet(),
        prebidKeywords,
    )

    @JvmStatic
    fun mergeJsonObjects(base: JSONObject, additional: JSONObject): JSONObject {
        try {
            val result = JSONObject(base.toString())

            additional.keys().forEach { key ->
                val value = additional.get(key)

                if (value is JSONObject && result.has(key) && result.get(key) is JSONObject) {
                    result.put(key, mergeJsonObjects(result.getJSONObject(key), value))
                } else {
                    result.put(key, value)
                }
            }
            return result
        } catch (e: JSONException) {
            Log.e("AudienzzUtil", "Error merging JSON objects", e)
        }
        return base
    }
}
