/*
 * Copyright (C) 2017 Baidu, Inc. All Rights Reserved.
 */
package com.baidu.aip.face;

import java.util.ArrayList;

import com.baidu.aip.FaceEnvironment;
import com.baidu.aip.FaceSDKManager;
import com.baidu.aip.ImageFrame;
import com.baidu.aip.face.stat.Ast;
import com.baidu.idl.facesdk.BDFaceUtils;
import com.baidu.idl.facesdk.FaceInfo;
import com.baidu.idl.facesdk.FaceSDK;
import com.baidu.idl.facesdk.FaceTracker;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;

/**
 * 封装了人脸检测的整体逻辑。
 */
public class FaceDetectManager {
    private Context mContext;
    private int mDetectMax = 1;

    /**
     * 设置检测人脸最大数
     *
     * @param max
     */
    public void setDetectMax(int max) {
        this.mDetectMax = max;
    }

    /**
     * 该回调用于回调，人脸检测结果。当没有人脸时，infos 为null,status为 FaceDetector.DETECT_CODE_NO_FACE_DETECTED
     */
    public interface OnFaceDetectListener {
        void onDetectFace(int status, FaceInfo[] infos, ImageFrame imageFrame);
    }

    public FaceDetectManager(Context context) {
        mContext = context;
        Ast.getInstance().init(context.getApplicationContext(), "3.3.0.0", "faceturnstile");
    }

    /**
     * 图片源，获取检测图片。
     */
    private ImageSource imageSource;
    /**
     * 人脸检测事件监听器
     */
    private OnFaceDetectListener listener;
    private FaceFilter faceFilter = new FaceFilter();
    private HandlerThread processThread;
    private Handler processHandler;
    private Handler uiHandler;
    private ImageFrame lastFrame;
    private int mPreviewDegree = 90;

    private ArrayList<FaceProcessor> preProcessors = new ArrayList<>();

    /**
     * 设置人脸检测监听器，检测后的结果会回调。
     *
     * @param listener 监听器
     */
    public void setOnFaceDetectListener(OnFaceDetectListener listener) {
        this.listener = listener;
    }

    /**
     * 设置图片帧来源
     *
     * @param imageSource 图片来源
     */
    public void setImageSource(ImageSource imageSource) {
        this.imageSource = imageSource;
    }

    /**
     * @return 返回图片来源
     */
    public ImageSource getImageSource() {
        return this.imageSource;
    }

    /**
     * 增加处理回调，在人脸检测前会被回调。
     *
     * @param processor 图片帧处理回调
     */
    public void addPreProcessor(FaceProcessor processor) {
        preProcessors.add(processor);
    }

    /**
     * 设置人检跟踪回调。
     *
     * @param onTrackListener 人脸回调
     */
    public void setOnTrackListener(FaceFilter.OnTrackListener onTrackListener) {
        faceFilter.setOnTrackListener(onTrackListener);
    }

    /**
     * @return 返回过虑器
     */
    public FaceFilter getFaceFilter() {
        return faceFilter;
    }

    public void start() {
        LogUtil.init();
        this.imageSource.addOnFrameAvailableListener(onFrameAvailableListener);
        processThread = new HandlerThread("process");
        processThread.setPriority(10);
        processThread.start();
        processHandler = new Handler(processThread.getLooper());
        uiHandler = new Handler();
        this.imageSource.start();
    }

    private Runnable processRunnable = new Runnable() {
        @Override
        public void run() {
            if (lastFrame == null) {
                return;
            }
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
            int[] argb;
            int width;
            int height;
            ArgbPool pool;
            synchronized (lastFrame) {
                argb = lastFrame.getArgb();
                width = lastFrame.getWidth();
                height = lastFrame.getHeight();
                pool = lastFrame.getPool();
                lastFrame = null;
            }
            process(argb, width, height, pool);
        }
    };

    public void stop() {
        this.imageSource.stop();
        this.imageSource.removeOnFrameAvailableListener(onFrameAvailableListener);
        if (processThread != null) {
            processThread.quit();
            processThread = null;
        }
        Ast.getInstance().immediatelyUpload();
    }

    public void setPreviewDegree(int degree) {
        this.mPreviewDegree = degree;
    }


    private void process(int[] argb, int width, int height, ArgbPool pool) {
        int value=0;

        ImageFrame frame = imageSource.borrowImageFrame();
        frame.setArgb(argb);
        frame.setWidth(width);
        frame.setHeight(height);
        frame.setPool(pool);
        //        frame.retain();

        for (FaceProcessor processor : preProcessors) {
            if (processor.process(this, frame)) {
                break;
            }
        }
        if(mDetectMax==1){//单人脸
            value = FaceSDKManager.getInstance().getFaceTracker(mContext)
                    .prepare_max_face_data_for_verify(frame.getArgb(), frame.getHeight(), frame.getWidth(),
                            FaceSDK.ImgType.ARGB.ordinal(), FaceTracker.ActionType.RECOGNIZE.ordinal());
//         value = FaceSDKManager.getInstance().detect(frame.getArgb(), frame.getWidth(), frame.getHeight());
        }else if(mDetectMax>1){//多人脸
            FaceSDKManager.getInstance().getFaceTracker(mContext).track(frame.getArgb(),
                    frame.getHeight(), frame.getWidth(),
                    FaceSDK.ImgType.ARGB.ordinal(), mDetectMax);
        }

        FaceInfo[] faces = FaceSDKManager.getInstance().getFaceTracker(mContext).get_TrackedFaceInfo();

        if (value == 0) {
            faceFilter.filter(faces, frame);
        }
        if (listener != null) {
            listener.onDetectFace(value, faces, frame);
        }

        Ast.getInstance().faceHit("detect",  30 * 60 * 1000, faces);


        frame.release();

    }

    private OnFrameAvailableListener onFrameAvailableListener = new OnFrameAvailableListener() {
        @Override
        public void onFrameAvailable(ImageFrame imageFrame) {
            lastFrame = imageFrame;
            processHandler.removeCallbacks(processRunnable);
            processHandler.post(processRunnable);
//            processRunnable.run();
        }
    };
}