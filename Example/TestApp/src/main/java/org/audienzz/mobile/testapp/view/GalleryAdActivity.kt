package org.audienzz.mobile.testapp.view

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.gms.ads.LoadAdError
import org.audienzz.mobile.AudienzzGalleryAdSlot
import org.audienzz.mobile.AudienzzGalleryAds
import org.audienzz.mobile.testapp.App

/**
 * An ePaper: one page per printed page, swiped horizontally, with ad pages 5 and 13.
 * [AudienzzGalleryAds] requests each ad when the reader is within the ad config's
 * `config.preloadDistancePages` (2 by default) of it — not when the screen opens, not when the page
 * is bound. Tap a printed page to open its article on top, then go back: the ad is kept, not
 * reloaded. Follow it with diagnostics: `AUDZ gallery preload …`.
 */
class GalleryAdActivity : AppCompatActivity() {

    private val slots = listOf(
        AudienzzGalleryAdSlot("epaper-1", CONFIG_ID, pageIndex = 4),
        AudienzzGalleryAdSlot("epaper-2", CONFIG_ID, pageIndex = 12),
    )
    private lateinit var galleryAds: AudienzzGalleryAds
    private lateinit var status: TextView
    private lateinit var pager: ViewPager2
    private val slotStatus = mutableMapOf<String, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "ePaper (gallery ads)"
        status = TextView(this).apply { setPadding(24, 16, 24, 16) }
        pager = ViewPager2(this)
        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(status)
                addView(pager, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            },
        )

        galleryAds = AudienzzGalleryAds(this, slots, screen = this)
        galleryAds.listener = object : AudienzzGalleryAds.Listener {
            override fun onAdViewCreated(slotKey: String) {
                setStatus(slotKey, "requested")
                // Its page may already be bound (preload distance 0): bind it now.
                slots.firstOrNull { it.slotKey == slotKey }?.let { pager.adapter?.notifyItemChanged(it.pageIndex) }
            }

            override fun onAdLoaded(slotKey: String) = setStatus(slotKey, "loaded")

            override fun onAdFailedToLoad(slotKey: String, error: LoadAdError?) =
                setStatus(slotKey, "failed ${error?.code ?: "(no config)"}")
        }
        pager.adapter = PagesAdapter()
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                galleryAds.updatePosition(position + positionOffset)
            }

            override fun onPageSelected(position: Int) = renderStatus(position)
        })
        renderStatus(0)
    }

    override fun onResume() {
        super.onResume()
        App.reportScreenIfChanged(this)
    }

    override fun onDestroy() {
        galleryAds.destroy()
        super.onDestroy()
    }

    private fun setStatus(slotKey: String, value: String) {
        slotStatus[slotKey] = value
        renderStatus(pager.currentItem)
    }

    private fun renderStatus(page: Int) {
        status.text = "Page ${page + 1}/$PAGE_COUNT · ads on pages 5 and 13 · " +
            slots.joinToString(" · ") { "${it.slotKey}: ${slotStatus[it.slotKey] ?: "not requested"}" }
    }

    private inner class PagesAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = PAGE_COUNT

        override fun getItemViewType(position: Int) =
            if (slots.any { it.pageIndex == position }) TYPE_AD else TYPE_PAGE

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val view: View = if (viewType == TYPE_AD) {
                FrameLayout(parent.context)
            } else {
                TextView(parent.context).apply {
                    gravity = Gravity.CENTER
                    textSize = 22f
                    setBackgroundColor(Color.rgb(238, 238, 238))
                }
            }
            view.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            return object : RecyclerView.ViewHolder(view) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val slot = slots.firstOrNull { it.pageIndex == position }
            if (slot != null) {
                val container = holder.itemView as FrameLayout
                if (!galleryAds.bind(slot.slotKey, container)) {
                    container.removeAllViews()
                    container.addView(
                        TextView(container.context).apply { text = "Ad not loaded yet"; gravity = Gravity.CENTER },
                        FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
                    )
                } else {
                    // Drop the placeholder, keep the ad.
                    for (i in container.childCount - 1 downTo 0) {
                        val child = container.getChildAt(i)
                        if (child !== galleryAds.adView(slot.slotKey)) container.removeView(child)
                    }
                }
            } else {
                (holder.itemView as TextView).apply {
                    text = "Printed page ${position + 1}\n(tap for the article)"
                    setOnClickListener {
                        startActivity(
                            Intent(this@GalleryAdActivity, GalleryArticleActivity::class.java)
                                .putExtra(GalleryArticleActivity.EXTRA_PAGE, position + 1),
                        )
                    }
                }
            }
        }
    }

    companion object {
        private const val CONFIG_ID = "46"
        private const val PAGE_COUNT = 16
        private const val TYPE_AD = 1
        private const val TYPE_PAGE = 2
    }
}

/** The mobile article view opened from a printed page: its own page, ad-free. */
class GalleryArticleActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Article from page ${intent.getIntExtra(EXTRA_PAGE, 0)}"
        setContentView(
            TextView(this).apply {
                setPadding(32, 32, 32, 32)
                text = "Go back: the ePaper ad you saw is still there, no new request."
            },
        )
    }

    override fun onResume() {
        super.onResume()
        App.reportScreenIfChanged(this)
    }

    companion object {
        const val EXTRA_PAGE = "page"
    }
}
