package com.simoncherry.artest.ui.fragment;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.opengl.GLES20;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.util.Size;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.simoncherry.artest.MediaLoaderCallback;
import com.simoncherry.artest.OnGetImageListener;
import com.simoncherry.artest.R;
import com.simoncherry.artest.contract.ARFaceContract;
import com.simoncherry.artest.model.ImageBean;
import com.simoncherry.artest.model.Ornament;
import com.simoncherry.artest.presenter.ARFacePresenter;
import com.simoncherry.artest.rajawali3d.AExampleFragment;
import com.simoncherry.artest.ui.adapter.FaceAdapter;
import com.simoncherry.artest.ui.adapter.OrnamentAdapter;
import com.simoncherry.artest.ui.custom.AutoFitTextureView;
import com.simoncherry.artest.ui.custom.CustomBottomSheet;
import com.simoncherry.artest.ui.custom.TrasparentTitleView;
import com.simoncherry.artest.util.BitmapUtils;
import com.simoncherry.artest.util.FileUtils;
import com.simoncherry.artest.util.OBJUtils;
import com.simoncherry.dlib.VisionDetRet;

import org.rajawali3d.Object3D;
import org.rajawali3d.animation.Animation;
import org.rajawali3d.animation.Animation3D;
import org.rajawali3d.animation.AnimationGroup;
import org.rajawali3d.lights.DirectionalLight;
import org.rajawali3d.loader.LoaderOBJ;
import org.rajawali3d.materials.Material;
import org.rajawali3d.materials.textures.ATexture;
import org.rajawali3d.materials.textures.StreamingTexture;
import org.rajawali3d.materials.textures.Texture;
import org.rajawali3d.math.vector.Vector3;
import org.rajawali3d.primitives.Plane;
import org.rajawali3d.renderer.ISurfaceRenderer;
import org.rajawali3d.view.SurfaceView;
import org.reactivestreams.Subscription;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import hugo.weaving.DebugLog;
import io.realm.Realm;
import io.realm.RealmChangeListener;
import io.realm.RealmList;
import io.realm.RealmResults;

/**
 * <pre>
 *     author : Donald
 *     e-mail : xxx@xx
 *     time   : 2017/06/22
 *     desc   :
 *     version: 1.0
 * </pre>
 */
public class ARFaceFragment extends AExampleFragment implements ARFaceContract.View{
    private static final String TAG = "ARMaskFragment";
    private static final int MINIMUM_PREVIEW_SIZE = 320;

    private TrasparentTitleView mScoreView;
    private AutoFitTextureView textureView;
    private ImageView ivDraw;
    private RecyclerView mRvFace;
    private RecyclerView mRvOrnament;
    private TextView mTvCameraHint;
    private TextView mTvSearchHint;
    private FaceAdapter mFaceAdapter;
    private CustomBottomSheet mFaceSheet;
    private OrnamentAdapter mOrnamentAdapter;
    private CustomBottomSheet mOrnamentSheet;
    private ProgressDialog mDialog;
    private FragmentToDraw mFragmentToDraw;

    private Context mContext;
    private ARFacePresenter mPresenter;
    private Handler mUIHandler;
    private Handler mTextureHandler;
    private Paint mFaceLandmarkPaint;

    private List<ImageBean> mImages = new ArrayList<>();
    private List<Ornament> mOrnaments = new ArrayList<>();
    private MediaLoaderCallback mediaLoaderCallback = null;
    private Subscription mSubscription = null;
    private Realm realm;
    private RealmResults<ImageBean> realmResults;

    private float lastX = 0;
    private float lastY = 0;
    private float lastZ = 0;
    private boolean isDrawLandMark = true;
    private boolean isBuildMask = false;
    private boolean isShowGIF = false;
    private String mSwapPath = "/storage/emulated/0/dlib/20130821040137899.jpg";
    private int mOrnamentId = -1;

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private String cameraId;
    private CameraCaptureSession captureSession;
    private CameraDevice cameraDevice;
    private Size previewSize;

    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private HandlerThread inferenceThread;
    private Handler inferenceHandler;
    private ImageReader previewReader;
    private CaptureRequest.Builder previewRequestBuilder;
    private CaptureRequest previewRequest;
    private final Semaphore cameraOpenCloseLock = new Semaphore(1);

    public static ARFaceFragment newInstance() {
        return new ARFaceFragment();
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        mContext = getContext();
        mPresenter = new ARFacePresenter(mContext, this);
        mTextureHandler = new Handler(Looper.getMainLooper());

        final FrameLayout fragmentFrame = new FrameLayout(getActivity());
        final FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(512, 512);
        fragmentFrame.setLayoutParams(params);
        fragmentFrame.setBackgroundColor(getResources().getColor(android.R.color.holo_blue_bright));
        fragmentFrame.setId(R.id.view_to_texture_frame);
        fragmentFrame.setVisibility(View.INVISIBLE);
        mLayout.addView(fragmentFrame);

        mFragmentToDraw = new FragmentToDraw();
        getActivity().getSupportFragmentManager().beginTransaction().add(R.id.view_to_texture_frame, mFragmentToDraw, "custom").commit();

        return mLayout;
    }

    @Override
    protected int getLayoutId() {
        return R.layout.fragment_ar_face;
    }

    @Override
    public void onViewCreated(final View view, final Bundle savedInstanceState) {
        initView(view);
        initFaceSheet();
        initOrnamentSheet();
        initOrnamentData();
        initRealm();
    }

    private void initView(View view) {
        textureView = (AutoFitTextureView) view.findViewById(R.id.texture);
        mScoreView = (TrasparentTitleView) view.findViewById(R.id.results);
        ivDraw = (ImageView) view.findViewById(R.id.iv_draw);
        mTvCameraHint = (TextView) view.findViewById(R.id.tv_hint);

        CheckBox checkShowWindow = (CheckBox) view.findViewById(R.id.check_show_window);
        CheckBox checkShowModel = (CheckBox) view.findViewById(R.id.check_show_model);
        CheckBox checkLandMark = (CheckBox) view.findViewById(R.id.check_land_mark);
        CheckBox checkDrawMode = (CheckBox) view.findViewById(R.id.check_draw_mode);
        CheckBox checkShowOrnament = (CheckBox) view.findViewById(R.id.check_show_ornament);
        Button btnBuildModel = (Button) view.findViewById(R.id.btn_build_model);
        Button btnFaceSheet = (Button) view.findViewById(R.id.btn_face_sheet);
        Button btnOrnament = (Button) view.findViewById(R.id.btn_ornament_sheet);
        Button btnResetFace = (Button) view.findViewById(R.id.btn_reset_face);

        checkShowWindow.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    mOnGetPreviewListener.setWindowVisible(true);
                } else {
                    mOnGetPreviewListener.setWindowVisible(false);
                }
            }
        });

        checkShowModel.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                ARFaceFragment.AccelerometerRenderer renderer = ((ARFaceFragment.AccelerometerRenderer) mRenderer);
                if (renderer.mMonkey != null) {
                    renderer.mMonkey.setVisible(isChecked);
                }
            }
        });

        checkLandMark.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                isDrawLandMark = isChecked;
            }
        });

        checkDrawMode.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                ((ARFaceFragment.AccelerometerRenderer) mRenderer).toggleWireframe();
            }
        });

        checkShowOrnament.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                ARFaceFragment.AccelerometerRenderer renderer = ((ARFaceFragment.AccelerometerRenderer) mRenderer);
                if (renderer.mOrnament != null) {
                    renderer.mOrnament.setVisible(isChecked);
                }
            }
        });

        btnBuildModel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mTvCameraHint.setText("正在检测人脸");
                mTvCameraHint.setVisibility(View.VISIBLE);
                mOnGetPreviewListener.setIsNeedMask(true);
            }
        });

        btnFaceSheet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mFaceSheet.show();
            }
        });

        btnOrnament.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mOrnamentSheet.show();
            }
        });

        btnResetFace.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new Handler().post(new Runnable() {
                    @Override
                    public void run() {
                        mPresenter.resetFaceTexture();
                        isBuildMask = true;
                    }
                });
            }
        });
    }

    private void initFaceSheet() {
        mFaceAdapter = new FaceAdapter(mContext, mImages);
        mFaceAdapter.setOnItemClickListener(new FaceAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(String path) {
                Toast.makeText(mContext, path, Toast.LENGTH_SHORT).show();
                mSwapPath = path;
                mFaceSheet.dismiss();

                showDialog("提示", "换脸中...");
                Thread mThread = new Thread() {
                    @Override
                    public void run() {
                        mPresenter.swapFace(mSwapPath);
                        isBuildMask = true;
                        dismissDialog();
                    }
                };
                mThread.start();
            }
        });

        View sheetView = LayoutInflater.from(mContext)
                .inflate(R.layout.layout_bottom_sheet, null);
        mTvSearchHint = (TextView) sheetView.findViewById(R.id.tv_hint);
        mRvFace = (RecyclerView) sheetView.findViewById(R.id.rv_gallery);
        mRvFace.setAdapter(mFaceAdapter);
        mRvFace.setLayoutManager(new GridLayoutManager(mContext, 3));
        mFaceSheet = new CustomBottomSheet(mContext);
        mFaceSheet.setContentView(sheetView);
        mFaceSheet.getWindow().findViewById(R.id.design_bottom_sheet)
                .setBackgroundResource(android.R.color.transparent);
    }

    private void initOrnamentSheet() {
        mOrnamentAdapter = new OrnamentAdapter(mContext, mOrnaments);
        mOrnamentAdapter.setOnItemClickListener(new OrnamentAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(int position) {
                mOrnamentSheet.dismiss();
                mOrnamentId = position;
                isBuildMask = true;
            }
        });

        View sheetView = LayoutInflater.from(mContext)
                .inflate(R.layout.layout_bottom_sheet, null);
        mRvOrnament = (RecyclerView) sheetView.findViewById(R.id.rv_gallery);
        mRvOrnament.setAdapter(mOrnamentAdapter);
        mRvOrnament.setLayoutManager(new GridLayoutManager(mContext, 4));
        mOrnamentSheet = new CustomBottomSheet(mContext);
        mOrnamentSheet.setContentView(sheetView);
        mOrnamentSheet.getWindow().findViewById(R.id.design_bottom_sheet)
                .setBackgroundResource(android.R.color.transparent);
    }

    private void initOrnamentData() {
        mOrnaments.addAll(mPresenter.getPresetOrnament());
        mOrnamentAdapter.notifyDataSetChanged();
    }

    private void initRealm() {
        realm = Realm.getDefaultInstance();
        realmResults = realm.where(ImageBean.class).equalTo("hasFace", true).findAllAsync();
        realmResults.addChangeListener(new RealmChangeListener<RealmResults<ImageBean>>() {
            @Override
            public void onChange(RealmResults<ImageBean> results) {
                if (results.size() > 0) {
                    Log.e(TAG, "results size: " + results.size());
                    mTvSearchHint.setVisibility(View.GONE);
                    mImages.clear();
                    mImages.addAll(results.subList(0, results.size()));
                    if (mFaceAdapter != null) {
                        mFaceAdapter.notifyDataSetChanged();
                        Log.e(TAG, "getItemCount: " + mFaceAdapter.getItemCount());
                    }
                } else {
                    mTvSearchHint.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    private void showDialog(final String title, final String content) {
        mDialog = ProgressDialog.show(mContext, title, content, true);
    }

    private void dismissDialog() {
        if (mDialog != null) {
            mDialog.dismiss();
        }
    }

    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mUIHandler = new Handler(Looper.getMainLooper());

        mFaceLandmarkPaint = new Paint();
        mFaceLandmarkPaint.setColor(Color.YELLOW);
        mFaceLandmarkPaint.setStrokeWidth(2);
        mFaceLandmarkPaint.setStyle(Paint.Style.STROKE);
    }

    @Override
    public void onResume() {
        super.onResume();
        startBackgroundThread();

        // When the screen is turned off and turned back on, the SurfaceTexture is already
        // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
        // a camera and start preview from here (otherwise, we wait until the surface is ready in
        // the SurfaceTextureListener).
        if (textureView.isAvailable()) {
            openCamera(textureView.getWidth(), textureView.getHeight());
        } else {
            textureView.setSurfaceTextureListener(surfaceTextureListener);
        }

        if (mediaLoaderCallback == null) {
            loadLocalImage();
        }
    }

    @Override
    public void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mSubscription != null) {
            mSubscription.cancel();
        }
        mRvFace.setAdapter(null);
        mRvOrnament.setAdapter(null);
        realmResults.removeAllChangeListeners();
        realm.close();
    }

    private final TextureView.SurfaceTextureListener surfaceTextureListener =
            new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(
                        final SurfaceTexture texture, final int width, final int height) {
                    openCamera(width, height);
                }

                @Override
                public void onSurfaceTextureSizeChanged(
                        final SurfaceTexture texture, final int width, final int height) {
                    configureTransform(width, height);
                }

                @Override
                public boolean onSurfaceTextureDestroyed(final SurfaceTexture texture) {
                    return true;
                }

                @Override
                public void onSurfaceTextureUpdated(final SurfaceTexture texture) {
                }
            };

    private final CameraDevice.StateCallback stateCallback =
            new CameraDevice.StateCallback() {
                @Override
                public void onOpened(final CameraDevice cd) {
                    // This method is called when the camera is opened.  We start camera preview here.
                    cameraOpenCloseLock.release();
                    cameraDevice = cd;
                    createCameraPreviewSession();
                }

                @Override
                public void onDisconnected(final CameraDevice cd) {
                    cameraOpenCloseLock.release();
                    cd.close();
                    cameraDevice = null;

                    if (mOnGetPreviewListener != null) {
                        mOnGetPreviewListener.deInitialize();
                    }
                }

                @Override
                public void onError(final CameraDevice cd, final int error) {
                    cameraOpenCloseLock.release();
                    cd.close();
                    cameraDevice = null;
                    final Activity activity = getActivity();
                    if (null != activity) {
                        activity.finish();
                    }

                    if (mOnGetPreviewListener != null) {
                        mOnGetPreviewListener.deInitialize();
                    }
                }
            };

    private void showToast(final String text) {
        final Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(
                    new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
                        }
                    });
        }
    }

    @SuppressLint("LongLogTag")
    @DebugLog
    private static Size chooseOptimalSize(
            final Size[] choices, final int width, final int height, final Size aspectRatio) {
        // Collect the supported resolutions that are at least as big as the preview Surface
        final List<Size> bigEnough = new ArrayList<Size>();
        for (final Size option : choices) {
            if (option.getHeight() >= MINIMUM_PREVIEW_SIZE && option.getWidth() >= MINIMUM_PREVIEW_SIZE) {
                Log.i(TAG, "Adding size: " + option.getWidth() + "x" + option.getHeight());
                bigEnough.add(option);
            } else {
                Log.i(TAG, "Not adding size: " + option.getWidth() + "x" + option.getHeight());
            }
        }

        // Pick the smallest of those, assuming we found any
        if (bigEnough.size() > 0) {
            final Size chosenSize = Collections.min(bigEnough, new ARFaceFragment.CompareSizesByArea());
            Log.i(TAG, "Chosen size: " + chosenSize.getWidth() + "x" + chosenSize.getHeight());
            return chosenSize;
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    @DebugLog
    @SuppressLint("LongLogTag")
    private void setUpCameraOutputs(final int width, final int height) {
        final Activity activity = getActivity();
        final CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            SparseArray<Integer> cameraFaceTypeMap = new SparseArray<>();
            // Check the facing types of camera devices
            for (final String cameraId : manager.getCameraIdList()) {
                final CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                final Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    if (cameraFaceTypeMap.get(CameraCharacteristics.LENS_FACING_FRONT) != null) {
                        cameraFaceTypeMap.append(CameraCharacteristics.LENS_FACING_FRONT, cameraFaceTypeMap.get(CameraCharacteristics.LENS_FACING_FRONT) + 1);
                    } else {
                        cameraFaceTypeMap.append(CameraCharacteristics.LENS_FACING_FRONT, 1);
                    }
                }

                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    if (cameraFaceTypeMap.get(CameraCharacteristics.LENS_FACING_FRONT) != null) {
                        cameraFaceTypeMap.append(CameraCharacteristics.LENS_FACING_BACK, cameraFaceTypeMap.get(CameraCharacteristics.LENS_FACING_BACK) + 1);
                    } else {
                        cameraFaceTypeMap.append(CameraCharacteristics.LENS_FACING_BACK, 1);
                    }
                }
            }

            Integer num_facing_back_camera = cameraFaceTypeMap.get(CameraCharacteristics.LENS_FACING_FRONT);  // by simon at 2017/04/25 -- 换前置
            for (final String cameraId : manager.getCameraIdList()) {
                final CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                final Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                // If facing back camera or facing external camera exist, we won't use facing front camera
                if (num_facing_back_camera != null && num_facing_back_camera > 0) {
                    // We don't use a front facing camera in this sample if there are other camera device facing types
                    if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {  // by simon at 2017/04/25 -- 换前置
                        continue;
                    }
                }

                final StreamConfigurationMap map =
                        characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                if (map == null) {
                    continue;
                }

                // For still image captures, we use the largest available size.
                final Size largest =
                        Collections.max(
                                Arrays.asList(map.getOutputSizes(ImageFormat.YUV_420_888)),
                                new ARFaceFragment.CompareSizesByArea());

                // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
                // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
                // garbage capture data.
                previewSize =
                        chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), width, height, largest);

                // We fit the aspect ratio of TextureView to the size of preview we picked.
                final int orientation = getResources().getConfiguration().orientation;
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    textureView.setAspectRatio(previewSize.getWidth(), previewSize.getHeight());
                } else {
                    textureView.setAspectRatio(previewSize.getHeight(), previewSize.getWidth());
                }

                ARFaceFragment.this.cameraId = cameraId;
                return;
            }
        } catch (final CameraAccessException e) {
            Log.e(TAG, "Exception!",  e);
        } catch (final NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
//            ErrorDialog.newInstance(getString(R.string.camera_error))
//                    .show(getChildFragmentManager(), FRAGMENT_DIALOG);
        }
    }

    @SuppressLint("LongLogTag")
    @DebugLog
    private void openCamera(final int width, final int height) {
        setUpCameraOutputs(width, height);
        configureTransform(width, height);
        final Activity activity = getActivity();
        final CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            if (ActivityCompat.checkSelfPermission(this.getActivity(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "checkSelfPermission CAMERA");
            }
            manager.openCamera(cameraId, stateCallback, backgroundHandler);
            Log.d(TAG, "open Camera");
        } catch (final CameraAccessException e) {
            Log.e(TAG, "Exception!", e);
        } catch (final InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    @DebugLog
    private void closeCamera() {
        try {
            cameraOpenCloseLock.acquire();
            if (null != captureSession) {
                captureSession.close();
                captureSession = null;
            }
            if (null != cameraDevice) {
                cameraDevice.close();
                cameraDevice = null;
            }
            if (null != previewReader) {
                previewReader.close();
                previewReader = null;
            }
            if (null != mOnGetPreviewListener) {
                mOnGetPreviewListener.deInitialize();
            }
        } catch (final InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            cameraOpenCloseLock.release();
        }
    }

    @DebugLog
    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("ImageListener");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());

        inferenceThread = new HandlerThread("InferenceThread");
        inferenceThread.start();
        inferenceHandler = new Handler(inferenceThread.getLooper());
    }

    @SuppressLint("LongLogTag")
    @DebugLog
    private void stopBackgroundThread() {
        backgroundThread.quitSafely();
        inferenceThread.quitSafely();
        try {
            backgroundThread.join();
            backgroundThread = null;
            backgroundHandler = null;

            inferenceThread.join();
            inferenceThread = null;
            inferenceThread = null;
        } catch (final InterruptedException e) {
            Log.e(TAG, "error" ,e );
        }
    }

    private final OnGetImageListener mOnGetPreviewListener = new OnGetImageListener();

    private final CameraCaptureSession.CaptureCallback captureCallback =
            new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureProgressed(
                        @NonNull final CameraCaptureSession session,
                        @NonNull final CaptureRequest request,
                        @NonNull final CaptureResult partialResult) {}

                @Override
                public void onCaptureCompleted(
                        @NonNull final CameraCaptureSession session,
                        @NonNull final CaptureRequest request,
                        @NonNull final TotalCaptureResult result) {}
            };

    @SuppressLint("LongLogTag")
    @DebugLog
    private void createCameraPreviewSession() {
        try {
            final SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;

            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());

            // This is the output Surface we need to start preview.
            final Surface surface = new Surface(texture);

            // We set up a CaptureRequest.Builder with the output Surface.
            previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(surface);

            Log.i(TAG, "Opening camera preview: " + previewSize.getWidth() + "x" + previewSize.getHeight());

            // Create the reader for the preview frames.
            previewReader =
                    ImageReader.newInstance(
                            previewSize.getWidth(), previewSize.getHeight(), ImageFormat.YUV_420_888, 2);

            previewReader.setOnImageAvailableListener(mOnGetPreviewListener, backgroundHandler);
            previewRequestBuilder.addTarget(previewReader.getSurface());

            // Here, we create a CameraCaptureSession for camera preview.
            cameraDevice.createCaptureSession(
                    Arrays.asList(surface, previewReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull final CameraCaptureSession cameraCaptureSession) {
                            // The camera is already closed
                            if (null == cameraDevice) {
                                return;
                            }

                            // When the session is ready, we start displaying the preview.
                            captureSession = cameraCaptureSession;
                            try {
                                // Auto focus should be continuous for camera preview.
                                previewRequestBuilder.set(
                                        CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                // Flash is automatically enabled when necessary.
                                previewRequestBuilder.set(
                                        CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

                                // Finally, we start displaying the camera preview.
                                previewRequest = previewRequestBuilder.build();
                                captureSession.setRepeatingRequest(
                                        previewRequest, captureCallback, backgroundHandler);
                            } catch (final CameraAccessException e) {
                                Log.e(TAG, "Exception!", e);
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull final CameraCaptureSession cameraCaptureSession) {
                            showToast("Failed");
                        }
                    },
                    null);
        } catch (final CameraAccessException e) {
            Log.e(TAG, "Exception!", e);
        }

        showDialog("提示", "正在初始化...");
        Thread mThread = new Thread() {
            @Override
            public void run() {
                mOnGetPreviewListener.initialize(
                        getActivity().getApplicationContext(), getActivity().getAssets(), mScoreView, inferenceHandler);
                dismissDialog();
            }
        };
        mThread.start();

        mOnGetPreviewListener.setLandMarkListener(new OnGetImageListener.LandMarkListener() {
            @Override
            public void onLandmarkChange(final List<VisionDetRet> results) {
                mUIHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        handleMouthOpen(results);
                        if (!isDrawLandMark) {
                            ivDraw.setImageResource(0);
                        }
                    }
                });

                if (isDrawLandMark) {
                    inferenceHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (results != null && results.size() > 0) {
                                drawLandMark(results.get(0));
                            }
                        }
                    });
                }
            }

            @Override
            public void onRotateChange(float x, float y, float z) {
                rotateModel(x, y, z);
            }

            @Override
            public void onTransChange(float x, float y, float z) {
                ARFaceFragment.AccelerometerRenderer renderer = ((ARFaceFragment.AccelerometerRenderer) mRenderer);
                //renderer.mContainer.setPosition(x/20, -y/20, z/20);
                renderer.getCurrentCamera().setPosition(-x/200, y/200, z/100);
            }

            @Override
            public void onMatrixChange(ArrayList<Double> elementList) {
            }
        });

        mOnGetPreviewListener.setBuildMaskListener(new OnGetImageListener.BuildMaskListener() {
            @Override
            public void onGetSuitableFace(final Bitmap bitmap, final ArrayList<Point> landmarks) {
                Log.e("rotateList", "onGetSuitableFace");
                new Handler().post(new Runnable() {
                    @Override
                    public void run() {
                        OBJUtils.buildFaceModel(mContext, bitmap, landmarks);
                        isBuildMask = true;
                    }
                });
            }
        });
    }

    private void handleMouthOpen(List<VisionDetRet> results) {
        if (results != null && results.size() > 0) {
            ArrayList<Point> landmarks = results.get(0).getFaceLandmarks();
            int mouthTop = landmarks.get(62).y;
            int mouthBottom = landmarks.get(66).y;
            int mouthAmplitude = mouthBottom - mouthTop;
            String openMouth = "openMouth: " + mouthAmplitude;
            mTvCameraHint.setText(openMouth);

            isShowGIF = mouthAmplitude >= 30;
        }
    }

    private void drawLandMark(VisionDetRet ret) {
        float resizeRatio = 1.0f;
        //float resizeRatio = 2.5f;    // 预览尺寸 480x320  /  截取尺寸 192x128  (另外悬浮窗尺寸是 810x540)
        Rect bounds = new Rect();
        bounds.left = (int) (ret.getLeft() * resizeRatio);
        bounds.top = (int) (ret.getTop() * resizeRatio);
        bounds.right = (int) (ret.getRight() * resizeRatio);
        bounds.bottom = (int) (ret.getBottom() * resizeRatio);

        final Bitmap mBitmap = Bitmap.createBitmap(previewSize.getHeight(), previewSize.getWidth(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(mBitmap);
        canvas.drawRect(bounds, mFaceLandmarkPaint);

        final ArrayList<Point> landmarks = ret.getFaceLandmarks();
        for (Point point : landmarks) {
            int pointX = (int) (point.x * resizeRatio);
            int pointY = (int) (point.y * resizeRatio);
            canvas.drawCircle(pointX, pointY, 2, mFaceLandmarkPaint);
        }

        mUIHandler.post(new Runnable() {
            @Override
            public void run() {
                ivDraw.setImageBitmap(mBitmap);
            }
        });
    }

    private void rotateModel(float x, float y, float z) {
        if (mRenderer != null) {
            boolean isJumpX = false;
            boolean isJumpY = false;
            boolean isJumpZ = false;
            float rotateX = x;
            float rotateY = y;
            float rotateZ = z;

            if (Math.abs(lastX-x) > 90) {
                Log.e("rotateException", "X 跳变");
                isJumpX = true;
                rotateX = lastX;
            }
            if (Math.abs(lastY-y) > 90) {
                Log.e("rotateException", "Y 跳变");
                isJumpY = true;
                rotateY = lastY;
            }
            if (Math.abs(lastZ-z) > 90) {
                Log.e("rotateException", "Z 跳变");
                isJumpZ = true;
                rotateZ = lastZ;
            }

            ((ARFaceFragment.AccelerometerRenderer) mRenderer).setAccelerometerValues(rotateZ, rotateY, -rotateX);

            if (!isJumpX) lastX = x;
            if (!isJumpY) lastY = y;
            if (!isJumpZ) lastZ = z;
        }
    }

    @DebugLog
    private void configureTransform(final int viewWidth, final int viewHeight) {
        final Activity activity = getActivity();
        if (null == textureView || null == previewSize || null == activity) {
            return;
        }
        final int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        final Matrix matrix = new Matrix();
        final RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        final RectF bufferRect = new RectF(0, 0, previewSize.getHeight(), previewSize.getWidth());
        final float centerX = viewRect.centerX();
        final float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            final float scale =
                    Math.max(
                            (float) viewHeight / previewSize.getHeight(),
                            (float) viewWidth / previewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        textureView.setTransform(matrix);
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        mSubscription = subscription;
        mSubscription.request(Long.MAX_VALUE);
    }

    private static class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(final Size lhs, final Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum(
                    (long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
        }
    }

    @Override
    public ISurfaceRenderer createRenderer() {
        return new ARFaceFragment.AccelerometerRenderer(getActivity(), this);
    }

    @Override
    protected void onBeforeApplyRenderer() {
        ((SurfaceView) mRenderSurface).setTransparent(true);
        super.onBeforeApplyRenderer();
    }

    private final class AccelerometerRenderer extends AExampleRenderer implements StreamingTexture.ISurfaceListener{
        private DirectionalLight mLight;
        private Object3D mContainer;
        private Object3D mMonkey;
        private Object3D mOrnament;
        private Vector3 mAccValues;

        private Object3D mPlane;
        private int mFrameCount;
        private Surface mSurface;
        private StreamingTexture mStreamingTexture;
        private volatile boolean mShouldUpdateTexture;
        private final float[] mMatrix = new float[16];

        AccelerometerRenderer(Context context, @Nullable AExampleFragment fragment) {
            super(context, fragment);
            mAccValues = new Vector3();
        }

        @Override
        protected void initScene() {
            try {
                mLight = new DirectionalLight(0.1f, -1.0f, -1.0f);
                mLight.setColor(1.0f, 1.0f, 1.0f);
                mLight.setPower(1);
                getCurrentScene().addLight(mLight);

                mContainer = new Object3D();
                showMaskModel();
                getCurrentScene().addChild(mContainer);

            } catch (Exception e) {
                e.printStackTrace();
            }

            getCurrentScene().setBackgroundColor(0);
        }

        final Runnable mUpdateTexture = new Runnable() {
            public void run() {
                // -- Draw the view on the canvas
                final Canvas canvas = mSurface.lockCanvas(null);
                canvas.drawColor(Color.TRANSPARENT);  // 不加这句的话，画面有残影，全是之前的动画轨迹
                mStreamingTexture.getSurfaceTexture().getTransformMatrix(mMatrix);
                mFragmentToDraw.getView().draw(canvas);
                mSurface.unlockCanvasAndPost(canvas);
                // -- Indicates that the texture should be updated on the OpenGL thread.
                mShouldUpdateTexture = true;
            }
        };

        @Override
        protected void onRender(long ellapsedRealtime, double deltaTime) {
            if (mSurface != null && mFrameCount++ >= (mFrameRate * 0.25)) {
                mPlane.setVisible(isShowGIF);
                mFrameCount = 0;
                mTextureHandler.post(mUpdateTexture);
            }
            if (mShouldUpdateTexture) {
                mStreamingTexture.update();
                mShouldUpdateTexture = false;
            }

            if (isBuildMask) {
                showMaskModel();
                isBuildMask = false;
                Activity activity = getActivity();
                if (activity != null) {
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mTvCameraHint.setVisibility(View.GONE);
                        }
                    });
                }
            }

            super.onRender(ellapsedRealtime, deltaTime);
            mContainer.setRotation(mAccValues.x, mAccValues.y, mAccValues.z);
        }

        void setAccelerometerValues(float x, float y, float z) {
            mAccValues.setAll(x, y, z);
        }

        void toggleWireframe() {
            mMonkey.setDrawingMode(mMonkey.getDrawingMode() == GLES20.GL_TRIANGLES ? GLES20.GL_LINES
                    : GLES20.GL_TRIANGLES);
        }

        void showMaskModel() {
            try {
                boolean isFaceVisible = true;
                boolean isOrnamentVisible = true;
                if (mMonkey != null) {
                    isFaceVisible = mMonkey.isVisible();
                    mMonkey.setScale(1.0f);
                    mMonkey.setPosition(0, 0, 0);
                    mContainer.removeChild(mMonkey);
                }
                if (mOrnament != null) {
                    isOrnamentVisible = mOrnament.isVisible();
                    mOrnament.setScale(1.0f);
                    mOrnament.setPosition(0, 0, 0);
                    mContainer.removeChild(mOrnament);
                }

                String modelDir = OBJUtils.getModelDir();
                String imagePath = modelDir + OBJUtils.IMG_FACE;
                String objPath = OBJUtils.DIR_NAME + File.separator + FileUtils.getMD5(imagePath) + "_obj";
                LoaderOBJ parser = new LoaderOBJ(this, objPath);
                parser.parse();
                mMonkey = parser.getParsedObject();
                ATexture texture = mMonkey.getMaterial().getTextureList().get(0);
                mMonkey.getMaterial().removeTexture(texture);
                mMonkey.setScale(0.06f);
                mMonkey.setY(-0.54f);
                mMonkey.setZ(0.15f);
                mMonkey.setVisible(isFaceVisible);

                String texturePath = FileUtils.getMD5(imagePath) + ".jpg";
                Bitmap bitmap = BitmapUtils.decodeSampledBitmapFromFilePath(modelDir + texturePath, 1024, 1024);
                mMonkey.getMaterial().addTexture(new Texture("canvas", bitmap));
                mMonkey.getMaterial().enableLighting(false);

                mContainer.addChild(mMonkey);

                if (mOrnamentId >= 0 && mOrnaments.size() > mOrnamentId) {
                    Ornament ornament = mOrnaments.get(mOrnamentId);
                    LoaderOBJ objParser1 = new LoaderOBJ(mContext.getResources(), mTextureManager, ornament.getModelResId());
                    objParser1.parse();
                    mOrnament = objParser1.getParsedObject();
                    mOrnament.setScale(ornament.getScale());
                    mOrnament.setPosition(ornament.getOffsetX(), ornament.getOffsetY(), ornament.getOffsetZ());
                    mOrnament.setRotation(ornament.getRotateX(), ornament.getRotateY(), ornament.getRotateZ());
                    mOrnament.getMaterial().setColor(ornament.getColor());
                    mOrnament.setVisible(isOrnamentVisible);
                    mContainer.addChild(mOrnament);

                    getCurrentScene().clearAnimations();
                    List<Animation3D> animation3Ds = ornament.getAnimation3Ds();
                    if (animation3Ds != null && animation3Ds.size() > 0) {
                        final AnimationGroup animGroup = new AnimationGroup();
                        animGroup.setRepeatMode(Animation.RepeatMode.REVERSE_INFINITE);

                        for (Animation3D animation3D : animation3Ds) {
                            animation3D.setTransformable3D(mOrnament);
                            animGroup.addAnimation(animation3D);
                        }

                        getCurrentScene().registerAnimation(animGroup);
                        animGroup.play();
                    }
                }

                mPlane = new Plane(0.5f, 0.5f, 1, 1);
                mPlane.setColor(0);
                mPlane.setPosition(0, -0.25f, 0.2f);
                mStreamingTexture = new StreamingTexture("viewTexture", this);
                Material material = new Material();
                material.setColorInfluence(0);
                try {
                    material.addTexture(mStreamingTexture);
                } catch (ATexture.TextureException e) {
                    e.printStackTrace();
                }
                mPlane.setMaterial(material);
                mPlane.setVisible(isShowGIF);
                mContainer.addChild(mPlane);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void setSurface(Surface surface) {
            mSurface = surface;
            mStreamingTexture.getSurfaceTexture().setDefaultBufferSize(512, 512);
        }
    }

    public static final class FragmentToDraw extends Fragment {
        ImageView mImageView;

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            final View view = inflater.inflate(R.layout.view_to_texture, container, false);
            mImageView = (ImageView) view.findViewById(R.id.iv_texture);
            Glide.with(this).load(R.drawable.wow).asGif()
                    .diskCacheStrategy(DiskCacheStrategy.SOURCE)
                    .into(mImageView);
            view.setVisibility(View.INVISIBLE);
            return view;
        }
    }

    private void loadLocalImage() {
        mediaLoaderCallback = new MediaLoaderCallback(mContext);
        mediaLoaderCallback.setOnLoadFinishedListener(new MediaLoaderCallback.OnLoadFinishedListener() {
            @Override
            public void onLoadFinished(RealmList<ImageBean> data) {
                //Toast.makeText(mContext, "Total Size: " + data.size(), Toast.LENGTH_SHORT).show();
                mPresenter.startFaceScanTask(data);
            }
        });
        getActivity().getSupportLoaderManager().initLoader(0, null, mediaLoaderCallback);
    }
}
