package com.example.englishflow.data;

public class ChatItem {
    public static final int ROLE_USER = 0;
    public static final int ROLE_AI = 1;
    public static final int ROLE_TYPING = 2;

    private final int role;
    private String message;
    private final String correction;
    private final String explanation;

    // Vocabulary card fields (non-null when AI detected a vocabulary query)
    private String vocabWord;
    private String vocabIpa;
    private String vocabMeaning;
    private String vocabExample;
    private String vocabExampleVi;

    public ChatItem(int role, String message, String correction, String explanation) {
        this.role = role;
        this.message = message;
        this.correction = correction;
        this.explanation = explanation;
    }

    public int getRole() { return role; }

    public String getMessage() { return message; }

    public void setMessage(String message) { this.message = message; }

    public String getCorrection() { return correction; }

    public String getExplanation() { return explanation; }

    public String getVocabWord() { return vocabWord; }

    public void setVocabWord(String vocabWord) { this.vocabWord = vocabWord; }

    public String getVocabIpa() { return vocabIpa; }

    public void setVocabIpa(String vocabIpa) { this.vocabIpa = vocabIpa; }

    public String getVocabMeaning() { return vocabMeaning; }

    public void setVocabMeaning(String vocabMeaning) { this.vocabMeaning = vocabMeaning; }

    public String getVocabExample() { return vocabExample; }

    public void setVocabExample(String vocabExample) { this.vocabExample = vocabExample; }

    public String getVocabExampleVi() { return vocabExampleVi; }

    public void setVocabExampleVi(String vocabExampleVi) { this.vocabExampleVi = vocabExampleVi; }

    public boolean hasVocab() {
        return vocabWord != null && !vocabWord.isEmpty();
    }
}
