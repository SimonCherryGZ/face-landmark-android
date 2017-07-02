package com.simoncherry.artest.ui.fragment;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraManager;
import android.opengl.GLES20;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.Toast;

import com.simoncherry.artest.OnGetImageListener;
import com.simoncherry.artest.R;
import com.simoncherry.artest.nekocode.MyCameraRenderer;
import com.simoncherry.artest.rajawali3d.AExampleFragment;
import com.simoncherry.artest.ui.adapter.FilterAdapter;
import com.simoncherry.artest.ui.custom.AutoFitTextureView;
import com.simoncherry.artest.ui.custom.CustomBottomSheet;
import com.simoncherry.artest.ui.custom.TrasparentTitleView;
import com.simoncherry.artest.util.BitmapUtils;
import com.simoncherry.artest.util.CameraUtils;
import com.simoncherry.artest.util.FileUtils;
import com.simoncherry.artest.util.OBJUtils;
import com.simoncherry.dlib.VisionDetRet;

import org.rajawali3d.Object3D;
import org.rajawali3d.lights.DirectionalLight;
import org.rajawali3d.loader.LoaderOBJ;
import org.rajawali3d.materials.textures.ATexture;
import org.rajawali3d.materials.textures.Texture;
import org.rajawali3d.math.vector.Vector3;
import org.rajawali3d.renderer.ISurfaceRenderer;
import org.rajawali3d.view.SurfaceView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import hugo.weaving.DebugLog;

/**
 * <pre>
 *     author : Donald
 *     e-mail : xxx@xx
 *     time   : 2017/06/22
 *     desc   :
 *     version: 1.0
 * </pre>
 */
public class CameraTestFragment extends AExampleFragment {
    private static final String TAG = "ARMaskFragment";

    private AutoFitTextureView textureView;
    private TrasparentTitleView mScoreView;
    private ImageView ivDraw;
    private Button btnBuildModel;
    private Button btnShowFilter;
    private ProgressDialog mDialog;
    private CustomBottomSheet mFilterSheet;
    private RecyclerView mRvFilter;
    private FilterAdapter mFilterAdapter;

    private Context mContext;
    private Handler mUIHandler;
    private Paint mFaceLandmarkPaint;
    private MyCameraRenderer mCameraRenderer;

    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private HandlerThread inferenceThread;
    private Handler inferenceHandler;

    private List<Integer> mFilterData;
    private float lastX = 0;
    private float lastY = 0;
    private float lastZ = 0;
    private boolean isDrawLandMark = true;
    private boolean isBuildMask = false;

    private OnGetImageListener mOnGetPreviewListener = null;

    private final TextureView.SurfaceTextureListener surfaceTextureListener =
            new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(
                        final SurfaceTexture texture, final int width, final int height) {
                    CameraUtils.openCamera(width, height);
                }

                @Override
                public void onSurfaceTextureSizeChanged(
                        final SurfaceTexture texture, final int width, final int height) {
                    CameraUtils.configureTransform(width, height);
                }

                @Override
                public boolean onSurfaceTextureDestroyed(final SurfaceTexture texture) {
                    return true;
                }

                @Override
                public void onSurfaceTextureUpdated(final SurfaceTexture texture) {
                }
            };

    public static CameraTestFragment newInstance() {
        return new CameraTestFragment();
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        mContext = getContext();
        return mLayout;
    }

    @Override
    protected int getLayoutId() {
        return R.layout.fragment_camera_test;
    }

    @Override
    public void onViewCreated(final View view, final Bundle savedInstanceState) {
        initView(view);
        initFilterSheet();
        initCamera();
    }

    private void initView(View view) {
        textureView = (AutoFitTextureView) view.findViewById(R.id.texture);
        mScoreView = (TrasparentTitleView) view.findViewById(R.id.results);
        ivDraw = (ImageView) view.findViewById(R.id.iv_draw);

        CheckBox checkShowCrop = (CheckBox) view.findViewById(R.id.check_show_crop);
        CheckBox checkShowModel = (CheckBox) view.findViewById(R.id.check_show_model);
        CheckBox checkLandMark = (CheckBox) view.findViewById(R.id.check_land_mark);
        CheckBox checkDrawMode = (CheckBox) view.findViewById(R.id.check_draw_mode);
        btnBuildModel = (Button) view.findViewById(R.id.btn_build_model);
        btnShowFilter = (Button) view.findViewById(R.id.btn_show_filter);

        checkShowCrop.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
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
                CameraTestFragment.AccelerometerRenderer renderer = ((CameraTestFragment.AccelerometerRenderer) mRenderer);
                renderer.mMonkey.setVisible(isChecked);
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
                ((CameraTestFragment.AccelerometerRenderer) mRenderer).toggleWireframe();
            }
        });

        btnBuildModel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mOnGetPreviewListener.setIsNeedMask(true);
            }
        });

        btnShowFilter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                btnBuildModel.setVisibility(View.GONE);
                btnShowFilter.setVisibility(View.GONE);
                mFilterSheet.show();
            }
        });
    }

    private void initFilterSheet() {
        mFilterData = new ArrayList<>();
        for (int i=0; i<20; i++) {
            mFilterData.add(i);
        }

        mFilterAdapter = new FilterAdapter(mContext, mFilterData);
        mFilterAdapter.setOnItemClickListener(new FilterAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(int position) {
                String resName = "filter" + position;
                int resId = getResources().getIdentifier(resName, "string", mContext.getPackageName());
                mCameraRenderer.setSelectedFilter(resId);
            }
        });

        View sheetView = LayoutInflater.from(mContext)
                .inflate(R.layout.layout_filter_sheet, null);
        mRvFilter = (RecyclerView) sheetView.findViewById(R.id.rv_filter);
        mRvFilter.setAdapter(mFilterAdapter);
        mRvFilter.setLayoutManager(new LinearLayoutManager(mContext, LinearLayoutManager.HORIZONTAL, false));
        mFilterSheet = new CustomBottomSheet(mContext);
        mFilterSheet.setContentView(sheetView);
        mFilterSheet.getWindow().findViewById(R.id.design_bottom_sheet)
                .setBackgroundResource(android.R.color.transparent);
        mFilterSheet.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        mFilterSheet.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                btnBuildModel.setVisibility(View.VISIBLE);
                btnShowFilter.setVisibility(View.VISIBLE);
            }
        });
    }

    private void initCamera() {
        CameraManager cameraManager = (CameraManager) getActivity().getSystemService(Context.CAMERA_SERVICE);
        int orientation = getResources().getConfiguration().orientation;
        int rotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();
        CameraUtils.init(textureView, cameraManager, orientation, rotation);
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
            CameraUtils.openCamera(textureView.getWidth(), textureView.getHeight());
        } else {
            if (mOnGetPreviewListener == null) {
                initGetPreviewListener();
            }
            if (mCameraRenderer == null) {
                mCameraRenderer = new MyCameraRenderer(mContext);
            }
//            textureView.setSurfaceTextureListener(surfaceTextureListener);
            textureView.setSurfaceTextureListener(mCameraRenderer);
        }
    }

    @Override
    public void onPause() {
        CameraUtils.closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        CameraUtils.releaseReferences();
    }

    private void initGetPreviewListener() {
        mOnGetPreviewListener = new OnGetImageListener();
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
                if (!isDrawLandMark) {
                    mUIHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            ivDraw.setImageResource(0);
                        }
                    });
                    return;
                }
                inferenceHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (results != null && results.size() > 0) {
                            VisionDetRet ret = results.get(0);
                            float resizeRatio = 1.0f;
                            //float resizeRatio = 2.5f;    // 预览尺寸 480x320  /  截取尺寸 192x128  (另外悬浮窗尺寸是 810x540)
                            Rect bounds = new Rect();
                            bounds.left = (int) (ret.getLeft() * resizeRatio);
                            bounds.top = (int) (ret.getTop() * resizeRatio);
                            bounds.right = (int) (ret.getRight() * resizeRatio);
                            bounds.bottom = (int) (ret.getBottom() * resizeRatio);

                            Size previewSize = CameraUtils.getPreviewSize();
                            if (previewSize != null) {
                                final Bitmap mBitmap = Bitmap.createBitmap(previewSize.getHeight(), previewSize.getWidth(), Bitmap.Config.ARGB_8888);
                                Canvas canvas = new Canvas(mBitmap);
                                canvas.drawRect(bounds, mFaceLandmarkPaint);

                                ArrayList<Point> landmarks = ret.getFaceLandmarks();
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
                        }
                    }
                });
            }

            @Override
            public void onRotateChange(float x, float y, float z) {
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

                    ((AccelerometerRenderer) mRenderer).setAccelerometerValues(rotateZ, rotateY, -rotateX);

                    if (!isJumpX) lastX = x;
                    if (!isJumpY) lastY = y;
                    if (!isJumpZ) lastZ = z;
                }
            }

            @Override
            public void onTransChange(float x, float y, float z) {
                AccelerometerRenderer renderer = ((AccelerometerRenderer) mRenderer);
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
                        OBJUtils.buildFaceModel(getContext(), bitmap, landmarks);
                        isBuildMask = true;
                    }
                });
            }
        });

        CameraUtils.setOnGetPreviewListener(mOnGetPreviewListener);
    }

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

    private void showDialog(final String title, final String content) {
        mDialog = ProgressDialog.show(mContext, title, content, true);
    }

    private void dismissDialog() {
        if (mDialog != null) {
            mDialog.dismiss();
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

        CameraUtils.setBackgroundHandler(backgroundHandler);
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

    @Override
    public ISurfaceRenderer createRenderer() {
        return new CameraTestFragment.AccelerometerRenderer(getActivity(), this);
    }

    @Override
    protected void onBeforeApplyRenderer() {
        ((SurfaceView) mRenderSurface).setTransparent(true);
        super.onBeforeApplyRenderer();
    }

    private final class AccelerometerRenderer extends AExampleRenderer {
        private DirectionalLight mLight;
        private Object3D mContainer;
        private Object3D mMonkey;
        private Vector3 mAccValues;

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

        @Override
        protected void onRender(long ellapsedRealtime, double deltaTime) {
            super.onRender(ellapsedRealtime, deltaTime);
            mContainer.setRotation(mAccValues.x, mAccValues.y, mAccValues.z);

            if (isBuildMask) {
                showMaskModel();
                isBuildMask = false;
            }
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
                if (mMonkey != null) {
                    mMonkey.setY(0);
                    mContainer.removeChild(mMonkey);
                }

                //String mImagePath = "/storage/emulated/0/dlib/20130821040137899.jpg";
                String mImagePath = "/storage/emulated/0/BuildMask/capture_face.jpg";
                String objDir ="BuildMask" + File.separator;
                String objName = FileUtils.getMD5(mImagePath) + "_obj";
                LoaderOBJ parser = new LoaderOBJ(this, objDir + objName);
                parser.parse();
                mMonkey = parser.getParsedObject();
                ATexture texture = mMonkey.getMaterial().getTextureList().get(0);
                mMonkey.getMaterial().removeTexture(texture);
                mMonkey.setScale(0.06f);
                mMonkey.setY(-0.54f);
                mMonkey.setZ(0.25f);

                File sdcard = Environment.getExternalStorageDirectory();
                String textureDir = sdcard.getAbsolutePath() + File.separator + "BuildMask" + File.separator;
                String textureName = FileUtils.getMD5(mImagePath) + ".jpg";
                Bitmap bitmap = BitmapUtils.decodeSampledBitmapFromFilePath(textureDir + textureName, 1024, 1024);
                mMonkey.getMaterial().addTexture(new Texture("canvas", bitmap));
                mMonkey.getMaterial().enableLighting(false);

                mContainer.addChild(mMonkey);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
