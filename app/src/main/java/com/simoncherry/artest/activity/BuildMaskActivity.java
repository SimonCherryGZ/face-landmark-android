package com.simoncherry.artest.activity;

import android.app.ProgressDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.alibaba.fastjson.JSON;
import com.simoncherry.artest.R;
import com.simoncherry.artest.util.BitmapUtils;
import com.simoncherry.artest.util.FileUtils;
import com.simoncherry.dlib.Constants;
import com.simoncherry.dlib.FaceDet;
import com.simoncherry.dlib.VisionDetRet;

import org.androidannotations.annotations.Background;
import org.androidannotations.annotations.Click;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.UiThread;
import org.androidannotations.annotations.ViewById;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import hugo.weaving.DebugLog;

@EActivity(R.layout.activity_build_mask)
public class BuildMaskActivity extends AppCompatActivity {

    private static final String TAG = BuildMaskActivity.class.getSimpleName();
    private static final int RESULT_LOAD_IMG = 1;

    @ViewById(R.id.iv_face)
    protected ImageView ivFace;
    @ViewById(R.id.btn_load_face)
    protected Button btnLoadFace;
    @ViewById(R.id.btn_create_obj)
    protected Button btnCreateOBJ;
    @ViewById(R.id.btn_load_obj)
    protected Button btnLoadOBJ;

    protected String mTestImgPath;
    FaceDet mFaceDet;
    private ProgressDialog mDialog;


//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_build_mask);
//    }

    @Click({R.id.btn_load_face})
    protected void launchGallery() {
        Toast.makeText(BuildMaskActivity.this, "Pick one image", Toast.LENGTH_SHORT).show();
        Intent galleryIntent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(galleryIntent, RESULT_LOAD_IMG);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RESULT_LOAD_IMG && resultCode == RESULT_OK && null != data) {
            Uri selectedImage = data.getData();
            String[] filePathColumn = {MediaStore.Images.Media.DATA};
            Cursor cursor = getContentResolver().query(selectedImage, filePathColumn, null, null, null);
            cursor.moveToFirst();
            int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
            mTestImgPath = cursor.getString(columnIndex);
            cursor.close();
            if (mTestImgPath != null) {
                runDetectAsync(mTestImgPath);
                Toast.makeText(this, "Img Path:" + mTestImgPath, Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "You haven't picked Image", Toast.LENGTH_LONG).show();
        }
    }

    @Background
    protected void runDetectAsync(@NonNull final String imgPath) {
        showDialog();

        final String targetPath = Constants.getFaceShapeModelPath();
        if (!new File(targetPath).exists()) {
            throw new RuntimeException("cannot find shape_predictor_68_face_landmarks.dat");
        }
        // Init
        if (mFaceDet == null) {
            mFaceDet = new FaceDet(Constants.getFaceShapeModelPath());
        }

        Log.i(TAG, "Image path: " + imgPath);
        Bitmap face = BitmapUtils.decodeSampledBitmapFromFilePath(imgPath, 1024, 1024);
        int faceWidth = face.getWidth();
        int faceHeight = face.getHeight();
        Bitmap bitmap = Bitmap.createBitmap(1024, 1024, Bitmap.Config.RGB_565);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawBitmap(face, (1024-faceWidth)/2, (1024-faceHeight)/2, null);
        canvas.save(Canvas.ALL_SAVE_FLAG);
        canvas.restore();
        face.recycle();
        face = null;

        File sdcard = Environment.getExternalStorageDirectory();
        String textureDir = sdcard.getAbsolutePath() + File.separator + "BuildMask" + File.separator;
        String textureName = "build_mask_texture";
        FileUtils.saveBitmapToFile(this, bitmap, textureDir, textureName + ".jpg");
        bitmap.recycle();
        bitmap = null;

        final String texturePath = textureDir + textureName + ".jpg";
        final List<VisionDetRet> faceList = mFaceDet.detect(texturePath);
        if (faceList != null && faceList.size() > 0) {
            saveLandmarkTxt(faceList.get(0).getFaceLandmarks(), textureDir, textureName);

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ivFace.setImageDrawable(drawRect(texturePath, faceList, Color.GREEN));
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

        dismissDialog();
    }

    @UiThread
    protected void showDialog() {
        mDialog = ProgressDialog.show(BuildMaskActivity.this, "Wait", "Person and face detection", true);
    }

    @UiThread
    protected void dismissDialog() {
        if (mDialog != null) {
            mDialog.dismiss();
        }
    }

    private void saveLandmarkTxt(ArrayList<Point> landmarks, String path, String name) {
        String jsonString = JSON.toJSONString(landmarks);
        Log.i(TAG, "landmarks: " + jsonString);

        String fileName = path + name + ".txt";
        try {
            int i = 0;
            FileWriter writer = new FileWriter(fileName);
            for (Point point : landmarks) {
                int pointX = point.x;
                int pointY = point.y ;
                String landmark = String.valueOf(pointX) + " " + String.valueOf(pointY) + "\n";
                Log.i(TAG, "write landmark[" + String.valueOf(i) + "]: " + landmark);
                i++;
                writer.write(landmark);
            }
            writer.flush();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
            Log.i(TAG, e.toString());
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
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bm, newWidth, newHeight, true);
        return resizedBitmap;
    }
}
