package com.kru13.httpserver;

import android.app.Application;
import android.content.Context;

/**
 * Created by Filip on 27.03.2017.
 */

public class HttpServer extends Application {

    private static Context mContext;

    public static Context getContext() {
        return mContext;
    }

    public void setContext(Context mContext) {
        this.mContext = mContext;
    }


}
