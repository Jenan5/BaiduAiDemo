package com.baidu.aip.face.turnstile.verifyFaces;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;


import com.baidu.aip.face.turnstile.bean.ShowFaceBean;

import org.xinkb.face.R;

import java.util.ArrayList;
import java.util.List;

public class FacesAdapter extends RecyclerView.Adapter<FacesAdapter.HVImg> {
    private Context context;
    private List<ShowFaceBean> userList;
    private List<String> userIds = new ArrayList<>();
    private List<String> unknownFaceToken = new ArrayList<>();
    private final Object lock = new Object();
    private Handler handler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if(msg.what == 1000){
                intervalRefresh();
            }
        }
    };

    public FacesAdapter(Context context, List<ShowFaceBean> userList) {
        this.context = context;
        this.userList = userList;
        updateUserIds();
        intervalRefresh();
    }

    @NonNull
    @Override
    public HVImg onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_user_face,null,false);
        return new HVImg(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HVImg hvImg, int i) {
        hvImg.userName.setText(Html.fromHtml(userList.get(i).getUser().getUser_info()));
        Bitmap bitmap = userList.get(i).getBitmap();
        if(bitmap!=null){
            Drawable drawable = new BitmapDrawable(context.getResources(),bitmap);
            hvImg.userFace.setImageDrawable(drawable);
        }
        bitmap = null;
//        if(bitmap!=null && !bitmap.isRecycled()){
//            bitmap.recycle();
//            bitmap=null;
//        }
    }

    @Override
    public int getItemCount() {
        return userList.size();
    }

    public class HVImg extends RecyclerView.ViewHolder{
        public ImageView userFace;
        public TextView userName;
        public HVImg(@NonNull View itemView) {
            super(itemView);
            userFace = itemView.findViewById(R.id.ig_user_face);
            userName = itemView.findViewById(R.id.tv_user_name);
        }
    }

    /**
     * 添加数据
     * @param data
     */
    public void addData(ShowFaceBean data){
        //已经识别的，则替换原来的，如果一直没有替换，5秒后删除，最多识别出5个人
        synchronized (lock){
            //未知用户，去重
            if(data.getUser().getUser_info().equals("未知")){
                int index2 = -1;
                for(int i = 0; i< unknownFaceToken.size(); i++){
                    if(data.getUser().getFaceToken().equals(unknownFaceToken.get(i))){
                        index2 = i;
                        break;
                    }
                }
                if(index2>-1 && index2 < userList.size()){
                    userList.remove(index2);
                }
            }else{//已经识别用户，去重
                int index = -1;
                for(int i=0;i<userIds.size();i++){
                    if(data.getUser().getUser_id().equals(userIds.get(i))){
                        index = i;
                        break;
                    }
                }
                if(index>-1 && index < userList.size()){
                    userList.remove(index);
                }
            }
            userList.add(data);
            updateUserIds();
            notifyDataSetChanged();
        }
    }

    private void updateUserIds(){
        if(!userList.isEmpty()){
            userIds.clear();
            for(ShowFaceBean bean:userList){
                unknownFaceToken.add(bean.getUser().getFaceToken());
                userIds.add(bean.getUser().getUser_id());
            }
        }
    }

    /**
     * 每隔5秒钟刷新一下数据
     */
    private void intervalRefresh(){
        synchronized (lock){
            List<ShowFaceBean> result = new ArrayList<>();
            if(!userList.isEmpty()){
                for(ShowFaceBean bean:userList){
                    if(bean.getAddTime()>0 && System.currentTimeMillis() - bean.getAddTime()<3000){
                        result.add(bean);
                    }
                }
            }

            userList.clear();
            userList.addAll(result);
            updateUserIds();
            result.clear();
            notifyDataSetChanged();
            Message msg = Message.obtain();
            msg.what = 1000;
            handler.sendMessageDelayed(msg,5000);
        }
    }
}
