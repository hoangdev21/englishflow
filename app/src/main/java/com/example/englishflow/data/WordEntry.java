package com.example.englishflow.data;

public class WordEntry {
    private final String word;
    private final String ipa;
    private final String meaning;
    private final String example;
    private final String category;

    public WordEntry(String word, String ipa, String meaning, String example, String category) {
        this.word = word;
        this.ipa = ipa;
        this.meaning = meaning;
        this.example = example;
        this.category = category;
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

    public String getExample() {
        return example;
    }

    public String getCategory() {
        return category;
    }
}
