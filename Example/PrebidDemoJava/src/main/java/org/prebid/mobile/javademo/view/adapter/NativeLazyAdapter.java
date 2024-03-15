package org.prebid.mobile.javademo.view.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.admanager.AdManagerAdRequest;
import com.google.android.gms.ads.admanager.AdManagerAdView;

import org.prebid.mobile.NativeAdUnit;
import org.prebid.mobile.NativeDataAsset;
import org.prebid.mobile.NativeEventTracker;
import org.prebid.mobile.NativeImageAsset;
import org.prebid.mobile.NativeTitleAsset;
import org.prebid.mobile.javademo.R;
import org.prebid.mobile.javademo.utils.Settings;

import java.security.InvalidParameterException;
import java.util.ArrayList;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

public class NativeLazyAdapter extends ListAdapter<String, RecyclerView.ViewHolder> {

    public NativeLazyAdapter() {
        super(new DiffUtil.ItemCallback<>() {
            @Override
            public boolean areItemsTheSame(@NonNull String oldItem, @NonNull String newItem) {
                return oldItem.equals(newItem);
            }

            @Override
            public boolean areContentsTheSame(@NonNull String oldItem, @NonNull String newItem) {
                return oldItem.equals(newItem);
            }
        });
    }

    @Override
    public int getItemViewType(int position) {
        return position > 0 && position % 40 == 0
               ? NativeAdHolder.HOLDER_TYPE
               : DefaultHolder.HOLDER_TYPE;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return switch (viewType) {
            case DefaultHolder.HOLDER_TYPE -> new DefaultHolder(parent);
            case NativeAdHolder.HOLDER_TYPE -> new NativeAdHolder(parent);
            default -> throw new InvalidParameterException();
        };
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof DefaultHolder) {
            ((DefaultHolder) holder).onBind(position);
        } else if (holder instanceof NativeAdHolder) {
            ((NativeAdHolder) holder).onBind(position);
        }
    }

    @Override
    public void onViewAttachedToWindow(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewAttachedToWindow(holder);

        if (holder instanceof NativeAdHolder) {
            ((NativeAdHolder) holder).onAttach();
        }
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewDetachedFromWindow(holder);

        if (holder instanceof NativeAdHolder) {
            ((NativeAdHolder) holder).onDetach();
        }
    }

    private class DefaultHolder extends RecyclerView.ViewHolder {

        private static final int HOLDER_TYPE = 0;

        private final TextView tvTestCaseName = itemView.findViewById(R.id.tvTestCaseName);

        public DefaultHolder(@NonNull ViewGroup parent) {
            super(LayoutInflater.from(parent.getContext())
                                .inflate(R.layout.item_lazy, parent, false));
        }

        public void onBind(int position) {
            tvTestCaseName.setText("Simple text #" + position);
        }
    }

    private class NativeAdHolder extends RecyclerView.ViewHolder {

        private static final int HOLDER_TYPE = 1;

        /**
         * Code was copied from GamOriginalApiNativeStyles
         */
        private static final String AD_UNIT_ID = "/21808260008/prebid-demo-original-native-styles";

        /**
         * Code was copied from GamOriginalApiNativeStyles
         */
        private static final String CONFIG_ID = "prebid-demo-banner-native-styles";

        private static NativeAdUnit nativeAdUnit;

        private final FrameLayout adWrapperView = itemView.findViewById(R.id.frameAdWrapper);

        public NativeAdHolder(@NonNull ViewGroup parent) {
            super(LayoutInflater.from(parent.getContext())
                                .inflate(R.layout.item_native_lazy, parent, false));
            createAd();
        }

        /**
         * Code was copied from GamOriginalApiNativeStyles
         */
        private void createAd() {
            nativeAdUnit = new NativeAdUnit(CONFIG_ID);
            configureNativeAdUnit(nativeAdUnit);

            AdManagerAdView gamView = new AdManagerAdView(itemView.getContext());
            gamView.setAdUnitId(AD_UNIT_ID);
            gamView.setAdSizes(AdSize.FLUID);

            adWrapperView.addView(gamView);

            AdManagerAdRequest.Builder builder = new AdManagerAdRequest.Builder();

            nativeAdUnit.setAutoRefreshInterval(Settings.get()
                                                        .getRefreshTimeSeconds());
            nativeAdUnit.fetchDemand(builder, resultCode -> {
                AdManagerAdRequest request = builder.build();
                gamView.loadAd(request);
            });
        }

        /**
         * Code was copied from GamOriginalApiNativeStyles
         */
        private void configureNativeAdUnit(NativeAdUnit adUnit) {
            adUnit.setContextType(NativeAdUnit.CONTEXT_TYPE.SOCIAL_CENTRIC);
            adUnit.setPlacementType(NativeAdUnit.PLACEMENTTYPE.CONTENT_FEED);
            adUnit.setContextSubType(NativeAdUnit.CONTEXTSUBTYPE.GENERAL_SOCIAL);
            ArrayList<NativeEventTracker.EVENT_TRACKING_METHOD> methods = new ArrayList<>();
            methods.add(NativeEventTracker.EVENT_TRACKING_METHOD.IMAGE);

            try {
                NativeEventTracker tracker =
                    new NativeEventTracker(NativeEventTracker.EVENT_TYPE.IMPRESSION, methods);
                adUnit.addEventTracker(tracker);
            } catch (Exception e) {
                e.printStackTrace();
            }
            NativeTitleAsset title = new NativeTitleAsset();
            title.setLength(90);
            title.setRequired(true);
            adUnit.addAsset(title);
            NativeImageAsset icon = new NativeImageAsset(20, 20, 20, 20);
            icon.setImageType(NativeImageAsset.IMAGE_TYPE.ICON);
            icon.setRequired(true);
            adUnit.addAsset(icon);
            NativeImageAsset image = new NativeImageAsset(200, 200, 200, 200);
            image.setImageType(NativeImageAsset.IMAGE_TYPE.MAIN);
            image.setRequired(true);
            adUnit.addAsset(image);
            NativeDataAsset data = new NativeDataAsset();
            data.setLen(90);
            data.setDataType(NativeDataAsset.DATA_TYPE.SPONSORED);
            data.setRequired(true);
            adUnit.addAsset(data);
            NativeDataAsset body = new NativeDataAsset();
            body.setRequired(true);
            body.setDataType(NativeDataAsset.DATA_TYPE.DESC);
            adUnit.addAsset(body);
            NativeDataAsset cta = new NativeDataAsset();
            cta.setRequired(true);
            cta.setDataType(NativeDataAsset.DATA_TYPE.CTATEXT);
            adUnit.addAsset(cta);
        }

        public void onAttach() {
            nativeAdUnit.setAutoRefreshInterval(Settings.get()
                                                        .getRefreshTimeSeconds());
        }

        public void onDetach() {
            nativeAdUnit.stopAutoRefresh();
        }

        public void onBind(int position) {
            // none
        }
    }
}
