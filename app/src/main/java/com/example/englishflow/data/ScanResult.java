package com.example.englishflow.data;

import java.util.List;

public class ScanResult {
    private final String word;
    private final String ipa;
    private final String meaning;
    private final String example;
    private final String category;
    private final String funFact;
    private final List<String> relatedWords;

    public ScanResult(String word, String ipa, String meaning, String example, String category, String funFact, List<String> relatedWords) {
        this.word = word;
        this.ipa = ipa;
        this.meaning = meaning;
        this.example = example;
        this.category = category;
        this.funFact = funFact;
        this.relatedWords = relatedWords;
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

    public String getFunFact() {
        return funFact;
    }

    public List<String> getRelatedWords() {
        return relatedWords;
    }
}
