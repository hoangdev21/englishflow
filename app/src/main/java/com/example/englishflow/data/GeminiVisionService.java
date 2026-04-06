package com.example.englishflow.data;

import android.graphics.Bitmap;
import android.util.Base64;

import com.example.englishflow.BuildConfig;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GeminiVisionService {
    private static final String DEFAULT_MODEL = "meta-llama/llama-4-scout-17b-16e-instruct";
    private static final String SECONDARY_MODEL = "meta-llama/llama-4-scout-17b-16e-instruct"; // same model
    private static final String TERTIARY_MODEL = "meta-llama/llama-4-scout-17b-16e-instruct"; // same model
    private static final String API_ENDPOINT = "https://api.groq.com/openai/v1/chat/completions";
    private static final Pattern ENGLISH_WORD_PATTERN = Pattern.compile("[a-zA-Z][a-zA-Z\\-]{1,}");
    private static final int MAX_CACHE_SIZE = 128;
    private static final long CACHE_TTL_MS = 15L * 60L * 1000L;
    private static final OkHttpClient httpClient = NetworkClientProvider.getAiClient();
    private static final Gson gson = new Gson();
    private static final Map<String, CachedVisionResult> RESPONSE_CACHE = java.util.Collections.synchronizedMap(
            new LinkedHashMap<String, CachedVisionResult>(MAX_CACHE_SIZE, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CachedVisionResult> eldest) {
                    return size() > MAX_CACHE_SIZE;
                }
            }
    );

    private final String apiKey;
    private final String modelName;

    public static class VisionResult {
        private final String primaryLabel;
        private final float confidence;
        private final boolean fromCache;
        private final String rawResponse;

        public VisionResult(String primaryLabel, float confidence, boolean fromCache, String rawResponse) {
            this.primaryLabel = primaryLabel;
            this.confidence = confidence;
            this.fromCache = fromCache;
            this.rawResponse = rawResponse;
        }

        public String getPrimaryLabel() {
            return primaryLabel;
        }

        public float getConfidence() {
            return confidence;
        }

        public boolean isFromCache() {
            return fromCache;
        }

        public String getRawResponse() {
            return rawResponse;
        }
    }

    private static class CachedVisionResult {
        private final VisionResult result;
        private final long createdAtMs;

        private CachedVisionResult(VisionResult result, long createdAtMs) {
            this.result = result;
            this.createdAtMs = createdAtMs;
        }
    }

    public GeminiVisionService() {
        this.apiKey = BuildConfig.GROQ_API_KEY;
        this.modelName = (BuildConfig.GROQ_MODEL == null || BuildConfig.GROQ_MODEL.trim().isEmpty())
                ? DEFAULT_MODEL
                : BuildConfig.GROQ_MODEL.trim();
        android.util.Log.d("GeminiVisionService", "Constructor called");
        android.util.Log.d("GeminiVisionService", "apiKey from BuildConfig: " + (apiKey != null ? (apiKey.isEmpty() ? "EMPTY" : apiKey.substring(0, Math.min(10, apiKey.length())) + "...") : "NULL"));
        android.util.Log.d("GeminiVisionService", "model from BuildConfig: " + modelName);
        
        if (apiKey == null || apiKey.isEmpty()) {
            android.util.Log.e("GeminiVisionService", "API key not configured!");
            throw new IllegalStateException("GROQ_API_KEY not configured. Add it to local.properties");
        }
        android.util.Log.d("GeminiVisionService", "API key validated successfully");
    }

    public static class VisionData {
        public String word;
        public String ipa;
        public String meaning;
        public String wordType;
        public String example;
        public String exampleVi;
        public String category;
        public String note;
        public java.util.List<String> related;
    }

    /**
     * Analyze an image using Groq + Vision and return a full ScanResult.
     */
    public ScanResult analyzeImageFull(Bitmap bitmap) throws Exception {
        if (bitmap == null) throw new IllegalArgumentException("Bitmap cannot be null");

        String prompt = "Identify the main object in this image and provide English learning details.\n"
                + "Return ONLY a JSON object with this structure:\n"
                + "{\n"
                + "  \"word\": \"English name (e.g., 'Apple')\",\n"
                + "  \"ipa\": \"IPA pronunciation (e.g., '/ˈæp.əl/')\",\n"
                + "  \"meaning\": \"Vietnamese translation\",\n"
                + "  \"wordType\": \"Part of speech (noun/verb/adj)\",\n"
                + "  \"example\": \"Short English sentence using the word\",\n"
                + "  \"exampleVi\": \"Vietnamese translation of the example\",\n"
                + "  \"category\": \"Category (e.g., Food, Tech, Nature)\",\n"
                + "  \"note\": \"One interesting fact or usage note in Vietnamese (Mẹo hay bằng tiếng Việt)\",\n"
                + "  \"related\": [\"3-4 related english words\"]\n"
                + "}\n"
                + "Strictly return JSON only. No other text.";

        VisionResult result = analyzeVision(bitmap, prompt, false, "full_scan");
        String json = result.getRawResponse();
        
        // Clean up common AI commentary if any
        if (json.contains("{") && json.contains("}")) {
            json = json.substring(json.indexOf("{"), json.lastIndexOf("}") + 1);
        }

        try {
            VisionData data = gson.fromJson(json, VisionData.class);
            return new ScanResult(
                    data.word != null ? data.word : "object",
                    data.ipa != null ? data.ipa : "-",
                    data.meaning != null ? data.meaning : "vật thể",
                    data.wordType != null ? data.wordType : "noun",
                    data.example != null ? data.example : "Empty example",
                    data.exampleVi != null ? data.exampleVi : "VD trống",
                    data.category != null ? data.category : "General",
                    data.note != null ? data.note : "Không có mẹo học",
                    data.related != null ? data.related : java.util.Arrays.asList("thing", "item")
            );
        } catch (Exception e) {
            android.util.Log.e("GeminiVisionService", "JSON Parse error: " + e.getMessage());
            // Fallback
            return new ScanResult(result.getPrimaryLabel(), "-", "vật thể", "noun", 
                "No example", "VD trống", "General", "JSON error", java.util.Arrays.asList("object"));
        }
    }

    public VisionResult analyzeImageWithConfidence(Bitmap bitmap) throws Exception {
        if (bitmap == null) throw new IllegalArgumentException("Bitmap cannot be null");
        String prompt = "Identify the main object or subject in this image. "
                + "Respond with ONLY the object name in English, nothing else. "
                + "For example: 'bottle', 'book', 'phone', 'cup', 'chair', 'table', etc. "
                + "If uncertain, respond 'object'.";
        return analyzeVision(bitmap, prompt, true, "scan");
    }

    public VisionResult analyzePreviewVietnamese(Bitmap bitmap) throws Exception {
        if (bitmap == null) throw new IllegalArgumentException("Bitmap cannot be null");
        String prompt = "Nhan dien vat the chinh trong anh va tra loi bang TIENG VIET. "
                + "Chi tra ve duy nhat 1-3 tu, khong giai thich, khong dau cau. "
                + "Neu khong chac, tra ve 'vat the'.";
        return analyzeVision(bitmap, prompt, false, "preview");
    }

    private VisionResult analyzeVision(Bitmap bitmap,
                                       String prompt,
                                       boolean expectEnglishLabel,
                                       String modeKey) throws Exception {
        String cacheKey = modeKey + ":" + buildBitmapFingerprint(bitmap);
        VisionResult cached = getCached(cacheKey);
        if (cached != null) {
            return cached;
        }

        String base64Image = bitmapToBase64(bitmap);

        String[] models = {
                modelName,
                SECONDARY_MODEL,
                TERTIARY_MODEL
        };

        Exception lastEx = null;
        for (String model : models) {
            try {
                JsonObject requestBody = buildVisionRequest(prompt, base64Image, model);
                String responseText = callGroqApi(requestBody);

                String normalizedLabel = expectEnglishLabel
                        ? extractBestEnglishLabel(responseText)
                        : extractBestVietnameseHint(responseText);
                float confidence = estimateConfidence(responseText, normalizedLabel, bitmap, expectEnglishLabel);

                VisionResult result = new VisionResult(normalizedLabel, confidence, false, responseText);
                cacheResult(cacheKey, result);
                return result;
            } catch (Exception e) {
                lastEx = e;
                String msg = e.getMessage() != null ? e.getMessage() : "";
                if (msg.contains("429") || msg.contains("400") || msg.contains("404")) {
                    android.util.Log.w("GeminiVisionService", "Model " + model + " failed (HTTP error), trying fallback...");
                    continue;
                }
                break; // Don't retry for other errors (e.g. 401)
            }
        }
        throw new RuntimeException("Error analyzing image with Groq (exhausted fallbacks): " + 
                (lastEx != null ? lastEx.getMessage() : "Unknown error"), lastEx);
    }

    private VisionResult getCached(String cacheKey) {
        CachedVisionResult cached = RESPONSE_CACHE.get(cacheKey);
        if (cached == null) {
            return null;
        }

        long age = System.currentTimeMillis() - cached.createdAtMs;
        if (age > CACHE_TTL_MS) {
            RESPONSE_CACHE.remove(cacheKey);
            return null;
        }

        return new VisionResult(
                cached.result.getPrimaryLabel(),
                Math.min(0.99f, cached.result.getConfidence() + 0.03f),
                true,
                cached.result.getRawResponse()
        );
    }

    private void cacheResult(String cacheKey, VisionResult result) {
        RESPONSE_CACHE.put(cacheKey, new CachedVisionResult(result, System.currentTimeMillis()));
    }

    private String extractBestEnglishLabel(String responseText) {
        if (responseText == null || responseText.trim().isEmpty()) {
            return "object";
        }

        String normalized = responseText
                .replace("\r", "\n")
                .replaceAll("[\"'`*#]", " ")
                .trim();
        if (normalized.isEmpty()) {
            return "object";
        }

        // Prefer the first plausible English token and ignore non-English sentences.
        Matcher matcher = ENGLISH_WORD_PATTERN.matcher(normalized);
        if (!matcher.find()) {
            return "object";
        }

        String token = matcher.group().toLowerCase(Locale.US);
        if (token.length() < 2) {
            return "object";
        }

        // Normalize frequent generic outputs to the fallback bucket.
        if ("image".equals(token) || "picture".equals(token) || "photo".equals(token)) {
            return "object";
        }

        return token;
    }

    private String extractBestVietnameseHint(String responseText) {
        if (responseText == null || responseText.trim().isEmpty()) {
            return "vat the";
        }

        String normalized = responseText
                .replace("\r", "\n")
                .replaceAll("[\"'`*#]", " ")
                .trim();
        if (normalized.isEmpty()) {
            return "vat the";
        }

        String[] lines = normalized.split("\\n");
        String candidate = lines.length > 0 ? lines[0] : normalized;
        candidate = candidate.split("[,.;:]")[0].trim().toLowerCase(Locale.US);
        if (candidate.isEmpty()) {
            return "vat the";
        }

        String[] words = candidate.split("\\s+");
        if (words.length > 3) {
            candidate = words[0] + " " + words[1] + " " + words[2];
        }

        if ("object".equals(candidate) || "unknown".equals(candidate)) {
            return "vat the";
        }

        return candidate;
    }

    private float estimateConfidence(String responseText,
                                     String normalizedLabel,
                                     Bitmap bitmap,
                                     boolean expectEnglishLabel) {
        float score = 0.45f;
        String response = responseText == null ? "" : responseText.trim();

        if (!response.isEmpty()) {
            score += 0.15f;
        }

        if (!"object".equalsIgnoreCase(normalizedLabel)
                && !"unknown".equalsIgnoreCase(normalizedLabel)
                && !"vat the".equalsIgnoreCase(normalizedLabel)) {
            score += 0.18f;
        }

        if (response.length() > 0 && response.length() <= 24) {
            score += 0.08f;
        }

        if (expectEnglishLabel && ENGLISH_WORD_PATTERN.matcher(normalizedLabel).matches()) {
            score += 0.08f;
        }

        int longest = Math.max(bitmap.getWidth(), bitmap.getHeight());
        if (longest >= 720) {
            score += 0.06f;
        }

        return Math.max(0.2f, Math.min(0.98f, score));
    }

    /**
     * Get Vietnamese meaning suggestion for an English word.
     */
    public String getVietnameseMeaning(String englishWord) throws Exception {
        if (englishWord == null || englishWord.trim().isEmpty()) {
            throw new IllegalArgumentException("English word cannot be empty");
        }

        String prompt = "Translate this English word to Vietnamese meaning only (short phrase, 1-5 words): " + englishWord 
                + "\nRespond with ONLY the Vietnamese translation, no explanation.";

        try {
            String[] models = {"llama-3.3-70b-versatile", "llama-3.1-8b-instant", "mixtral-8x7b-32768", modelName};
            for (String model : models) {
                try {
                    JsonObject requestBody = buildTextRequest(prompt, model);
                    String responseText = callGroqApi(requestBody);
                    
                    if (responseText == null || responseText.trim().isEmpty()) {
                        continue;
                    }
                    
                    String[] lines = responseText.trim().split("\n");
                    if (lines.length > 0) {
                        return lines[0].trim();
                    }
                } catch (Exception e) {
                    String msg = e.getMessage() != null ? e.getMessage() : "";
                    if (msg.contains("429") || msg.contains("400") || msg.contains("404")) {
                        continue;
                    }
                    break;
                }
            }
            return null;
        } catch (Exception e) {
            return null; // Fallback on error, don't throw
        }
    }

    /**
     * Correct grammar in an English sentence.
     */
    public String correctGrammar(String sentence) throws Exception {
        if (sentence == null || sentence.trim().isEmpty()) {
            throw new IllegalArgumentException("Sentence cannot be empty");
        }

        String prompt = "Correct the grammar in this sentence and explain the correction briefly.\nSentence: " + sentence 
                + "\nProvide: [CORRECTED SENTENCE] then [EXPLANATION]";

        String[] models = {"llama-3.3-70b-versatile", "llama-3.1-8b-instant", "mixtral-8x7b-32768", modelName};
        Exception lastEx = null;
        for (String model : models) {
            try {
                JsonObject requestBody = buildTextRequest(prompt, model);
                return callGroqApi(requestBody);
            } catch (Exception e) {
                lastEx = e;
                String msg = e.getMessage() != null ? e.getMessage() : "";
                if (msg.contains("429") || msg.contains("400") || msg.contains("404")) {
                    continue;
                }
                break;
            }
        }
        throw new RuntimeException("Grammar correction error (exhausted fallbacks): " + (lastEx != null ? lastEx.getMessage() : ""), lastEx);
    }

    /**
     * Build text-only request for Groq Chat Completions API
     */
    private JsonObject buildTextRequest(String prompt, String model) {
        JsonObject request = new JsonObject();
        request.addProperty("model", model);
        request.addProperty("temperature", 0.2);
        request.addProperty("max_tokens", 256);

        JsonArray messages = new JsonArray();
        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        user.addProperty("content", prompt);
        messages.add(user);
        request.add("messages", messages);
        
        return request;
    }

    /**
     * Build image + text request for Groq Vision API
     */
    private JsonObject buildVisionRequest(String prompt, String base64Image, String model) {
        JsonObject request = new JsonObject();
        request.addProperty("model", model);
        request.addProperty("temperature", 0.2);
        request.addProperty("max_tokens", 1024); // Increased for full scan detail

        JsonArray messages = new JsonArray();
        JsonObject user = new JsonObject();
        user.addProperty("role", "user");

        JsonArray content = new JsonArray();
        JsonObject textPart = new JsonObject();
        textPart.addProperty("type", "text");
        textPart.addProperty("text", prompt);
        content.add(textPart);

        JsonObject imagePart = new JsonObject();
        imagePart.addProperty("type", "image_url");
        JsonObject imageUrl = new JsonObject();
        imageUrl.addProperty("url", "data:image/jpeg;base64," + base64Image);
        imagePart.add("image_url", imageUrl);
        content.add(imagePart);

        user.add("content", content);
        messages.add(user);
        request.add("messages", messages);
        
        return request;
    }

    /**
     * Call Groq API with HTTP request
     */
    private String callGroqApi(JsonObject requestBody) throws Exception {
        android.util.Log.d("GeminiVisionService", "API Key status: " + (apiKey != null && apiKey.length() > 10 ? apiKey.substring(0, 10) + "..." : "INVALID"));

        String requestBodyStr = requestBody.toString();
        android.util.Log.d("GeminiVisionService", "Request body: " + requestBodyStr.substring(0, Math.min(200, requestBodyStr.length())) + "...");

        String currentModel = requestBody.has("model") ? requestBody.get("model").getAsString() : modelName;
        android.util.Log.d("GeminiVisionService", "Using model handle: " + currentModel);

        RequestBody body = RequestBody.create(
                requestBodyStr,
                MediaType.parse("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url(API_ENDPOINT)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + apiKey)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            android.util.Log.d("GeminiVisionService", "Single request response code: " + response.code());

            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                throw new RuntimeException("Groq API error: HTTP " + response.code() + " - " + errorBody);
            }

            String responseBody = response.body() != null ? response.body().string() : "";
            if (responseBody.isEmpty()) {
                throw new RuntimeException("Empty response from Gemini API");
            }

            android.util.Log.d("GeminiVisionService", "Response body: " + responseBody.substring(0, Math.min(300, responseBody.length())) + "...");

            JsonObject parsedResponse = gson.fromJson(responseBody, JsonObject.class);
            if (parsedResponse == null) {
                throw new RuntimeException("Failed to parse JSON response");
            }

            if (parsedResponse.has("choices")) {
                JsonArray choices = parsedResponse.getAsJsonArray("choices");
                if (choices != null && choices.size() > 0) {
                    JsonObject choice = choices.get(0).getAsJsonObject();
                    if (choice != null && choice.has("message")) {
                        JsonObject message = choice.getAsJsonObject("message");
                        if (message != null && message.has("content")) {
                            String text = message.get("content").getAsString();
                            if (text != null && !text.isEmpty()) {
                                android.util.Log.d("GeminiVisionService", "Extracted text: " + text);
                                return text;
                            }
                        }
                    }
                }
            }

            throw new RuntimeException("Invalid Groq API response format: missing choices or content");
        }
    }

    /**
     * Convert Bitmap to Base64 string (useful for API calls).
     */
    private String bitmapToBase64(Bitmap bitmap) {
        bitmap = downscaleIfNeeded(bitmap, 1024);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream);
        byte[] imageBytes = outputStream.toByteArray();
        // Remove newlines from Base64 to avoid API errors
        return Base64.encodeToString(imageBytes, Base64.NO_WRAP);
    }

    private Bitmap downscaleIfNeeded(Bitmap src, int maxDim) {
        int w = src.getWidth();
        int h = src.getHeight();
        int longest = Math.max(w, h);
        if (longest <= maxDim) {
            return src;
        }

        float scale = (float) maxDim / (float) longest;
        int newW = Math.max(1, Math.round(w * scale));
        int newH = Math.max(1, Math.round(h * scale));
        return Bitmap.createScaledBitmap(src, newW, newH, true);
    }

    private String buildBitmapFingerprint(Bitmap bitmap) {
        Bitmap sample = downscaleIfNeeded(bitmap, 48);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        sample.compress(Bitmap.CompressFormat.JPEG, 40, out);
        byte[] data = out.toByteArray();

        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(data);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format(Locale.US, "%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return bitmap.getWidth() + "x" + bitmap.getHeight() + ":" + (data.length > 0 ? data[0] : 0);
        }
    }

    /**
     * Check if API key is configured.
     */
    public static boolean isApiKeyConfigured() {
        return BuildConfig.GROQ_API_KEY != null && !BuildConfig.GROQ_API_KEY.isEmpty();
    }

    /**
     * Get configured API key (for debugging/logging).
     */
    public static String getApiKeyStatus() {
        if (!isApiKeyConfigured()) {
            return "NOT_CONFIGURED";
        }
        String key = BuildConfig.GROQ_API_KEY;
        return key.substring(0, Math.min(5, key.length())) + "..." + key.substring(Math.max(0, key.length() - 5));
    }
}
