package org.audienzz.mobile

import org.json.JSONObject
import org.prebid.mobile.ContentObject
import org.prebid.mobile.DataObject

class AudienzzContentObject constructor(internal val prebidContentObject: ContentObject) {

    /**
     * ID uniquely identifying the content.
     */
    var id: String?
        get() = prebidContentObject.id
        set(value) {
            prebidContentObject.id = value
        }

    /**
     * Episode number.
     */
    var episode: Int?
        get() = prebidContentObject.episode
        set(value) {
            prebidContentObject.episode = value
        }

    /**
     * Content title.
     */
    var title: String?
        get() = prebidContentObject.title
        set(value) {
            prebidContentObject.title = value
        }

    /**
     * Content series.
     */
    var series: String?
        get() = prebidContentObject.series
        set(value) {
            prebidContentObject.series = value
        }

    /**
     * Content season.
     */
    var season: String?
        get() = prebidContentObject.season
        set(value) {
            prebidContentObject.season = value
        }

    /**
     * Artist credited with the content.
     */
    var artist: String?
        get() = prebidContentObject.artist
        set(value) {
            prebidContentObject.artist = value
        }

    /**
     * Genre that best describes the content.
     */
    var genre: String?
        get() = prebidContentObject.genre
        set(value) {
            prebidContentObject.genre = value
        }

    /**
     * Album to which the content belongs; typically for audio.
     */
    var album: String?
        get() = prebidContentObject.album
        set(value) {
            prebidContentObject.album = value
        }

    /**
     * International Standard Recording Code conforming to ISO- 3901.
     */
    var isrc: String?
        get() = prebidContentObject.isrc
        set(value) {
            prebidContentObject.isrc = value
        }

    /**
     * URL of the content, for buy-side contextualization or review.
     */
    var url: String?
        get() = prebidContentObject.url
        set(value) {
            prebidContentObject.url = value
        }

    /**
     * Array of IAB content categories that describe the content producer.
     */
    var categories: List<String>
        get() = prebidContentObject.categories
        set(value) {
            prebidContentObject.categories = arrayListOf<String>().apply { addAll(value) }
        }

    /**
     * Production quality.
     */
    var productionQuality: Int?
        get() = prebidContentObject.productionQuality
        set(value) {
            prebidContentObject.productionQuality = value
        }

    /**
     * Type of content (game, video, text, etc.).
     */
    var context: Int?
        get() = prebidContentObject.context
        set(value) {
            prebidContentObject.context = value
        }

    /**
     * Content rating (e.g., MPAA).
     */
    var contentRating: String?
        get() = prebidContentObject.contentRating
        set(value) {
            prebidContentObject.contentRating = value
        }

    /**
     * User rating of the content (e.g., number of stars, likes, etc.).
     */
    var userRating: String?
        get() = prebidContentObject.userRating
        set(value) {
            prebidContentObject.userRating = value
        }

    /**
     * Media rating per IQG guidelines.
     */
    var qaMediaRating: Int?
        get() = prebidContentObject.qaMediaRating
        set(value) {
            prebidContentObject.qaMediaRating = value
        }

    /**
     * Comma separated list of keywords describing the content.
     */
    var keywords: String?
        get() = prebidContentObject.keywords
        set(value) {
            prebidContentObject.keywords = value
        }

    /**
     * Live stream. 0 = not live, 1 = content is live (e.g., stream, live blog).
     */
    var liveStream: Int?
        get() = prebidContentObject.liveStream
        set(value) {
            prebidContentObject.liveStream = value
        }

    /**
     * Source relationship. 0 = indirect, 1 = direct.
     */
    var sourceRelationship: Int?
        get() = prebidContentObject.sourceRelationship
        set(value) {
            prebidContentObject.sourceRelationship = value
        }

    /**
     * Length of content in seconds; appropriate for video or audio.
     */
    var length: Int?
        get() = prebidContentObject.length
        set(value) {
            prebidContentObject.length = value
        }

    /**
     * Content language using ISO-639-1-alpha-2.
     */
    var language: String?
        get() = prebidContentObject.language
        set(value) {
            prebidContentObject.language = value
        }

    /**
     * Indicator of whether or not the content is embeddable (e.g., an embeddable video player),
     * where 0 = no, 1 = yes.
     */
    var embeddable: Int?
        get() = prebidContentObject.embeddable
        set(value) {
            prebidContentObject.embeddable = value
        }

    /**
     * This object defines the producer of the content in which the ad will be shown.
     */
    var producer: AudienzzProducerObject?
        get() = prebidContentObject.producer?.let { AudienzzProducerObject(it) }
        set(value) {
            prebidContentObject.producer = value?.prebidProducerObject
        }

    constructor() : this(ContentObject())

    /**
     * @return JSONObject if at least one parameter was set; otherwise null.
     */
    fun getJsonObject(): JSONObject = prebidContentObject.jsonObject

    fun addData(dataObject: AudienzzDataObject) {
        prebidContentObject.addData(dataObject.prebidDataObject)
    }

    fun getDataList(): List<AudienzzDataObject> =
        prebidContentObject.dataList.map { AudienzzDataObject(it) }

    fun setDataList(dataObjects: List<AudienzzDataObject>) {
        prebidContentObject.dataList = arrayListOf<DataObject>().apply {
            addAll(dataObjects.map { it.prebidDataObject })
        }
    }

    fun clearDataList() {
        prebidContentObject.clearDataList()
    }

    class AudienzzProducerObject internal constructor(
        internal val prebidProducerObject: ContentObject.ProducerObject,
    ) {

        /**
         * Content producer or originator ID.
         */
        var id: String?
            get() = prebidProducerObject.id
            set(value) {
                prebidProducerObject.id = value
            }

        /**
         * Content producer or originator name (e.g., “Warner Bros”).
         */
        var name: String?
            get() = prebidProducerObject.name
            set(value) {
                prebidProducerObject.name = value
            }

        /**
         * Highest level domain of the content producer (e.g., “producer.com”).
         */
        var domain: String?
            get() = prebidProducerObject.name
            set(value) {
                prebidProducerObject.name = value
            }

        constructor() : this(ContentObject.ProducerObject())

        fun getJsonObject(): JSONObject? = prebidProducerObject.jsonObject

        fun addCategory(category: String) = prebidProducerObject.addCategory(category)

        fun getCategories(): List<String> = prebidProducerObject.categories

        fun setCategories(categories: ArrayList<String>) {
            prebidProducerObject.setCategories(categories)
        }
    }
}
