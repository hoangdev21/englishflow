package com.example.englishflow.data;

public class FlashcardItem {
    private final String emoji;
    private final String word;
    private final String ipa;
    private final String meaning;
    private final String example;

    public FlashcardItem(String emoji, String word, String ipa, String meaning, String example) {
        this.emoji = emoji;
        this.word = word;
        this.ipa = ipa;
        this.meaning = meaning;
        this.example = example;
    }

    public String getEmoji() {
        return emoji;
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
}
