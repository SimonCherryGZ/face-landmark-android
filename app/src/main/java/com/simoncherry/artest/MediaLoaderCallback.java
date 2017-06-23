package com.simoncherry.artest;

import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.text.TextUtils;

import com.simoncherry.artest.model.ImageBean;

import java.io.File;

import io.realm.RealmList;

/**
 * Created by Simon on 2017/6/23.
 */

public class MediaLoaderCallback implements LoaderManager.LoaderCallbacks<Cursor> {

    private Context mContext;

    public MediaLoaderCallback(Context mContext) {
        this.mContext = mContext;
    }

    private final String[] IMAGE_PROJECTION = {
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media._ID };

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        CursorLoader cursorLoader = new CursorLoader(mContext,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, IMAGE_PROJECTION,
                IMAGE_PROJECTION[4]+">0 AND "+IMAGE_PROJECTION[3]+"=? OR "+IMAGE_PROJECTION[3]+"=? ",
                new String[]{"image/jpeg", "image/png"}, IMAGE_PROJECTION[2] + " ASC");  // DESC 降序
        return cursorLoader;
    }

    private boolean fileExist(String path){
        if(!TextUtils.isEmpty(path)){
            return new File(path).exists();
        }
        return false;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        if (data != null) {
            if (data.getCount() > 0) {
                RealmList<ImageBean> realmList = new RealmList<>();
                data.moveToFirst();
                do {
                    String path = data.getString(data.getColumnIndexOrThrow(IMAGE_PROJECTION[0]));
                    String name = data.getString(data.getColumnIndexOrThrow(IMAGE_PROJECTION[1]));
                    long dateTime = data.getLong(data.getColumnIndexOrThrow(IMAGE_PROJECTION[2]));
                    long id = data.getLong(data.getColumnIndexOrThrow(IMAGE_PROJECTION[5]));
                    if (fileExist(path)) {
                        ImageBean bean = new ImageBean();
                        bean.setId(id);
                        bean.setPath(path);
                        bean.setName(name);
                        bean.setDate(dateTime);
                        realmList.add(bean);
                    }

                } while (data.moveToNext());

                if (onLoadFinishedListener != null) {
                    onLoadFinishedListener.onLoadFinished(realmList);
                }
            }
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
    }

    public interface OnLoadFinishedListener {
        void onLoadFinished(RealmList<ImageBean> data);
    }

    private OnLoadFinishedListener onLoadFinishedListener;

    public void setOnLoadFinishedListener(OnLoadFinishedListener onLoadFinishedListener) {
        this.onLoadFinishedListener = onLoadFinishedListener;
    }
}
