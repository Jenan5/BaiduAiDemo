/*
 * Copyright (C) 2017 Baidu, Inc. All Rights Reserved.
 */
package com.baidu.aip.face.turnstile;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.view.TextureView;
import android.widget.ImageView;
import android.widget.TextView;

import com.baidu.aip.ImageFrame;
import com.baidu.aip.face.FaceCropper;
import com.baidu.aip.face.FaceDetectManager;
import com.baidu.aip.face.RtspImageSource;
import com.baidu.aip.face.TexturePreviewView;
import com.baidu.aip.face.turnstile.bean.VerifyResultBean;
import com.baidu.aip.face.turnstile.exception.FaceError;
import com.baidu.aip.face.turnstile.model.FaceModel;
import com.baidu.aip.face.turnstile.model.RegResult;
import com.baidu.aip.face.turnstile.utils.HttpUtil;
import com.baidu.aip.face.turnstile.utils.ImageUtil;
import com.baidu.aip.face.turnstile.utils.JsonHelper;
import com.baidu.aip.face.turnstile.utils.OnResultListener;
import com.baidu.idl.facesdk.FaceInfo;
import com.google.gson.Gson;

import org.json.JSONObject;
import org.xinkb.face.R;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.UUID;

/**
 * 该Activity 展示了，如果使用网络摄像头(RTSP协议)进行人脸识别。
 * 需要有网络摄像头支持。以及rtsp 的本地so库。
 */
public class RtspTestActivity extends AppCompatActivity {

    private RtspImageSource rtspImageSource = new RtspImageSource();

    private ImageView imageView;
    private TextView nameTextView;
    private TexturePreviewView previewView;
    private TextureView textureView;

    private FaceDetectManager faceDetectManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        faceDetectManager = new FaceDetectManager(getApplicationContext());
        setContentView(R.layout.activity_detected);
        imageView = findViewById(R.id.test_view);
        previewView = (TexturePreviewView) findViewById(R.id.preview_view);
        textureView = (TextureView) findViewById(R.id.texture_view);

        // rtsp 的图像不是镜面的
        previewView.setMirrored(false);
        // 设置预览View用于预览
        rtspImageSource.setPreviewView(previewView);

        // rtsp网络摄像头地址。具体格式见，摄像头说明书。
//        String url = String.format(Locale.ENGLISH,
//                "rtsp://admin:cb123456@192.168.1.64:554", "192.168.1.64");
        String url = "rtsp://admin:cb123456@192.168.1.64:554/MPEG-4/main/av_stream";
        rtspImageSource.setUrl(url);

        faceDetectManager.setImageSource(rtspImageSource);
        faceDetectManager.setOnFaceDetectListener(new FaceDetectManager.OnFaceDetectListener() {
            @Override
            public void onDetectFace(int retCode, FaceInfo[] infos, ImageFrame frame) {
                showFrame(frame.getArgb(), infos, frame.getWidth(), frame.getHeight());
                if (retCode == 0) {
                    upload(infos, frame.getArgb(), frame.getWidth());//单人脸识别
                }
                if (infos == null) {
                    shouldUpload = true;
                }
            }
        });

        nameTextView = (TextView) findViewById(R.id.name_text_view);
        textureView.setOpaque(false);
        textureView.setKeepScreenOn(true);
    }

    @Override
    protected void onStart() {
        super.onStart();
        faceDetectManager.start();
    }

    @Override
    protected void onStop() {
        super.onStop();
        faceDetectManager.stop();
    }

    private void showUserInfo(FaceModel model) {
        if (model != null) {
            String text = String.format(Locale.ENGLISH, "%s%.2f",
                    model.getUserInfo(), model.getScore());
            nameTextView.setText(text);
        }
    }

    private boolean shouldUpload = true;

    /**
     * 单个人脸的检测，识别
     */
    private void upload(FaceInfo[] infos, int[] argb, int width) {
        if (infos != null) {
            if (!shouldUpload) {
                return;
            }
            shouldUpload = false;
            // 截取人脸那部分图片。
            final Bitmap face = FaceCropper.getFace(argb, infos[0], width);
            new Thread() {
                @Override
                public void run() {
                    super.run();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            imageView.setImageBitmap(face);
                        }
                    });
                }
            }.start();

            try {
                File file = File.createTempFile(UUID.randomUUID().toString() + "", ".jpg");
                // 压缩到200 * 200
                ImageUtil.resize(face, file, 200, 200);
//                identify(file);
                identify2(file);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            shouldUpload = true;
        }
    }

    /**
     * 人脸识别
     */
    private void identify(File file){
        APIService.getInstance().identify(new OnResultListener<FaceModel>() {
            @Override
            public void onResult(FaceModel result) {
                if (result == null) {
                    return;
                }
                if (result.getScore() < 80) {
                    shouldUpload = true;
                }
                showUserInfo(result);
            }

            @Override
            public void onError(FaceError error) {
                error.printStackTrace();
                shouldUpload = true;
            }
        }, file,false);
    }

    /**
     * 人脸识别
     */
    private void identify2(File file){
        APIService.getInstance().identify(new OnResultListener<RegResult>() {
            @Override
            public void onResult(RegResult result) {
                if (result == null) {
                    return;
                }
                nameTextView.setText(handleVerifyResult(result.getJsonRes()));
            }

            @Override
            public void onError(FaceError error) {
                error.printStackTrace();
                shouldUpload = true;
            }
        }, file,false);
    }

    private String handleVerifyResult(String result){
        Gson gson = new Gson();
        try{
            VerifyResultBean verifyResult = gson.fromJson(result,VerifyResultBean.class);
            if(verifyResult!=null && verifyResult.getResult()!=null && !verifyResult.getResult().getUser_list().isEmpty()){
                StringBuilder stringBuilder = new StringBuilder();
                for(VerifyResultBean.User user:verifyResult.getResult().getUser_list()){
                    stringBuilder.append(Html.fromHtml(user.getUser_info())).append("; ");
                }
                return "识别结果："+stringBuilder;
            }
        }catch (Exception e){
            return e.getMessage();
        }
        return result;
    }

    private Paint paint = new Paint();

    {
        paint.setColor(Color.YELLOW);
        paint.setStyle(Paint.Style.STROKE);
    }

    RectF rectF = new RectF();

    private void showFrame(int[] argbc, FaceInfo[] infos, int iwidth, int iheight) {
        Canvas canvas = textureView.lockCanvas();
        if (canvas == null) {
            return;
        }
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        if (infos != null) {

            int[] points = new int[8];
            for (FaceInfo faceInfo : infos) {

                faceInfo.getRectPoints(points);

                int left = points[2];
                int top = points[3];
                int right = points[6];
                int bottom = points[7];
                //
                int width = right - left;
                int height = bottom - top;

                left = faceInfo.mCenter_x - width / 2;
                top = faceInfo.mCenter_y - height / 2;

                rectF.top = top;
                rectF.left = left;
                rectF.right = left + width;
                rectF.bottom = top + height;

                // 从原始图片坐标映射到显示坐标。
                previewView.mapFromOriginalRect(rectF);
                canvas.drawRect(rectF, paint);
            }
        }
        textureView.unlockCanvasAndPost(canvas);
    }
}
