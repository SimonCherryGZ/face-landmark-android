package com.simoncherry.artest.ui.activity;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.simoncherry.artest.R;
import com.simoncherry.artest.util.BitmapUtils;
import com.simoncherry.artest.util.DialogUtils;
import com.simoncherry.artest.util.FileUtils;
import com.simoncherry.artest.util.JNIUtils;
import com.simoncherry.artest.util.OBJUtils;
import com.simoncherry.dlib.VisionDetRet;

import org.androidannotations.annotations.Background;
import org.androidannotations.annotations.Click;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.UiThread;
import org.androidannotations.annotations.ViewById;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import hugo.weaving.DebugLog;

@EActivity(R.layout.activity_build_mask)
public class BuildMaskActivity extends AppCompatActivity {

    private static final String TAG = BuildMaskActivity.class.getSimpleName();
    private static final int RESULT_LOAD_IMG = 123;
    private static final int RESULT_FOR_SWAP = 456;

    @ViewById(R.id.iv_face)
    protected ImageView ivFace;
    @ViewById(R.id.btn_load_face)
    protected Button btnLoadFace;
    @ViewById(R.id.btn_create_obj)
    protected Button btnCreateOBJ;
    @ViewById(R.id.btn_load_obj)
    protected Button btnLoadOBJ;
    @ViewById(R.id.btn_swap_face)
    protected Button btnSwapFace;

    private ProgressDialog mDialog;
    private String mCurrentImgPath = null;


    @Click({R.id.btn_load_face})
    protected void launchGallery() {
        Toast.makeText(BuildMaskActivity.this, "选择一张人脸图片", Toast.LENGTH_SHORT).show();
        Intent galleryIntent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(galleryIntent, RESULT_LOAD_IMG);
    }

    @Click({R.id.btn_create_obj})
    protected void createOBJ() {
        if (mCurrentImgPath == null) {
            Toast.makeText(BuildMaskActivity.this, "没有找到人脸图片", Toast.LENGTH_SHORT).show();
            return;
        }

        String objDir = OBJUtils.getModelDir();
        String objName = FileUtils.getMD5(mCurrentImgPath);
        String objPath = objDir + objName + "_obj";
        File file = new File(objPath);
        if (!file.exists()) {
            OBJUtils.createObjFile(BuildMaskActivity.this, mCurrentImgPath);
            Toast.makeText(BuildMaskActivity.this, "Done!", Toast.LENGTH_SHORT).show();
        } else {
            DialogUtils.showDialog(this, "该人脸OBJ文件已存在", "是否重新生成？", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    OBJUtils.createObjFile(BuildMaskActivity.this, mCurrentImgPath);
                }
            });
        }
    }

    @Click({R.id.btn_load_obj})
    protected void showMaskModel() {
        if (mCurrentImgPath == null) {
            Toast.makeText(BuildMaskActivity.this, "没有找到人脸图片", Toast.LENGTH_SHORT).show();
        } else {
            Intent intent = new Intent(this, ShowMaskActivity.class);
            intent.putExtra(ShowMaskActivity.IMG_KEY, mCurrentImgPath);
            startActivity(intent);
        }
    }

    @Click({R.id.btn_swap_face})
    protected void swapFaceAndCreateNewTexture() {
        if (mCurrentImgPath == null) {
            Toast.makeText(BuildMaskActivity.this, "没有找到人脸图片", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(BuildMaskActivity.this, "选择替换的人脸图片", Toast.LENGTH_SHORT).show();
            Intent galleryIntent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(galleryIntent, RESULT_FOR_SWAP);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && null != data) {
            Uri selectedImage = data.getData();
            String[] filePathColumn = {MediaStore.Images.Media.DATA};
            Cursor cursor = getContentResolver().query(selectedImage, filePathColumn, null, null, null);
            cursor.moveToFirst();
            int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
            String imgPath = cursor.getString(columnIndex);

            if (requestCode == RESULT_LOAD_IMG) {
                doTask1(imgPath);
            } else {
                doTask2(imgPath);
            }

            cursor.close();
        } else {
            Toast.makeText(this, "取消选择", Toast.LENGTH_LONG).show();
        }
    }

    private void doTask1(String imgPath) {
        mCurrentImgPath = imgPath;
        if (mCurrentImgPath != null) {
            ivFace.setImageBitmap(BitmapUtils.decodeSampledBitmapFromFilePath(mCurrentImgPath, 1024, 1024));
            Toast.makeText(this, "Img Path:" + mCurrentImgPath, Toast.LENGTH_SHORT).show();

            String landmarkDir = OBJUtils.getModelDir();
            String landmarkName = FileUtils.getMD5(mCurrentImgPath);
            String landmarkPath = landmarkDir + landmarkName + ".txt";
            File file = new File(landmarkPath);
            if (!file.exists()) {
                createVerticesAndCoordinates(mCurrentImgPath);
            } else {
                DialogUtils.showDialog(this, "该人脸关键点txt已存在", "是否重新生成？", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        createVerticesAndCoordinates(mCurrentImgPath);
                    }
                });
            }
        }
    }

    private void doTask2(String swapPath) {
        String landmarkDir = OBJUtils.getModelDir();

        String[] pathArray = new String[2];
        pathArray[0] = swapPath;
        pathArray[1] = mCurrentImgPath;

        showDialog();
        for (int i=0; i<2; i++) {
            String landmarkName = FileUtils.getMD5(pathArray[i]) + OBJUtils.TXT_LANDMARK;
            File file = new File(landmarkDir + landmarkName);
            //0if (!file.exists())
            {
                createLandmark(pathArray[i]);
            }
        }
        dismissDialog();

        doFaceSwap(pathArray);
    }

    @Background
    protected void doFaceSwap(String[] pathArray) {
        showDialog();
        final String result = JNIUtils.doFaceSwap(pathArray);

        if (result != null) {
            final File file = new File(result);
            if (file.exists()) {
                OBJUtils.createTexture(BuildMaskActivity.this, result, mCurrentImgPath);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ivFace.setImageBitmap(BitmapUtils.decodeSampledBitmapFromFilePath(result, 1024, 1024));
                    }
                });
            } else {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(BuildMaskActivity.this, "cannot do face swap!", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        } else {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(BuildMaskActivity.this, "cannot do face swap!", Toast.LENGTH_SHORT).show();
                }
            });
        }

        dismissDialog();
    }

    protected void createLandmark(@NonNull final String imgPath) {
        List<VisionDetRet> faceList = OBJUtils.getFaceDet().detect(imgPath);
        if (faceList != null && faceList.size() > 0) {
            String landmarkDir = OBJUtils.getModelDir();
            String landmarkName = FileUtils.getMD5(imgPath) + OBJUtils.TXT_LANDMARK;
            OBJUtils.writeLandmarkToDisk(faceList.get(0).getFaceLandmarks(), landmarkDir, landmarkName);

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(BuildMaskActivity.this, "Done!", Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getApplicationContext(), "No face", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    @Background
    protected void createVerticesAndCoordinates(@NonNull final String imgPath) {
        showDialog();
        OBJUtils.createVerticesAndCoordinates(BuildMaskActivity.this, imgPath);
        dismissDialog();
    }

    @UiThread
    protected void showDialog() {
        mDialog = ProgressDialog.show(BuildMaskActivity.this, "Wait", "Processing...", true);
    }

    @UiThread
    protected void dismissDialog() {
        if (mDialog != null) {
            mDialog.dismiss();
        }
    }

    @DebugLog
    protected BitmapDrawable drawRect(Bitmap bm, List<VisionDetRet> results, int color) {
        android.graphics.Bitmap.Config bitmapConfig = bm.getConfig();
        // set default bitmap config if none
        if (bitmapConfig == null) {
            bitmapConfig = android.graphics.Bitmap.Config.ARGB_8888;
        }
        // resource bitmaps are imutable,
        // so we need to convert it to mutable one
        bm = bm.copy(bitmapConfig, true);
        int width = bm.getWidth();
        int height = bm.getHeight();
        Log.i(TAG, "bm.getWidth(): " + width);
        Log.i(TAG, "bm.getHeight(): " + height);
        // By ratio scale
        float aspectRatio = bm.getWidth() / (float) bm.getHeight();

        final int MAX_SIZE = ivFace.getWidth();
        Log.i(TAG, "ivFace.getWidth(): " + MAX_SIZE);
        int newWidth = MAX_SIZE;
        int newHeight = MAX_SIZE;
        float resizeRatio = 1;
        newHeight = Math.round(newWidth / aspectRatio);
        if (bm.getWidth() > MAX_SIZE && bm.getHeight() > MAX_SIZE) {
            Log.i(TAG, "Resize Bitmap");
            bm = getResizedBitmap(bm, newWidth, newHeight);
            resizeRatio = (float) bm.getWidth() / (float) width;
            Log.i(TAG, "resizeRatio " + resizeRatio);
        }

        // Create canvas to draw
        Canvas canvas = new Canvas(bm);
        Paint paint = new Paint();
        paint.setColor(color);
        paint.setStrokeWidth(2);
        paint.setStyle(Paint.Style.STROKE);
        // Loop result list
        //for (VisionDetRet ret : results)
        {
            VisionDetRet ret = results.get(0);
            Rect bounds = new Rect();
            bounds.left = (int) (ret.getLeft() * resizeRatio);
            bounds.top = (int) (ret.getTop() * resizeRatio);
            bounds.right = (int) (ret.getRight() * resizeRatio);
            bounds.bottom = (int) (ret.getBottom() * resizeRatio);
            canvas.drawRect(bounds, paint);
            // Get landmark
            ArrayList<Point> landmarks = ret.getFaceLandmarks();
            for (Point point : landmarks) {
                Log.e(TAG, point.toString());
                int pointX = (int) (point.x * resizeRatio);
                int pointY = (int) (point.y * resizeRatio);
                canvas.drawCircle(pointX, pointY, 2, paint);
            }
        }

        return new BitmapDrawable(getResources(), bm);
    }

    @DebugLog
    protected BitmapDrawable drawRect(String path, List<VisionDetRet> results, int color) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        Bitmap bm = BitmapFactory.decodeFile(path, options);
        return drawRect(bm, results, color);
    }

    @DebugLog
    protected Bitmap getResizedBitmap(Bitmap bm, int newWidth, int newHeight) {
        return Bitmap.createScaledBitmap(bm, newWidth, newHeight, true);
    }
}
