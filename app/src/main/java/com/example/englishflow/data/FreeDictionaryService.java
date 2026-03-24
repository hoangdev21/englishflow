package com.example.englishflow.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.UnknownHostException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class FreeDictionaryService {
    private static final String[] BASE_URLS = {
            "https://api.dictionaryapi.dev/api/v2/entries/en/",
            "https://dictionaryapi.dev/api/v2/entries/en/"
    };
    private static final int MAX_DEFINITIONS_PER_POS = 2;
    private static final int MAX_SYNONYMS = 5;

    private final OkHttpClient okHttpClient;

    public interface LookupCallback {
        void onSuccess(DictionaryResult result);
        void onNotFound();
        void onError(Exception exception);
    }

    public FreeDictionaryService(OkHttpClient okHttpClient) {
        this.okHttpClient = okHttpClient;
    }

    public void lookupWord(String word, LookupCallback callback) {
        if (word == null || word.trim().isEmpty()) {
            callback.onError(new IllegalArgumentException("Word is empty"));
            return;
        }

        String normalized = sanitizeQueryWord(word);
        String encodedWord = URLEncoder.encode(normalized, StandardCharsets.UTF_8);

        lookupWithFallback(normalized, encodedWord, 0, callback);
    }

    private void lookupWithFallback(String normalized, String encodedWord, int baseIndex, LookupCallback callback) {
        if (baseIndex >= BASE_URLS.length) {
            callback.onError(new IOException("Unable to connect dictionary service"));
            return;
        }

        Request request = new Request.Builder()
                .url(BASE_URLS[baseIndex] + encodedWord)
                .get()
                .build();

        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (e instanceof UnknownHostException && baseIndex + 1 < BASE_URLS.length) {
                    lookupWithFallback(normalized, encodedWord, baseIndex + 1, callback);
                    return;
                }
                callback.onError(e);
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (Response closeableResponse = response) {
                    if (closeableResponse.code() == 404) {
                        callback.onNotFound();
                        return;
                    }
                    if (!closeableResponse.isSuccessful()) {
                        if (baseIndex + 1 < BASE_URLS.length) {
                            lookupWithFallback(normalized, encodedWord, baseIndex + 1, callback);
                            return;
                        }
                        callback.onError(new IOException("HTTP " + closeableResponse.code()));
                        return;
                    }

                    String body = closeableResponse.body() != null ? closeableResponse.body().string() : "";
                    DictionaryResult result = parseResult(body, normalized);
                    callback.onSuccess(result);
                } catch (Exception exception) {
                    callback.onError(exception);
                }
            }
        });
    }

    private DictionaryResult parseResult(String rawJson, String fallbackWord) {
        JsonElement root = JsonParser.parseString(rawJson);
        if (!root.isJsonArray()) {
            return new DictionaryResult(fallbackWord, "", "", new ArrayList<>(), new ArrayList<>());
        }

        JsonArray entries = root.getAsJsonArray();
        if (entries.size() == 0 || !entries.get(0).isJsonObject()) {
            return new DictionaryResult(fallbackWord, "", "", new ArrayList<>(), new ArrayList<>());
        }

        JsonObject firstEntry = entries.get(0).getAsJsonObject();
        String word = getString(firstEntry, "word");
        if (word.isEmpty()) {
            word = fallbackWord;
        }

        String ipa = "";
        String audioUrl = "";
        if (firstEntry.has("phonetics") && firstEntry.get("phonetics").isJsonArray()) {
            JsonArray phonetics = firstEntry.getAsJsonArray("phonetics");
            for (JsonElement element : phonetics) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject phonetic = element.getAsJsonObject();
                if (ipa.isEmpty()) {
                    String text = getString(phonetic, "text");
                    if (!text.isEmpty()) {
                        ipa = text;
                    }
                }
                if (audioUrl.isEmpty()) {
                    String audio = getString(phonetic, "audio");
                    if (!audio.isEmpty()) {
                        audioUrl = audio;
                    }
                }
                if (!ipa.isEmpty() && !audioUrl.isEmpty()) {
                    break;
                }
            }
        }

        List<DictionaryResult.Definition> definitions = new ArrayList<>();
        Set<String> synonymSet = new LinkedHashSet<>();

        if (firstEntry.has("meanings") && firstEntry.get("meanings").isJsonArray()) {
            JsonArray meanings = firstEntry.getAsJsonArray("meanings");
            for (JsonElement meaningElement : meanings) {
                if (!meaningElement.isJsonObject()) {
                    continue;
                }

                JsonObject meaningObj = meaningElement.getAsJsonObject();
                String partOfSpeech = getString(meaningObj, "partOfSpeech");

                int definitionsAdded = 0;
                if (meaningObj.has("definitions") && meaningObj.get("definitions").isJsonArray()) {
                    JsonArray defsArray = meaningObj.getAsJsonArray("definitions");
                    for (JsonElement defElement : defsArray) {
                        if (!defElement.isJsonObject()) {
                            continue;
                        }
                        JsonObject defObj = defElement.getAsJsonObject();
                        String meaningText = getString(defObj, "definition");
                        if (meaningText.isEmpty()) {
                            continue;
                        }
                        String exampleText = getString(defObj, "example");
                        definitions.add(new DictionaryResult.Definition(partOfSpeech, meaningText, exampleText));
                        definitionsAdded++;
                        if (definitionsAdded >= MAX_DEFINITIONS_PER_POS) {
                            break;
                        }
                    }
                }

                if (meaningObj.has("synonyms") && meaningObj.get("synonyms").isJsonArray()) {
                    JsonArray synonymsArray = meaningObj.getAsJsonArray("synonyms");
                    for (JsonElement synonymElement : synonymsArray) {
                        if (!synonymElement.isJsonPrimitive()) {
                            continue;
                        }
                        String synonym = synonymElement.getAsString().trim();
                        if (!synonym.isEmpty()) {
                            synonymSet.add(synonym);
                        }
                        if (synonymSet.size() >= MAX_SYNONYMS) {
                            break;
                        }
                    }
                }
                if (synonymSet.size() >= MAX_SYNONYMS) {
                    break;
                }
            }
        }

        return new DictionaryResult(word, ipa, audioUrl, definitions, new ArrayList<>(synonymSet));
    }

    private String sanitizeQueryWord(String query) {
        String trimmed = query.trim();
        int spaceIndex = trimmed.indexOf(' ');
        if (spaceIndex > 0) {
            return trimmed.substring(0, spaceIndex).toLowerCase();
        }
        return trimmed.toLowerCase();
    }

    private String getString(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return "";
        }
        try {
            return object.get(key).getAsString().trim();
        } catch (Exception ignored) {
            return "";
        }
    }
}
