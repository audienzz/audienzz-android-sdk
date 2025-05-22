package org.audienzz.mobile.api.data

import org.prebid.mobile.api.data.Position

enum class AudienzzPosition(internal val prebidPosition: Position) {
    TOP_LEFT(prebidPosition = Position.TOP_LEFT),
    TOP(prebidPosition = Position.TOP),
    TOP_RIGHT(prebidPosition = Position.TOP_RIGHT),
    RIGHT(prebidPosition = Position.RIGHT),
    BOTTOM_RIGHT(prebidPosition = Position.BOTTOM_RIGHT),
    BOTTOM(prebidPosition = Position.BOTTOM),
    BOTTOM_LEFT(prebidPosition = Position.BOTTOM_LEFT),
    LEFT(prebidPosition = Position.LEFT), ;

    companion object {

        @JvmStatic
        internal fun fromPrebidPosition(position: Position) =
            AudienzzPosition.entries.find { it.prebidPosition == position } ?: TOP_LEFT
    }
}
