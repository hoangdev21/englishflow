package com.example.englishflow.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MyMemoryService {
    private static final String BASE_URL = "https://api.mymemory.translated.net/get";

    private final OkHttpClient okHttpClient;

    public interface TranslationCallback {
        void onSuccess(String translatedWord);
        void onError(Exception exception);
    }

    public MyMemoryService(OkHttpClient okHttpClient) {
        this.okHttpClient = okHttpClient;
    }

    public void translateViToEn(String text, TranslationCallback callback) {
        if (text == null || text.trim().isEmpty()) {
            callback.onError(new IllegalArgumentException("Text is empty"));
            return;
        }

        String encodedText = URLEncoder.encode(text.trim(), StandardCharsets.UTF_8);
        String url = BASE_URL + "?q=" + encodedText + "&langpair=vi|en";

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError(e);
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (Response closeableResponse = response) {
                    if (!closeableResponse.isSuccessful()) {
                        callback.onError(new IOException("HTTP " + closeableResponse.code()));
                        return;
                    }

                    String body = closeableResponse.body() != null ? closeableResponse.body().string() : "";
                    String translated = parseTranslatedText(body);
                    String normalizedWord = extractFirstWord(translated);
                    if (normalizedWord.isEmpty()) {
                        callback.onError(new IllegalStateException("Unable to translate query"));
                        return;
                    }
                    callback.onSuccess(normalizedWord);
                } catch (Exception exception) {
                    callback.onError(exception);
                }
            }
        });
    }

    private String parseTranslatedText(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonObject responseData = root.getAsJsonObject("responseData");
        if (responseData == null || !responseData.has("translatedText") || responseData.get("translatedText").isJsonNull()) {
            return "";
        }
        return responseData.get("translatedText").getAsString();
    }

    private String extractFirstWord(String translatedText) {
        if (translatedText == null) {
            return "";
        }

        String trimmed = translatedText.trim();
        if (trimmed.isEmpty()) {
            return "";
        }

        String firstToken = trimmed.split("\\s+")[0];
        String cleaned = firstToken.toLowerCase(Locale.US).replaceAll("[^a-z]", "");
        return cleaned.trim();
    }
}
