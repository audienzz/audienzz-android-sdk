package org.audienzz.mobile.meta

import org.junit.Assert.assertEquals
import org.junit.Test

class AudienzzMetaTest {

    /**
     * The core SDK detects Meta by this class name (`MetaMediation.ADAPTER_CLASS`) and Ad Manager
     * reports it in ResponseInfo. A rename in a future adapter would silently switch Meta off.
     */
    @Test
    fun adapterClassIsWhereTheCoreSdkLooks() {
        Class.forName("com.google.ads.mediation.facebook.FacebookMediationAdapter")
    }

    @Test
    fun reportsTheBundledAdapterVersion() {
        assertEquals("6.21.0.2", AudienzzMeta.adapterVersion)
    }
}
