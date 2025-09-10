package org.audienzz.mobile.testapp.adapter

import android.app.AlertDialog
import android.content.Context
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.gms.ads.LoadAdError
import org.audienzz.mobile.AudienzzResultCode
import org.audienzz.mobile.testapp.AdPreferences
import org.audienzz.mobile.testapp.R
import org.audienzz.mobile.testapp.interfaces.Bindable
import org.audienzz.mobile.testapp.view.SingleAdActivity

abstract class BaseAdHolder(parent: ViewGroup) : Bindable, RecyclerView.ViewHolder(
    LayoutInflater.from(parent.context)
        .inflate(R.layout.layout_ads_container, parent, false),
) {

    protected val adContainer: LinearLayout = itemView.findViewById(R.id.adContainer)

    @get:StringRes protected abstract val titleRes: Int

    protected abstract fun createAds()

    override fun onBind(position: Int) {
        adContainer.removeAllViews()
        addTitle()
        addTestControls()
        createAds()
    }

    private fun addTestControls() {
        val linearLayout = LinearLayout(adContainer.context)
        adContainer.addView(linearLayout)
        addOpenInNewActivityButton(linearLayout)
        addShowAdCheckbox(linearLayout)
    }

    private fun addShowAdCheckbox(parent: ViewGroup) {
        val checkbox = CheckBox(adContainer.context).apply {
            text = adContainer.resources.getText(R.string.show_ad_checkbox)
        }
        parent.addView(checkbox)
        checkbox.isChecked = AdPreferences.isAdEnabled(adContainer.context, itemViewType)
        checkbox.setOnCheckedChangeListener { _, isChecked ->
            AdPreferences.setAdEnabled(adContainer.context, itemViewType, isChecked)
        }
    }

    private fun addOpenInNewActivityButton(parent: ViewGroup) {
        createButton(R.string.open_in_new_activity, parent = parent).apply {
            isEnabled = true
            setOnClickListener {
                context.startActivity(SingleAdActivity.newIntent(context, itemViewType))
            }
            (layoutParams as LinearLayout.LayoutParams).weight = 1f
        }
    }

    open fun onAttach() {
        // empty
    }

    open fun onDetach() {
        // empty
    }

    protected fun addBottomMargin(view: View?) {
        val pxMargin = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            MARGIN_SIZE,
            adContainer.resources.displayMetrics,
        ).toInt()
        (view?.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin = pxMargin
    }

    protected fun createButton(@StringRes stringRes: Int, parent: ViewGroup = adContainer): Button {
        val button = Button(adContainer.context).apply {
            text = adContainer.resources.getText(stringRes)
            isEnabled = false
            gravity = Gravity.CENTER
        }
        parent.addView(button)
        addBottomMargin(button)
        return button
    }

    protected fun downloadImage(url: String, imageView: ImageView) {
        Glide.with(imageView.context.applicationContext)
            .load(url)
            .into(imageView)
    }

    private fun addTitle() {
        val title = TextView(adContainer.context).apply {
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SIZE)
            text = adContainer.resources.getText(titleRes)
        }
        adContainer.addView(title)
    }

    protected fun showErrorDialog(context: Context, message: String) {
        AlertDialog.Builder(context)
            .setMessage(message)
            .setTitle(context.resources.getString(titleRes))
            .setPositiveButton(context.resources.getString(R.string.error_close)) { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }

    protected fun showFetchErrorDialog(context: Context, resultCode: AudienzzResultCode?) {
        if (resultCode != AudienzzResultCode.SUCCESS) {
            val message = adContainer.resources.getString(
                R.string.error_message_fetch_code,
                resultCode?.name,
            )
            showErrorDialog(context, message)
        }
    }

    protected fun showAdLoadingErrorDialog(context: Context, throwable: LoadAdError) {
        val message = adContainer.resources.getString(
            R.string.error_message_load,
            throwable.message,
        )
        showErrorDialog(context, message)
    }

    protected fun logFindCreativeSizeError(errorCode: Int) {
        val message = adContainer.resources.getString(
            R.string.error_message_find_prebid_creative_size,
            errorCode,
        )
        Log.w(TAG, message)
    }

    companion object {
        private const val TAG = "AdHolder"
        private const val MARGIN_SIZE = 12f
        private const val TEXT_SIZE = 24f
        const val DEFAULT_REFRESH_TIME = 60
    }
}
