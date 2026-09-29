package org.audienzz.mobile.di

import org.junit.Assert.assertSame
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class MainComponentOwnershipTest {
    @Test fun `repeated SDK initialization preserves the singleton graph`() {
        val app = RuntimeEnvironment.getApplication()
        MainComponent.init(app)
        // Resolve a service without creating a live analytics transport in the test.
        val original = MainComponent.ppidManager
        assertNotNull(original)
        MainComponent.init(app)
        assertSame(original, MainComponent.ppidManager)
    }
}
