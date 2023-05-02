package com.yash.ymplayer;

import android.app.Application;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.yash.logging.LogHelper;
import com.yash.logging.settings.LogHelperSettings;
import com.yash.ymplayer.util.ConverterUtil;
import com.yash.ymplayer.interfaces.Keys;
import com.yash.youtube_extractor.utility.HttpUtility;
import com.yash.youtube_extractor.utility.RequestUtility;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Cache;

public class YMPlayer extends Application {
    private static final String TAG = "YMPlayer";
    SharedPreferences preferences;
    UncaughtExceptionHandler defaultUncaughtExceptionHandler;
    ExecutorService executor;

    @Override
    public void onCreate() {
        super.onCreate();
        preferences  = PreferenceManager.getDefaultSharedPreferences(this);
        LogHelperSettings logHelperSettings = new LogHelperSettings();
        logHelperSettings.setFileLogging(false);
        LogHelper.setLogHelperSettings(logHelperSettings);
        LogHelper.deploy(this, TAG);
        executor = Executors.newSingleThreadExecutor();

        HttpUtility.initialise(new Cache(getCacheDir(), 40*1024*1024), null);
        RequestUtility.updateSettings(PreferenceManager.getDefaultSharedPreferences(this));

        defaultUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            Log.e(TAG, "Fatal Exception: ", e);

            String exception = ConverterUtil.toStringStackTrace(e);
            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean(Keys.PREFERENCE_KEYS.IS_EXCEPTION,true);
            editor.putString(Keys.PREFERENCE_KEYS.EXCEPTION,exception);
            editor.apply();

            LogHelper.flush();

            defaultUncaughtExceptionHandler.uncaughtException(t,e);
        });
    }


    @Override
    public void onTerminate() {
        super.onTerminate();
        LogHelper.flush();

    }


    @Override
    public void onLowMemory() {
        super.onLowMemory();
        LogHelper.flush();

    }
}
