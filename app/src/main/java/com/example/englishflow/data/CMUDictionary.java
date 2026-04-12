package com.example.englishflow.data;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * Lazy-loaded CMU Pronouncing Dictionary reader.
 *
 * Dictionary is loaded once in background thread. Call preloadAsync() early
 * (for example in Activity.onCreate) so lookups are warm when needed.
 */
public final class CMUDictionary {

    private static final String TAG = "CMUDictionary";
    private static final String ASSET_FILE_NAME = "cmudict.dict";
    private static final Pattern PRONUNCIATION_VARIANT_SUFFIX = Pattern.compile("\\(\\d+\\)$");

    private static volatile CMUDictionary instance;

    private final Context appContext;
    private final ExecutorService loadExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean loadStarted = new AtomicBoolean(false);
    private final Object callbackLock = new Object();
    private final List<LoadCallback> pendingCallbacks = new ArrayList<>();
    private final ConcurrentHashMap<String, String> resolvedCache = new ConcurrentHashMap<>();
    private final Set<String> unknownArpabetWarnings = ConcurrentHashMap.newKeySet();

    private volatile boolean loaded;
    private volatile boolean fallbackMode;
    private volatile Map<String, String> ipaByWord = Collections.emptyMap();

    private CMUDictionary(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
    }

    @NonNull
    public static CMUDictionary getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (CMUDictionary.class) {
                if (instance == null) {
                    instance = new CMUDictionary(context);
                }
            }
        }
        return instance;
    }

    public void preloadAsync() {
        preloadAsync(null, null);
    }

    public void preloadAsync(@Nullable Runnable onReady, @Nullable Runnable onFallback) {
        if (loaded) {
            runImmediateCallback(onReady, onFallback, fallbackMode);
            return;
        }

        if (onReady != null || onFallback != null) {
            boolean runImmediate;
            boolean fallbackSnapshot;
            synchronized (callbackLock) {
                runImmediate = loaded;
                fallbackSnapshot = fallbackMode;
                if (!runImmediate) {
                    pendingCallbacks.add(new LoadCallback(onReady, onFallback));
                }
            }
            if (runImmediate) {
                runImmediateCallback(onReady, onFallback, fallbackSnapshot);
                return;
            }
        }

        if (loadStarted.compareAndSet(false, true)) {
            loadExecutor.execute(this::loadDictionaryInternal);
        }
    }

    @Nullable
    public String getIPA(@Nullable String word) {
        if (TextUtils.isEmpty(word)) {
            return null;
        }

        if (!loaded) {
            preloadAsync();
            return null;
        }

        String key = normalizeWordKey(word);
        if (key.isEmpty()) {
            return null;
        }

        String cached = resolvedCache.get(key);
        if (cached != null) {
            return cached;
        }

        String resolved = ipaByWord.get(key);
        if (resolved != null) {
            resolvedCache.putIfAbsent(key, resolved);
        }
        return resolved;
    }

    private void loadDictionaryInternal() {
        boolean loadFailed = false;
        Map<String, String> loadedMap = new HashMap<>(140_000);

        try (InputStream inputStream = appContext.getAssets().open(ASSET_FILE_NAME);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.startsWith(";;;")) {
                    continue;
                }

                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }

                String[] parts = trimmed.split("\\s+", 2);
                if (parts.length < 2) {
                    continue;
                }

                String rawWord = parts[0];
                String baseWord = PRONUNCIATION_VARIANT_SUFFIX.matcher(rawWord).replaceAll("");
                String key = normalizeWordKey(baseWord);
                if (key.isEmpty() || loadedMap.containsKey(key)) {
                    continue;
                }

                String ipaBody = arpabetToIpa(parts[1]);
                if (!ipaBody.isEmpty()) {
                    loadedMap.put(key, "/" + ipaBody + "/");
                }
            }

            ipaByWord = Collections.unmodifiableMap(loadedMap);
            resolvedCache.clear();
            if (ipaByWord.isEmpty()) {
                loadFailed = true;
                Log.w(TAG, "CMU dictionary loaded 0 entries. Falling back to internal IPA map.");
            } else {
                Log.i(TAG, "CMU dictionary loaded: " + ipaByWord.size() + " entries");
            }
        } catch (IOException e) {
            loadFailed = true;
            Log.w(TAG, "Failed to load cmudict.dict from assets. Falling back to internal IPA map.", e);
            ipaByWord = Collections.emptyMap();
            resolvedCache.clear();
        } finally {
            fallbackMode = loadFailed;
            loaded = true;
            notifyLoadCallbacks(loadFailed);
            loadExecutor.shutdown();
        }
    }

    @NonNull
    private String arpabetToIpa(@NonNull String arpabetSequence) {
        String[] phones = arpabetSequence.trim().split("\\s+");
        StringBuilder builder = new StringBuilder(phones.length * 2);

        for (String phone : phones) {
            if (phone == null || phone.isEmpty()) {
                continue;
            }

            String basePhone = phone.replaceAll("\\d", "");
            String ipa = ARPABET_TO_IPA.get(basePhone);
            if (ipa != null) {
                builder.append(ipa);
            } else if (!basePhone.isEmpty() && unknownArpabetWarnings.add(basePhone)) {
                Log.w(TAG, "Unknown ARPAbet token encountered: " + basePhone);
            }
        }

        return builder.toString();
    }

    private void notifyLoadCallbacks(boolean fallback) {
        List<LoadCallback> callbacks;
        synchronized (callbackLock) {
            if (pendingCallbacks.isEmpty()) {
                return;
            }
            callbacks = new ArrayList<>(pendingCallbacks);
            pendingCallbacks.clear();
        }

        for (LoadCallback callback : callbacks) {
            runImmediateCallback(callback.onReady, callback.onFallback, fallback);
        }
    }

    private void runImmediateCallback(@Nullable Runnable onReady,
                                      @Nullable Runnable onFallback,
                                      boolean fallback) {
        Runnable callback = fallback ? onFallback : onReady;
        if (callback != null) {
            callback.run();
        }
    }

    @NonNull
    private String normalizeWordKey(@Nullable String word) {
        if (word == null) {
            return "";
        }
        return word.toLowerCase(Locale.US).replaceAll("[^a-z]", "").trim();
    }

    private static final Map<String, String> ARPABET_TO_IPA = createArpabetToIpaMap();

    @NonNull
    private static Map<String, String> createArpabetToIpaMap() {
        Map<String, String> map = new HashMap<>();

        map.put("AE", "æ");
        map.put("AH", "ʌ");
        map.put("AO", "ɔ");
        map.put("AW", "aʊ");
        map.put("AY", "aɪ");
        map.put("AX", "ə");
        map.put("IX", "ɪ");
        map.put("EL", "əl");
        map.put("EM", "əm");
        map.put("EN", "ən");
        map.put("NX", "n");
        map.put("DX", "ɾ");
        map.put("Q", "ʔ");
        map.put("EH", "ɛ");
        map.put("ER", "ɜr");
        map.put("EY", "eɪ");
        map.put("IH", "ɪ");
        map.put("IY", "iː");
        map.put("OW", "oʊ");
        map.put("OY", "ɔɪ");
        map.put("UH", "ʊ");
        map.put("UW", "uː");
        map.put("AA", "ɑ");

        map.put("B", "b");
        map.put("CH", "tʃ");
        map.put("D", "d");
        map.put("DH", "ð");
        map.put("F", "f");
        map.put("G", "g");
        map.put("HH", "h");
        map.put("JH", "dʒ");
        map.put("K", "k");
        map.put("L", "l");
        map.put("M", "m");
        map.put("N", "n");
        map.put("NG", "ŋ");
        map.put("P", "p");
        map.put("R", "r");
        map.put("S", "s");
        map.put("SH", "ʃ");
        map.put("T", "t");
        map.put("TH", "θ");
        map.put("V", "v");
        map.put("W", "w");
        map.put("Y", "j");
        map.put("Z", "z");
        map.put("ZH", "ʒ");

        return Collections.unmodifiableMap(map);
    }

    private static final class LoadCallback {
        @Nullable
        final Runnable onReady;
        @Nullable
        final Runnable onFallback;

        LoadCallback(@Nullable Runnable onReady, @Nullable Runnable onFallback) {
            this.onReady = onReady;
            this.onFallback = onFallback;
        }
    }
}
