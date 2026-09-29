package org.audienzz.mobile.screen

/** One presentation, captured when shown (not prefetched). Analytics stays on the request page. */
internal class InterstitialPageRecovery {
    private var coordinator: ScreenAdCoordinator? = null
    private var revision = 0L

    fun onShown() {
        if (coordinator != null) return
        val owner = screenAdCoordinator ?: return
        coordinator = owner
        revision = owner.beginInterstitial(this)
    }

    fun finish(dismissed: Boolean) {
        val owner = coordinator ?: return
        coordinator = null
        owner.endInterstitial(this, revision, dismissed)
    }
}
