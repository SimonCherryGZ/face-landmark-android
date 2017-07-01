/*
 * Copyright 2016 nekocode
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.simoncherry.artest.nekocode;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.util.Log;
import android.util.SparseArray;
import android.view.TextureView;

import com.simoncherry.artest.R;
import com.simoncherry.artest.nekocode.filter.AsciiArtFilter;
import com.simoncherry.artest.nekocode.filter.BasicDeformFilter;
import com.simoncherry.artest.nekocode.filter.BlueorangeFilter;
import com.simoncherry.artest.nekocode.filter.CameraFilter;
import com.simoncherry.artest.nekocode.filter.ChromaticAberrationFilter;
import com.simoncherry.artest.nekocode.filter.ContrastFilter;
import com.simoncherry.artest.nekocode.filter.CrackedFilter;
import com.simoncherry.artest.nekocode.filter.CrosshatchFilter;
import com.simoncherry.artest.nekocode.filter.EMInterferenceFilter;
import com.simoncherry.artest.nekocode.filter.EdgeDetectionFilter;
import com.simoncherry.artest.nekocode.filter.JFAVoronoiFilter;
import com.simoncherry.artest.nekocode.filter.LegofiedFilter;
import com.simoncherry.artest.nekocode.filter.LichtensteinEsqueFilter;
import com.simoncherry.artest.nekocode.filter.MappingFilter;
import com.simoncherry.artest.nekocode.filter.MoneyFilter;
import com.simoncherry.artest.nekocode.filter.NoiseWarpFilter;
import com.simoncherry.artest.nekocode.filter.OriginalFilter;
import com.simoncherry.artest.nekocode.filter.PixelizeFilter;
import com.simoncherry.artest.nekocode.filter.PolygonizationFilter;
import com.simoncherry.artest.nekocode.filter.RefractionFilter;
import com.simoncherry.artest.nekocode.filter.TileMosaicFilter;
import com.simoncherry.artest.nekocode.filter.TrianglesMosaicFilter;
import com.simoncherry.artest.util.CameraUtils;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

/**
 * @author nekocode (nekocode.cn@gmail.com)
 */
public class MyCameraRenderer implements Runnable, TextureView.SurfaceTextureListener {
    private static final String TAG = "CameraRenderer";
    private static final int EGL_OPENGL_ES2_BIT = 4;
    private static final int EGL_CONTEXT_CLIENT_VERSION = 0x3098;
    private static final int DRAW_INTERVAL = 1000 / 30;

    private Thread renderThread;
    private Context context;
    private SurfaceTexture surfaceTexture;
    private int gwidth, gheight;

    private EGLDisplay eglDisplay;
    private EGLSurface eglSurface;
    private EGLContext eglContext;
    private EGL10 egl10;

    private SurfaceTexture cameraSurfaceTexture;
    private int cameraTextureId;
    private CameraFilter selectedFilter;
    private int selectedFilterId = R.string.filter0;
    private SparseArray<CameraFilter> cameraFilterMap = new SparseArray<>();

    public MyCameraRenderer(Context context) {
        this.context = context;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        gwidth = -width;
        gheight = -height;

        CameraUtils.configureTransform(width, height);
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        if (renderThread != null && renderThread.isAlive()) {
            renderThread.interrupt();
        }
        CameraFilter.release();

        return true;
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        if (renderThread != null && renderThread.isAlive()) {
            renderThread.interrupt();
        }
        renderThread = new Thread(this);

        surfaceTexture = surface;
        gwidth = -width;
        gheight = -height;

        // Open camera
//        Pair<Camera.CameraInfo, Integer> backCamera = getBackCamera();
//        final int backCameraId = backCamera.second;
//        camera = Camera.open(backCameraId);
        CameraUtils.openCamera(width, height);

        cameraTextureId = MyGLUtils.genTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES);
        cameraSurfaceTexture = new SurfaceTexture(cameraTextureId);
        CameraUtils.createCameraPreviewSession(cameraSurfaceTexture);

        // Start rendering
        renderThread.start();
    }

    public void setSelectedFilter(int id) {
        selectedFilterId = id;
        selectedFilter = cameraFilterMap.get(id);
        if (selectedFilter != null)
            selectedFilter.onAttach();
    }

    @Override
    public void run() {
        initGL(surfaceTexture);

        // Setup camera filters map
        cameraFilterMap.append(R.string.filter0, new OriginalFilter(context));
        cameraFilterMap.append(R.string.filter1, new EdgeDetectionFilter(context));
        cameraFilterMap.append(R.string.filter2, new PixelizeFilter(context));
        cameraFilterMap.append(R.string.filter3, new EMInterferenceFilter(context));
        cameraFilterMap.append(R.string.filter4, new TrianglesMosaicFilter(context));
        cameraFilterMap.append(R.string.filter5, new LegofiedFilter(context));
        cameraFilterMap.append(R.string.filter6, new TileMosaicFilter(context));
        cameraFilterMap.append(R.string.filter7, new BlueorangeFilter(context));
        cameraFilterMap.append(R.string.filter8, new ChromaticAberrationFilter(context));
        cameraFilterMap.append(R.string.filter9, new BasicDeformFilter(context));
        cameraFilterMap.append(R.string.filter10, new ContrastFilter(context));
        cameraFilterMap.append(R.string.filter11, new NoiseWarpFilter(context));
        cameraFilterMap.append(R.string.filter12, new RefractionFilter(context));
        cameraFilterMap.append(R.string.filter13, new MappingFilter(context));
        cameraFilterMap.append(R.string.filter14, new CrosshatchFilter(context));
        cameraFilterMap.append(R.string.filter15, new LichtensteinEsqueFilter(context));
        cameraFilterMap.append(R.string.filter16, new AsciiArtFilter(context));
        cameraFilterMap.append(R.string.filter17, new MoneyFilter(context));
        cameraFilterMap.append(R.string.filter18, new CrackedFilter(context));
        cameraFilterMap.append(R.string.filter19, new PolygonizationFilter(context));
        cameraFilterMap.append(R.string.filter20, new JFAVoronoiFilter(context));
        setSelectedFilter(selectedFilterId);

        // Create texture for camera preview
//        cameraTextureId = MyGLUtils.genTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES);
//        cameraSurfaceTexture = new SurfaceTexture(cameraTextureId);

        // Start camera preview
//        try {
//            camera.setPreviewTexture(cameraSurfaceTexture);
//            camera.startPreview();
//        } catch (IOException ioe) {
//            // Something bad happened
//        }

        // Render loop
        while (!Thread.currentThread().isInterrupted()) {
            try {
                if (gwidth < 0 && gheight < 0)
                    GLES20.glViewport(0, 0, gwidth = -gwidth, gheight = -gheight);

                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

                // Update the camera preview texture
                synchronized (this) {
                    cameraSurfaceTexture.updateTexImage();
                }

                // Draw camera preview
                selectedFilter.draw(cameraTextureId, gwidth, gheight);

                // Flush
                GLES20.glFlush();
                egl10.eglSwapBuffers(eglDisplay, eglSurface);

                Thread.sleep(DRAW_INTERVAL);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        cameraSurfaceTexture.release();
        GLES20.glDeleteTextures(1, new int[]{cameraTextureId}, 0);
    }

    private void initGL(SurfaceTexture texture) {
        egl10 = (EGL10) EGLContext.getEGL();

        eglDisplay = egl10.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
        if (eglDisplay == EGL10.EGL_NO_DISPLAY) {
            throw new RuntimeException("eglGetDisplay failed " +
                    android.opengl.GLUtils.getEGLErrorString(egl10.eglGetError()));
        }

        int[] version = new int[2];
        if (!egl10.eglInitialize(eglDisplay, version)) {
            throw new RuntimeException("eglInitialize failed " +
                    android.opengl.GLUtils.getEGLErrorString(egl10.eglGetError()));
        }

        int[] configsCount = new int[1];
        EGLConfig[] configs = new EGLConfig[1];
        int[] configSpec = {
                EGL10.EGL_RENDERABLE_TYPE,
                EGL_OPENGL_ES2_BIT,
                EGL10.EGL_RED_SIZE, 8,
                EGL10.EGL_GREEN_SIZE, 8,
                EGL10.EGL_BLUE_SIZE, 8,
                EGL10.EGL_ALPHA_SIZE, 8,
                EGL10.EGL_DEPTH_SIZE, 0,
                EGL10.EGL_STENCIL_SIZE, 0,
                EGL10.EGL_NONE
        };

        EGLConfig eglConfig = null;
        if (!egl10.eglChooseConfig(eglDisplay, configSpec, configs, 1, configsCount)) {
            throw new IllegalArgumentException("eglChooseConfig failed " +
                    android.opengl.GLUtils.getEGLErrorString(egl10.eglGetError()));
        } else if (configsCount[0] > 0) {
            eglConfig = configs[0];
        }
        if (eglConfig == null) {
            throw new RuntimeException("eglConfig not initialized");
        }

        int[] attrib_list = {EGL_CONTEXT_CLIENT_VERSION, 2, EGL10.EGL_NONE};
        eglContext = egl10.eglCreateContext(eglDisplay, eglConfig, EGL10.EGL_NO_CONTEXT, attrib_list);
        eglSurface = egl10.eglCreateWindowSurface(eglDisplay, eglConfig, texture, null);

        if (eglSurface == null || eglSurface == EGL10.EGL_NO_SURFACE) {
            int error = egl10.eglGetError();
            if (error == EGL10.EGL_BAD_NATIVE_WINDOW) {
                Log.e(TAG, "eglCreateWindowSurface returned EGL10.EGL_BAD_NATIVE_WINDOW");
                return;
            }
            throw new RuntimeException("eglCreateWindowSurface failed " +
                    android.opengl.GLUtils.getEGLErrorString(error));
        }

        if (!egl10.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            throw new RuntimeException("eglMakeCurrent failed " +
                    android.opengl.GLUtils.getEGLErrorString(egl10.eglGetError()));
        }
    }

//    private Pair<Camera.CameraInfo, Integer> getBackCamera() {
//        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
//        final int numberOfCameras = Camera.getNumberOfCameras();
//
//        for (int i = 0; i < numberOfCameras; ++i) {
//            Camera.getCameraInfo(i, cameraInfo);
//            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
//                return new Pair<>(cameraInfo, i);
//            }
//        }
//        return null;
//    }
}