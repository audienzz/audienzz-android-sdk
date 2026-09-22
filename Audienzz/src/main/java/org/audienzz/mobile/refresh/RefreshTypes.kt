package org.audienzz.mobile.refresh

/**
 * A reason periodic refresh is currently blocked.
 *
 * Reasons are independent and additive: clearing one never clears another. A single boolean could
 * not express "the publisher paused this banner AND it also scrolled out of view", so a visibility
 * resume silently undid a publisher pause.
 */
enum class RefreshBlockReason {
    /** The app called a public pause API. Only a public resume clears it. */
    PUBLISHER,

    /** This slot spent its initial request plus ten refreshes; only a new page resets its budget. */
    REFRESH_LIMIT,

    /** The banner's page is not the active one (see the page coordinator). */
    PAGE_INACTIVE,

    /** The app is backgrounded. */
    APP_BACKGROUND,

    /** The ad view is not attached to a window. */
    DETACHED,

    /** The banner is not sufficiently visible in the viewport. */
    NOT_VISIBLE,

    /**
     * A host that does its own visibility detection says the banner cannot be seen — a React
     * Native or Flutter cover the native geometry listener has no way to observe.
     *
     * Kept separate from [NOT_VISIBLE] because the two answer different questions and have
     * different owners. Sharing one reason meant a scroll that brought the banner back into the
     * viewport cleared a cover the host had reported, and removing a cover cleared a genuine
     * offscreen hold — each writer silently undoing the other.
     */
    HOST_REPORTED_HIDDEN,
}

/**
 * Why a request is being made. The four kinds have different eligibility rules, so they are not
 * interchangeable: only [PERIODIC_REFRESH] is governed by the refresh interval and the full block
 * set, while a first load keeps its lazy/prefetch behaviour.
 */
enum class RefreshRequestReason {
    /** The banner's very first load, driven by lazy loading or an immediate load. */
    FIRST_LOAD,

    /** A page impression reported this banner's page, so it serves a fresh creative. */
    PAGE_IMPRESSION,

    /** The refresh interval elapsed while the banner was eligible. */
    PERIODIC_REFRESH,

    /** A bounded retry after a failed load. */
    LOAD_RETRY,
}
