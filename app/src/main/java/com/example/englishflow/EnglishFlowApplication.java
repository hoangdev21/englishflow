package com.example.englishflow;

import android.app.Application;
import android.util.Log;

import androidx.appcompat.app.AppCompatDelegate;

import com.example.englishflow.data.AppSettingsStore;
import com.example.englishflow.data.UserPresenceTracker;
import com.google.firebase.FirebaseApp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;

public class EnglishFlowApplication extends Application {

    private static final String TAG = "EnglishFlowApp";

    private UserPresenceTracker presenceTracker;

    @Override
    public void onCreate() {
        super.onCreate();

        AppSettingsStore settingsStore = new AppSettingsStore(this);
        AppCompatDelegate.setDefaultNightMode(settingsStore.getNightModeValue());

        FirebaseApp firebaseApp = FirebaseApp.initializeApp(this);
        if (firebaseApp == null) {
            Log.w(TAG, "FirebaseApp init returned null, skip presence tracker startup");
            return;
        }

        try {
            FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                    .setPersistenceEnabled(true)
                    .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                    .build();
            FirebaseFirestore.getInstance().setFirestoreSettings(settings);
        } catch (Exception e) {
            Log.w(TAG, "Unable to apply Firestore cache settings", e);
        }

        presenceTracker = new UserPresenceTracker();
        presenceTracker.start();
    }
}