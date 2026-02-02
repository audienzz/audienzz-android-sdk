package org.audienzz.mobile.api.config

object RemoteAdSizeMapper {

    fun map(size: String): RemoteAdSize {
        val parts = size.split("x")
        return RemoteAdSize(
            width = parts[0].toInt(),
            height = parts[1].toInt(),
        )
    }
}
