package com.simoncherry.artest.activity;

import android.app.ProgressDialog;
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
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
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

    private String mCurrentImgPath = null;


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

    @Click({R.id.btn_create_obj})
    protected void createOBJ() {
        doCreateObjFile();
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
        mCurrentImgPath = imgPath;
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
        String textureName = FileUtils.getMD5(imgPath);
        FileUtils.saveBitmapToFile(this, bitmap, textureDir, textureName + ".jpg");
        bitmap.recycle();
        bitmap = null;

        final String texturePath = textureDir + textureName + ".jpg";
        final List<VisionDetRet> faceList = mFaceDet.detect(texturePath);
        if (faceList != null && faceList.size() > 0) {
            ArrayList<Point> landmarks = faceList.get(0).getFaceLandmarks();
            saveLandmarkTxt(landmarks, textureDir, textureName);
            saveLandmark2Vertices(landmarks, textureDir, textureName);
            saveUVMapCoordinate(landmarks, textureDir, textureName);

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    //ivFace.setImageDrawable(drawRect(texturePath, faceList, Color.GREEN));
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

    private void saveLandmark2Vertices(ArrayList<Point> landmarks, String path, String name) {
        ArrayList<Point> vertices = new ArrayList<>();
        for (int i=0; i<40; i++) {
            vertices.add(new Point(0, 0));
        }

        for (int i=0; i<vertices.size(); i++) {
            vertices.set(i, getVertexByIndex(landmarks, i));
        }

        Point chin_point = landmarks.get(8);
        Point what_point = vertices.get(0);
        float z_scale = (what_point.x - chin_point.x) / 30f / -2.15f;

        ArrayList<Float> zAxisPoints = new ArrayList<>();
        InputStream is = getResources().openRawResource(R.raw.z_axis);
        try {
            InputStreamReader reader = new InputStreamReader(is);
            BufferedReader br = new BufferedReader(reader);
            for(String str; (str = br.readLine()) != null; ) {  // 这里不能用while(br.readLine()) != null) 因为循环条件已经读了一条
                zAxisPoints.add(Float.parseFloat(str));
            }
            br.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
        Log.i(TAG, "get z-axis: " + zAxisPoints.toString());

        DecimalFormat decimalFormat = new DecimalFormat(".000000");
        String fileName = path + name + "_vertices.txt";
        try {
            int i = 0;
            FileWriter writer = new FileWriter(fileName);
            for (Point point : vertices) {
                float pointZ = zAxisPoints.get(i) * z_scale;
                float pointX = (point.x - chin_point.x) / 30f;
                float pointY = (point.y - chin_point.y) / -30f;
                String landmark = "v " + decimalFormat.format(pointX) + " " + decimalFormat.format(pointY) + " " + decimalFormat.format(pointZ) + "\n";
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

    private Point getVertexByIndex(ArrayList<Point> landmarks, int index) {
        switch (index) {
            case 0:
                return landmarks.get(36);
            case 1:
                return landmarks.get(39);
            case 2:
                return getMeanPoint(landmarks, 40, 41);
            case 3:
                return getMeanPoint(landmarks, 37, 38);
            case 4:
                return landmarks.get(20);
            case 5:
                return landmarks.get(27);
            case 6:
                return landmarks.get(23);
            case 7:
                return landmarks.get(42);
            case 8:
                return landmarks.get(35);
            case 9:
                return getMeanPoint(landmarks, 43, 44);
            case 10:
                return landmarks.get(45);
            case 11:
                return getMeanPoint(landmarks, 46, 47);
            case 12:
                return getMeanPoint(landmarks, 31, 35);
            case 13:
                return landmarks.get(31);
            case 14:
                return landmarks.get(33);
            case 15:
                return landmarks.get(62);
            case 16:
                return landmarks.get(48);
            case 17:
                return landmarks.get(54);
            case 18:
                return landmarks.get(57);
            case 19:
            case 31:
                return landmarks.get(8);
            case 20:
                return getMeanPoint(landmarks, 20, 23);
            case 21:
                return landmarks.get(25);
            case 22:
                return landmarks.get(16);
            case 23:
            case 36:
                return landmarks.get(15);
            case 24:
            case 34:
                return landmarks.get(12);
            case 25:
            case 32:
                return landmarks.get(10);
            case 26:
                return landmarks.get(18);
            case 27:
                return landmarks.get(0);
            case 28:
            case 37:
                return landmarks.get(1);
            case 29:
            case 35:
                return landmarks.get(4);
            case 30:
            case 33:
                return landmarks.get(6);
            case 38:
                return getMeanPoint(landmarks, 36, 39);
            case 39:
                return getMeanPoint(landmarks, 42, 45);
            default:
                return landmarks.get(0);
        }
    }

    private Point getMeanPoint(ArrayList<Point> landmarks, int p1, int p2) {
        Point point1 = landmarks.get(p1);
        Point point2 = landmarks.get(p2);
        return new Point((point1.x+point2.x)/2, (point1.y+point2.y)/2);
    }

    private void saveUVMapCoordinate(ArrayList<Point> landmarks, String path, String name) {
        ArrayList<Point> coordinates = new ArrayList<>();
        for (int i=0; i<40; i++) {
            coordinates.add(new Point(0, 0));
        }

        for (int i=0; i<coordinates.size(); i++) {
            coordinates.set(i, getCoordinateByIndex(landmarks, i));
        }

        DecimalFormat decimalFormat = new DecimalFormat(".0000");
        String fileName = path + name + "_coordinates.txt";
        try {
            int i = 0;
            FileWriter writer = new FileWriter(fileName);
            for (Point point : coordinates) {
                float x = point.x / 1024.0f;
                float y = 1 - point.y / 1024.0f;
                String string = "vt " + decimalFormat.format(x) + " " + decimalFormat.format(y) + "\n";
                Log.i(TAG, "write coordinates[" + String.valueOf(i) + "]: " + string);
                i++;
                writer.write(string);
            }
            writer.flush();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
            Log.i(TAG, e.toString());
        }
    }

    private Point getCoordinateByIndex(ArrayList<Point> landmarks, int index) {
        switch (index) {
            case 0:
                return landmarks.get(18);
            case 1:
                return landmarks.get(0);
            case 2:
                return landmarks.get(36);
            case 3:
                return getDoNotKnowHowToSay(landmarks, 17, 0, 1);
            case 4:
                return getIntersectPoint(landmarks, 5, 3);
            case 5:
                return getIntersectPoint(landmarks, 6, 5);
            case 6:
                return landmarks.get(48);
            case 7:
                return getDoNotKnowHowToSay(landmarks, 8, 6, 7);
            case 8:
                return landmarks.get(57);
            case 9:
                return landmarks.get(62);
            case 10:
                return landmarks.get(33);
            case 11:
                return landmarks.get(31);
            case 12:
                return getMeanPoint(landmarks, 31, 35);
            case 13:
                return landmarks.get(27);
            case 14:
                return getMeanPoint(landmarks, 40, 41);
            case 15:
                return landmarks.get(39);
            case 16:
                return landmarks.get(20);
            case 17:
                return getMeanPoint(landmarks, 37, 38);
            case 18:
                return getMeanPoint(landmarks, 20, 23);
            case 19:
                return landmarks.get(23);
            case 20:
                return landmarks.get(42);
            case 21:
                return landmarks.get(35);
            case 22:
                return getMeanPoint(landmarks, 46, 47);
            case 23:
                return landmarks.get(45);
            case 24:
                return getMeanPoint(landmarks, 43, 44);
            case 25:
                return landmarks.get(25);
            case 26:
                return landmarks.get(16);
            case 27:
                return getDoNotKnowHowToSay(landmarks, 26, 15, 16);
            case 28:
                return landmarks.get(54);
            case 29:
                return getIntersectPoint(landmarks, 10, 12);
            case 30:
                return getIntersectPoint(landmarks, 11, 13);
            case 31:
                return landmarks.get(15);
            case 32:
                return landmarks.get(12);
            case 33:
                return landmarks.get(10);
            case 34:
                return landmarks.get(8);
            case 35:
                return landmarks.get(6);
            case 36:
                return landmarks.get(1);
            case 37:
                return landmarks.get(4);
            case 38:
                return getMeanPoint(landmarks, 36, 39);
            case 39:
                return getMeanPoint(landmarks, 42, 45);
            default:
                return landmarks.get(0);
        }
    }

    private Point getIntersectPoint(ArrayList<Point> landmarks, int p1, int p2) {
        Point point1 = landmarks.get(p1);
        Point point2 = landmarks.get(p2);
        return new Point(point1.x, point2.y);
    }

    private Point getDoNotKnowHowToSay(ArrayList<Point> landmarks, int p1, int p2, int p3) {
        Point point1 = landmarks.get(p1);
        Point point2 = landmarks.get(p2);
        Point point3 = landmarks.get(p3);
        return new Point(point1.x, (point2.y + point3.y)/2);
    }

    private void doCreateObjFile() {
        if (mCurrentImgPath == null) {
            Toast.makeText(BuildMaskActivity.this, "没有找到人脸图片", Toast.LENGTH_SHORT).show();
            return;
        }
        // 读取预设模型base_mask_obj
        StringBuilder stringBuilder = new StringBuilder();
        InputStream is = getResources().openRawResource(R.raw.base_mask_obj);
        try {
            InputStreamReader reader = new InputStreamReader(is);
            BufferedReader br = new BufferedReader(reader);
            for(String str; (str = br.readLine()) != null; ) {  // 这里不能用while(br.readLine()) != null) 因为循环条件已经读了一条
                stringBuilder.append(str).append("\n");
            }
            br.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
        String obj_str = stringBuilder.toString();
        Log.i(TAG, "read base_mask_obj: " + obj_str);

        // 替换新的顶点和UV坐标
        String[] ss = obj_str.split("\n");
        List<String> vertices = readVerticesFromTxt();
        List<String> coordinates = readCoordinatesFromTxt();

        for (int i=4; i<44; i++) {
            ss[i] = vertices.get(i-4);
        }

        for (int i=44; i<84; i++) {
            ss[i] = coordinates.get(i-44);
        }

        // 保存
        File sdcard = Environment.getExternalStorageDirectory();
        String path = sdcard.getAbsolutePath() + File.separator + "BuildMask" + File.separator;
        String name = FileUtils.getMD5(mCurrentImgPath);
        String fileName = path + name + "_obj";

        try {
            int i = 0;
            FileWriter writer = new FileWriter(fileName);
            for (String s : ss) {
                Log.i(TAG, "write obj[" + String.valueOf(i) + "]: " + s);
                i++;
                writer.write(s + "\n");
            }
            writer.flush();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
            Log.i(TAG, e.toString());
        }

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(BuildMaskActivity.this, "Done!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private List<String> readVerticesFromTxt() {
        File sdcard = Environment.getExternalStorageDirectory();
        String path = sdcard.getAbsolutePath() + File.separator + "BuildMask" + File.separator;
        String name = FileUtils.getMD5(mCurrentImgPath) + "_vertices.txt";
        String fileName = path + name;

        final List<String> vertices = new ArrayList<>();
        try {
            FileReader fileReader = new FileReader(fileName);
            BufferedReader br = new BufferedReader(fileReader);
            for(String str; (str = br.readLine()) != null; ) {  // 这里不能用while(br.readLine()) != null) 因为循环条件已经读了一条
                vertices.add(str);
            }
            br.close();

        } catch (Exception e) {
            e.printStackTrace();
        }

        return vertices;
    }

    private List<String> readCoordinatesFromTxt() {
        File sdcard = Environment.getExternalStorageDirectory();
        String path = sdcard.getAbsolutePath() + File.separator + "BuildMask" + File.separator;
        String name = FileUtils.getMD5(mCurrentImgPath) + "_coordinates.txt";
        String fileName = path + name;

        final List<String> coordinate = new ArrayList<>();
        try {
            FileReader fileReader = new FileReader(fileName);
            BufferedReader br = new BufferedReader(fileReader);
            for(String str; (str = br.readLine()) != null; ) {  // 这里不能用while(br.readLine()) != null) 因为循环条件已经读了一条
                coordinate.add(str);
            }
            br.close();

        } catch (Exception e) {
            e.printStackTrace();
        }

        return coordinate;
    }
}
