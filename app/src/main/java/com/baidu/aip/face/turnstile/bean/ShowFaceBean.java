package com.baidu.aip.face.turnstile.bean;

import android.graphics.Bitmap;

public class ShowFaceBean{

    public ShowFaceBean(VerifyResultBean.User user, Bitmap bitmap, long addTime) {
        this.user = user;
        this.bitmap = bitmap;
        this.addTime = addTime;
    }

    private VerifyResultBean.User user;
    private Bitmap bitmap;
    private long addTime;//添加的时间

    public long getAddTime() {
        return addTime;
    }

    public void setAddTime(long addTime) {
        this.addTime = addTime;
    }

    public VerifyResultBean.User getUser() {
        return user;
    }

    public void setUser(VerifyResultBean.User user) {
        this.user = user;
    }

    public Bitmap getBitmap() {
        return bitmap;
    }

    public void setBitmap(Bitmap bitmap) {
        this.bitmap = bitmap;
    }

}
