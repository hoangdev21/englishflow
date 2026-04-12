package com.example.englishflow.data;

import android.content.Context;
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
    private static final long ACCESS_FLUSH_INTERVAL_MS = 60_000L;

    private final Context appContext;
    private FirebaseUserStore userStore;
    private AppRepository repository;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable heartbeatRunnable = this::pushOnlineHeartbeat;
    private boolean isStarted;
    private long activeSessionStartMs;

    public UserPresenceTracker(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
    }

    public void start() {
        if (isStarted) {
            return;
        }
        ProcessLifecycleOwner.get().getLifecycle().addObserver(this);
        isStarted = true;
    }

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        activeSessionStartMs = System.currentTimeMillis();
        getRepository().markAppForegroundStarted(activeSessionStartMs);
        updateCurrentUserPresence(true);
        scheduleHeartbeat();
    }

    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
        handler.removeCallbacks(heartbeatRunnable);
        flushActiveSession(true);
        getRepository().markAppForegroundStopped();
        updateCurrentUserPresence(false);
    }

    private void scheduleHeartbeat() {
        handler.removeCallbacks(heartbeatRunnable);
        handler.postDelayed(heartbeatRunnable, HEARTBEAT_INTERVAL_MS);
    }

    private void pushOnlineHeartbeat() {
        updateCurrentUserPresence(true);
        flushActiveSession(false);
        if (ProcessLifecycleOwner.get().getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
            scheduleHeartbeat();
        }
    }

    private void flushActiveSession(boolean force) {
        long now = System.currentTimeMillis();
        long start = activeSessionStartMs;
        if (start <= 0L || now <= start) {
            return;
        }

        long elapsed = now - start;
        if (!force && elapsed < ACCESS_FLUSH_INTERVAL_MS) {
            return;
        }

        try {
            getRepository().recordAppAccessSession(start, now);
        } catch (Exception e) {
            Log.w(TAG, "Unable to record app access session", e);
        }
        activeSessionStartMs = now;
        getRepository().markAppForegroundStarted(activeSessionStartMs);
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

    private AppRepository getRepository() {
        if (repository == null) {
            repository = AppRepository.getInstance(appContext);
        }
        return repository;
    }
}