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
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.util.Log;
import android.view.TextureView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.baidu.aip.FaceEnvironment;
import com.baidu.aip.ImageFrame;
import com.baidu.aip.face.FaceCropper;
import com.baidu.aip.face.FaceDetectManager;
import com.baidu.aip.face.RtspImageSource;
import com.baidu.aip.face.TexturePreviewView;
import com.baidu.aip.face.turnstile.bean.ShowFaceBean;
import com.baidu.aip.face.turnstile.bean.VerifyResultBean;
import com.baidu.aip.face.turnstile.exception.FaceError;
import com.baidu.aip.face.turnstile.model.FaceModel;
import com.baidu.aip.face.turnstile.model.RegResult;
import com.baidu.aip.face.turnstile.utils.ImageUtil;
import com.baidu.aip.face.turnstile.utils.OnResultListener;
import com.baidu.aip.face.turnstile.verifyFaces.FacesAdapter;
import com.baidu.idl.facesdk.FaceInfo;
import com.google.gson.Gson;

import org.xinkb.face.R;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 该Activity 展示了，如果使用网络摄像头(RTSP协议)进行人脸识别。
 * 需要有网络摄像头支持。以及rtsp 的本地so库。--多人
 */
public class RtspTestActivity2 extends AppCompatActivity {

    private RtspImageSource rtspImageSource = new RtspImageSource();

    private TextView infoTv;
    private TexturePreviewView previewView;
    private TextureView textureView;
    private RecyclerView rlFacesList;
    private FacesAdapter facesAdapter;
    private List<ShowFaceBean> faceBeanList= new ArrayList<>();

    private FaceDetectManager faceDetectManager;
    private int detectCount=0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        faceDetectManager = new FaceDetectManager(getApplicationContext());
        //设置可以多人脸检测
        faceDetectManager.setDetectMax(FaceEnvironment.VALUE_MAX_CROP_IMAGE_NUM);
        setContentView(R.layout.activity_detected2);
        previewView = (TexturePreviewView) findViewById(R.id.preview_view);
        textureView = (TextureView) findViewById(R.id.texture_view);
        rlFacesList = findViewById(R.id.rl_faces_list);
        infoTv = findViewById(R.id.info_tv);
        LinearLayoutManager mLayoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        rlFacesList.setLayoutManager(mLayoutManager);
        facesAdapter = new FacesAdapter(this,faceBeanList);
        rlFacesList.setAdapter(facesAdapter);

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
                    uploadForMultiFaces(infos, frame.getArgb(), frame.getWidth());//多人脸识别

                }
                Log.d("face-detect","retCode:"+retCode);
                new Thread() {
                    @Override
                    public void run() {
                        super.run();
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if(detectCount>100){
                                    detectCount=0;
                                }
                                infoTv.setText("detect:"+(detectCount++));
                            }
                        });
                    }
                }.start();
                if (infos == null) {
                    changeShouldUpload(true);
                }
            }
        });

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
        }
    }

    private boolean shouldUpload = true;
    private final Object lock = new Object();
    private boolean getShouldUpload(){
        synchronized (lock){
            return shouldUpload;
        }
    }

    private void changeShouldUpload(boolean value){
        synchronized (lock){
            shouldUpload = value;
        }
    }

    /**
     * 多人脸的检测，识别
     */
    private void uploadForMultiFaces(FaceInfo[] infos, int[] argb, int width) {
        if (infos != null) {
            Log.d("face-detect","shouldUpload:"+getShouldUpload());

            if(facesAdapter.getItemCount()==0){
                changeShouldUpload(true);
            }else if(facesAdapter.getItemCount()>6){
                changeShouldUpload(false);
            }

            if (!getShouldUpload()) {
                return;
            }
            changeShouldUpload(false);
            Toast.makeText(this, "检测到人脸数："+infos.length, Toast.LENGTH_SHORT).show();
            for(FaceInfo faceInfo:infos){
                // 截取人脸那部分图片。
                final Bitmap face = FaceCropper.getFace(argb, faceInfo, width);
                try {
                    File file = File.createTempFile(UUID.randomUUID().toString() + "", ".jpg");
                    // 压缩到200 * 200
                    ImageUtil.resize(face, file, 200, 200);
                    identify(file,face,false);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        } else {
            changeShouldUpload(true);
            Log.d("face-detect","infos is null:");
        }
    }

//    /**
//     * 多人脸的检测，识别
//     */
//    private void uploadForMultiFaces2(FaceInfo[] infos, int[] argb, int width,int height) {
//        if (infos != null) {
//            Log.d("face-detect","shouldUpload:"+shouldUpload);
//            if (!shouldUpload) {
//                return;
//            }
//            shouldUpload = false;
//            Toast.makeText(this, "检测到人脸数："+infos.length, Toast.LENGTH_SHORT).show();
//            Bitmap face = null;
//            boolean isMultiFace = false;
//            int picWith = 200;
//            int picHeight = 200;
//            if(infos.length==1){
//                // 截取人脸那部分图片。
//                face = FaceCropper.getFace(argb, infos[0], width);
//                Log.d("face-detect","单脸检测");
//            }else if(infos.length>1){
//                face = FaceCropper.getFaces(argb,width,height);
//                isMultiFace = true;
//                picWith = 500;
//                picHeight = 500;
//                Log.d("face-detect","多脸检测");
//            }
//
//            try {
//                File file = File.createTempFile(UUID.randomUUID().toString() + "", ".jpg");
//                // 压缩到200 * 200
//                ImageUtil.resize(face, file, picWith, picHeight);
//                identify(file,face,isMultiFace);
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//
//        } else {
//            shouldUpload = true;
//            Log.d("face-detect","infos is null:");
//        }
//    }

    ExecutorService executorService = Executors.newCachedThreadPool();
    /**
     * 人脸识别
     */
    private void identify(final File file, final Bitmap bitmap, final boolean isMultiFace){
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                Log.d("face-detect","start identify ");
                APIService.getInstance().identify(new OnResultListener<RegResult>() {
                    @Override
                    public void onResult(RegResult result) {
                        if (result == null) {
                            return;
                        }
                        Log.d("face-detect","identify onResult json "+result.jsonRes);
                        VerifyResultBean.User user = handleVerifyResult(result.getJsonRes());
                        updateFaceList(user,bitmap);
                        changeShouldUpload(true);
                    }

                    @Override
                    public void onError(FaceError error) {
                        error.printStackTrace();
                        changeShouldUpload(true);
                        Log.d("face-detect","identify error");
                    }
                }, file,isMultiFace);
            }
        };
        executorService.execute(runnable);
    }

    /**
     * 处理识别结果
     * @param result
     * @return
     */
    private VerifyResultBean.User handleVerifyResult(String result){
        Gson gson = new Gson();
        try{
            VerifyResultBean verifyResult = gson.fromJson(result,VerifyResultBean.class);
            if(verifyResult!=null && verifyResult.getResult()!=null && !verifyResult.getResult().getUser_list().isEmpty()){
                VerifyResultBean.User user = verifyResult.getResult().getUser_list().get(0);
                user.setFaceToken(verifyResult.getResult().getFace_token());
                return user;
            }
        }catch (Exception e){
            return null;
        }
        return null;
    }

    /**
     * 更新显示结果
     */
    private synchronized void updateFaceList(VerifyResultBean.User user, Bitmap bitmap){
        if(user == null){
            return;
        }
        if(user.getScore()<80){
            user.setUser_info("未知");
        }
        ShowFaceBean bean = new ShowFaceBean(user,bitmap,System.currentTimeMillis());
        facesAdapter.addData(bean);
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
