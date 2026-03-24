package com.example.englishflow.data;

import java.net.UnknownHostException;
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

        freeDictionaryService.lookupWord(lookupQuery, new FreeDictionaryService.LookupCallback() {
            @Override
            public void onSuccess(DictionaryResult result) {
                callback.onSuccess(result);
            }

            @Override
            public void onNotFound() {
                callback.onNotFound(lookupQuery);
            }

            @Override
            public void onError(Exception exception) {
                callback.onError("Loi tra tu dien: " + safeMessage(exception));
            }
        });
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
