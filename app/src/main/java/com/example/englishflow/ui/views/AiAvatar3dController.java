package com.example.englishflow.ui.views;

import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.webkit.WebViewAssetLoader;

import com.example.englishflow.R;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Shared controller to render the AI avatar GLB in WebView (no native 3D libs).
 */
public final class AiAvatar3dController {

    private static final String TAG = "AiAvatar3dController";
    private static final String BRIDGE_NAME = "AvatarBridge";
    private static final String APP_ASSETS_BASE_URL = "https://appassets.androidplatform.net/";
    private static final String SCRIPT_PATH = "/assets/model-viewer.min.js";
    private static final String MODEL_PATH = "/res/raw/ai_bot_avatar.glb";
    private static final String MODEL_URL = APP_ASSETS_BASE_URL + "res/raw/ai_bot_avatar.glb";
    private static final String MODEL_VIEWER_SCRIPT_URL = APP_ASSETS_BASE_URL + "assets/model-viewer.min.js";
    private static final long LOAD_TIMEOUT_MS = 9_000L;

    private static final Object CACHE_LOCK = new Object();
    @Nullable
    private static byte[] cachedModelViewerScript;
    @Nullable
    private static byte[] cachedAvatarGlb;
    private static volatile boolean cacheWarmupStarted;

    private final WebView webView;
    @Nullable
    private final View fallbackView;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final WebViewAssetLoader assetLoader;
    private final float visualScale;
    private final float verticalBias;
    private final float depthBias;
    private final float yawDegrees;

    private boolean destroyed;
    private boolean modelLoaded;

    private final Runnable timeoutRunnable = () -> {
        if (!destroyed && !modelLoaded) {
            Log.w(TAG, "GLB load timeout, fallback to static avatar");
            showFallbackOnly();
        }
    };

    public AiAvatar3dController(
            @NonNull WebView webView,
            @Nullable View fallbackView,
            float modelScale,
            float yOffset,
            float zOffset,
            float yawDegrees
    ) {
        this.webView = webView;
        this.fallbackView = fallbackView;
        this.visualScale = clamp(modelScale, 0.8f, 2.2f);
        this.verticalBias = clamp(yOffset, -0.6f, 0.6f);
        this.depthBias = clamp(zOffset, -1f, 1f);
        this.yawDegrees = yawDegrees;
        this.assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(webView.getContext()))
                .addPathHandler("/res/", new WebViewAssetLoader.ResourcesPathHandler(webView.getContext()))
                .build();
    }

    public void loadModel() {
        if (destroyed) {
            return;
        }
        modelLoaded = false;
        warmupCacheAsync(webView.getContext().getApplicationContext());
        configureWebView();

        webView.setVisibility(View.VISIBLE);

        if (fallbackView != null) {
            fallbackView.setAlpha(1f);
            fallbackView.setVisibility(View.VISIBLE);
        }

        mainHandler.removeCallbacks(timeoutRunnable);
        mainHandler.postDelayed(timeoutRunnable, LOAD_TIMEOUT_MS);
        webView.loadDataWithBaseURL(APP_ASSETS_BASE_URL, buildHtml(), "text/html", "UTF-8", null);
    }

    public void onResume() {
        if (destroyed) {
            return;
        }
        try {
            webView.onResume();
        } catch (Exception e) {
            Log.w(TAG, "WebView resume failed", e);
        }
    }

    public void onPause() {
        if (destroyed) {
            return;
        }
        try {
            webView.onPause();
        } catch (Exception e) {
            Log.w(TAG, "WebView pause failed", e);
        }
    }

    public void onDestroy() {
        destroyed = true;
        mainHandler.removeCallbacks(timeoutRunnable);
        try {
            webView.onPause();
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.removeJavascriptInterface(BRIDGE_NAME);
            webView.setWebViewClient(new WebViewClient());
            webView.destroy();
        } catch (Exception e) {
            Log.w(TAG, "WebView destroy failed", e);
        }
    }

    private void configureWebView() {
        webView.setBackgroundColor(Color.TRANSPARENT);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setVerticalScrollBarEnabled(false);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setLoadsImagesAutomatically(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(true);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            settings.setOffscreenPreRaster(true);
        }

        webView.removeJavascriptInterface(BRIDGE_NAME);
        webView.addJavascriptInterface(new JsBridge(), BRIDGE_NAME);
        webView.setWebViewClient(new LocalWebViewClient());
    }

    @NonNull
    private String buildHtml() {
        float orbitDistance = clamp((118f / visualScale) + (depthBias * 12f), 52f, 120f);
        float orbitPitch = clamp(86f + (verticalBias * 20f), 70f, 100f);
        float fieldOfView = clamp(30f - ((visualScale - 1f) * 8f), 16f, 32f);
        float cssScale = clamp(1.10f + ((visualScale - 1f) * 0.55f), 1f, 1.7f);
        float translateY = clamp(-4f + (verticalBias * 14f), -18f, 18f);

        String yaw = formatDecimal(yawDegrees);
        String pitch = formatDecimal(orbitPitch);
        String distance = formatDecimal(orbitDistance);
        String fov = formatDecimal(fieldOfView);
        String scale = formatDecimal(cssScale);
        String offsetY = formatDecimal(translateY);

        return "<!doctype html>"
                + "<html><head><meta charset='utf-8'>"
                + "<meta name='viewport' content='width=device-width, initial-scale=1, viewport-fit=cover'>"
                + "<link rel='modulepreload' href='" + MODEL_VIEWER_SCRIPT_URL + "'>"
                + "<link rel='preload' as='fetch' href='" + MODEL_URL + "' crossorigin='anonymous'>"
                + "<style>"
                + "html,body{margin:0;padding:0;width:100%;height:100%;overflow:hidden;background:transparent;}"
                + "model-viewer{width:100%;height:100%;background:transparent;--poster-color:transparent;--progress-bar-color:transparent;"
                + "transform:scale(" + scale + ") translateY(" + offsetY + "%);transform-origin:center center;opacity:0;transition:opacity .18s ease;}"
                + "</style>"
                + "<script type='module' src='" + MODEL_VIEWER_SCRIPT_URL + "'></script>"
                + "</head><body>"
                + "<model-viewer id='avatar'"
                + " src='" + MODEL_URL + "'"
                + " autoplay"
                + " loading='eager'"
                + " reveal='auto'"
                + " disable-pan"
                + " disable-zoom"
                + " interaction-prompt='none'"
                + " touch-action='none'"
                + " shadow-intensity='0'"
                + " exposure='1.0'"
                + " camera-orbit='" + yaw + "deg " + pitch + "deg " + distance + "%'"
                + " min-camera-orbit='" + yaw + "deg " + pitch + "deg " + distance + "%'"
                + " max-camera-orbit='" + yaw + "deg " + pitch + "deg " + distance + "%'"
                + " field-of-view='" + fov + "deg'>"
                + "</model-viewer>"
                + "<script>"
                + "var host=window." + BRIDGE_NAME + ";"
                + "function notifyLoaded(){if(host&&host.onModelLoaded){host.onModelLoaded();}}"
                + "function notifyError(msg){if(host&&host.onModelError){host.onModelError(String(msg||'unknown'));}}"
                + "var mv=document.getElementById('avatar');"
                + "mv.addEventListener('load',function(){mv.style.opacity='1';notifyLoaded();});"
                + "mv.addEventListener('error',function(e){notifyError((e&&e.message)?e.message:'model-error');});"
                + "window.addEventListener('error',function(e){notifyError((e&&e.message)?e.message:'script-error');});"
                + "</script></body></html>";
    }

    private static void warmupCacheAsync(@NonNull Context context) {
        if (cacheWarmupStarted) {
            return;
        }
        synchronized (CACHE_LOCK) {
            if (cacheWarmupStarted) {
                return;
            }
            cacheWarmupStarted = true;
        }
        Thread cacheThread = new Thread(() -> {
            try {
                getOrLoadModelViewerScript(context);
                getOrLoadAvatarGlb(context);
            } catch (IOException e) {
                Log.w(TAG, "Avatar cache warmup failed", e);
            }
        }, "avatar-cache-warmup");
        cacheThread.setDaemon(true);
        cacheThread.start();
    }

    @NonNull
    private static byte[] getOrLoadModelViewerScript(@NonNull Context context) throws IOException {
        synchronized (CACHE_LOCK) {
            if (cachedModelViewerScript != null) {
                return cachedModelViewerScript;
            }
        }
        byte[] loaded = readAllBytes(context.getAssets().open("model-viewer.min.js"));
        synchronized (CACHE_LOCK) {
            if (cachedModelViewerScript == null) {
                cachedModelViewerScript = loaded;
            }
            return cachedModelViewerScript;
        }
    }

    @NonNull
    private static byte[] getOrLoadAvatarGlb(@NonNull Context context) throws IOException {
        synchronized (CACHE_LOCK) {
            if (cachedAvatarGlb != null) {
                return cachedAvatarGlb;
            }
        }
        byte[] loaded = readAllBytes(context.getResources().openRawResource(R.raw.ai_bot_avatar));
        synchronized (CACHE_LOCK) {
            if (cachedAvatarGlb == null) {
                cachedAvatarGlb = loaded;
            }
            return cachedAvatarGlb;
        }
    }

    @NonNull
    private static byte[] readAllBytes(@NonNull InputStream inputStream) throws IOException {
        try (InputStream in = inputStream; ByteArrayOutputStream out = new ByteArrayOutputStream(8192)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    @NonNull
    private static WebResourceResponse createResponse(@NonNull String mimeType, @Nullable String encoding, @NonNull byte[] data) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Cache-Control", "public, max-age=31536000, immutable");
        headers.put("Access-Control-Allow-Origin", "*");
        return new WebResourceResponse(mimeType, encoding, 200, "OK", headers, new ByteArrayInputStream(data));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    @NonNull
    private static String formatDecimal(float value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private void hideFallbackSmoothly() {
        if (fallbackView == null) {
            return;
        }
        fallbackView.animate()
                .alpha(0f)
                .setDuration(180L)
                .withEndAction(() -> {
                    if (fallbackView != null) {
                        fallbackView.setVisibility(View.GONE);
                    }
                })
                .start();
    }

    private void showFallbackOnly() {
        mainHandler.removeCallbacks(timeoutRunnable);
        webView.setVisibility(View.GONE);
        if (fallbackView != null) {
            fallbackView.setAlpha(1f);
            fallbackView.setVisibility(View.VISIBLE);
        }
    }

    private final class JsBridge {
        @JavascriptInterface
        public void onModelLoaded() {
            mainHandler.post(() -> {
                if (destroyed) {
                    return;
                }
                modelLoaded = true;
                mainHandler.removeCallbacks(timeoutRunnable);
                hideFallbackSmoothly();
            });
        }

        @JavascriptInterface
        public void onModelError(String message) {
            mainHandler.post(() -> {
                if (destroyed) {
                    return;
                }
                Log.w(TAG, "GLB render error: " + message);
                showFallbackOnly();
            });
        }
    }

    private final class LocalWebViewClient extends WebViewClient {
        @Nullable
        private WebResourceResponse interceptFromCache(@Nullable Uri uri) {
            if (uri == null) {
                return null;
            }
            String path = uri.getPath();
            if (path == null) {
                return null;
            }
            try {
                if (SCRIPT_PATH.equals(path)) {
                    return createResponse("text/javascript", "utf-8", getOrLoadModelViewerScript(webView.getContext().getApplicationContext()));
                }
                if (MODEL_PATH.equals(path)) {
                    return createResponse("model/gltf-binary", null, getOrLoadAvatarGlb(webView.getContext().getApplicationContext()));
                }
            } catch (IOException e) {
                Log.w(TAG, "Failed to serve cached avatar resource: " + path, e);
            }
            return null;
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            WebResourceResponse cached = interceptFromCache(request == null ? null : request.getUrl());
            if (cached != null) {
                return cached;
            }
            return request == null ? null : assetLoader.shouldInterceptRequest(request.getUrl());
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
            Uri uri = Uri.parse(url);
            WebResourceResponse cached = interceptFromCache(uri);
            if (cached != null) {
                return cached;
            }
            return assetLoader.shouldInterceptRequest(uri);
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (request != null && request.isForMainFrame()) {
                showFallbackOnly();
            }
        }

        @Override
        @SuppressWarnings("deprecation")
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            showFallbackOnly();
        }

        @Override
        public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
            showFallbackOnly();
            return true;
        }
    }
}
