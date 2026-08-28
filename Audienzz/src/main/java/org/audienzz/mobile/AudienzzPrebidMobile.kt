package org.audienzz.mobile

import android.app.Activity
import android.app.Application
import android.content.Context
import android.view.View
import androidx.annotation.FloatRange
import androidx.annotation.MainThread
import com.google.android.gms.ads.MobileAds
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.audienzz.mobile.api.config.AndroidOrtbConfig
import org.audienzz.mobile.api.config.GamConfig
import org.audienzz.mobile.api.config.OrtbConfig
import org.audienzz.mobile.api.config.RemoteAdUnitConfig
import org.audienzz.mobile.api.data.AudienzzInitializationStatus
import org.audienzz.mobile.api.exceptions.AudienzzAdException
import org.audienzz.mobile.api.rendering.AudienzzPrebidMobileInterstitialControllerInterface
import org.audienzz.mobile.api.rendering.pluginrenderer.AudienzzPluginEventListener
import org.audienzz.mobile.api.rendering.pluginrenderer.AudienzzPrebidMobilePluginRenderer
import org.audienzz.mobile.configuration.AudienzzAdUnitConfiguration
import org.audienzz.mobile.configuration.AudienzzPBSConfig
import org.audienzz.mobile.di.MainComponent
import org.audienzz.mobile.event.eventLogger
import org.audienzz.mobile.rendering.bidding.data.bid.AudienzzBidResponse
import org.audienzz.mobile.rendering.bidding.interfaces.AudienzzInterstitialControllerListener
import org.audienzz.mobile.rendering.bidding.listeners.AudienzzDisplayVideoListener
import org.audienzz.mobile.rendering.bidding.listeners.AudienzzDisplayViewListener
import org.audienzz.mobile.rendering.listeners.AudienzzSdkInitializationListener
import org.audienzz.mobile.util.CurrentActivityTracker
import org.audienzz.mobile.util.PpidManager
import org.json.JSONObject
import org.prebid.mobile.PrebidMobile
import org.prebid.mobile.PrebidMobile.LogLevel
import org.prebid.mobile.TargetingParams
import org.prebid.mobile.api.rendering.PrebidMobileInterstitialControllerInterface
import org.prebid.mobile.api.rendering.pluginrenderer.PluginEventListener
import org.prebid.mobile.api.rendering.pluginrenderer.PrebidMobilePluginRenderer
import org.prebid.mobile.configuration.AdUnitConfiguration
import org.prebid.mobile.rendering.bidding.data.bid.BidResponse
import org.prebid.mobile.rendering.bidding.interfaces.InterstitialControllerListener
import org.prebid.mobile.rendering.bidding.listeners.DisplayVideoListener
import org.prebid.mobile.rendering.bidding.listeners.DisplayViewListener
import org.prebid.mobile.rendering.listeners.SdkInitializationListener

object AudienzzPrebidMobile {
    val ppidManager: PpidManager?
        get() = MainComponent.ppidManager

    internal var companyId: String = ""

    /** Publisher id used for remote config — reported as analytics `website_id`. */
    internal var publisherId: String? = null

    /** Cached backend smart-refresh-v2 flag from the publisher config (set during init). */
    private var backendSmartRefreshV2: Boolean? = null

    /**
     * Local override for the screen-aware smart-refresh model (directional viewport gate +
     * screen-navigation pause/reload). Takes precedence over the backend `smartRefreshV2` for the
     * remainder of the session. `null` (default) = defer to the backend value; `false`/`true` =
     * force off/on regardless of the backend.
     */
    @JvmStatic
    var smartRefreshV2Override: Boolean? = null

    /**
     * Resolved smart-refresh-v2 flag: local override wins, else the backend publisher config, else
     * `false` (legacy smart refresh). Reads the backend value cached at init, so it is `false`
     * until remote config loads unless a local override is set.
     */
    @JvmStatic
    fun isSmartRefreshV2Enabled(): Boolean =
        smartRefreshV2Override ?: backendSmartRefreshV2 ?: false

    /**
     * Automatic screen tracking. When true (default), the SDK observes Activity and Fragment
     * lifecycle and fires a page impression (and drives screen-aware smart refresh) on every screen
     * change — Activities, fragment navigation, and ViewPager2 tabs — with no per-screen code.
     * Set to false before init to opt out and drive screens yourself via [onScreenResumed].
     */
    @JvmStatic
    var autoScreenTracking: Boolean = true

    private var screenTracker: org.audienzz.mobile.screen.ScreenTracker? = null

    /** Single sink for both the auto tracker and the manual API: page impression + v2 coordinator. */
    private fun notifyScreenResumed(screen: Any, screenName: String) {
        eventLogger?.onScreenResumed(screenName)
        if (isSmartRefreshV2Enabled()) {
            org.audienzz.mobile.screen.screenAdCoordinator?.onScreenResumed(screen)
        }
    }

    /** Schain object for audienzz **/
    internal var schainObject: JSONObject? = null

    /**
     * Minimum refresh interval allowed. 30 seconds
     */
    @JvmStatic
    val AUTO_REFRESH_DELAY_MIN: Int = PrebidMobile.AUTO_REFRESH_DELAY_MIN

    /**
     * Maximum refresh interval allowed. 120 seconds
     */
    @JvmStatic
    val AUTO_REFRESH_DELAY_MAX: Int = PrebidMobile.AUTO_REFRESH_DELAY_MAX

    @JvmStatic
    val SCHEME_HTTPS: String = PrebidMobile.SCHEME_HTTPS

    @JvmStatic
    val SCHEME_HTTP: String = PrebidMobile.SCHEME_HTTP

    /**
     * SDK version
     */
    @JvmStatic
    val SDK_VERSION: String = PrebidMobile.SDK_VERSION

    /**
     * SDK name provided for MRAID_ENV in {@link MraidEnv}
     */
    @JvmStatic
    val SDK_NAME: String = PrebidMobile.SDK_NAME

    /**
     * Currently implemented MRAID version.
     */
    @JvmStatic
    val MRAID_VERSION: String = PrebidMobile.MRAID_VERSION

    /**
     * Currently implemented Native Ads version.
     */
    @JvmStatic
    val NATIVE_VERSION: String = PrebidMobile.NATIVE_VERSION

    /**
     * Open measurement SDK version
     */
    @JvmStatic
    val OMSDK_VERSION: String = PrebidMobile.OMSDK_VERSION

    /**
     * Tested Google SDK version.
     */
    @JvmStatic
    val TESTED_GOOGLE_SDK_VERSION: String = PrebidMobile.TESTED_GOOGLE_SDK_VERSION

    @JvmStatic
    var isUseCacheForReportingWithRenderingApi: Boolean
        get() = PrebidMobile.isUseCacheForReportingWithRenderingApi()
        set(value) {
            PrebidMobile.setUseCacheForReportingWithRenderingApi(value)
        }

    @JvmStatic
    var timeoutMillis: Int
        get() = PrebidMobile.getTimeoutMillis()
        set(value) {
            PrebidMobile.setTimeoutMillis(value)
        }

    @JvmStatic
    var prebidServerAccountId: String
        get() = PrebidMobile.getPrebidServerAccountId()
        set(value) {
            PrebidMobile.setPrebidServerAccountId(value)
        }

    @JvmStatic
    var audienzzHost: AudienzzHost = AudienzzHost.APPNEXUS

    @JvmStatic
    var isShareGeoLocation: Boolean
        get() = PrebidMobile.isShareGeoLocation()
        set(value) {
            PrebidMobile.setShareGeoLocation(value)
        }

    /**
     * List containing objects that hold External User Id parameters for the current application user.
     */
    @JvmStatic
    var externalUserIds: List<AudienzzExternalUserId>
        get() = TargetingParams.getExternalUserIds()
            .map {
                AudienzzExternalUserId(
                    it,
                    it.uniqueIds.map { uniqueId ->
                        AudienzzExternalUserId.AudienzzUniqueId(
                            uniqueId.id,
                            uniqueId.atype,
                        )
                            .apply {
                                uniqueId.setExt(it.ext)
                            }
                    },
                )
            }
        set(value) {
            TargetingParams.setExternalUserIds(value.map { it.prebidExternalUserId })
        }

    /**
     * HashMap containing a list of custom headers to add to requests
     */
    @JvmStatic
    var customerHeaders: Map<String, String>?
        get() = PrebidMobile.getCustomHeaders()
        set(value) {
            PrebidMobile.setCustomHeaders(value?.let { HashMap(it) })
        }

    @JvmStatic
    var storeAuctionResponse: String?
        get() = PrebidMobile.getStoredAuctionResponse()
        set(value) {
            PrebidMobile.setStoredAuctionResponse(value)
        }

    @JvmStatic
    val storedBidResponses: Map<String, String>
        get() = PrebidMobile.getStoredBidResponses()

    @JvmStatic
    var isPbsDebug: Boolean
        get() = PrebidMobile.getPbsDebug()
        set(value) {
            PrebidMobile.setPbsDebug(value)
        }

    /**
     * boolean that states if the ID will be set to the Asset array (in the Native Ad Request)
     */
    @JvmStatic
    var enabledAssignNativeAssetId: Boolean
        get() = PrebidMobile.shouldAssignNativeAssetID()
        set(value) {
            PrebidMobile.assignNativeAssetID(value)
        }

    /**
     * Return 'true' if Prebid Rendering SDK is initialized completely
     */
    @JvmStatic
    val isSdkInitialized: Boolean
        get() = PrebidMobile.isSdkInitialized()

    @JvmStatic
    var logLevel: AudienzzLogLevel
        get() = AudienzzLogLevel.fromPrebidLogLevel(PrebidMobile.getLogLevel())
        set(value) {
            PrebidMobile.setLogLevel(value.prebidLogLevel)
        }

    @JvmStatic
    var customLogger: AudienzzLogUtil.AudienzzPrebidLogger?
        get() = PrebidMobile.getCustomLogger()?.let { AudienzzLogUtil.getAudienzzPrebidLogger(it) }
        set(value) {
            value?.let { AudienzzLogUtil.getPrebidLogger(it) }
                ?.let { PrebidMobile.setCustomLogger(it) }
        }

    /**
     * Sets full valid URL for the /status endpoint of the PBS.
     * Request to /status is sent when you call
     * {@link PrebidMobile#initializeSdk(Context, SdkInitializationListener)}.
     *
     * @see <a href="https://docs.prebid.org/prebid-server/endpoints/pbs-endpoint-status.html">
     * GET /status</a>
     */
    @JvmStatic
    var customStatusEndpoint: String?
        get() = PrebidMobile.getCustomStatusEndpoint()
        set(value) {
            PrebidMobile.setCustomStatusEndpoint(value)
        }

    @JvmStatic
    var isIncludeWinnersFlag: Boolean
        get() = PrebidMobile.getIncludeWinnersFlag()
        set(value) {
            PrebidMobile.setIncludeWinnersFlag(value)
        }

    @JvmStatic
    var isIncludeBidderKeysFlag: Boolean
        get() = PrebidMobile.getIncludeBidderKeysFlag()
        set(value) {
            PrebidMobile.setIncludeBidderKeysFlag(value)
        }

    @JvmStatic
    var pbsConfig: AudienzzPBSConfig?
        get() = PrebidMobile.getPbsConfig()?.let { AudienzzPBSConfig(it) }
        set(value) {
            PrebidMobile.setPbsConfig(value?.prebidPBSConfig)
        }

    /**
     * Priority Policy: PBSConfig > SDKConfig > Default
     * creativeFactoryTimeout in ms
     */
    @JvmStatic
    var createFactoryTimeout: Int
        get() = PrebidMobile.getCreativeFactoryTimeout()
        set(value) {
            PrebidMobile.setCreativeFactoryTimeout(value)
        }

    private const val TAG = "AudienzzPrebidMobile"

    internal val CURRENT_ACTIVITY_TRACKER = CurrentActivityTracker()

    private val PLUGIN_RENDERER_CACHE =
        mutableMapOf<AudienzzPrebidMobilePluginRenderer, PrebidMobilePluginRenderer>()

    private val SCOPE = CoroutineScope(
        Dispatchers.Main + SupervisorJob() + CoroutineExceptionHandler { _, throwable ->
            android.util.Log.e(TAG, "CoroutineScope exception", throwable)
        },
    )

    init {
        prebidServerAccountId = "3927"
        // No hardcoded /status endpoint. A wrong/unreachable status URL blocks Prebid init: the
        // status request runs on a 10s executor timeout (SdkInitializer), and on timeout Prebid
        // calls initializationFailed(), which CLEARS the context — so every fetchDemand then returns
        // INVALID_CONTEXT (empty ads). With null, Prebid derives /status only for appnexus-style
        // hosts (…/openrtb2/auction) and safely SKIPS the check for custom hosts (e.g. nexx360).
        // The remote path overrides this with the backend statusUrl when the publisher config
        // provides one.
        customStatusEndpoint = null
        isShareGeoLocation = true
        enabledAssignNativeAssetId = true
        AudienzzTargetingParams.omidPartnerName = "Google"
        AudienzzTargetingParams.omidPartnerVersion = MobileAds.getVersion().toString()
    }

    private fun getPrebidMobilePluginRendererCached(
        prebidMobilePluginRenderer: AudienzzPrebidMobilePluginRenderer,
    ): PrebidMobilePluginRenderer {
        PLUGIN_RENDERER_CACHE[prebidMobilePluginRenderer]?.let { return it }
        val renderer = getPrebidMobilePluginRenderer(prebidMobilePluginRenderer)
        PLUGIN_RENDERER_CACHE[prebidMobilePluginRenderer] = renderer
        return renderer
    }

    /**
     * Initializes the main SDK classes and makes request to Prebid server to check its status.
     * If you use custom /status endpoint set it with
     * ({@link PrebidMobile#setCustomStatusEndpoint(String)}) before starting initialization.
     * <p>
     * Calls SdkInitializationListener callback with enum initialization status parameter:
     * <p>
     * SUCCEEDED - Prebid SDK is initialized successfully and ready to work.
     * <p>
     * FAILED - Prebid SDK is failed to initialize and is not able to work.
     * <p>
     * SERVER_STATUS_WARNING - Prebid SDK failed to check the PBS status. The SDK is initialized and
     * able to work, though.
     * <p>
     * To get the description of the problem you can call
     * {@link InitializationStatus#getDescription()}
     *
     * @param context  any context (must be not null)
     * @param companyId Company ID provided for the app by Audienzz
     * @param enablePpid Controls if unique PPID would be generated for users and used along with
     * ad requests
     * @param appVolume Global GMA ad audio level. Range: 0.0 (muted) – 1.0 (full device volume).
     *                  Defaults to 0.0 (muted). Can be overridden at any time via [setAppVolume].
     * @param sdkInitializationListener initialization listener (can be null).
     *                 <p>
     */
    @MainThread
    @JvmStatic
    fun initializeSdk(
        context: Context,
        companyId: String,
        enablePpid: Boolean = false,
        prebidServerUrl: String? = null,
        @FloatRange(from = 0.0, to = 1.0) appVolume: Float = 0f,
        sdkInitializationListener: AudienzzSdkInitializationListener?,
    ) {
        this.companyId = companyId
        val listener = SdkInitializationListener { status ->
            // M5: flush any consent/COPPA values the publisher set before init reached Prebid.
            AudienzzTargetingParams.onPrebidInitialized()
            sdkInitializationListener?.onInitializationComplete(
                AudienzzInitializationStatus.fromPrebidInitializationStatus(status),
            )
            ppidManager?.setAutomaticPpidEnabled(enablePpid)
        }
        registerActivityCallbacks(context)
        MainComponent.init(context)
        configureGam(context, GamConfig(appVolume = appVolume))
        PrebidMobile.initializeSdk(context, prebidServerUrl ?: audienzzHost.hostUrl, listener)
    }

    /**
     * Initializes the SDK with remote configuration support.
     * This method fetches ad configurations from the backend API using the provided publisher ID.
     *
     * @param context  any context (must be not null)
     * @param publisherId Publisher ID provided by Audienzz for remote configuration
     * @param enablePpid Controls if unique PPID would be generated for users and used along with
     * ad requests
     * @param sdkInitializationListener initialization listener (can be null)
     */
    @MainThread
    @JvmStatic
    fun initializeRemoteSdk(
        context: Context,
        publisherId: String,
        enablePpid: Boolean = false,
        sdkInitializationListener: AudienzzSdkInitializationListener?,
    ) {
        registerActivityCallbacks(context)
        MainComponent.init(context)

        this.publisherId = publisherId

        MainComponent.remoteConfigManager?.let { manager ->
            manager.initialize(publisherId)

            SCOPE.launch {
                val publisherConfig = withContext(Dispatchers.IO) {
                    manager.getPublisherConfig(publisherId)
                }

                companyId = publisherConfig?.ortbConfig?.schainConfig?.sellerId ?: "1"
                backendSmartRefreshV2 = publisherConfig?.smartRefreshV2

                val baseUrl = publisherConfig?.prebidServerConfig?.url ?: audienzzHost.hostUrl
                val prebidServerUrl = if (isPbsDebug) {
                    val sep = if (baseUrl.contains("?")) "&" else "?"
                    if (baseUrl.contains("test=1")) baseUrl else "$baseUrl${sep}test=1"
                } else {
                    baseUrl
                }
                android.util.Log.d(TAG, "Initializing Prebid with URL: $prebidServerUrl")

                if (publisherConfig != null) {
                    PrebidMobile.setPrebidServerAccountId(
                        publisherConfig.prebidServerConfig.accountId.toString(),
                    )

                    // The Prebid init health check (GET /status) must target the configured PBS
                    // server, not the hardcoded default. A failed status check aborts init and
                    // clears the Prebid context, so every fetchDemand then returns INVALID_CONTEXT
                    // (empty ads). Use the backend-provided statusUrl when present; otherwise keep
                    // whatever default was set.
                    publisherConfig.prebidServerConfig.statusUrl?.let { customStatusEndpoint = it }

                    publisherConfig.ortbConfig?.let { configureOrtb(it) }
                    publisherConfig.androidConfig?.ortbConfig?.let { configureAndroidOrtb(it) }
                }

                val listener = SdkInitializationListener { status ->
                    // M5: flush any consent/COPPA values the publisher set before init reached Prebid.
                    AudienzzTargetingParams.onPrebidInitialized()
                    sdkInitializationListener?.onInitializationComplete(
                        AudienzzInitializationStatus.fromPrebidInitializationStatus(status),
                    )
                    ppidManager?.setAutomaticPpidEnabled(enablePpid)
                }

                configureGam(context, publisherConfig?.gamConfig)
                PrebidMobile.initializeSdk(context, prebidServerUrl, listener)
            }
        }
    }

    /**
     * Fetches a remote ad unit configuration by its ID.
     * The SDK must have been initialized via [initializeRemoteSdk] first.
     *
     * @param configId the ad unit config ID as defined in the Audienzz dashboard (e.g. "46", "47")
     * @param callback called on the main thread with the config, or null if not found / not initialized
     */
    @JvmStatic
    fun getAdUnitConfig(configId: String, callback: (RemoteAdUnitConfig?) -> Unit) {
        SCOPE.launch {
            val config = withContext(Dispatchers.IO) {
                MainComponent.remoteConfigManager?.getAdUnitConfig(configId)
            }
            callback(config)
        }
    }

    private fun configureOrtb(ortb: OrtbConfig) {
        AudienzzTargetingParams.publisherName = ortb.publisherName
        ortb.domain?.let { AudienzzTargetingParams.domain = it }
        ortb.schainConfig?.let { schain ->
            setSchainObject(
                """
                    { "source": 
                        { "schain": {
                            "ver": "1.0",
                            "complete": 1,
                            "nodes": [
                                {
                                    "asi": "${schain.advertisingSystemDomain.orEmpty()}",
                                    "sid": "${schain.sellerId.orEmpty()}",
                                    "hp": 1
                                }
                            ]
                            }
                        } 
                    }
                """.trimMargin(),
            )
        }
    }

    private fun configureAndroidOrtb(androidOrtb: AndroidOrtbConfig) {
        AudienzzTargetingParams.bundleName = androidOrtb.bundleName
        androidOrtb.storeUrl?.let { AudienzzTargetingParams.storeUrl = it }
    }

    /**
     * Initializes GMA and applies the app volume from [gamConfig].
     * If [gamConfig] is null or its [GamConfig.appVolume] is absent, defaults to 0.0 (muted).
     * The volume is clamped to the valid GMA range [0.0, 1.0].
     */
    private fun configureGam(context: Context, gamConfig: GamConfig?) {
        val volume = (gamConfig?.appVolume ?: 0f).coerceIn(0f, 1f)
        MobileAds.initialize(context) {
            MobileAds.setAppVolume(volume)
            MobileAds.setAppMuted(volume == 0f)
            android.util.Log.d(TAG, "GMA app volume set to $volume, muted=${volume == 0f}")
        }
    }

    private fun registerActivityCallbacks(context: Context) {
        val app = context.applicationContext as? Application ?: return
        app.registerActivityLifecycleCallbacks(CURRENT_ACTIVITY_TRACKER)
        app.registerActivityLifecycleCallbacks(org.audienzz.mobile.util.AppForegroundMonitor)
        // Automatic screen tracking (opt-out via autoScreenTracking). Registered once.
        if (autoScreenTracking && screenTracker == null) {
            val tracker = org.audienzz.mobile.screen.ScreenTracker { screen, name ->
                notifyScreenResumed(screen, name)
            }
            screenTracker = tracker
            app.registerActivityLifecycleCallbacks(tracker)
        }
    }

    /**
     * Call this in every Activity or Fragment's onResume() to track screen impressions.
     * Generates a new pageImpressionId for the screen and fires a pageImpression event.
     * All ad events fired after this call will be associated with this screen visit.
     *
     * @param activity the current Activity
     */
    @JvmStatic
    fun onScreenResumed(activity: Activity) {
        // Ignored while automatic tracking is on — it already observes Activities (avoids
        // double-counting). Turn off autoScreenTracking to drive screens manually.
        if (autoScreenTracking) return
        notifyScreenResumed(activity, activity.componentName.className)
    }

    /**
     * Manual screen signal for a Fragment. The Fragment is the screen identity, so different
     * Fragments — including ViewPager2 tabs — are distinct screens. Ignored while
     * [autoScreenTracking] is on (auto already observes Fragments).
     */
    @JvmStatic
    fun onScreenResumed(fragment: androidx.fragment.app.Fragment) {
        if (autoScreenTracking) return
        notifyScreenResumed(fragment, fragment.javaClass.name)
    }

    /**
     * Manual screen signal by an opaque key (e.g. a route name from Jetpack Compose / Flutter /
     * React Native). The key string is the screen identity. Always applied — automatic tracking
     * can't see non-Activity/Fragment screens, so this is how you report them.
     */
    @JvmStatic
    fun onScreenResumed(screenKey: String) {
        notifyScreenResumed(screenKey, screenKey)
    }

    @JvmStatic
    fun addStoredBidResponse(bidder: String, responseId: String) {
        PrebidMobile.addStoredBidResponse(bidder, responseId)
    }

    @JvmStatic
    fun clearStoredBidResponses() {
        PrebidMobile.clearStoredBidResponses()
    }

    /**
     * Check Google Mobile Ads compatibility for original API.
     * Show logs if version is not compatible.
     *
     * @param googleAdsVersion - MobileAds.getVersion().toString()
     */
    @JvmStatic
    fun checkGoogleMobileAdsCompatibility(googleAdsVersion: String) {
        PrebidMobile.checkGoogleMobileAdsCompatibility(googleAdsVersion)
    }

    /**
     * Sets the global app volume for Google Mobile Ads ad audio.
     *
     * Can be called at any time after SDK initialization to update the volume mid-session.
     * The value set here takes precedence over the backend [GamConfig.appVolume] for the
     * remainder of the session.
     *
     * @param volume Audio level in range [0.0, 1.0]. 0.0 = muted, 1.0 = full device volume.
     *               Values outside the range are clamped automatically.
     */
    @JvmStatic
    fun setAppVolume(@FloatRange(from = 0.0, to = 1.0) volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        if (clamped != volume) {
            android.util.Log.w(TAG, "setAppVolume: $volume is out of [0.0, 1.0], clamped to $clamped")
        }
        MobileAds.setAppVolume(clamped)
        MobileAds.setAppMuted(clamped == 0f)
        android.util.Log.d(TAG, "GMA app volume updated to $clamped, muted=${clamped == 0f}")
    }

    /**
     * Priority Policy: PBSConfig > SDKConfig > Default
     * @return creativeFactoryTimeoutPreRender in ms
     */
    @JvmStatic
    fun registerPluginRenderer(prebidMobilePluginRenderer: AudienzzPrebidMobilePluginRenderer) {
        PrebidMobile.registerPluginRenderer(
            getPrebidMobilePluginRendererCached(prebidMobilePluginRenderer),
        )
    }

    @JvmStatic
    fun unregisterPluginRenderer(prebidMobilePluginRenderer: AudienzzPrebidMobilePluginRenderer) {
        PrebidMobile.unregisterPluginRenderer(
            getPrebidMobilePluginRenderer(prebidMobilePluginRenderer),
        )
        PLUGIN_RENDERER_CACHE.remove(prebidMobilePluginRenderer)
    }

    @JvmStatic
    fun containsPluginRenderer(
        prebidMobilePluginRenderer: AudienzzPrebidMobilePluginRenderer,
    ): Boolean = PrebidMobile.containsPluginRenderer(
        getPrebidMobilePluginRenderer(prebidMobilePluginRenderer),
    )

    /**
     * Set publisher schain object to use with ad requests
     *
     * @param schain
     */
    @JvmStatic
    fun setSchainObject(schain: String) {
        // H6: store the schain as its own source and rebuild the canonical global ORTB. Routing it
        // through setGlobalOrtbConfig would have stored the schain AS the publisher's ORTB and let
        // a later targeting change wipe it (and vice-versa).
        schainObject = JSONObject(schain)
        AudienzzTargetingParams.rebuildGlobalOrtb()
    }

    private fun getPrebidMobilePluginRenderer(
        prebidMobilePluginRenderer: AudienzzPrebidMobilePluginRenderer,
    ) = object : PrebidMobilePluginRenderer {
        override fun getName(): String = prebidMobilePluginRenderer.getName()

        override fun getVersion(): String = prebidMobilePluginRenderer.getVersion()

        override fun getData(): JSONObject? = prebidMobilePluginRenderer.getData()

        override fun registerEventListener(
            pluginEventListener: PluginEventListener?,
            listenerKey: String?,
        ) {
            prebidMobilePluginRenderer.registerEventListener(
                object : AudienzzPluginEventListener {
                    override fun getPluginRendererName(): String? =
                        pluginEventListener?.pluginRendererName
                },
                listenerKey,
            )
        }

        override fun unregisterEventListener(listenerKey: String?) {
            prebidMobilePluginRenderer.unregisterEventListener(listenerKey)
        }

        override fun createBannerAdView(
            context: Context,
            displayViewListener: DisplayViewListener,
            displayVideoListener: DisplayVideoListener?,
            adUnitConfiguration: AdUnitConfiguration,
            bidResponse: BidResponse,
        ): View = prebidMobilePluginRenderer.createBannerAdView(
            context = context,
            displayViewListener = getDisplayViewListener(displayViewListener),
            displayVideoListener = displayVideoListener?.let { getDisplayVideoListener(it) },
            adUnitConfiguration = AudienzzAdUnitConfiguration(adUnitConfiguration),
            bidResponse = AudienzzBidResponse(bidResponse),
        )

        override fun createInterstitialController(
            context: Context,
            interstitialControllerListener: InterstitialControllerListener,
            adUnitConfiguration: AdUnitConfiguration,
            bidResponse: BidResponse,
        ): PrebidMobileInterstitialControllerInterface =
            getPrebidMobileInterstitialControllerInterface(
                prebidMobilePluginRenderer.createInterstitialController(
                    context,
                    getInterstitialControllerListener(interstitialControllerListener),
                    AudienzzAdUnitConfiguration(adUnitConfiguration),
                    AudienzzBidResponse(bidResponse),
                ),
            )

        override fun isSupportRenderingFor(adUnitConfiguration: AdUnitConfiguration?): Boolean =
            prebidMobilePluginRenderer.isSupportRenderingFor(
                adUnitConfiguration?.let { AudienzzAdUnitConfiguration(it) },
            )
    }

    private fun getPrebidMobileInterstitialControllerInterface(
        controllerInterface: AudienzzPrebidMobileInterstitialControllerInterface,
    ) = object : PrebidMobileInterstitialControllerInterface {
        override fun loadAd(adUnitConfiguration: AdUnitConfiguration?, bidResponse: BidResponse?) {
            controllerInterface.loadAd(
                adUnitConfiguration?.let { AudienzzAdUnitConfiguration(it) },
                bidResponse?.let { AudienzzBidResponse(it) },
            )
        }

        override fun show() {
            controllerInterface.show()
        }

        override fun destroy() {
            controllerInterface.destroy()
        }
    }

    private fun getInterstitialControllerListener(
        interstitialControllerListener: InterstitialControllerListener,
    ) = object : AudienzzInterstitialControllerListener {
        override fun onInterstitialReadyForDisplay() {
            interstitialControllerListener.onInterstitialReadyForDisplay()
        }

        override fun onInterstitialClicked() {
            interstitialControllerListener.onInterstitialClicked()
        }

        override fun onInterstitialFailedToLoad(exception: AudienzzAdException?) {
            interstitialControllerListener.onInterstitialFailedToLoad(exception?.prebidAdException)
        }

        override fun onInterstitialDisplayed() {
            interstitialControllerListener.onInterstitialDisplayed()
        }

        override fun onInterstitialClosed() {
            interstitialControllerListener.onInterstitialClosed()
        }
    }

    private fun getDisplayViewListener(displayViewListener: DisplayViewListener) =
        object : AudienzzDisplayViewListener {
            override fun onAdLoaded() {
                displayViewListener.onAdLoaded()
            }

            override fun onAdDisplayed() {
                displayViewListener.onAdDisplayed()
            }

            override fun onAdFailed(exception: AudienzzAdException?) {
                displayViewListener.onAdFailed(exception?.prebidAdException)
            }

            override fun onAdClicked() {
                displayViewListener.onAdClicked()
            }

            override fun onAdClosed() {
                displayViewListener.onAdClosed()
            }
        }

    private fun getDisplayVideoListener(displayVideoListener: DisplayVideoListener) =
        object : AudienzzDisplayVideoListener {
            override fun onVideoCompleted() {
                displayVideoListener.onVideoCompleted()
            }

            override fun onVideoPaused() {
                displayVideoListener.onVideoPaused()
            }

            override fun onVideoResumed() {
                displayVideoListener.onVideoResumed()
            }

            override fun onVideoUnMuted() {
                displayVideoListener.onVideoUnMuted()
            }

            override fun onVideoMuted() {
                displayVideoListener.onVideoMuted()
            }
        }

    enum class AudienzzLogLevel(internal val prebidLogLevel: LogLevel) {
        NONE(LogLevel.NONE),
        DEBUG(LogLevel.DEBUG),
        WARN(LogLevel.WARN),
        ERROR(LogLevel.ERROR), ;

        val value: Int = prebidLogLevel.value

        companion object {

            internal fun fromPrebidLogLevel(logLevel: LogLevel) =
                AudienzzLogLevel.entries.find { it.prebidLogLevel == logLevel } ?: NONE
        }
    }
}
