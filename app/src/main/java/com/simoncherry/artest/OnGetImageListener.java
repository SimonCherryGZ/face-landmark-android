/*
 * Copyright 2016 Tzutalin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.simoncherry.artest;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.media.Image;
import android.media.Image.Plane;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Handler;
import android.os.Trace;
import android.util.Log;

import com.simoncherry.artest.ui.custom.FloatingCameraWindow;
import com.simoncherry.artest.ui.custom.TrasparentTitleView;
import com.simoncherry.artest.util.ImageUtils;
import com.simoncherry.dlib.Constants;
import com.simoncherry.dlib.FaceDet;
import com.simoncherry.dlib.VisionDetRet;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Class that takes in preview frames and converts the image to Bitmaps to process with dlib lib.
 */
public class OnGetImageListener implements OnImageAvailableListener {
    private static final boolean SAVE_PREVIEW_BITMAP = false;

    private static final int NUM_CLASSES = 1001;
    private static final int INPUT_SIZE = 224;
    private static final int IMAGE_MEAN = 117;
    private static final String TAG = "OnGetImageListener";

    private int mScreenRotation = 90;

    private int mPreviewWdith = 0;
    private int mPreviewHeight = 0;
    private byte[][] mYUVBytes;
    private int[] mRGBBytes = null;
    private Bitmap mRGBframeBitmap = null;
    private Bitmap mCroppedBitmap = null;

    private boolean mIsNeedMask = false;
    private boolean mIsComputing = false;
    private Handler mInferenceHandler;

    private Context mContext;
    private FaceDet mFaceDet;
    private TrasparentTitleView mTransparentTitleView;
    private FloatingCameraWindow mWindow;
    private Paint mFaceLandmardkPaint;

    public void initialize(
            final Context context,
            final AssetManager assetManager,
            final TrasparentTitleView scoreView,
            final Handler handler) {
        this.mContext = context;
        this.mTransparentTitleView = scoreView;
        this.mInferenceHandler = handler;
        mFaceDet = new FaceDet(Constants.getFaceShapeModelPath());
        mWindow = new FloatingCameraWindow(mContext);

        mFaceLandmardkPaint = new Paint();
        mFaceLandmardkPaint.setColor(Color.YELLOW);
        mFaceLandmardkPaint.setStrokeWidth(2);
        mFaceLandmardkPaint.setStyle(Paint.Style.STROKE);
    }

    public void deInitialize() {
        synchronized (OnGetImageListener.this) {
            if (mFaceDet != null) {
                mFaceDet.release();
            }

            if (mWindow != null) {
                mWindow.release();
            }
        }
    }

    private void drawResizedBitmap(final Bitmap src, final Bitmap dst) {
        final Matrix matrix = new Matrix();

        final float scaleFactorW = (float) dst.getWidth() / src.getWidth();
        final float scaleFactorH = (float) dst.getHeight() / src.getHeight();
        matrix.postScale(scaleFactorW, scaleFactorH);

        final Canvas canvas = new Canvas(dst);
        canvas.drawBitmap(src, matrix, null);
    }

    @Override
    public void onImageAvailable(final ImageReader reader) {
        Image image = null;
        try {
            image = reader.acquireLatestImage();

            if (image == null) {
                return;
            }

            // No mutex needed as this method is not reentrant.
            if (mIsComputing) {
                image.close();
                return;
            }
            mIsComputing = true;

            Trace.beginSection("imageAvailable");

            final Plane[] planes = image.getPlanes();

            // Initialize the storage bitmaps once when the resolution is known.
            if (mPreviewWdith != image.getWidth() || mPreviewHeight != image.getHeight()) {
                mPreviewWdith = image.getWidth();
                mPreviewHeight = image.getHeight();

                Log.d(TAG, String.format("Initializing at size %dx%d", mPreviewWdith, mPreviewHeight));
                mRGBBytes = new int[mPreviewWdith * mPreviewHeight];
                mRGBframeBitmap = Bitmap.createBitmap(mPreviewWdith, mPreviewHeight, Config.ARGB_8888);
//                mCroppedBitmap = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Config.ARGB_8888);

                float ratio = (float)mPreviewWdith / mPreviewHeight;
                mCroppedBitmap = Bitmap.createBitmap(INPUT_SIZE, (int) (INPUT_SIZE * ratio), Config.ARGB_8888);
                Log.e("mCroppedBitmap", "width: " + mCroppedBitmap.getWidth());    // mCroppedBitmap: width: 224
                Log.e("mCroppedBitmap", "height: " + mCroppedBitmap.getHeight());  // mCroppedBitmap: height: 336

                mYUVBytes = new byte[planes.length][];
                for (int i = 0; i < planes.length; ++i) {
                    mYUVBytes[i] = new byte[planes[i].getBuffer().capacity()];
                }
            }

            for (int i = 0; i < planes.length; ++i) {
                planes[i].getBuffer().get(mYUVBytes[i]);
            }

            final int yRowStride = planes[0].getRowStride();
            final int uvRowStride = planes[1].getRowStride();
            final int uvPixelStride = planes[1].getPixelStride();
            ImageUtils.convertYUV420ToARGB8888(
                    mYUVBytes[0],
                    mYUVBytes[1],
                    mYUVBytes[2],
                    mRGBBytes,
                    mPreviewWdith,
                    mPreviewHeight,
                    yRowStride,
                    uvRowStride,
                    uvPixelStride,
                    false);

            image.close();
        } catch (final Exception e) {
            if (image != null) {
                image.close();
            }
            Log.e(TAG, "Exception!", e);
            Trace.endSection();
            return;
        }

        mRGBframeBitmap.setPixels(mRGBBytes, 0, mPreviewWdith, 0, 0, mPreviewWdith, mPreviewHeight);
        Log.e("mRGBframeBitmap", "width: " + mRGBframeBitmap.getWidth());    // mRGBframeBitmap: width: 480
        Log.e("mRGBframeBitmap", "height: " + mRGBframeBitmap.getHeight());  // mRGBframeBitmap: height: 320

        Matrix mtx = new Matrix();
        mtx.preScale(-1.0f, 1.0f);
        mtx.postRotate(90.0f);
        Bitmap mRotateBitmap = Bitmap.createBitmap(mRGBframeBitmap, 0, 0, mRGBframeBitmap.getWidth(), mRGBframeBitmap.getHeight(), mtx, true);
        drawResizedBitmap(mRotateBitmap, mCroppedBitmap);

        if (SAVE_PREVIEW_BITMAP) {
            ImageUtils.saveBitmap(mCroppedBitmap);
        }

        mInferenceHandler.post(
                new Runnable() {
                    @Override
                    public void run() {
                        if (!new File(Constants.getFaceShapeModelPath()).exists()) {
                            throw new RuntimeException("cannot find shape_predictor_68_face_landmarks.dat");
                        }

                        long startTime = System.currentTimeMillis();
                        List<VisionDetRet> results = null;
                        synchronized (OnGetImageListener.this) {
                            if (mFaceDet != null) {
                                results = mFaceDet.detect(mCroppedBitmap);
                            }
                        }

                        // Draw on bitmap
                        if (results != null && results.size() > 0) {
                            VisionDetRet ret = results.get(0);
                            //Canvas canvas = new Canvas(mCroppedBitmap);
                            //drawLandmarks(canvas, ret);
                            // add by simon at 2017/05/01 -- 描绘三维头部姿态
                            //drawHeadPose(canvas, ret);
                            // add by simon at 2017/05/04 -- 获取3轴旋转角度
                            handleRotation(ret);
                            // add by simon at 2017/05/07
                            handleTransition(ret);
                            //handleRotation2(ret);

                            if (landMarkListener != null) {
                                landMarkListener.onLandmarkChange(results);
                            }
                        }

                        long endTime = System.currentTimeMillis();
                        mTransparentTitleView.setText("Time cost: " + String.valueOf((endTime - startTime) / 1000f) + " sec");

                        if (mWindow != null) {
                            mWindow.setRGBBitmap(mCroppedBitmap);
                        }
                        mIsComputing = false;
                    }
                });

        Trace.endSection();
    }

    private void drawLandmarks(Canvas canvas, VisionDetRet ret) {
        float resizeRatio = 1.0f;
        Rect bounds = new Rect();
        bounds.left = (int) (ret.getLeft() * resizeRatio);
        bounds.top = (int) (ret.getTop() * resizeRatio);
        bounds.right = (int) (ret.getRight() * resizeRatio);
        bounds.bottom = (int) (ret.getBottom() * resizeRatio);
        canvas.drawRect(bounds, mFaceLandmardkPaint);

        // Draw landmark
        ArrayList<Point> landmarks = ret.getFaceLandmarks();
        for (Point point : landmarks) {
            int pointX = (int) (point.x * resizeRatio);
            int pointY = (int) (point.y * resizeRatio);
            canvas.drawCircle(pointX, pointY, 2, mFaceLandmardkPaint);
        }
    }

    private void drawHeadPose(Canvas canvas, VisionDetRet ret) {
        float resizeRatio = 1.0f;
        ArrayList<Point> landmarks = ret.getFaceLandmarks();
        ArrayList<Point> headPoses = ret.getPosePoints();
        if (headPoses != null) {
            //Log.e("headPoses: ", headPoses.toString());
            int temp = 0;
            for (Point point : headPoses) {
                int pointX = (int) (point.x * resizeRatio);
                int pointY = (int) (point.y * resizeRatio);
                if (temp == 0) {
                    mFaceLandmardkPaint.setColor(Color.RED);
                } else if (temp == 1) {
                    mFaceLandmardkPaint.setColor(Color.GREEN);
                } else if (temp == 2) {
                    mFaceLandmardkPaint.setColor(Color.BLUE);
                }
                canvas.drawLine(landmarks.get(30).x, landmarks.get(30).y, pointX, pointY, mFaceLandmardkPaint);
                temp++;
            }
            mFaceLandmardkPaint.setColor(Color.YELLOW);
        } else {
            Log.e("headPoses: ", "null");
        }
    }

    private void handleRotation(VisionDetRet ret) {
        ArrayList<Float> rotateList = ret.getRotate();
        if (rotateList != null && rotateList.size() >= 3) {
            Log.e("rotateList: ", rotateList.toString());
            if (landMarkListener != null) {
                float x = rotateList.get(0);
                float y = rotateList.get(1);
                float z = rotateList.get(2);
                landMarkListener.onRotateChange(x, y, z);

                if (mIsNeedMask) {
                    boolean xIsGood = (x >= -12) && (x <= -8);
                    boolean yIsGood = (y >= -3) && (y <= 3);
                    boolean zIsGood = (z >= -3) && (z <= 3);
                    if (xIsGood && yIsGood && zIsGood) {
                        Log.e("rotateList: ", "good rotation to build 3d face model");
                        mIsNeedMask = false;
                        if (buildMaskListener != null) {
                            buildMaskListener.onGetSuitableFace(mRGBframeBitmap, ret.getFaceLandmarks());
                        }
                    }
                }
            }
        } else {
            Log.e("rotateList: ", "null");
        }
    }

    private void handleTransition(VisionDetRet ret) {
        ArrayList<Float> transList = ret.getTrans();
        if (transList != null && transList.size() >= 3) {
            Log.e("transList: ", transList.toString());
            if (landMarkListener != null) {
                landMarkListener.onTransChange(transList.get(0), transList.get(1), transList.get(2));
            }
        } else {
            Log.e("transList: ", "null");
        }
    }

    private void handleRotation2(VisionDetRet ret) {
        ArrayList<Double> rotationList = ret.getRotation();
        if (rotationList != null && rotationList.size() >= 16) {
            Log.e("rotationList: ", rotationList.toString());
            if (landMarkListener != null) {
                landMarkListener.onMatrixChange(rotationList);
            }
        } else {
            Log.e("rotationList: ", "null");
        }
    }

    public void setIsNeedMask(boolean mIsNeedMask) {
        this.mIsNeedMask = mIsNeedMask;
    }

    public boolean isWindowVisible() {
        return mWindow.isWindowVisible();
    }

    public void setWindowVisible(boolean isVisible) {
        mWindow.setWindowVisible(isVisible);
    }

    public interface LandMarkListener {
        void onLandmarkChange(List<VisionDetRet> results);
        void onRotateChange(float x, float y, float z);
        void onTransChange(float x, float y, float z);
        void onMatrixChange(ArrayList<Double> elementList);
    }

    private LandMarkListener landMarkListener;

    public void setLandMarkListener(LandMarkListener landMarkListener) {
        this.landMarkListener = landMarkListener;
    }

    public interface BuildMaskListener {
        void onGetSuitableFace(Bitmap bitmap, ArrayList<Point> landmarks);
    }

    private BuildMaskListener buildMaskListener;

    public void setBuildMaskListener(BuildMaskListener buildMaskListener) {
        this.buildMaskListener = buildMaskListener;
    }
}
