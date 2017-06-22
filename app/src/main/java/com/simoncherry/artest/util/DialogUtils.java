package com.simoncherry.artest.util;

import android.content.Context;
import android.content.DialogInterface;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.view.View;

/**
 * <pre>
 *     author : Donald
 *     e-mail : xxx@xx
 *     time   : 2017/06/22
 *     desc   :
 *     version: 1.0
 * </pre>
 */
public class DialogUtils {

    public static void showDialog(
            Context context, @NonNull String title, @NonNull String message,
            View view,
            DialogInterface.OnClickListener positiveCallback,
            DialogInterface.OnClickListener negativeCallback) {
        AlertDialog.Builder builder = new android.support.v7.app.AlertDialog.Builder(context);
        builder.setTitle(title);
        builder.setMessage(message);
        if (view != null) {
            builder.setView(view, 60, 20, 60, 20);
        }
        builder.setPositiveButton("确定", positiveCallback);
        builder.setNegativeButton("取消", negativeCallback);
        builder.show();
    }

    public static void showDialog(
            Context context, String title, String message,
            DialogInterface.OnClickListener positiveCallback,
            DialogInterface.OnClickListener negativeCallback) {
        showDialog(context, title, message, null, positiveCallback, negativeCallback);
    }

    public static void showDialog(
            Context context, String title, String message, View view,
            DialogInterface.OnClickListener positiveCallback) {
        showDialog(context, title, message, view, positiveCallback, null);
    }

    public static void showDialog(
            Context context, String title, String message,
            DialogInterface.OnClickListener positiveCallback) {
        showDialog(context, title, message, positiveCallback, null);
    }
}
