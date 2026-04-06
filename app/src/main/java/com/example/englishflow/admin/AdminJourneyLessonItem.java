package com.example.englishflow.admin;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class AdminJourneyLessonItem {

    private final String documentId;
    private final String lessonId;
    private final String title;
    private final String minLevel;
    private final String status;
    private final int order;
    private final int minExchanges;
    private final int vocabularyCount;
    private final List<String> keywords;

    public AdminJourneyLessonItem(String documentId,
                                  String lessonId,
                                  String title,
                                  String minLevel,
                                  String status,
                                  int order,
                                  int minExchanges,
                                  int vocabularyCount,
                                  List<String> keywords) {
        this.documentId = safeText(documentId);
        this.lessonId = safeText(lessonId);
        this.title = safeText(title);
        this.minLevel = safeText(minLevel);
        this.status = safeText(status).toLowerCase(Locale.US);
        this.order = Math.max(1, order);
        this.minExchanges = Math.max(2, minExchanges);
        this.vocabularyCount = Math.max(0, vocabularyCount);

        if (keywords == null || keywords.isEmpty()) {
            this.keywords = Collections.emptyList();
        } else {
            List<String> values = new ArrayList<>();
            for (String keyword : keywords) {
                String value = safeText(keyword);
                if (!value.isEmpty()) {
                    values.add(value);
                }
            }
            this.keywords = Collections.unmodifiableList(values);
        }
    }

    public String getDocumentId() {
        return documentId;
    }

    public String getLessonId() {
        return lessonId;
    }

    public String getTitle() {
        return title;
    }

    public String getMinLevel() {
        return minLevel;
    }

    public String getStatus() {
        return status;
    }

    public int getOrder() {
        return order;
    }

    public int getMinExchanges() {
        return minExchanges;
    }

    public int getVocabularyCount() {
        return vocabularyCount;
    }

    @NonNull
    public List<String> getKeywords() {
        return keywords;
    }

    public boolean isVisibleToLearner() {
        return !("draft".equals(status)
                || "archived".equals(status)
                || "hidden".equals(status)
                || "disabled".equals(status));
    }

    private static String safeText(String text) {
        return text == null ? "" : text.trim();
    }
}
