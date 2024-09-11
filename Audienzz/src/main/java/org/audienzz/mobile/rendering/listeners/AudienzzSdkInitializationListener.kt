package org.audienzz.mobile.rendering.listeners

import org.audienzz.mobile.api.data.AudienzzInitializationStatus

fun interface AudienzzSdkInitializationListener {

    fun onInitializationComplete(status: AudienzzInitializationStatus)
}
