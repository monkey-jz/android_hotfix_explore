package com.jz.hotfixexplore;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import java.util.ArrayList;

/**
 * @author: JerryZhu
 * @datetime: 2023/4/25
 */
public class MainActivity extends Activity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        requestPermission();
        initView();

    }

    private void initView() {
        findViewById(R.id.btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(MainActivity.this, TestClass.getTestString(), Toast.LENGTH_SHORT).show();
            }
        });
        findViewById(R.id.btn_fixcode).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                HotfixUtils.fixClass(MainActivity.this,getClassLoader(),"/sdcard/fixed.jar");
            }
        });
        findViewById(R.id.btn_fixres).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ArrayList<Activity> activities = new ArrayList<>();
                activities.add(MainActivity.this);
                HotfixUtils.fixRes(MainActivity.this,"/sdcard/app-debug.apk",activities);
                ImageView imageView = (ImageView) findViewById(R.id.res);
                imageView.setImageResource(R.drawable.test);
            }
        });
        findViewById(R.id.btn_fixso).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                HotfixUtils.fixSo(MainActivity.this);
                String stringFromJNI = HotfixUtils.stringFromJNI();
                Log.e(Constant.TAG,"stringFromJNI: " + stringFromJNI);
            }
        });
    }

    private void requestPermission() {
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                ArrayList<String> requestPermissions = new ArrayList<>();
                int hasSdcardRead = checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE);
                if (hasSdcardRead != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions.add(android.Manifest.permission.READ_EXTERNAL_STORAGE);
                }
                int hasSdcardWrite = checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
                if (hasSdcardWrite != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
                }
                if (!requestPermissions.isEmpty()) {
                    String[] requestArray = new String[requestPermissions.size()];
                    for (int i = 0; i < requestArray.length; i++) {
                        requestArray[i] = requestPermissions.get(i);
                    }
                    ActivityCompat.requestPermissions(this, requestArray, 100);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
