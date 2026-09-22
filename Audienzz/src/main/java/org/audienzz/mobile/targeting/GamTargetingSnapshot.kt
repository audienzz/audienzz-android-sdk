package org.audienzz.mobile.targeting

import android.os.Bundle
import android.util.Log
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import java.lang.reflect.Modifier

/**
 * GMA 25.1.0 retains the builder's targeting Bundle by reference; it has no public request-copy
 * API. Detach ONLY that bundle on the newly built request, preserving every publisher option
 * (including mediation extras, exclusions and timeout, which public getters cannot fully copy).
 *
 * Locate by type and identity, never obfuscated field names. consumer-rules.pro preserves these
 * fields under R8. Real-GMA request tests must pass on every dependency upgrade. If a future GMA
 * changes its shape, keep ad loading functional and report the compatibility failure.
 */
internal object GamTargetingSnapshot {
    private var warned = false

    fun detach(request: AdManagerAdRequest) {
        val targeting = request.customTargeting
        try {
            for (holder in AdRequest::class.java.declaredFields) {
                if (Modifier.isStatic(holder.modifiers) || holder.type.isPrimitive) continue
                holder.isAccessible = true
                val state = holder.get(request) ?: continue
                for (field in state.javaClass.declaredFields) {
                    if (Modifier.isStatic(field.modifiers) || field.type != Bundle::class.java) continue
                    field.isAccessible = true
                    if (field.get(state) === targeting) {
                        field.set(state, Bundle(targeting))
                        check(request.customTargeting !== targeting)
                        return
                    }
                }
            }
            error("GMA request targeting field not found")
        } catch (error: Exception) {
            if (!warned) {
                warned = true
                Log.w("AudienzzTargeting", "GMA request targeting isolation is unavailable", error)
            }
        }
    }
}
