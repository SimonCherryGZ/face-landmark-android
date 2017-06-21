package com.simoncherry.artest.fragment;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;

import com.simoncherry.artest.R;
import com.simoncherry.artest.rajawali3d.AExampleFragment;
import com.simoncherry.artest.util.FileUtils;

import org.rajawali3d.Object3D;
import org.rajawali3d.cameras.ArcballCamera;
import org.rajawali3d.debug.DebugVisualizer;
import org.rajawali3d.debug.GridFloor;
import org.rajawali3d.lights.DirectionalLight;
import org.rajawali3d.loader.LoaderOBJ;
import org.rajawali3d.materials.Material;
import org.rajawali3d.materials.methods.DiffuseMethod;

import java.io.File;

/**
 * Created by Simon on 2017/6/21.
 */

public class ShowMaskFragment extends AExampleFragment {

    public final static String IMG_KEY = "img_key";
    private String mImagePath = null;

    public static ShowMaskFragment newInstance(String imgPath) {
        ShowMaskFragment fragment = new ShowMaskFragment();
        Bundle args = new Bundle();
        args.putString(IMG_KEY, imgPath);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle bundle = getArguments();
        if (bundle != null) {
            mImagePath = bundle.getString(IMG_KEY, null);
        }
    }

    @Override
    public AExampleRenderer createRenderer() {
        return new ArcBallCameraRenderer(getActivity(), this);
    }

    private final class ArcBallCameraRenderer extends AExampleRenderer {
        public ArcBallCameraRenderer(Context context, @Nullable AExampleFragment fragment) {
            super(context, fragment);
        }

        @Override
        protected void initScene() {
            try {
                DirectionalLight light = new DirectionalLight();
                light.setLookAt(1, -1, 1);
                light.enableLookAt();
                light.setPower(1.5f);
                getCurrentScene().addLight(light);

                light = new DirectionalLight();
                light.setLookAt(-1, 1, -1);
                light.enableLookAt();
                light.setPower(1.5f);
                getCurrentScene().addLight(light);

                DebugVisualizer debugViz = new DebugVisualizer(this);
                debugViz.addChild(new GridFloor());
                getCurrentScene().addChild(debugViz);

                String textureDir ="BuildMask" + File.separator;
                String textureName = FileUtils.getMD5(mImagePath) + "_obj";
                LoaderOBJ parser = new LoaderOBJ(this, textureDir + textureName);
                parser.parse();
                Object3D monkey = parser.getParsedObject();
                monkey.setScale(0.65f);

                Material material = new Material();
                material.enableLighting(true);
                material.setDiffuseMethod(new DiffuseMethod.Lambert());
                material.setColor(0x990000);

                monkey.setMaterial(material);
                getCurrentScene().addChild(monkey);

                ArcballCamera arcBall = new ArcballCamera(mContext, ((Activity)mContext).findViewById(R.id.layout_container));
                arcBall.setPosition(4, 4, 4);
                getCurrentScene().replaceAndSwitchCamera(getCurrentCamera(), arcBall);
            } catch(Exception e) {
                e.printStackTrace();
            }
        }
    }
}
