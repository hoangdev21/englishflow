package com.example.englishflow.data;

import android.content.Context;
import android.util.Log;

import com.example.englishflow.BuildConfig;
import com.example.englishflow.database.EnglishFlowDatabase;
import com.example.englishflow.database.entity.CustomVocabularyEntity;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class GroqChatService {
    private static final String TAG = "GroqChatService";
    private static final String API_ENDPOINT = "https://api.groq.com/openai/v1/chat/completions";
    private static final String DEFAULT_MODEL = "llama-3.3-70b-versatile";
    
    private final String apiKey;
    private final String modelName;
    private final OkHttpClient httpClient;
    private final Gson gson;
    private final EnglishFlowDatabase database;

    public GroqChatService(Context context) {
        this.apiKey = BuildConfig.GROQ_API_KEY;
        // Fix: Use a known good model. The "llama-4-scout" placeholder from build.gradle is invalid for Groq.
        this.modelName = "llama-3.3-70b-versatile"; 
        this.httpClient = new OkHttpClient();
        this.gson = new Gson();
        this.database = EnglishFlowDatabase.getInstance(context);
    }

    public interface ChatCallback {
        void onSuccess(String response, String correction, String explanation);
        void onError(String error);
    }

    public void getChatResponse(String userMessage, String topic, ChatCallback callback) {
        new Thread(() -> {
            try {
                // 1. RAG: Search relevant vocabulary
                List<CustomVocabularyEntity> contextVocab = searchRelevantVocab(userMessage);
                
                // 2. Build Prompt
                String systemPrompt = buildSystemPrompt(topic, contextVocab);
                
                // 3. Call API
                JsonObject requestBody = buildChatRequest(systemPrompt, userMessage);
                String rawResponse = callGroqApi(requestBody);
                
                // 4. Parse Response (Expecting a specific format for internal RAG logic)
                parseAndCallback(rawResponse, callback);
                
            } catch (Exception e) {
                Log.e(TAG, "Chat error", e);
                callback.onError(e.getMessage());
            }
        }).start();
    }

    private List<CustomVocabularyEntity> searchRelevantVocab(String message) {
        try {
            String[] tokens = message.toLowerCase(Locale.US).split("\\s+");
            List<String> listToken = Arrays.asList(tokens);
            return database.customVocabularyDao().searchRelevant(listToken, message);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private String buildSystemPrompt(String topic, List<CustomVocabularyEntity> vocab) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are Flow, a refined and highly expert English tutor from the EnglishFlow app. ");
        sb.append("You have deep expertise in the topic: ").append(topic).append(". ");
        sb.append("Always provide high-level, professional advice tailored specifically to this domain.\n\n");
        
        sb.append("CRITICAL FORMATTING RULES:\n");
        sb.append("1. LANGUAGE: Respond in Vietnamese. Keep core English terms in English.\n");
        sb.append("2. STRUCTURE: Every piece of information MUST be on a NEW LINE. Use DOUBLE NEWLINES (\\n\\n) between sections for clinical clarity.\n");
        sb.append("3. VOCABULARY QUERIES: If the user asks about a word, you MUST follow this EXACT multi-line template (NO PARAGRAPHS):\n\n");
        sb.append("✿ **Từ vựng**: [Word]\\n\\n");
        sb.append("☁ **Phiên âm (IPA)**: [Phonetic]\\n\\n");
        sb.append("☂ **Loại từ**: (Danh từ/Động từ/Tính từ/Trạng từ...)\\n\\n");
        sb.append("ϟ **Nghĩa**: [Clear definition in Vietnamese]\\n\\n");
        sb.append("💡 **Ví dụ & Dịch**:\\n");
        sb.append(" ➠ [Example Sentence 1]\\n");
        sb.append("  ⇋ [Dịch nghĩa ví dụ 1]\\n\\n");
        sb.append(" ➠ [Example Sentence 2]\\n");
        sb.append("  ⇋ [Dịch nghĩa ví dụ 2]\\n\\n");
        sb.append("📝 **Lưu ý (nếu có)**: [Usage notes or common collocations]\\n\\n");
        
        sb.append("\n4. RAG CONTEXT: Use the following database information if it helps answer accurately:\n");
        if (vocab != null && !vocab.isEmpty()) {
            for (CustomVocabularyEntity v : vocab) {
                sb.append("- Word: ").append(v.word);
                if (v.ipa != null) sb.append(" [").append(v.ipa).append("]");
                sb.append(" | Nghĩa: ").append(v.meaning);
                if (v.example != null) sb.append(" | Ví dụ: ").append(v.example);
                sb.append("\n");
            }
        }
        
        sb.append("\n5. CORRECTION: If the user's input has English grammar/spelling errors, provide accurate corrections in the JSON fields below.\n");
        
        sb.append("\nRESPONSE FORMAT (Strictly JSON):\n");
        sb.append("{\n");
        sb.append("  \"answer\": \"your structured, professional response with explicit double newlines \\n\\n between sections\",\n");
        sb.append("  \"correction\": \"only the corrected English text (null if no error)\",\n");
        sb.append("  \"explanation\": \"short Vietnamese explanation of the fix (null if no error)\"\n");
        sb.append("}");
        
        return sb.toString();
    }

    private JsonObject buildChatRequest(String systemPrompt, String userMessage) {
        JsonObject request = new JsonObject();
        request.addProperty("model", modelName);
        request.addProperty("temperature", 0.5);
        request.addProperty("max_tokens", 512);
        
        // Fix: Correct OpenAI JSON mode structure
        JsonObject responseFormat = new JsonObject();
        responseFormat.addProperty("type", "json_object");
        request.add("response_format", responseFormat);

        JsonArray messages = new JsonArray();
        
        JsonObject system = new JsonObject();
        system.addProperty("role", "system");
        system.addProperty("content", systemPrompt);
        messages.add(system);
        
        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        user.addProperty("content", userMessage);
        messages.add(user);
        
        request.add("messages", messages);
        return request;
    }

    private String callGroqApi(JsonObject requestBody) throws Exception {
        RequestBody body = RequestBody.create(
                requestBody.toString(),
                MediaType.parse("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url(API_ENDPOINT)
                .post(body)
                .addHeader("Authorization", "Bearer " + apiKey)
                .build();

        Log.d(TAG, "Calling Groq with model: " + modelName);

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String err = response.body() != null ? response.body().string() : "Empty";
                Log.e(TAG, "Groq Error: " + response.code() + " - " + err);
                throw new RuntimeException("API error: " + response.code() + " - " + err);
            }
            String result = response.body().string();
            JsonObject json = gson.fromJson(result, JsonObject.class);
            return json.getAsJsonArray("choices")
                    .get(0).getAsJsonObject()
                    .get("message").getAsJsonObject()
                    .get("content").getAsString();
        }
    }

    private void parseAndCallback(String rawResponse, ChatCallback callback) {
        try {
            Log.d(TAG, "Raw response: " + rawResponse);
            // Handle cases where the LLM might wrap JSON in markdown blocks
            String sanitized = rawResponse.trim();
            if (sanitized.startsWith("```json")) {
                sanitized = sanitized.substring(7);
            }
            if (sanitized.contains("```")) {
                sanitized = sanitized.replace("```json", "").replace("```", "");
            }
            sanitized = sanitized.trim();

            JsonObject json = gson.fromJson(sanitized, JsonObject.class);
            String answer = json.has("answer") && !json.get("answer").isJsonNull() ? json.get("answer").getAsString() : "Xin lỗi, đã có lỗi định dạng kết quả.";
            String correction = json.has("correction") && !json.get("correction").isJsonNull() ? json.get("correction").getAsString() : null;
            String explanation = json.has("explanation") && !json.get("explanation").isJsonNull() ? json.get("explanation").getAsString() : null;
            
            callback.onSuccess(answer, correction, explanation);
        } catch (Exception e) {
            Log.e(TAG, "Parsing error: " + e.getMessage());
            // Fallback: If parsing fails, just show the raw response as the answer
            callback.onSuccess(rawResponse, null, null);
        }
    }
}
