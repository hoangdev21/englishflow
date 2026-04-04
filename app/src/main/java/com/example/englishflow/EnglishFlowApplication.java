package com.example.englishflow;

import android.app.Application;
import android.util.Log;

import com.example.englishflow.data.UserPresenceTracker;
import com.google.firebase.FirebaseApp;

public class EnglishFlowApplication extends Application {

    private static final String TAG = "EnglishFlowApp";

    private UserPresenceTracker presenceTracker;

    @Override
    public void onCreate() {
        super.onCreate();

        FirebaseApp firebaseApp = FirebaseApp.initializeApp(this);
        if (firebaseApp == null) {
            Log.w(TAG, "FirebaseApp init returned null, skip presence tracker startup");
            return;
        }

        presenceTracker = new UserPresenceTracker();
        presenceTracker.start();
    }
}