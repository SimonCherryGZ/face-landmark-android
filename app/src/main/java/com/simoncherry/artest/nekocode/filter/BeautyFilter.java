package com.simoncherry.artest.nekocode.filter;

import android.content.Context;
import android.opengl.GLES20;

import com.simoncherry.artest.R;
import com.simoncherry.artest.nekocode.MyGLUtils;

/**
 * Created by Simon on 2017/7/2.
 */

public class BeautyFilter extends CameraFilter {
    private int program;

    public BeautyFilter(Context context) {
        super(context);

        // Build shaders
        program = MyGLUtils.buildProgram(context, R.raw.vertext, R.raw.beauty);
    }

    @Override
    public void onDraw(int cameraTexId, int canvasWidth, int canvasHeight) {
        setupShaderInputs(program,
                new int[]{canvasWidth, canvasHeight},
                new int[]{cameraTexId},
                new int[][]{});
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    }
}
