
AR相机
===================================
根据人脸图片构建简单的3D人脸模型，然后在摄像头预览画面中展示。基于该人脸模型进行换脸或者添加装饰品。
  
基于 
----------------------------------- 
* [dlib-android-app](https://github.com/tzutalin/dlib-android-app) 
* [dlib-android](https://github.com/tzutalin/dlib-android) 提供Android平台可用的Dlib库。
* More Than Technical[这篇文章](http://www.morethantechnical.com/2012/10/17/head-pose-estimation-with-opencv-opengl-revisited-w-code/) 的头部姿态估算算法。
* [LearnOpenCV.com](http://www.learnopencv.com/face-swap-using-opencv-c-python/) 的换脸算法。
* [Rajawali](https://github.com/Rajawali/Rajawali) OpenGL ES引擎。

应用截图 
-----------------------------------
### GIF演示
![image](https://github.com/SimonCherryGZ/face-landmark-android/raw/master/screenshots/GIF_1.gif)
![image](https://github.com/SimonCherryGZ/face-landmark-android/raw/master/screenshots/GIF_2.gif)
### 显示人脸3D模型
![image](https://github.com/SimonCherryGZ/face-landmark-android/raw/master/screenshots/1.jpg)
![image](https://github.com/SimonCherryGZ/face-landmark-android/raw/master/screenshots/2.jpg)
![image](https://github.com/SimonCherryGZ/face-landmark-android/raw/master/screenshots/3.jpg)
### 显示装饰品
![image](https://github.com/SimonCherryGZ/face-landmark-android/raw/master/screenshots/4.jpg)
![image](https://github.com/SimonCherryGZ/face-landmark-android/raw/master/screenshots/5.jpg)
![image](https://github.com/SimonCherryGZ/face-landmark-android/raw/master/screenshots/6.jpg)
![image](https://github.com/SimonCherryGZ/face-landmark-android/raw/master/screenshots/7.jpg)
### 测试页面
![image](https://github.com/SimonCherryGZ/face-landmark-android/raw/master/screenshots/8.jpg)
![image](https://github.com/SimonCherryGZ/face-landmark-android/raw/master/screenshots/9.jpg)

原理
-----------------------------------  
* 根据人脸图片构建3D人脸模型

	使用Dlib可以检测出人脸的68个关键点：  
  
	![image](https://github.com/SimonCherryGZ/face-landmark-android/raw/master/screenshots/landmarks.jpg)
  
	根据这68个点可以连线得到一个低面数的人脸模型：  
  
	![image](https://github.com/SimonCherryGZ/face-landmark-android/raw/master/screenshots/base_model.jpg)
  
	我使用[Blender](https://www.blender.org/) 建立了该模型，这里称其为BaseModel，格式为obj。
  
* 动态修改BaseModel

	查询obj文件结构可知，以“v”开头的行描述的是模型的顶点，以“vt”开头的行描述的是模型的贴图坐标点。  
  
  那么只要找到这些点与人类关键点的对应关系，就可以简单地通过替换对应行的数据，来达到动态修改模型的目的。


不足
-----------------------------------  
* Dlib库依赖shape_predictor_68_face_landmarks.dat，其大小约100M，加载需要花费数秒。
* Dlib库检测人脸的速度与图像的大小成反比，因此需要对摄像头的预览画面进行截取。  

  截取得太小的话也检测不出人脸。在大小不影响检测的情况下，检测速度依然不理想。
* 由于是从单张图片构建3D人脸，无法获取Z轴的数值，所以在估算三维姿态时用的是模拟数据，得出的数值可能不准确。

构建
-----------------------------------  
1. 按照[dlib-android](https://github.com/tzutalin/dlib-android) 描述的步骤来构建Dlib-Android库。
2. 从[dlib-android-app](https://github.com/tzutalin/dlib-android-app/blob/d0170613f36046b8e122a5de651029ecb1af947e/app/src/main/res/raw/shape_predictor_68_face_landmarks.dat) 中找到shape_predictor_68_face_landmarks.dat，将其复制到手机根目录中。

依赖库 
-----------------------------------  
  * [RxJava](https://github.com/ReactiveX/RxJava)
  * [fastjson](https://github.com/alibaba/fastjson)
  * [Realm](https://github.com/realm/realm-java)
  * [Glide](https://github.com/bumptech/glide)


> Written with [StackEdit](https://stackedit.io/).
