package com.simoncherry.artest.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;

import com.simoncherry.artest.R;
import com.simoncherry.artest.ui.fragment.ShowMaskFragment;

public class ShowMaskActivity extends AppCompatActivity {

    public final static String IMG_KEY = "img_key";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_show_mask);

        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras != null) {
            String imgPath = extras.getString(ShowMaskFragment.IMG_KEY, null);
            if (imgPath != null) {
                Fragment fragment = ShowMaskFragment.newInstance(imgPath);
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.layout_container, fragment)
                        .commit();
            } else {
                throw new RuntimeException();
            }
        } else {
            throw new RuntimeException();
        }
    }
}
