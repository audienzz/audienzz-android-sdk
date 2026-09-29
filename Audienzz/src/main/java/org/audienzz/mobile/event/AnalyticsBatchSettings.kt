package org.audienzz.mobile.event

/** Publisher-wide delivery policy. Read at each send; never changes an HTTP request in flight. */
internal object AnalyticsBatchSettings {
    const val DEFAULT_SIZE = 10
    const val MAX_SIZE = 15
    @Volatile private var batchSize = DEFAULT_SIZE

    fun resolve(value: Int?): Int = value?.takeIf { it > 0 }?.coerceAtMost(MAX_SIZE) ?: DEFAULT_SIZE
    fun applyBackendConfig(value: Int?) { batchSize = resolve(value) }
    fun current(): Int = batchSize
}
