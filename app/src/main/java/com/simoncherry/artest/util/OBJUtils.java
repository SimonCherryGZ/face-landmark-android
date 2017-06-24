package com.simoncherry.artest.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Point;
import android.os.Environment;
import android.util.Log;

import com.alibaba.fastjson.JSON;
import com.simoncherry.artest.R;
import com.simoncherry.dlib.Constants;
import com.simoncherry.dlib.FaceDet;
import com.simoncherry.dlib.VisionDetRet;

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

/**
 * Created by Simon on 2017/6/22.
 */

public class OBJUtils {
    private final static String TAG = OBJUtils.class.getSimpleName();

    private static FaceDet mFaceDet = null;

    public static FaceDet getFaceDet() {
        if (mFaceDet == null) {
            final String targetPath = Constants.getFaceShapeModelPath();
            if (!new File(targetPath).exists()) {
                throw new RuntimeException("cannot find shape_predictor_68_face_landmarks.dat");
            }
            mFaceDet = new FaceDet(Constants.getFaceShapeModelPath());
        }
        return mFaceDet;
    }

    public static void buildModel(Context context, Bitmap bitmap, ArrayList<Point> landmarks) {
        File sdcard = Environment.getExternalStorageDirectory();
        String faceDir = sdcard.getAbsolutePath() + File.separator + "BuildMask" + File.separator;
        String faceName = "capture_face.jpg";

        Matrix mtx = new Matrix();
        mtx.postRotate(-90.0f);
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), mtx, true);
        FileUtils.saveBitmapToFile(context, bitmap, faceDir, faceName);

        String facePath = faceDir + faceName;
        String landmarkName = FileUtils.getMD5(facePath) + "_original";
        saveLandmarkTxt(landmarks, faceDir, landmarkName);

        createVerticesAndCoordinates(context, facePath);

        createObjFile(context, facePath);
    }

    private static void createVerticesAndCoordinates(Context context, String imgPath) {
        Bitmap face = BitmapUtils.decodeSampledBitmapFromFilePath(imgPath, 1024, 1024);
        int faceWidth = face.getWidth();
        int faceHeight = face.getHeight();
        float scale;
        if (faceHeight >= faceWidth) {
            scale = 1024.0f / faceHeight;
        } else {
            scale = 1024.0f / faceWidth;
        }
        Matrix matrix = new Matrix();
        matrix.postScale(scale, scale);// 使用后乘
        face = Bitmap.createBitmap(face, 0, 0, faceWidth, faceHeight, matrix, false);

        faceWidth = face.getWidth();
        faceHeight = face.getHeight();
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
        FileUtils.saveBitmapToFile(context, bitmap, textureDir, textureName + ".jpg");
        bitmap.recycle();
        bitmap = null;

        final String texturePath = textureDir + textureName + ".jpg";
        final List<VisionDetRet> faceList = getFaceDet().detect(texturePath);
        if (faceList != null && faceList.size() > 0) {
            ArrayList<Point> landmarks = faceList.get(0).getFaceLandmarks();
            saveLandmarkTxt(landmarks, textureDir, textureName);
            saveVertices(context, landmarks, textureDir, textureName);
            saveUVMapCoordinate(landmarks, textureDir, textureName);
        }

        Log.e(TAG, "createVerticesAndCoordinates done!");
    }

    private static void createTexture(Context context, String srcPath, String dstPath) {
        Bitmap face = BitmapUtils.decodeSampledBitmapFromFilePath(srcPath, 1024, 1024);
        int faceWidth = face.getWidth();
        int faceHeight = face.getHeight();
        float scale;
        if (faceHeight >= faceWidth) {
            scale = 1024.0f / faceHeight;
        } else {
            scale = 1024.0f / faceWidth;
        }
        Matrix matrix = new Matrix();
        matrix.postScale(scale, scale);// 使用后乘
        face = Bitmap.createBitmap(face, 0, 0, faceWidth, faceHeight, matrix, false);

        faceWidth = face.getWidth();
        faceHeight = face.getHeight();
        Bitmap bitmap = Bitmap.createBitmap(1024, 1024, Bitmap.Config.RGB_565);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawBitmap(face, (1024-faceWidth)/2, (1024-faceHeight)/2, null);
        canvas.save(Canvas.ALL_SAVE_FLAG);
        canvas.restore();
        face.recycle();
        face = null;

        File sdcard = Environment.getExternalStorageDirectory();
        String textureDir = sdcard.getAbsolutePath() + File.separator + "BuildMask" + File.separator;
        String textureName = FileUtils.getMD5(dstPath);
        FileUtils.saveBitmapToFile(context, bitmap, textureDir, textureName + ".jpg");
        bitmap.recycle();
        bitmap = null;
    }

    private static void detectAndSaveLandmark(String imgPath) {
        List<VisionDetRet> faceList = getFaceDet().detect(imgPath);
        if (faceList != null && faceList.size() > 0) {
            File sdcard = Environment.getExternalStorageDirectory();
            String landmarkDir = sdcard.getAbsolutePath() + File.separator + "BuildMask" + File.separator;
            String landmarkName = FileUtils.getMD5(imgPath) + "_original";
            saveLandmarkTxt(faceList.get(0).getFaceLandmarks(), landmarkDir, landmarkName);
        }
    }

    public static void saveLandmarkTxt(ArrayList<Point> landmarks, String imgPath) {
        File sdcard = Environment.getExternalStorageDirectory();
        String landmarkDir = sdcard.getAbsolutePath() + File.separator + "BuildMask" + File.separator;
        String landmarkName = FileUtils.getMD5(imgPath) + "_original";
        File file = new File(landmarkDir + landmarkName + ".txt");
        if (!file.exists()) {
            saveLandmarkTxt(landmarks, landmarkDir, landmarkName);
        }
    }

    private static void saveLandmarkTxt(ArrayList<Point> landmarks, String path, String name) {
        String jsonString = JSON.toJSONString(landmarks);
        Log.e(TAG, "landmarks: " + jsonString);

        String fileName = path + name + ".txt";
        try {
            int i = 0;
            FileWriter writer = new FileWriter(fileName);
            for (Point point : landmarks) {
                int pointX = point.x;
                int pointY = point.y ;
                String landmark = String.valueOf(pointX) + " " + String.valueOf(pointY) + "\n";
                Log.e(TAG, "write landmark[" + String.valueOf(i) + "]: " + landmark);
                i++;
                writer.write(landmark);
            }
            writer.flush();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, e.toString());
        }
    }

    private static void saveVertices(Context context, ArrayList<Point> landmarks, String path, String name) {
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
        InputStream is = context.getResources().openRawResource(R.raw.z_axis);
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
        Log.e(TAG, "get z-axis: " + zAxisPoints.toString());

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
                Log.e(TAG, "write landmark[" + String.valueOf(i) + "]: " + landmark);
                i++;
                writer.write(landmark);
            }
            writer.flush();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, e.toString());
        }
    }

    private static Point getVertexByIndex(ArrayList<Point> landmarks, int index) {
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

    private static Point getMeanPoint(ArrayList<Point> landmarks, int p1, int p2) {
        Point point1 = landmarks.get(p1);
        Point point2 = landmarks.get(p2);
        return new Point((point1.x+point2.x)/2, (point1.y+point2.y)/2);
    }

    private static void saveUVMapCoordinate(ArrayList<Point> landmarks, String path, String name) {
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
                Log.e(TAG, "write coordinates[" + String.valueOf(i) + "]: " + string);
                i++;
                writer.write(string);
            }
            writer.flush();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, e.toString());
        }
    }

    private static Point getCoordinateByIndex(ArrayList<Point> landmarks, int index) {
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

    private static Point getIntersectPoint(ArrayList<Point> landmarks, int p1, int p2) {
        Point point1 = landmarks.get(p1);
        Point point2 = landmarks.get(p2);
        return new Point(point1.x, point2.y);
    }

    private static Point getDoNotKnowHowToSay(ArrayList<Point> landmarks, int p1, int p2, int p3) {
        Point point1 = landmarks.get(p1);
        Point point2 = landmarks.get(p2);
        Point point3 = landmarks.get(p3);
        return new Point(point1.x, (point2.y + point3.y)/2);
    }

    private static void createObjFile(Context context, String mCurrentImgPath) {
        File sdcard = Environment.getExternalStorageDirectory();
        String path = sdcard.getAbsolutePath() + File.separator + "BuildMask" + File.separator + "base_mask.mtl";
        FileUtils.copyFileFromRawToOthers(context, R.raw.base_mask, path);
        path = sdcard.getAbsolutePath() + File.separator + "BuildMask" + File.separator + "base_texture.jpg";
        FileUtils.copyFileFromRawToOthers(context, R.raw.base_texture, path);

        // 读取预设模型base_mask_obj
        StringBuilder stringBuilder = new StringBuilder();
        InputStream is = context.getResources().openRawResource(R.raw.base_mask_obj);
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
        Log.e(TAG, "read base_mask_obj: " + obj_str);

        // 替换新的顶点和UV坐标
        String[] ss = obj_str.split("\n");
        List<String> vertices = readVerticesFromTxt(mCurrentImgPath);
        List<String> coordinates = readCoordinatesFromTxt(mCurrentImgPath);

        for (int i=4; i<44; i++) {
            ss[i] = vertices.get(i-4);
        }

        for (int i=44; i<84; i++) {
            ss[i] = coordinates.get(i-44);
        }

        // 保存
        path = sdcard.getAbsolutePath() + File.separator + "BuildMask" + File.separator;
        String name = FileUtils.getMD5(mCurrentImgPath);
        String fileName = path + name + "_obj";

        try {
            int i = 0;
            FileWriter writer = new FileWriter(fileName);
            for (String s : ss) {
                Log.e(TAG, "write obj[" + String.valueOf(i) + "]: " + s);
                i++;
                writer.write(s + "\n");
            }
            writer.flush();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, e.toString());
        }

        Log.e(TAG, "doCreateObjFile done!");
    }

    private static List<String> readVerticesFromTxt(String mCurrentImgPath) {
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

    private static List<String> readCoordinatesFromTxt(String mCurrentImgPath) {
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

    public static void swapFace(Context context, String[] pathArray,  String resultPath) {
        File sdcard = Environment.getExternalStorageDirectory();
        String landmarkDir = sdcard.getAbsolutePath() + File.separator + "BuildMask" + File.separator;

        for (int i=0; i<2; i++) {
            String landmarkName = FileUtils.getMD5(pathArray[i]) + "_original.txt";
            File file = new File(landmarkDir + landmarkName);
            //if (!file.exists())
            {
                detectAndSaveLandmark(pathArray[i]);
            }
        }

        String swapResult = JNIUtils.doFaceSwap(pathArray);

        if (swapResult != null) {
            final File file = new File(swapResult);
            if (file.exists()) {
                createTexture(context, swapResult, resultPath);
            }
        }
    }
}
