package org.audienzz.mobile

import androidx.annotation.Size
import org.prebid.mobile.LogUtil
import org.prebid.mobile.LogUtil.PrebidLogger

object AudienzzLogUtil {

    val NONE = LogUtil.NONE
    val VERBOSE = LogUtil.VERBOSE
    val DEBUG = LogUtil.DEBUG
    val INFO = LogUtil.INFO
    val WARN = LogUtil.WARN
    val ERROR = LogUtil.ERROR
    val ASSERT = LogUtil.ASSERT

    var logLevel: Int
        get() = LogUtil.getLogLevel()
        set(value) {
            LogUtil.setLogLevel(value)
        }

    fun setLogger(logger: AudienzzPrebidLogger) {
        LogUtil.setLogger(getPrebidLogger(logger))
    }

    /**
     * Prints a message with VERBOSE priority and default BASE_TAG
     */
    fun verbose(message: String) {
        LogUtil.verbose(message)
    }

    /**
     * Prints a message with DEBUG priority and default BASE_TAG
     */
    fun debug(message: String) {
        LogUtil.debug(message)
    }

    /**
     * Prints a message with INFO priority and default BASE_TAG
     */
    fun info(message: String) {
        LogUtil.info(message)
    }

    /**
     * Prints a message with WARNING priority and default BASE_TAG
     */
    fun warning(message: String) {
        LogUtil.warning(message)
    }

    /**
     * Prints a message with ERROR priority and default BASE_TAG
     */
    fun error(message: String) {
        LogUtil.error(message)
    }

    /**
     * Prints a message with VERBOSE priority.
     */
    fun verbose(@Size(max = 23) tag: String, message: String) {
        LogUtil.verbose(tag, message)
    }

    /**
     * Prints a message with DEBUG priority.
     */
    fun debug(@Size(max = 23) tag: String, message: String) {
        LogUtil.debug(tag, message)
    }

    /**
     * Prints a message with INFO priority.
     */
    fun info(@Size(max = 23) tag: String, message: String) {
        LogUtil.info(tag, message)
    }

    /**
     * Prints a message with WARN priority.
     */
    fun warning(@Size(max = 23) tag: String, message: String) {
        LogUtil.warning(tag, message)
    }

    /**
     * Prints a message with ERROR priority.
     */
    fun error(@Size(max = 23) tag: String, message: String) {
        LogUtil.error(tag, message)
    }

    /**
     * Prints a message with ASSERT priority.
     */
    fun wtf(@Size(max = 23) tag: String, message: String) {
        LogUtil.wtf(tag, message)
    }

    /**
     * Prints a message with ERROR priority and exception.
     */
    fun error(tag: String, message: String, throwable: Throwable) {
        LogUtil.error(tag, message, throwable)
    }

    interface AudienzzPrebidLogger {

        fun println(messagePriority: Int, tag: String?, message: String?)

        fun e(tag: String?, message: String?, throwable: Throwable?)
    }

    internal fun getPrebidLogger(logger: AudienzzPrebidLogger) =
        object : PrebidLogger {
            override fun println(messagePriority: Int, tag: String?, message: String?) {
                logger.println(messagePriority, tag, message)
            }

            override fun e(tag: String?, message: String?, throwable: Throwable?) {
                logger.e(tag, message, throwable)
            }
        }

    internal fun getAudienzzPrebidLogger(logger: PrebidLogger) =
        object : AudienzzPrebidLogger {
            override fun println(messagePriority: Int, tag: String?, message: String?) {
                logger.println(messagePriority, tag, message)
            }

            override fun e(tag: String?, message: String?, throwable: Throwable?) {
                logger.e(tag, message, throwable)
            }
        }
}
