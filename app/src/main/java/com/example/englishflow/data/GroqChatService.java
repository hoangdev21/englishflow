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
        this.modelName = "llama-3.3-70b-versatile"; 
        this.httpClient = new OkHttpClient();
        this.gson = new Gson();
        this.database = EnglishFlowDatabase.getInstance(context);
    }

    public interface ChatCallback {
        void onSuccess(String response, String correction, String explanation,
                       String vocabWord, String vocabIpa, String vocabMeaning,
                       String vocabExample, String vocabExampleVi);
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
                
                // 4. Parse Response
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
        sb.append("3. VOCABULARY QUERIES: If the user asks about a word or phrase, you MUST:\n");
        sb.append("   a) Follow this EXACT multi-line template in the 'answer' field:\n\n");
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
        sb.append("   b) ALSO fill the vocab_* JSON fields below with the extracted data.\n\n");
        
        sb.append("4. RAG CONTEXT: Use the following database information if it helps answer accurately:\n");
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
        sb.append("  \"explanation\": \"short Vietnamese explanation of the fix (null if no error)\",\n");
        sb.append("  \"vocab_word\": \"the English word/phrase being queried (null if not a vocabulary query)\",\n");
        sb.append("  \"vocab_ipa\": \"the IPA phonetic transcription (null if not a vocabulary query)\",\n");
        sb.append("  \"vocab_meaning\": \"the Vietnamese meaning/definition (null if not a vocabulary query)\",\n");
        sb.append("  \"vocab_example\": \"the best English example sentence (null if not a vocabulary query)\",\n");
        sb.append("  \"vocab_example_vi\": \"Vietnamese translation of the example sentence (null if not a vocabulary query)\"\n");
        sb.append("}");
        
        return sb.toString();
    }

    private JsonObject buildChatRequest(String systemPrompt, String userMessage) {
        JsonObject request = new JsonObject();
        request.addProperty("model", modelName);
        request.addProperty("temperature", 0.5);
        request.addProperty("max_tokens", 700);
        
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
            String sanitized = rawResponse.trim();
            if (sanitized.startsWith("```json")) {
                sanitized = sanitized.substring(7);
            }
            if (sanitized.contains("```")) {
                sanitized = sanitized.replace("```json", "").replace("```", "");
            }
            sanitized = sanitized.trim();

            JsonObject json = gson.fromJson(sanitized, JsonObject.class);
            
            String answer = getStringOrNull(json, "answer");
            if (answer == null) answer = "Xin lỗi, đã có lỗi định dạng kết quả.";
            
            String correction  = getStringOrNull(json, "correction");
            String explanation = getStringOrNull(json, "explanation");
            String vocabWord      = getStringOrNull(json, "vocab_word");
            String vocabIpa       = getStringOrNull(json, "vocab_ipa");
            String vocabMeaning   = getStringOrNull(json, "vocab_meaning");
            String vocabExample   = getStringOrNull(json, "vocab_example");
            String vocabExampleVi = getStringOrNull(json, "vocab_example_vi");
            
            callback.onSuccess(answer, correction, explanation,
                    vocabWord, vocabIpa, vocabMeaning, vocabExample, vocabExampleVi);
        } catch (Exception e) {
            Log.e(TAG, "Parsing error: " + e.getMessage());
            callback.onSuccess(rawResponse, null, null, null, null, null, null, null);
        }
    }

    private String getStringOrNull(JsonObject json, String key) {
        if (json.has(key) && !json.get(key).isJsonNull()) {
            String val = json.get(key).getAsString().trim();
            return val.isEmpty() || val.equalsIgnoreCase("null") ? null : val;
        }
        return null;
    }
}
