package com.example.englishflow.data;

import android.content.Context;
import android.util.Log;

import com.example.englishflow.BuildConfig;
import com.example.englishflow.database.EnglishFlowDatabase;
import com.example.englishflow.database.entity.CustomVocabularyEntity;
import com.example.englishflow.ui.MapConversationActivity;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
        // Use model from BuildConfig if provided, fallback to standard versatile model
        this.modelName = (BuildConfig.GROQ_MODEL != null && !BuildConfig.GROQ_MODEL.isEmpty()) 
                ? BuildConfig.GROQ_MODEL : "llama-3.1-70b-versatile"; 
        
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .build();
                
        this.gson = new Gson();
        this.database = EnglishFlowDatabase.getInstance(context);
    }

    public interface ChatCallback {
        void onSuccess(String response, String correction, String explanation,
                       String vocabWord, String vocabIpa, String vocabMeaning,
                       String vocabExample, String vocabExampleVi);
        void onError(String error);
    }

    public interface RawCallback {
        void onSuccess(String rawResponse);
        void onError(String error);
    }

    public static class RagContext {
        public String lessonId = "";
        public String lessonTitle = "";
        public String minLevel = "";
        public String scenario = "";
        public List<String> keywords = new ArrayList<>();
        public List<String> expectedExpressions = new ArrayList<>();
        public List<String> knowledgeChunks = new ArrayList<>();

        public boolean hasAnyContext() {
            return (lessonId != null && !lessonId.trim().isEmpty())
                    || (lessonTitle != null && !lessonTitle.trim().isEmpty())
                    || (minLevel != null && !minLevel.trim().isEmpty())
                    || (scenario != null && !scenario.trim().isEmpty())
                    || (keywords != null && !keywords.isEmpty())
                    || (expectedExpressions != null && !expectedExpressions.isEmpty())
                    || (knowledgeChunks != null && !knowledgeChunks.isEmpty());
        }
    }

    public void getChatResponse(String userMessage, String topic, ChatCallback callback) {
        getChatResponse(userMessage, topic, null, null, null, callback);
    }

    public void getChatResponse(String userMessage, String topic, String customSystemPrompt, List<com.example.englishflow.ui.MapConversationActivity.ChatMessage> history, ChatCallback callback) {
        getChatResponse(userMessage, topic, customSystemPrompt, history, null, callback);
    }

    public void getChatResponse(String userMessage,
                                String topic,
                                String customSystemPrompt,
                                List<com.example.englishflow.ui.MapConversationActivity.ChatMessage> history,
                                RagContext ragContext,
                                ChatCallback callback) {
        new Thread(() -> {
            try {
                // 1. RAG: Search relevant vocabulary
                List<CustomVocabularyEntity> contextVocab = searchRelevantVocab(userMessage, ragContext);
                
                // 2. Build Prompt
                String systemPrompt = buildSystemPrompt(topic, contextVocab, ragContext);
                if (customSystemPrompt != null && !customSystemPrompt.trim().isEmpty()) {
                    systemPrompt += "\n\nROLEPLAY ADD-ON:\n" + customSystemPrompt.trim();
                }
                
                // 3. Call API
                JsonObject requestBody = buildChatRequestWithHistory(systemPrompt, userMessage, history);
                String rawResponse = callGroqApi(requestBody);
                
                // 4. Parse Response
                parseAndCallback(rawResponse, callback);
                
            } catch (Exception e) {
                Log.e(TAG, "Chat error", e);
                callback.onError(e.getMessage());
            }
        }).start();
    }

    public void getRawAiResponse(String prompt, RawCallback callback) {
        new Thread(() -> {
            try {
                JsonObject requestBody = new JsonObject();
                requestBody.addProperty("model", modelName);
                requestBody.addProperty("temperature", 0.8);
                requestBody.addProperty("max_tokens", 800);
                
                JsonArray messages = new JsonArray();
                JsonObject system = new JsonObject();
                system.addProperty("role", "system");
                system.addProperty("content", "You are a helpful language learning assistant. Provide natural and concise English phrases. NEVER RESPOND IN JSON FORMAT. ONLY PLAIN TEXT.");
                messages.add(system);
                
                JsonObject user = new JsonObject();
                user.addProperty("role", "user");
                user.addProperty("content", prompt);
                messages.add(user);
                
                requestBody.add("messages", messages);
                
                String response = callGroqApi(requestBody);
                callback.onSuccess(response);
            } catch (Exception e) {
                callback.onError(e.getMessage());
            }
        }).start();
    }

    private JsonObject buildChatRequestWithHistory(String systemPrompt, String userMessage, List<com.example.englishflow.ui.MapConversationActivity.ChatMessage> history) {
        JsonObject request = new JsonObject();
        request.addProperty("model", modelName);
        request.addProperty("temperature", 0.7);
        request.addProperty("max_tokens", 2000);
        
        JsonObject responseFormat = new JsonObject();
        responseFormat.addProperty("type", "json_object");
        request.add("response_format", responseFormat);

        JsonArray messages = new JsonArray();
        
        // System Prompt - MUST contain the word "json" for Groq JSON mode to work
        String finalSystemPrompt = systemPrompt;
        if (!finalSystemPrompt.toLowerCase().contains("json")) {
            finalSystemPrompt += "\n\nCRITICAL: You MUST respond in valid JSON format.";
        }

        JsonObject system = new JsonObject();
        system.addProperty("role", "system");
        system.addProperty("content", finalSystemPrompt);
        messages.add(system);
        
        // Add History
        if (history != null) {
            // Take only last 10 messages for context
            int start = Math.max(0, history.size() - 10);
            for (int i = start; i < history.size(); i++) {
                MapConversationActivity.ChatMessage msg = history.get(i);
                JsonObject h = new JsonObject();
                h.addProperty("role", msg.isAi ? "assistant" : "user");
                h.addProperty("content", msg.text);
                messages.add(h);
            }
        }
        
        // Current User Message
        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        user.addProperty("content", userMessage);
        messages.add(user);
        
        request.add("messages", messages);
        return request;
    }

    private List<CustomVocabularyEntity> searchRelevantVocab(String message, RagContext ragContext) {
        try {
            Set<String> tokenSet = new LinkedHashSet<>();
            addTokens(tokenSet, message);

            if (ragContext != null) {
                if (ragContext.keywords != null) {
                    for (String keyword : ragContext.keywords) {
                        addTokens(tokenSet, keyword);
                    }
                }
                if (ragContext.expectedExpressions != null) {
                    for (String expression : ragContext.expectedExpressions) {
                        addTokens(tokenSet, expression);
                    }
                }
            }

            if (tokenSet.isEmpty()) {
                return new ArrayList<>();
            }

            List<String> listToken = new ArrayList<>(tokenSet);
            if (listToken.size() > 20) {
                listToken = listToken.subList(0, 20);
            }

            String query = message;
            if (ragContext != null && ragContext.keywords != null && !ragContext.keywords.isEmpty()) {
                query = message + " " + String.join(" ", ragContext.keywords);
            }
            return database.customVocabularyDao().searchRelevant(listToken, query);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private void addTokens(Set<String> tokens, String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return;
        }

        String cleaned = raw.toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        if (cleaned.isEmpty()) {
            return;
        }

        for (String token : cleaned.split(" ")) {
            if (token.length() >= 2) {
                tokens.add(token);
            }
        }
    }

    private static final String SYSTEM_PROMPT =
            "Bạn là 'Flow' - một Gia sư tiếng Anh song ngữ (Bilingual English Tutor) chuyên nghiệp.\n\n" +
            "QUY TẮC NGÔN NGỮ (BẮT BUỘC):\n" +
            "1. LUÔN LUÔN giao tiếp và giải thích bằng TIẾNG VIỆT với người dùng.\n" +
            "2. Chỉ sử dụng tiếng Anh khi đưa ra ví dụ, dạy từ vựng, hoặc đặt câu hỏi luyện tập.\n" +
            "3. Ngay cả khi người dùng nói tiếng Anh, bạn vẫn phải phản hồi bằng Tiếng Việt (kèm dịch câu trả lời sang tiếng Anh nếu cần).\n\n" +
            "QUY TẮC PHẢN HỒI (JSON):\n" +
            "1. KHÔNG ĐƯỢC trả về mảng (Arrays []). Mọi giá trị trong JSON phải là Chuỗi (Strings \"\").\n" +
            "2. Mọi nội dung hiển thị cho người dùng (bao gồm lời chào, danh sách từ vựng, giải thích, câu hỏi tiếp theo) PHẢI nằm trọn vẹn trong trường 'response'.\n" +
            "3. Sử dụng cấu trúc sau cho mỗi từ vựng trong trường 'response':\n" +
            "   ✿ **Từ vựng**: [Word]\n" +
            "   ☁ **Phiên âm**: /[IPA]/\n" +
            "   ⚡ **Nghĩa**: [Nghĩa]\n" +
            "   ➠ **Ví dụ**: [Example]\n\n" +
            "QUY TẮC TRÌNH BÀY:\n" +
            "- Luôn sử dụng XUỐNG DÒNG KẾP giữa các đoạn.\n" +
            "- Trả về JSON với các trường: response, correction, explanation, vocab_word, vocab_ipa, vocab_meaning, vocab_example, vocab_example_vi.";

    private String buildSystemPrompt(String topic, List<CustomVocabularyEntity> vocab, RagContext ragContext) {
        StringBuilder sb = new StringBuilder();
        sb.append(SYSTEM_PROMPT);
        sb.append("\n\nCHỦ ĐỀ: ").append(topic);

        if (ragContext != null && ragContext.hasAnyContext()) {
            sb.append("\n\nRAG LESSON CONTEXT:");
            if (ragContext.lessonId != null && !ragContext.lessonId.trim().isEmpty()) {
                sb.append("\n- lesson_id: ").append(ragContext.lessonId.trim());
            }
            if (ragContext.lessonTitle != null && !ragContext.lessonTitle.trim().isEmpty()) {
                sb.append("\n- lesson_title: ").append(ragContext.lessonTitle.trim());
            }
            if (ragContext.minLevel != null && !ragContext.minLevel.trim().isEmpty()) {
                sb.append("\n- learner_level: ").append(ragContext.minLevel.trim());
            }
            if (ragContext.scenario != null && !ragContext.scenario.trim().isEmpty()) {
                sb.append("\n- scenario: ").append(ragContext.scenario.trim());
            }

            if (ragContext.keywords != null && !ragContext.keywords.isEmpty()) {
                sb.append("\n- target_keywords: ").append(String.join(", ", trimList(ragContext.keywords, 12)));
            }

            if (ragContext.expectedExpressions != null && !ragContext.expectedExpressions.isEmpty()) {
                sb.append("\n- expected_expressions: ").append(String.join(", ", trimList(ragContext.expectedExpressions, 12)));
            }

            if (ragContext.knowledgeChunks != null && !ragContext.knowledgeChunks.isEmpty()) {
                sb.append("\n- knowledge_chunks:");
                for (String chunk : trimList(ragContext.knowledgeChunks, 8)) {
                    sb.append("\n  • ").append(chunk);
                }
            }
        }
        
        if (vocab != null && !vocab.isEmpty()) {
            sb.append("\n\nDỮ LIỆU THAM KHẢO:\n");
            for (CustomVocabularyEntity v : vocab) {
                sb.append("- ").append(v.word).append(": ").append(v.meaning).append("\n");
            }
        }

        sb.append("\nRÀNG BUỘC THÊM:\n");
        sb.append("- Trả lời ngắn gọn, tự nhiên, ưu tiên tính hội thoại.\n");
        sb.append("- Nếu người học sai, sửa nhẹ nhàng và đưa 1 gợi ý cụ thể.\n");
        sb.append("- Không bịa thêm kiến thức ngoài RAG context khi có sẵn dữ liệu.");
        return sb.toString();
    }

    private List<String> trimList(List<String> source, int max) {
        List<String> result = new ArrayList<>();
        if (source == null || source.isEmpty() || max <= 0) {
            return result;
        }
        for (String item : source) {
            if (item == null || item.trim().isEmpty()) {
                continue;
            }
            result.add(item.trim());
            if (result.size() >= max) {
                break;
            }
        }
        return result;
    }

    private JsonObject buildChatRequest(String systemPrompt, String userMessage) {
        JsonObject request = new JsonObject();
        request.addProperty("model", modelName);
        request.addProperty("temperature", 0.6);
        request.addProperty("max_tokens", 2500);
        
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
                String err = response.body() != null ? response.body().string() : "Unknown Error";
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
            
            // Extract JSON block if surrounded by text
            if (sanitized.contains("{")) {
                int start = sanitized.indexOf("{");
                int end = sanitized.lastIndexOf("}");
                if (start != -1 && end != -1) {
                    sanitized = sanitized.substring(start, end + 1);
                }
            }

            JsonObject json = gson.fromJson(sanitized, JsonObject.class);
            
            // Comprehensive key search (Case-insensitive & Fallbacks)
            String answer = searchKeys(json, "response", "answer", "content", "result", "message", "nội dung", "phần trả lời", "phản hồi");
            
            // Total fallback: take whole input if no key matches
            if (answer == null) answer = rawResponse; 
            
            String correction  = searchKeys(json, "correction", "corrected", "sửa lỗi", "chỉnh sửa");
            String explanation = searchKeys(json, "explanation", "explain", "giải thích");
            String vocabWord      = searchKeys(json, "vocab_word", "word", "từ vựng");
            String vocabIpa       = searchKeys(json, "vocab_ipa", "ipa", "phonetic", "phiên âm");
            String vocabMeaning   = searchKeys(json, "vocab_meaning", "meaning", "definition", "nghĩa");
            String vocabExample   = searchKeys(json, "vocab_example", "example", "ví dụ");
            String vocabExampleVi = searchKeys(json, "vocab_example_vi", "example_vi", "translation", "dịch ví dụ");
            
            callback.onSuccess(answer, correction, explanation,
                    vocabWord, vocabIpa, vocabMeaning, vocabExample, vocabExampleVi);
        } catch (Exception e) {
            Log.e(TAG, "Parsing error: " + e.getMessage());
            callback.onSuccess(rawResponse, null, null, null, null, null, null, null);
        }
    }

    private String searchKeys(JsonObject json, String... keys) {
        for (String key : keys) {
            // Case-insensitive check
            for (String actualKey : json.keySet()) {
                if (actualKey.equalsIgnoreCase(key)) {
                    String val = getStringOrNull(json, actualKey);
                    if (val != null) return val;
                }
            }
        }
        return null;
    }

    private String getStringOrNull(JsonObject json, String key) {
        if (!json.has(key) || json.get(key).isJsonNull()) return null;
        
        com.google.gson.JsonElement element = json.get(key);
        if (element.isJsonPrimitive()) {
            String val = element.getAsString().trim();
            return (val.isEmpty() || val.equalsIgnoreCase("null")) ? null : val;
        } else if (element.isJsonArray()) {
            // If it's an array, join elements with newlines (prevents crash and shows content)
            StringBuilder sb = new StringBuilder();
            com.google.gson.JsonArray array = element.getAsJsonArray();
            for (int i = 0; i < array.size(); i++) {
                if (i > 0) sb.append("\n");
                sb.append(array.get(i).getAsString());
            }
            return sb.toString();
        } else {
            // Fallback for objects or other types
            return element.toString();
        }
    }
}
