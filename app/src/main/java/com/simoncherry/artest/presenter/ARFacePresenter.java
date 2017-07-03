package com.simoncherry.artest.presenter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;

import com.simoncherry.artest.R;
import com.simoncherry.artest.contract.ARFaceContract;
import com.simoncherry.artest.model.ImageBean;
import com.simoncherry.artest.model.Ornament;
import com.simoncherry.artest.util.BitmapUtils;
import com.simoncherry.artest.util.FileUtils;
import com.simoncherry.artest.util.OBJUtils;
import com.simoncherry.artest.util.ViewUtils;
import com.simoncherry.dlib.VisionDetRet;

import org.rajawali3d.animation.Animation3D;
import org.rajawali3d.animation.RotateOnAxisAnimation;
import org.rajawali3d.animation.ScaleAnimation3D;
import org.rajawali3d.math.vector.Vector3;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.ArrayList;
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

    public List<Ornament> getPresetOrnament() {
        List<Ornament> ornaments = new ArrayList<>();
        ornaments.add(getGlass());
        ornaments.add(getMoustache());
        ornaments.add(getCatEar());
        ornaments.add(getTigerNose());
        ornaments.add(getHeart());
        ornaments.add(getVMask());
        ornaments.add(getCatMask());
        return ornaments;
    }

    private Ornament getGlass() {
        Ornament ornament = new Ornament();
        ornament.setModelResId(R.raw.glasses_obj);
        ornament.setImgResId(R.drawable.ic_glasses);
        ornament.setScale(0.005f);
        ornament.setOffset(0, 0, 0.2f);
        ornament.setRotate(-90.0f, 90.0f, 90.0f);
        ornament.setColor(Color.BLACK);
        return ornament;
    }

    private Ornament getMoustache() {
        Ornament ornament = new Ornament();
        ornament.setModelResId(R.raw.moustache_obj);
        ornament.setImgResId(R.drawable.ic_moustache);
        ornament.setScale(0.15f);
        ornament.setOffset(0, -0.25f, 0.2f);
        ornament.setRotate(-90.0f, 90.0f, 90.0f);
        ornament.setColor(Color.BLACK);
        return ornament;
    }

    private Ornament getCatEar() {
        Ornament ornament = new Ornament();
        ornament.setModelResId(R.raw.cat_ear_obj);
        ornament.setImgResId(R.drawable.ic_cat);
        ornament.setScale(11.0f);
        ornament.setOffset(0, 0.6f, -0.2f);
        ornament.setRotate(0.0f, 0.0f, 0.0f);
        ornament.setColor(0xffe06666);

        List<Animation3D> animation3Ds = new ArrayList<>();
        Animation3D anim = new RotateOnAxisAnimation(Vector3.Axis.X, -30);
        anim.setDurationMilliseconds(300);
        anim.setInterpolator(new AccelerateDecelerateInterpolator());
        anim.setRepeatCount(2);
        animation3Ds.add(anim);
        ornament.setAnimation3Ds(animation3Ds);

        return ornament;
    }

    private Ornament getTigerNose() {
        Ornament ornament = new Ornament();
        ornament.setModelResId(R.raw.tiger_nose_obj);
        ornament.setImgResId(R.drawable.ic_tiger);
        ornament.setScale(0.002f);
        ornament.setOffset(0, -0.3f, 0.2f);
        ornament.setRotate(0.0f, 0.0f, 0.0f);
        ornament.setColor(0xffe06666);
        return ornament;
    }

    private Ornament getHeart() {
        Ornament ornament = new Ornament();
        ornament.setModelResId(R.raw.heart_eyes_obj);
        ornament.setImgResId(R.drawable.ic_heart);
        ornament.setScale(0.17f);
        ornament.setOffset(0, 0.0f, 0.1f);
        ornament.setRotate(0.0f, 0.0f, 0.0f);
        ornament.setColor(0xffcc0000);

        List<Animation3D> animation3Ds = new ArrayList<>();
        Animation3D anim = new ScaleAnimation3D(new Vector3(0.3f, 0.3f, 0.3f));
        anim.setDurationMilliseconds(300);
        anim.setInterpolator(new LinearInterpolator());
        animation3Ds.add(anim);
        ornament.setAnimation3Ds(animation3Ds);
        return ornament;
    }

    private Ornament getVMask() {
        Ornament ornament = new Ornament();
        ornament.setModelResId(R.raw.v_mask_obj);
        ornament.setImgResId(R.drawable.ic_v_mask);
        ornament.setScale(0.12f);
        ornament.setOffset(0, -0.1f, 0.0f);
        ornament.setRotate(0, 0, 0);
        ornament.setColor(Color.BLACK);
        return ornament;
    }

    private Ornament getCatMask() {
        Ornament ornament = new Ornament();
        ornament.setModelResId(R.raw.cat_mask_obj);
        ornament.setImgResId(R.drawable.ic_cat_mask);
        ornament.setScale(0.12f);
        ornament.setOffset(0, -0.1f, -0.1f);
        ornament.setRotate(0, 0, 0);
        ornament.setColor(Color.DKGRAY);
        return ornament;
    }
}
