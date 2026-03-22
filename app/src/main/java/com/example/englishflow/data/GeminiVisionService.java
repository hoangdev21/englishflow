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
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GeminiVisionService {
    private static final String DEFAULT_MODEL = "meta-llama/llama-4-scout-17b-16e-instruct";
    private static final String API_ENDPOINT = "https://api.groq.com/openai/v1/chat/completions";
    private static final Pattern ENGLISH_WORD_PATTERN = Pattern.compile("[a-zA-Z][a-zA-Z\\-]{1,}");
    private static final OkHttpClient httpClient = new OkHttpClient();
    private static final Gson gson = new Gson();

    private final String apiKey;
    private final String modelName;

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

    /**
    * Analyze an image using Groq + Llama 4 Vision and extract object labels.
     * Returns a label string that can be processed by ScanAnalyzer.
     */
    public String analyzeImage(Bitmap bitmap) throws Exception {
        if (bitmap == null) {
            throw new IllegalArgumentException("Bitmap cannot be null");
        }

        String base64Image = bitmapToBase64(bitmap);
        String prompt = "Identify the main object or subject in this image. "
                + "Respond with ONLY the object name in English, nothing else. "
                + "For example: 'bottle', 'book', 'phone', 'cup', 'chair', 'table', etc. "
                + "If uncertain, respond 'object'.";

        try {
            JsonObject requestBody = buildVisionRequest(prompt, base64Image);
            String responseText = callGroqApi(requestBody);

            return extractBestEnglishLabel(responseText);
        } catch (Exception e) {
            throw new RuntimeException("Error analyzing image with Groq: " + e.getMessage(), e);
        }
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
            JsonObject requestBody = buildTextRequest(prompt);
            String responseText = callGroqApi(requestBody);
            
            if (responseText == null || responseText.trim().isEmpty()) {
                return null;
            }
            
            String[] lines = responseText.trim().split("\n");
            if (lines.length == 0) {
                return null;
            }
            
            return lines[0].trim();
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

        try {
            JsonObject requestBody = buildTextRequest(prompt);
            return callGroqApi(requestBody);
        } catch (Exception e) {
            throw new RuntimeException("Grammar correction error: " + e.getMessage(), e);
        }
    }

    /**
     * Build text-only request for Groq Chat Completions API
     */
    private JsonObject buildTextRequest(String prompt) {
        JsonObject request = new JsonObject();
        request.addProperty("model", modelName);
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
    private JsonObject buildVisionRequest(String prompt, String base64Image) {
        JsonObject request = new JsonObject();
        request.addProperty("model", modelName);
        request.addProperty("temperature", 0.2);
        request.addProperty("max_tokens", 64);

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

        android.util.Log.d("GeminiVisionService", "Using single model: " + modelName);

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
