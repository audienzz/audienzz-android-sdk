import android.util.Log
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import org.json.JSONObject

class CustomTargetingManager(
    private val sdkPlatform: String = "android",
    private val sdkVersion: String = "",
) {
    private val targetingMap = mutableMapOf<String, String>()

    /** Add single key-value targeting */
    fun addCustomTargeting(key: String, value: String) {
        targetingMap[key] = value
    }

    /** Add single key - multiple values targeting */
    fun addCustomTargeting(key: String, values: Set<String>) {
        targetingMap[key] = values.joinToString(",")
    }

    /** Remove targeting for specific key */
    fun removeCustomTargeting(key: String) {
        targetingMap.remove(key)
    }

    /** Clear all targeting */
    fun clearCustomTargeting() {
        targetingMap.clear()
    }

    /** For ORTB - build the custom targeting part of JSON */
    fun buildOrtbCustomTargeting(): JSONObject {
        val ortbJson = JSONObject()

        if (targetingMap.isNotEmpty()) {
            ortbJson.put(
                "app",
                JSONObject().apply {
                    put(
                        "content",
                        JSONObject().apply {
                            put(
                                "keywords",
                                buildKeywordsString(),
                            )
                        },
                    )
                },
            )
        }

        return ortbJson
    }

    // Build keywords string in format "KEY=VALUE, KEY=VALUE2"
    private fun buildKeywordsString(): String {
        val keywordPairs = mutableListOf<String>()

        targetingMap.forEach { (key, value) ->
            if (value.contains(",")) {
                value.split(",").forEach { singleValue ->
                    keywordPairs.add("$key=${singleValue.trim()}")
                }
            } else {
                keywordPairs.add("$key=$value")
            }
        }

        return keywordPairs.joinToString(",")
    }

    /** For GAM requests - apply global targeting to a target AdManagerAdRequest.Builder */
    fun applyToGamRequestBuilder(
        requestBuilder: AdManagerAdRequest.Builder,
    ): AdManagerAdRequest.Builder {
        // Always inject SDK metadata first so it is present in every GAM request
        // regardless of whether the publisher configured any custom targeting.
        // These values are used for GAM reporting and version-based line-item targeting.
        requestBuilder.addCustomTargeting("au_sdk", sdkPlatform)
        if (sdkVersion.isNotEmpty()) {
            requestBuilder.addCustomTargeting("au_v", sdkVersion)
        }

        targetingMap.forEach { (key, value) ->
            if (value.contains(",")) {
                requestBuilder.addCustomTargeting(key, value.split(","))
            } else {
                requestBuilder.addCustomTargeting(key, value)
            }
        }

        Log.d(TAG, "GAM custom targeting applied:")
        Log.d(TAG, "  au_sdk = $sdkPlatform")
        if (sdkVersion.isNotEmpty()) Log.d(TAG, "  au_v   = $sdkVersion")
        targetingMap.forEach { (key, value) -> Log.d(TAG, "  $key = $value") }

        return requestBuilder
    }

    private companion object {
        const val TAG = "AUCustomTargeting"
    }
}
