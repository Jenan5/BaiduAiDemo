/*
 * Copyright (C) 2017 Baidu, Inc. All Rights Reserved.
 */
package com.baidu.aip.face.turnstile;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.baidu.aip.FaceSDKManager;
import com.baidu.idl.facesdk.FaceSDK;

import org.xinkb.face.R;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private Button registerButton;
    private Button detectedButton;
    private Button rtsp_button;
    private Button rtsp_button2;
    private TextView sdkVeresion;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        registerButton = (Button) findViewById(R.id.register_button);
        detectedButton = (Button) findViewById(R.id.detect_button);
        rtsp_button = findViewById(R.id.rtsp_button);
        rtsp_button2 = findViewById(R.id.rtsp_button2);
        sdkVeresion = findViewById(R.id.sdk_version);
        addListener();
        sdkVeresion.setText("人脸识别版本号："+FaceSDK.getVersion());
    }

    private void addListener() {
        registerButton.setOnClickListener(this);
        detectedButton.setOnClickListener(this);
        rtsp_button.setOnClickListener(this);
        rtsp_button2.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {

        if (registerButton == v) {
            Intent intent = new Intent(MainActivity.this, RegisterActivity.class);
            startActivity(intent);
        } else if (detectedButton == v) {
            Intent intent = new Intent(MainActivity.this, DetectActivity.class);
            startActivity(intent);
        } else if(rtsp_button == v){
            //单人检测
            Toast.makeText(this, "如果需要使用网络摄像头，请开启facesdk->build.gradle->externalNativeBuild编译，"
                    + "会生成rstp.so,使用rtsp接受网络视频流，使用ffmpge解码视频帧", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(MainActivity.this, RtspTestActivity.class);
            startActivity(intent);
        }else{
            //多人检测
            Toast.makeText(this, "如果需要使用网络摄像头，请开启facesdk->build.gradle->externalNativeBuild编译，"
                    + "会生成rstp.so,使用rtsp接受网络视频流，使用ffmpge解码视频帧", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(MainActivity.this, RtspTestActivity2.class);
            startActivity(intent);
        }
    }
}
