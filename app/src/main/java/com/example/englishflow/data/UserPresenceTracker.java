package com.example.englishflow.data;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class UserPresenceTracker implements DefaultLifecycleObserver {

    private static final String TAG = "UserPresenceTracker";
    private static final long HEARTBEAT_INTERVAL_MS = 90_000L;

    private FirebaseUserStore userStore;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable heartbeatRunnable = this::pushOnlineHeartbeat;
    private boolean isStarted;

    public void start() {
        if (isStarted) {
            return;
        }
        ProcessLifecycleOwner.get().getLifecycle().addObserver(this);
        isStarted = true;
    }

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        updateCurrentUserPresence(true);
        scheduleHeartbeat();
    }

    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
        handler.removeCallbacks(heartbeatRunnable);
        updateCurrentUserPresence(false);
    }

    private void scheduleHeartbeat() {
        handler.removeCallbacks(heartbeatRunnable);
        handler.postDelayed(heartbeatRunnable, HEARTBEAT_INTERVAL_MS);
    }

    private void pushOnlineHeartbeat() {
        updateCurrentUserPresence(true);
        if (ProcessLifecycleOwner.get().getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
            scheduleHeartbeat();
        }
    }

    private void updateCurrentUserPresence(boolean isOnline) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            return;
        }

        try {
            getUserStore().updatePresence(currentUser.getUid(), isOnline);
        } catch (IllegalStateException e) {
            Log.w(TAG, "Skip presence update because Firebase is not initialized yet", e);
        }
    }

    private FirebaseUserStore getUserStore() {
        if (userStore == null) {
            userStore = new FirebaseUserStore();
        }
        return userStore;
    }
}