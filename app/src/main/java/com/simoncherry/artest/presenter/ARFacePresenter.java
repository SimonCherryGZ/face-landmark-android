package com.simoncherry.artest.presenter;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import com.simoncherry.artest.contract.ARFaceContract;
import com.simoncherry.artest.model.ImageBean;
import com.simoncherry.artest.util.BitmapUtils;
import com.simoncherry.artest.util.FileUtils;
import com.simoncherry.artest.util.OBJUtils;
import com.simoncherry.artest.util.ViewUtils;
import com.simoncherry.dlib.VisionDetRet;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.List;

import io.reactivex.Flowable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.annotations.NonNull;
import io.reactivex.functions.Predicate;
import io.reactivex.schedulers.Schedulers;
import io.realm.Realm;
import io.realm.RealmList;

/**
 * Created by Simon on 2017/6/23.
 */

public class ARFacePresenter implements ARFaceContract.Presenter {
    private final static String TAG = ARFacePresenter.class.getSimpleName();

    private Context mContext;
    private ARFaceContract.View mView;

    public ARFacePresenter(Context mContext, ARFaceContract.View mView) {
        this.mContext = mContext;
        this.mView = mView;
    }

    @Override
    public void resetFaceTexture() {
        String modelDir = OBJUtils.getModelDir();
        String texturePath = FileUtils.getMD5(modelDir + OBJUtils.IMG_TEXTURE) + ".jpg";
        String facePath = FileUtils.getMD5(modelDir + OBJUtils.IMG_FACE) + ".jpg";
        FileUtils.copyFile(
                modelDir + texturePath,
                modelDir + facePath);
    }

    @Override
    public void swapFace(String swapPath) {
        String modelDir = OBJUtils.getModelDir();
        String[] pathArray = new String[2];
        pathArray[0] = swapPath;
        pathArray[1] = modelDir + OBJUtils.IMG_TEXTURE;
        String texture = modelDir + OBJUtils.IMG_FACE;
        OBJUtils.swapFace(mContext, pathArray, texture);
    }

    @Override
    public void startFaceScanTask(RealmList<ImageBean> data) {
        Flowable.fromIterable(data)
                .filter(new Predicate<ImageBean>() {
                    @Override
                    public boolean test(@NonNull ImageBean imageBean) throws Exception {
                        return imageBean != null && imageBean.isNotNull();
                    }
                })
                .filter(new Predicate<ImageBean>() {  // 有人脸的表中不存在
                    @Override
                    public boolean test(@NonNull ImageBean imageBean) throws Exception {
                        Realm realm = Realm.getDefaultInstance();
                        boolean isNotExist = realm.where(ImageBean.class).equalTo("id", imageBean.getId()).findFirst() == null;
                        realm.close();
                        if (!isNotExist) {
                            Log.e(TAG, imageBean.getId() + " 此图片已检测过存在人脸，跳过");
                        }
                        return isNotExist;
                    }
                })
                .filter(new Predicate<ImageBean>() {  // 没有人脸的表中不存在
                    @Override
                    public boolean test(@NonNull ImageBean imageBean) throws Exception {
                        Realm realm = Realm.getDefaultInstance();
                        boolean isNotExist = realm.where(ImageBean.class).equalTo("id", imageBean.getId()).findFirst() == null;
                        realm.close();
                        if (!isNotExist) {
                            Log.e(TAG, imageBean.getId() + " 此图片已检测过不存在人脸，跳过");
                        }
                        return isNotExist;
                    }
                })
                .filter(new Predicate<ImageBean>() {
                    @Override
                    public boolean test(@NonNull ImageBean imageBean) throws Exception {
                        String path = imageBean.getPath();
                        int screenWidth = ViewUtils.getScreenWidth(mContext);
                        Bitmap bitmap = BitmapUtils.getRequireWidthBitmap(BitmapUtils.getEvenWidthBitmap(path), screenWidth);
                        if (bitmap != null) {
                            final List<VisionDetRet> faceList = OBJUtils.getFaceDet().detect(bitmap);
                            if (faceList != null && faceList.size() > 0) {
                                Log.e(TAG, path + " - has face");
                                OBJUtils.saveLandmarkTxt(faceList.get(0).getFaceLandmarks(), imageBean.getPath());
                                return true;
                            } else {
                                saveImageToRealm(imageBean, false);
                            }
                        }

                        Log.e(TAG, path + " - no face");
                        return false;
                    }
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<ImageBean>() {
                    @Override
                    public void onSubscribe(Subscription s) {
                        Log.e(TAG, "onSubscribe");
                        mView.onSubscribe(s);
                    }

                    @Override
                    public void onNext(ImageBean imageBean) {
                        Log.e(TAG, imageBean.toString());
                        saveImageToRealm(imageBean, true);
                    }

                    @Override
                    public void onError(Throwable t) {
                        Log.e(TAG, t.toString());
                    }

                    @Override
                    public void onComplete() {
                        Log.e(TAG, "onComplete");
                    }
                });
    }

    private void saveImageToRealm(final ImageBean imageBean, final boolean hasFace) {
        Realm realm = Realm.getDefaultInstance();
        realm.executeTransaction(new Realm.Transaction() {
            @Override
            public void execute(Realm realm) {
                ImageBean skipBean = new ImageBean();
                skipBean.setId(imageBean.getId());
                skipBean.setPath(imageBean.getPath());
                skipBean.setName(imageBean.getName());
                skipBean.setDate(imageBean.getDate());
                skipBean.setHasFace(hasFace);
                realm.copyToRealmOrUpdate(skipBean);
            }
        });
        realm.close();
    }
}
