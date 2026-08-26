package org.prebid.mobile;

import androidx.annotation.Nullable;

import org.prebid.mobile.rendering.bidding.data.bid.BidResponse;

/**
 * Bridges to {@link AdUnit#bidResponse}, the {@code protected} field where Prebid Mobile retains the
 * full winning bid response after {@code fetchDemand} on the original API. Lives in
 * {@code org.prebid.mobile} so it can read that field via Java package access (no reflection), which
 * keeps it compile-time checked and minification-safe.
 *
 * Returns {@code null} when no successful bid has been stored yet.
 */
public final class AudienzzBidResponseAccessor {

    private AudienzzBidResponseAccessor() {
    }

    @Nullable
    public static BidResponse getBidResponse(AdUnit adUnit) {
        return adUnit.bidResponse;
    }
}
