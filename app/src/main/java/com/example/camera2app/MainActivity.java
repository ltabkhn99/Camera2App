package com.example.camera2app;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentTransaction;

import android.content.Context;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.TextView;

import com.example.camera2app.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {
    private static ActivityMainBinding mActivityMainBinding;
    private PhotoFragment mPhotoFragment;
    private VideoFragment mVideoFragment;
    private Camera2BasicFragment mCamera2BasicFragment;
    private FragmentTransaction mFragmentTransaction;
    private TextView btnVideoMode;
    private TextView btnPhotoMode;
    private ImageButton btnSetting;
    static boolean openApp = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mActivityMainBinding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(mActivityMainBinding.getRoot());

        initView();

        showPhotoFragment();

        setOnClickListener();
    }

    private void setOnClickListener() {
        btnVideoMode.setOnClickListener(v -> {
            showVideoFragment();
            btnVideoMode.setTextColor(getResources().getColor(R.color.yellow));
            btnPhotoMode.setTextColor(getResources().getColor(R.color.white));
        });

        btnPhotoMode.setOnClickListener(v -> {
            showPhotoFragment();
            btnPhotoMode.setTextColor(getResources().getColor(R.color.yellow));
            btnVideoMode.setTextColor(getResources().getColor(R.color.white));
        });

        btnSetting.setOnClickListener(v -> {
            Animation animZoomIn = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.zoom_in);
            btnSetting.startAnimation(animZoomIn);
        });
    }

    private void initView() {
        btnVideoMode = mActivityMainBinding.btnVideoMode;
        btnPhotoMode = mActivityMainBinding.btnPhotoMode;
        btnSetting = mActivityMainBinding.settingButton;
    }

    private void showPhotoFragment() {
        mFragmentTransaction = getSupportFragmentManager().beginTransaction();
        if (mPhotoFragment == null) {
            mPhotoFragment = new PhotoFragment();
        }
        mFragmentTransaction.replace(mActivityMainBinding.containerFragment.getId(), mPhotoFragment).addToBackStack(null).commit();
    }

    private void showVideoFragment() {
        mFragmentTransaction = getSupportFragmentManager().beginTransaction();
        if (mVideoFragment == null) {
            mVideoFragment = new VideoFragment();
        }
        mFragmentTransaction.replace(mActivityMainBinding.containerFragment.getId(), mVideoFragment).addToBackStack(null).commit();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mActivityMainBinding = null;
    }

    public static ActivityMainBinding getBinding() {
        return mActivityMainBinding;
    }
}
