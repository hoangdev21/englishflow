package com.example.englishflow.data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class MapLessonFlowStep implements Serializable {

    private final String type;
    private final String content;
    private final String word;
    private final String ipa;
    private final String meaning;
    private final String instruction;
    private final String question;
    private final String expectedKeyword;
    private final String hint;
    private final String roleContext;
    private final List<String> acceptedAnswers;

    public MapLessonFlowStep(String type,
                             String content,
                             String word,
                             String ipa,
                             String meaning,
                             String instruction,
                             String question,
                             String expectedKeyword,
                             String hint,
                             String roleContext,
                             List<String> acceptedAnswers) {
        this.type = safe(type);
        this.content = safe(content);
        this.word = safe(word);
        this.ipa = safe(ipa);
        this.meaning = safe(meaning);
        this.instruction = safe(instruction);
        this.question = safe(question);
        this.expectedKeyword = safe(expectedKeyword);
        this.hint = safe(hint);
        this.roleContext = safe(roleContext);
        if (acceptedAnswers == null || acceptedAnswers.isEmpty()) {
            this.acceptedAnswers = Collections.emptyList();
        } else {
            List<String> normalized = new ArrayList<>();
            for (String item : acceptedAnswers) {
                String value = safe(item);
                if (!value.isEmpty()) {
                    normalized.add(value);
                }
            }
            this.acceptedAnswers = Collections.unmodifiableList(normalized);
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public String getType() {
        return type;
    }

    public String getTypeNormalized() {
        return type.toLowerCase(Locale.US);
    }

    public String getContent() {
        return content;
    }

    public String getWord() {
        return word;
    }

    public String getIpa() {
        return ipa;
    }

    public String getMeaning() {
        return meaning;
    }

    public String getInstruction() {
        return instruction;
    }

    public String getQuestion() {
        return question;
    }

    public String getExpectedKeyword() {
        return expectedKeyword;
    }

    public String getHint() {
        return hint;
    }

    public String getRoleContext() {
        return roleContext;
    }

    public List<String> getAcceptedAnswers() {
        return acceptedAnswers;
    }
}
