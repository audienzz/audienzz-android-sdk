import com.google.android.gms.ads.admanager.AdManagerAdRequest
import org.json.JSONObject

class CustomTargetingManager {
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

        return keywordPairs.joinToString(", ")
    }

    /** For GAM requests - apply global targeting to a target AdManagerAdRequest.Builder */
    fun applyToGamRequestBuilder(
        requestBuilder: AdManagerAdRequest.Builder,
    ): AdManagerAdRequest.Builder {
        targetingMap.forEach { (key, value) ->
            if (value.contains(",")) {
                requestBuilder.addCustomTargeting(key, value.split(","))
            } else {
                requestBuilder.addCustomTargeting(key, value)
            }
        }
        return requestBuilder
    }
}
