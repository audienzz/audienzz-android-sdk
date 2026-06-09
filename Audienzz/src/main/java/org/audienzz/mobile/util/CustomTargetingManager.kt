import android.util.Log
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import org.json.JSONObject

class CustomTargetingManager(
    private val sdkPlatform: String = "android",
    private val sdkVersion: String = "",
) {
    private val targetingMap = mutableMapOf<String, String>()

    /** Keys set by SDK/bridge init — invisible to publishers.
     *  Cannot be removed via removeCustomTargeting / clearCustomTargeting. */
    private val reservedTargetingMap = mutableMapOf<String, String>()

    /** Add single key-value targeting */
    fun addCustomTargeting(key: String, value: String) {
        targetingMap[key] = value
    }

    /** Add single key - multiple values targeting */
    fun addCustomTargeting(key: String, values: Set<String>) {
        targetingMap[key] = values.joinToString(",")
    }

    /** Store a reserved (SDK-internal) key-value. Never cleared by publisher calls. */
    fun setReservedTargeting(key: String, value: String) {
        reservedTargetingMap[key] = value
    }

    /** Returns true if the key is in the reserved map. */
    fun isReserved(key: String): Boolean = reservedTargetingMap.containsKey(key)

    /** Remove targeting for specific key — silently skips reserved keys. */
    fun removeCustomTargeting(key: String) {
        if (isReserved(key)) return
        targetingMap.remove(key)
    }

    /** Clear all targeting — preserves reserved keys. */
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
        // Publisher keys first, then SDK keys — reserved keys always win.
        targetingMap.forEach { (key, value) ->
            if (value.contains(",")) {
                requestBuilder.addCustomTargeting(key, value.split(","))
            } else {
                requestBuilder.addCustomTargeting(key, value)
            }
        }

        // SDK-owned keys applied after publisher keys so they always win.
        requestBuilder.addCustomTargeting("au_sdk", sdkPlatform)
        if (sdkVersion.isNotEmpty()) {
            requestBuilder.addCustomTargeting("au_v", sdkVersion)
        }
        reservedTargetingMap.forEach { (key, value) ->
            requestBuilder.addCustomTargeting(key, value)
        }

        Log.d(TAG, "GAM custom targeting applied:")
        Log.d(TAG, "  au_sdk = $sdkPlatform")
        if (sdkVersion.isNotEmpty()) Log.d(TAG, "  au_v   = $sdkVersion")
        reservedTargetingMap.forEach { (key, value) -> Log.d(TAG, "  $key = $value [reserved]") }
        targetingMap.forEach { (key, value) -> Log.d(TAG, "  $key = $value") }

        return requestBuilder
    }

    private companion object {
        const val TAG = "AUCustomTargeting"
    }
}
