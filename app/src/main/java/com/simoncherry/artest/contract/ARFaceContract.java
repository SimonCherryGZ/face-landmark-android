package com.simoncherry.artest.contract;

import com.simoncherry.artest.model.ImageBean;

import org.reactivestreams.Subscription;

import io.realm.RealmList;

/**
 * Created by Simon on 2017/6/23.
 */

public interface ARFaceContract {
    interface View {
        void onSubscribe(Subscription subscription);
    }

    interface Presenter {
        void resetFaceTexture();
        void swapFace(String swapPath);
        void startFaceScanTask(final RealmList<ImageBean> data);
    }
}
