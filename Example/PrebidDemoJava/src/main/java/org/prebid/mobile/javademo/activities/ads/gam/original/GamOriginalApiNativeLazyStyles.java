package org.prebid.mobile.javademo.activities.ads.gam.original;

import android.os.Bundle;

import org.prebid.mobile.javademo.R;
import org.prebid.mobile.javademo.databinding.ActivityLazyBinding;
import org.prebid.mobile.javademo.view.adapter.NativeLazyAdapter;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;

public class GamOriginalApiNativeLazyStyles extends AppCompatActivity {

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActivityLazyBinding binding = DataBindingUtil.setContentView(this, R.layout.activity_lazy);
        NativeLazyAdapter adapter = new NativeLazyAdapter();
        binding.list.setAdapter(adapter);

        adapter.submitList(createMockData());
    }

    private List<String> createMockData() {
        List<String> list = new ArrayList<>();

        for (int i = 0; i < 200; i++) {
            list.add(String.valueOf(i));
        }

        return list;
    }
}
