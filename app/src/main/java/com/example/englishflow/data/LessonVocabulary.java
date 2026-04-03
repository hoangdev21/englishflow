package com.example.englishflow.data;

public class LessonVocabulary implements java.io.Serializable {
    private final String word;
    private final String ipa;
    private final String meaning;
    private final String example;
    private boolean isPracticed;

    public LessonVocabulary(String word, String ipa, String meaning, String example) {
        this.word = word;
        this.ipa = ipa;
        this.meaning = meaning;
        this.example = example;
        this.isPracticed = false;
    }

    public String getWord() { return word; }
    public String getIpa() { return ipa; }
    public String getMeaning() { return meaning; }
    public String getExample() { return example; }
    public boolean isPracticed() { return isPracticed; }
    public void setPracticed(boolean practiced) { isPracticed = practiced; }
}
