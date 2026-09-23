import android.util.Log
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import org.json.JSONObject

class CustomTargetingManager(
    private val sdkPlatform: String = "android",
    private val sdkVersion: String = "",
) {
    /**
     * The single SDK identification key sent on every GAM request: platform and version in one
     * value, e.g. `android-0.2.2`.
     *
     * It used to be two keys — `au_sdk = android` and `au_v = 0.2.2`. One key is what GAM line-item
     * targeting and reporting actually want, because "this platform on this version" is a single
     * condition; expressing it as two forced every rule to AND them together.
     *
     * **`au_v` is no longer sent.** Anything keyed on it in Ad Manager needs to move to `au_sdk`
     * matching `<platform>-<version>`. The version is omitted only when the SDK could not resolve
     * one, in which case the value is the bare platform.
     */
    private val sdkPlatformVersion: String =
        if (sdkVersion.isEmpty()) sdkPlatform else "$sdkPlatform-$sdkVersion"

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

        // SDK-owned key applied after publisher keys so it always wins.
        requestBuilder.addCustomTargeting("au_sdk", sdkPlatformVersion)
        reservedTargetingMap.forEach { (key, value) ->
            requestBuilder.addCustomTargeting(key, value)
        }

        Log.d(TAG, "GAM custom targeting applied:")
        Log.d(TAG, "  au_sdk = $sdkPlatformVersion")
        reservedTargetingMap.forEach { (key, value) -> Log.d(TAG, "  $key = $value [reserved]") }
        targetingMap.forEach { (key, value) -> Log.d(TAG, "  $key = $value") }

        return requestBuilder
    }

    private companion object {
        const val TAG = "AUCustomTargeting"
    }
}
