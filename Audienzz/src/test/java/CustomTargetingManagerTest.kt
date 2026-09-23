// No package declaration: `CustomTargetingManager` itself has none, so it lives in the root
// package despite its directory. Matching that is the only way to reach it from a test; giving the
// class a proper package is a separate change with call sites to update.

import com.google.android.gms.ads.admanager.AdManagerAdRequest
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The SDK-owned GAM targeting key.
 *
 * Ad-ops line items and reporting are keyed on this, so its exact shape is a contract with people
 * outside this repository — worth a test rather than a comment.
 */
class CustomTargetingManagerTest {

    private fun applied(manager: CustomTargetingManager): Map<String, String> {
        val builder = mockk<AdManagerAdRequest.Builder>()
        val captured = mutableMapOf<String, String>()
        val key = slot<String>()
        val value = slot<String>()
        every { builder.addCustomTargeting(capture(key), capture(value)) } answers {
            captured[key.captured] = value.captured
            builder
        }
        manager.applyToGamRequestBuilder(builder)
        return captured
    }

    @Test
    fun `platform and version are one key`() {
        val keys = applied(CustomTargetingManager(sdkPlatform = "android", sdkVersion = "0.2.2"))
        assertEquals("android-0.2.2", keys["au_sdk"])
    }

    @Test
    fun `the separate version key is gone`() {
        val keys = applied(CustomTargetingManager(sdkPlatform = "android", sdkVersion = "0.2.2"))
        // Anything in Ad Manager keyed on au_v has to move to au_sdk matching <platform>-<version>.
        assertEquals(null, keys["au_v"])
    }

    @Test
    fun `an unresolved version leaves the bare platform rather than a trailing dash`() {
        val keys = applied(CustomTargetingManager(sdkPlatform = "android", sdkVersion = ""))
        assertEquals("android", keys["au_sdk"])
    }

    @Test
    fun `publisher targeting still travels, and cannot overwrite the SDK key`() {
        val manager = CustomTargetingManager(sdkPlatform = "android", sdkVersion = "0.2.2")
        manager.addCustomTargeting("section", "sport")
        manager.addCustomTargeting("au_sdk", "spoofed")
        val keys = applied(manager)
        assertEquals("sport", keys["section"])
        assertEquals("android-0.2.2", keys["au_sdk"])
    }
}
