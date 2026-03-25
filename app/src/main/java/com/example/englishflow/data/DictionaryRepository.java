package com.example.englishflow.data;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;

public class DictionaryRepository {
    private static final Pattern VIETNAMESE_CHAR_PATTERN = Pattern.compile("[àáâãèéêìíòóôõùúýăđơưạảấầẩẫậắằẳẵặẹẻẽếềểễệỉịọỏốồổỗộớờởỡợụủứừửữựỳỵỷỹ]", Pattern.CASE_INSENSITIVE);

    private final FreeDictionaryService freeDictionaryService;
    private final MyMemoryService myMemoryService;

    public interface SearchCallback {
        void onSuccess(DictionaryResult result);
        void onNotFound(String query);
        void onError(String message);
    }

    public DictionaryRepository(FreeDictionaryService freeDictionaryService, MyMemoryService myMemoryService) {
        this.freeDictionaryService = freeDictionaryService;
        this.myMemoryService = myMemoryService;
    }

    public void search(String query, SearchCallback callback) {
        if (query == null || query.trim().isEmpty()) {
            callback.onError("Vui long nhap tu can tra");
            return;
        }

        String normalizedQuery = query.trim();
        if (containsVietnameseChars(normalizedQuery)) {
            myMemoryService.translateViToEn(normalizedQuery, new MyMemoryService.TranslationCallback() {
                @Override
                public void onSuccess(String translatedWord) {
                    lookupEnglishWord(translatedWord, callback);
                }

                @Override
                public void onError(Exception exception) {
                    callback.onError("Khong the dich tieng Viet: " + safeMessage(exception));
                }
            });
            return;
        }

        lookupEnglishWord(normalizedQuery, callback);
    }

    private void lookupEnglishWord(String query, SearchCallback callback) {
        String lookupQuery = sanitizeEnglishQuery(query);
        if (lookupQuery.isEmpty()) {
            callback.onError("Tu can tra khong hop le");
            return;
        }

        // 1. First, translate the word itself (word-to-word translation)
        myMemoryService.translateEnToVi(lookupQuery, new MyMemoryService.RawTranslationCallback() {
            @Override
            public void onSuccess(String translatedWord) {
                // Now lookup the dictionary for IPA, Synonyms, and English definitions
                performFinalLookup(lookupQuery, translatedWord, callback);
            }

            @Override
            public void onError(Exception e) {
                // If word translation fails, proceed with empty translated word
                performFinalLookup(lookupQuery, "", callback);
            }
        });
    }

    private void performFinalLookup(String lookupQuery, String translatedWord, SearchCallback callback) {
        freeDictionaryService.lookupWord(lookupQuery, new FreeDictionaryService.LookupCallback() {
            @Override
            public void onSuccess(DictionaryResult result) {
                result.setTranslatedWord(translatedWord);
                
                // If there are definitions, translate the first one's meaning for the explanation
                if (result.getDefinitions() != null && !result.getDefinitions().isEmpty()) {
                    DictionaryResult.Definition def = result.getDefinitions().get(0);
                    String englishMeaning = def.getMeaning();
                    
                    myMemoryService.translateEnToVi(englishMeaning, new MyMemoryService.RawTranslationCallback() {
                        @Override
                        public void onSuccess(String translatedMeaning) {
                            def.setTranslatedMeaning(translatedMeaning);
                            def.setUsageNote(generateUsageNote(def.getPartOfSpeech(), lookupQuery));
                            callback.onSuccess(result);
                        }

                        @Override
                        public void onError(Exception e) {
                            // Even if meaning translation fails, we have word translation and English result
                            def.setUsageNote(generateUsageNote(def.getPartOfSpeech(), lookupQuery));
                            callback.onSuccess(result);
                        }
                    });
                } else {
                    callback.onSuccess(result);
                }
            }

            @Override
            public void onNotFound() {
                // If dictionary not found, but we translated the word, we can still show something!
                if (translatedWord != null && !translatedWord.isEmpty()) {
                    DictionaryResult result = new DictionaryResult();
                    result.setWord(lookupQuery);
                    result.setTranslatedWord(translatedWord);
                    result.setIpa("");
                    ArrayList<DictionaryResult.Definition> definitions = new ArrayList<>();
                    definitions.add(new DictionaryResult.Definition("translation", translatedWord, "Dùng trong giao tiếp hàng ngày."));
                    result.setDefinitions(definitions);
                    callback.onSuccess(result);
                } else {
                    callback.onNotFound(lookupQuery);
                }
            }

            @Override
            public void onError(Exception exception) {
                String message = safeMessage(exception);
                if (exception instanceof UnknownHostException || message.contains("Unable to connect dictionary service")) {
                    // Fallback: return a minimal translation-based result so UI still works without DNS.
                    myMemoryService.translateEnToVi(lookupQuery, new MyMemoryService.RawTranslationCallback() {
                        @Override
                        public void onSuccess(String translatedText) {
                            DictionaryResult result = new DictionaryResult();
                            result.setWord(lookupQuery);
                            result.setIpa("");
                            result.setAudioUrl("");
                            ArrayList<DictionaryResult.Definition> definitions = new ArrayList<>();
                            definitions.add(new DictionaryResult.Definition("translation", translatedText, ""));
                            result.setDefinitions(definitions);
                            result.setSynonyms(new ArrayList<>());
                            callback.onSuccess(result);
                        }

                        @Override
                        public void onError(Exception fallbackError) {
                            DictionaryResult offlineResult = buildOfflineFallback(lookupQuery);
                            if (offlineResult != null) {
                                callback.onSuccess(offlineResult);
                                return;
                            }
                            callback.onError("Lỗi tra từ điển: " + safeMessage(fallbackError));
                        }
                    });
                    return;
                }
                DictionaryResult offlineResult = buildOfflineFallback(lookupQuery);
                if (offlineResult != null) {
                    callback.onSuccess(offlineResult);
                    return;
                }
                callback.onError("Lỗi tra từ điển: " + message);
            }
        });
    }

    private DictionaryResult buildOfflineFallback(String query) {
        String word = query == null ? "" : query.trim().toLowerCase(Locale.US);
        if (word.isEmpty()) {
            return null;
        }

        DictionaryResult result = new DictionaryResult();
        result.setWord(word);
        result.setIpa("");
        result.setAudioUrl("");

        ArrayList<DictionaryResult.Definition> definitions = new ArrayList<>();
        ArrayList<String> synonyms = new ArrayList<>();

        switch (word) {
            case "happy":
                definitions.add(new DictionaryResult.Definition("adjective", "vui vẻ, hạnh phúc", "She feels happy today."));
                synonyms.addAll(Arrays.asList("glad", "joyful", "cheerful"));
                break;
            case "sad":
                definitions.add(new DictionaryResult.Definition("adjective", "buồn", "He looks sad after the movie."));
                synonyms.addAll(Arrays.asList("unhappy", "down", "sorrowful"));
                break;
            case "book":
                definitions.add(new DictionaryResult.Definition("noun", "quyển sách", "I read a book every night."));
                synonyms.addAll(Arrays.asList("volume", "publication"));
                break;
            case "good":
                definitions.add(new DictionaryResult.Definition("adjective", "tốt", "This is a good idea."));
                synonyms.addAll(Arrays.asList("great", "nice", "fine"));
                break;
            case "bad":
                definitions.add(new DictionaryResult.Definition("adjective", "xấu, tệ", "The weather is bad today."));
                synonyms.addAll(Arrays.asList("poor", "awful", "terrible"));
                break;
            default:
                return null;
        }

        result.setDefinitions(definitions);
        result.setSynonyms(synonyms);
        return result;
    }

    private boolean containsVietnameseChars(String text) {
        return VIETNAMESE_CHAR_PATTERN.matcher(text).find();
    }

    private String sanitizeEnglishQuery(String query) {
        String lower = query == null ? "" : query.trim().toLowerCase(Locale.US);
        if (lower.isEmpty()) {
            return "";
        }
        int firstSpace = lower.indexOf(' ');
        if (firstSpace > 0) {
            lower = lower.substring(0, firstSpace);
        }
        return lower.replaceAll("[^a-z'-]", "");
    }

    private String generateUsageNote(String pos, String word) {
        if (pos == null || pos.trim().isEmpty()) return "Dùng để diễn đạt ý của bạn trong giao tiếp.";
        String lowerPos = pos.toLowerCase(Locale.US);
        if (lowerPos.contains("noun")) return "Dùng như một danh từ chỉ sự vật, hiện tượng.";
        if (lowerPos.contains("verb")) return "Dùng để chỉ hành động hoặc trạng thái.";
        if (lowerPos.contains("adjective")) return "Dùng để bổ nghĩa cho danh từ, miêu tả tính chất.";
        if (lowerPos.contains("adverb")) return "Dùng để bổ nghĩa cho động từ hoặc tính từ.";
        if (lowerPos.contains("preposition")) return "Dùng để chỉ vị trí, thời gian hoặc quan hệ.";
        if (lowerPos.contains("pronoun")) return "Dùng để thay thế cho danh từ.";
        if (lowerPos.contains("interjection")) return "Dùng để biểu thị cảm xúc thốt lên.";
        return "Từ này được sử dụng trong ngữ cảnh " + pos + ".";
    }

    private String safeMessage(Exception exception) {
        if (exception instanceof UnknownHostException) {
            return "Không thể kết nối máy chủ từ điển. Hãy kiểm tra Internet và thử lại.";
        }
        if (exception == null || exception.getMessage() == null || exception.getMessage().trim().isEmpty()) {
            return "Unknown error";
        }
        return exception.getMessage();
    }
}
