LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
OpenCV_INSTALL_MODULES := on
OpenCV_CAMERA_MODULES := off
OPENCV_LIB_TYPE :=STATIC
ifeq ("$(wildcard $(OPENCV_MK_PATH))","")
include D:\Simon\Android\Git\AR2\dlib\src\main\third_party\opencv\jni\OpenCV.mk
else
include $(OPENCV_MK_PATH)
endif

LOCAL_MODULE := JNI_APP
LOCAL_SRC_FILES =: jni_app.cpp \
                   md5.cpp
LOCAL_LDLIBS +=  -lm -llog

include $(BUILD_SHARED_LIBRARY)