#include <stdio.h>
#include <jni.h>
#include <stdlib.h>
#include <iostream>
#include <fstream>
#include <dirent.h>
#include <opencv2/opencv.hpp>
#include<android/log.h>
/* Header for class com_simoncherry_jnidemo_JNIUtils */
#include "jni_app.h"
#include "md5.h"

#define LOG    "AverageFace-jni" // 这个是自定义的LOG的标识
#define LOGD(...)  __android_log_print(ANDROID_LOG_DEBUG,LOG,__VA_ARGS__) // 定义LOGD类型
#define LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG,__VA_ARGS__) // 定义LOGI类型
#define LOGW(...)  __android_log_print(ANDROID_LOG_WARN,LOG,__VA_ARGS__) // 定义LOGW类型
#define LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG,__VA_ARGS__) // 定义LOGE类型
#define LOGF(...)  __android_log_print(ANDROID_LOG_FATAL,LOG,__VA_ARGS__) // 定义LOGF类型

#ifdef __cplusplus
extern "C" {
#endif

using namespace cv;
using namespace std;
IplImage * change4channelTo3InIplImage(IplImage * src);

IplImage * change4channelTo3InIplImage(IplImage * src) {
    if (src->nChannels != 4) {
        return NULL;
    }

    IplImage * destImg = cvCreateImage(cvGetSize(src), IPL_DEPTH_8U, 3);
    for (int row = 0; row < src->height; row++) {
        for (int col = 0; col < src->width; col++) {
            CvScalar s = cvGet2D(src, row, col);
            cvSet2D(destImg, row, col, s);
        }
    }

    return destImg;
}

IplImage * changeFuckIplImage(IplImage * src) {
    IplImage * destImg = cvCreateImage(cvGetSize(src), IPL_DEPTH_8U, 3);
    for (int row = 0; row < src->height; row++) {
        for (int col = 0; col < src->width; col++) {
            CvScalar s = cvGet2D(src, row, col);
            cvSet2D(destImg, row, col, s);
        }
    }

    return destImg;
}

/*
 * Class:     com_simoncherry_artest_util_JNIUtils
 * Method:    doGrayScale
 * Signature: ([III)[I
 */
JNIEXPORT jintArray JNICALL Java_com_simoncherry_artest_util_JNIUtils_doGrayScale
        (JNIEnv *env, jclass obj, jintArray buf, jint w, jint h) {
    LOGE("doGrayScale Start");

    jint *cbuf;
    cbuf = env->GetIntArrayElements(buf, JNI_FALSE);
    if (cbuf == NULL) {
        return 0;
    }

    cv::Mat imgData(h, w, CV_8UC4, (unsigned char *) cbuf);

    uchar* ptr = imgData.ptr(0);
    for(int i = 0; i < w*h; i ++){
        //计算公式：Y(亮度) = 0.299*R + 0.587*G + 0.114*B
        //对于一个int四字节，其彩色值存储方式为：BGRA
        int grayScale = (int)(ptr[4*i+2]*0.299 + ptr[4*i+1]*0.587 + ptr[4*i+0]*0.114);
        ptr[4*i+1] = grayScale;
        ptr[4*i+2] = grayScale;
        ptr[4*i+0] = grayScale;
    }

    int size = w * h;
    jintArray result = env->NewIntArray(size);
    env->SetIntArrayRegion(result, 0, size, cbuf);
    env->ReleaseIntArrayElements(buf, cbuf, 0);

    LOGE("doGrayScale End");
    return result;
}

jstring str2jstring(JNIEnv* env, const char* pat) {
    //定义java String类 strClass
    jclass strClass = (env)->FindClass("Ljava/lang/String;");
    //获取String(byte[],String)的构造器,用于将本地byte[]数组转换为一个新String
    jmethodID ctorID = (env)->GetMethodID(strClass, "<init>", "([BLjava/lang/String;)V");
    //建立byte数组
    jbyteArray bytes = (env)->NewByteArray(strlen(pat));
    //将char* 转换为byte数组
    (env)->SetByteArrayRegion(bytes, 0, strlen(pat), (jbyte*)pat);
    // 设置String, 保存语言类型,用于byte数组转换至String时的参数
    jstring encoding = (env)->NewStringUTF("GB2312");
    //将byte数组转换为java String,并输出
    return (jstring)(env)->NewObject(strClass, ctorID, bytes, encoding);
}

std::string jstring2str(JNIEnv* env, jstring jstr) {
    char* rtn = NULL;
    jclass clsstring = env->FindClass("java/lang/String");
    jstring strencode = env->NewStringUTF("GB2312");
    jmethodID mid = env->GetMethodID(clsstring, "getBytes", "(Ljava/lang/String;)[B");
    jbyteArray barr= (jbyteArray)env->CallObjectMethod(jstr,mid,strencode);
    jsize alen = env->GetArrayLength(barr);
    jbyte* ba = env->GetByteArrayElements(barr,JNI_FALSE);
    if(alen > 0) {
        rtn = (char*)malloc(alen+1);
        memcpy(rtn,ba,alen);
        rtn[alen]=0;
    }
    env->ReleaseByteArrayElements(barr,ba,0);
    std::string stemp(rtn);
    free(rtn);
    return stemp;
}

const char* string2printf(JNIEnv *env, std::string str) {
    jstring jstr = env->NewStringUTF(str.c_str());
    return env->GetStringUTFChars(jstr, NULL);
}

// Compute similarity transform given two pairs of corresponding points.
// OpenCV requires 3 pairs of corresponding points.
// We are faking the third one.
void similarityTransform(std::vector<cv::Point2f>& inPoints, std::vector<cv::Point2f>& outPoints, cv::Mat &tform) {
    double s60 = sin(60 * M_PI / 180.0);
    double c60 = cos(60 * M_PI / 180.0);

    vector <Point2f> inPts = inPoints;
    vector <Point2f> outPts = outPoints;

    inPts.push_back(cv::Point2f(0,0));
    outPts.push_back(cv::Point2f(0,0));


    inPts[2].x =  c60 * (inPts[0].x - inPts[1].x) - s60 * (inPts[0].y - inPts[1].y) + inPts[1].x;
    inPts[2].y =  s60 * (inPts[0].x - inPts[1].x) + c60 * (inPts[0].y - inPts[1].y) + inPts[1].y;

    outPts[2].x =  c60 * (outPts[0].x - outPts[1].x) - s60 * (outPts[0].y - outPts[1].y) + outPts[1].x;
    outPts[2].y =  s60 * (outPts[0].x - outPts[1].x) + c60 * (outPts[0].y - outPts[1].y) + outPts[1].y;


    tform = cv::estimateRigidTransform(inPts, outPts, false);
}

// Calculate Delaunay triangles for set of points
// Returns the vector of indices of 3 points for each triangle
static void calculateDelaunayTriangles(Rect rect, vector<Point2f> &points, vector< vector<int> > &delaunayTri){

    // Create an instance of Subdiv2D
    Subdiv2D subdiv(rect);

    // Insert points into subdiv
    for( vector<Point2f>::iterator it = points.begin(); it != points.end(); it++)
        subdiv.insert(*it);

    vector<Vec6f> triangleList;
    subdiv.getTriangleList(triangleList);
    vector<Point2f> pt(3);
    vector<int> ind(3);

    for( size_t i = 0; i < triangleList.size(); i++ )
    {
        Vec6f t = triangleList[i];
        pt[0] = Point2f(t[0], t[1]);
        pt[1] = Point2f(t[2], t[3]);
        pt[2] = Point2f(t[4], t[5 ]);

        if ( rect.contains(pt[0]) && rect.contains(pt[1]) && rect.contains(pt[2])){
            for(int j = 0; j < 3; j++)
                for(size_t k = 0; k < points.size(); k++)
                    if(abs(pt[j].x - points[k].x) < 1.0 && abs(pt[j].y - points[k].y) < 1)
                        ind[j] = k;

            delaunayTri.push_back(ind);
        }
    }
}

// Apply affine transform calculated using srcTri and dstTri to src
void applyAffineTransform(Mat &warpImage, Mat &src, vector<Point2f> &srcTri, vector<Point2f> &dstTri) {
    // Given a pair of triangles, find the affine transform.
    Mat warpMat = getAffineTransform( srcTri, dstTri );

    // Apply the Affine Transform just found to the src image
    warpAffine( src, warpImage, warpMat, warpImage.size(), INTER_LINEAR, BORDER_REFLECT_101);
}


// Warps and alpha blends triangular regions from img1 and img2 to img
void warpTriangle(Mat &img1, Mat &img2, vector<Point2f> t1, vector<Point2f> t2) {
    // Find bounding rectangle for each triangle
    Rect r1 = boundingRect(t1);
    Rect r2 = boundingRect(t2);

    // Offset points by left top corner of the respective rectangles
    vector<Point2f> t1Rect, t2Rect;
    vector<Point> t2RectInt;
    for(int i = 0; i < 3; i++)
    {
        //tRect.push_back( Point2f( t[i].x - r.x, t[i].y -  r.y) );
        t2RectInt.push_back( Point((int)(t2[i].x - r2.x), (int)(t2[i].y - r2.y)) ); // for fillConvexPoly

        t1Rect.push_back( Point2f( t1[i].x - r1.x, t1[i].y -  r1.y) );
        t2Rect.push_back( Point2f( t2[i].x - r2.x, t2[i].y - r2.y) );
    }

    // Get mask by filling triangle
    Mat mask = Mat::zeros(r2.height, r2.width, CV_32FC3);
    fillConvexPoly(mask, t2RectInt, Scalar(1.0, 1.0, 1.0), 16, 0);

    // Apply warpImage to small rectangular patches
    Mat img1Rect, img2Rect;
    img1(r1).copyTo(img1Rect);

    Mat warpImage = Mat::zeros(r2.height, r2.width, img1Rect.type());

    applyAffineTransform(warpImage, img1Rect, t1Rect, t2Rect);

    // Copy triangular region of the rectangular patch to the output image
    multiply(warpImage,mask, warpImage);
    multiply(img2(r2), Scalar(1.0,1.0,1.0) - mask, img2(r2));
    img2(r2) = img2(r2) + warpImage;
}

// Constrains points to be inside boundary
void constrainPoint(Point2f &p, Size sz) {
    p.x = min(max( (double)p.x, 0.0), (double)(sz.width - 1));
    p.y = min(max( (double)p.y, 0.0), (double)(sz.height - 1));
}

// Read points from list of text file
void readPoints(vector<string> pointsFileNames, vector<vector<Point2f> > &pointsVec) {

    for(size_t i = 0; i < pointsFileNames.size(); i++) {
        vector<Point2f> points;
        ifstream ifs(pointsFileNames[i].c_str());
        float x, y;
        while(ifs >> x >> y)
            points.push_back(Point2f((float)x, (float)y));

        pointsVec.push_back(points);
    }
}

// Read names from the directory
void readFileNames(JNIEnv *env, string dirName, vector<string> &imageFnames, vector<string> &ptsFnames) {
    DIR *dir;
    struct dirent *ent;
    int count = 0;

    //image extensions
    string imgExt = "jpg";
    string imgExt2 = "png";
    string txtExt = "txt";

    if ((dir = opendir (dirName.c_str())) != NULL) {
        /* print all the files and directories within directory */
        while ((ent = readdir (dir)) != NULL) {
            if(count < 2) {
                count++;
                continue;
            }

            string path = dirName;
            string fname = ent->d_name;

            if (fname.find(imgExt, (fname.length() - imgExt.length())) != std::string::npos) {
                path.append(fname);
                imageFnames.push_back(path);
            }
            else if (fname.find(imgExt2, (fname.length() - imgExt2.length())) != std::string::npos) {
                path.append(fname);
                imageFnames.push_back(path);
            }
            else if (fname.find(txtExt, (fname.length() - txtExt.length())) != std::string::npos) {
                path.append(fname);
                ptsFnames.push_back(path);
            }
            LOGE("printf path %s", string2printf(env, path));  // TODO
        }
        closedir (dir);
    }
}

std::vector<cv::Point2d> get_2d_image_points(vector<Point2f> points)
{
    std::vector<cv::Point2d> image_points;
    image_points.push_back( cv::Point2d( points[30].x, points[30].y ) );    // Nose tip
    image_points.push_back( cv::Point2d( points[8].x, points[8].y ) );      // Chin
    image_points.push_back( cv::Point2d( points[36].x, points[36].y ) );    // Left eye left corner
    image_points.push_back( cv::Point2d( points[45].x, points[45].y ) );    // Right eye right corner
    image_points.push_back( cv::Point2d( points[48].x, points[48].y ) );    // Left Mouth corner
    image_points.push_back( cv::Point2d( points[54].x, points[54].y ) );    // Right mouth corner
    return image_points;

}

cv::Mat get_camera_matrix(float focal_length, cv::Point2d center)
{
    cv::Mat camera_matrix = (cv::Mat_<double>(3,3) << focal_length, 0, center.x, 0 , focal_length, center.y, 0, 0, 1);
    return camera_matrix;
}

/*
 * Class:     com_simoncherry_artest_util_JNIUtils
 * Method:    doFaceSwap
 * Signature: ([Ljava/lang/String;)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_com_simoncherry_artest_util_JNIUtils_doFaceSwap
        (JNIEnv *env, jclass obj, jobjectArray stringArray) {
    LOGE("doFaceSwap Start");
    int stringCount = env->GetArrayLength(stringArray);
    if (stringCount < 2) {
        return NULL;
    }

    vector<string> imageNames, ptsNames;

    for (int i=0; i<2; i++) {
        jstring prompt = (jstring) (env->GetObjectArrayElement(stringArray, i));
        std::string img_path = jstring2str(env, prompt);
        imageNames.push_back(img_path);

        const char *rawString = env->GetStringUTFChars(prompt, 0);
        LOGE("printf path %s", rawString);
        // Don't forget to call `ReleaseStringUTFChars` when you're done.
        env->ReleaseStringUTFChars(prompt, rawString);

        std::string txt_path = MD5(img_path).toStr();
        txt_path = "/sdcard/BuildMask/" + txt_path + "_original.txt";
        ptsNames.push_back(txt_path);
        LOGE("printf md5 %s", string2printf(env, txt_path));
    }

    if(imageNames.empty() || ptsNames.empty() || imageNames.size() != ptsNames.size()) {
        return NULL;
    }

    // Read points
    vector<vector<Point2f> > allPoints;
    readPoints(ptsNames, allPoints);

    vector<Point2f> points1 = allPoints[0];
    vector<Point2f> points2 = allPoints[1];

    Mat img1 = imread(imageNames[0]);
    Mat img2 = imread(imageNames[1]);
    Mat img1Warped = img2.clone();

    //convert Mat to float data type
    img1.convertTo(img1, CV_32F);
    img1Warped.convertTo(img1Warped, CV_32F);

    // Find convex hull
    vector<Point2f> hull1;
    vector<Point2f> hull2;
    vector<int> hullIndex;

    convexHull(points2, hullIndex, false, false);

    for(int i = 0; i < hullIndex.size(); i++)
    {
        hull1.push_back(points1[hullIndex[i]]);
        hull2.push_back(points2[hullIndex[i]]);
    }

    // Find delaunay triangulation for points on the convex hull
    vector< vector<int> > dt;
    Rect rect(0, 0, img1Warped.cols, img1Warped.rows);
    calculateDelaunayTriangles(rect, hull2, dt);

    // Apply affine transformation to Delaunay triangles
    for(size_t i = 0; i < dt.size(); i++)
    {
        vector<Point2f> t1, t2;
        // Get points for img1, img2 corresponding to the triangles
        for(size_t j = 0; j < 3; j++)
        {
            t1.push_back(hull1[dt[i][j]]);
            t2.push_back(hull2[dt[i][j]]);
        }

        warpTriangle(img1, img1Warped, t1, t2);
    }

    // Calculate mask
    vector<Point> hull8U;
    for(int i = 0; i < hull2.size(); i++)
    {
        Point pt(hull2[i].x, hull2[i].y);
        hull8U.push_back(pt);
    }

    Mat mask = Mat::zeros(img2.rows, img2.cols, img2.depth());
    fillConvexPoly(mask,&hull8U[0], hull8U.size(), Scalar(255,255,255));

    // Clone seamlessly.
    Rect r = boundingRect(hull2);
    Point center = (r.tl() + r.br()) / 2;

    Mat output;
    img1Warped.convertTo(img1Warped, CV_8UC3);
    seamlessClone(img1Warped,img2, mask, center, output, NORMAL_CLONE);

    vector<int> parameters;
    parameters.push_back(CV_IMWRITE_JPEG_QUALITY);
    parameters.push_back(100);
    std::string result_path = "/sdcard/face_swap_result.jpg";
    imwrite(result_path, output, parameters);
    LOGE("doFaceSwap End");
    return env->NewStringUTF(result_path.c_str());
}

#ifdef __cplusplus
}
#endif
