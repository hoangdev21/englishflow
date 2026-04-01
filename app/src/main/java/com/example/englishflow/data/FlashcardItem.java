package com.example.englishflow.data;

public class FlashcardItem {
    private final String emoji;
    private final String word;
    private final String ipa;
    private final String meaning;
    private final String example;
    private final String exampleVi;
    private final String usage;

    public FlashcardItem(String emoji, String word, String ipa, String meaning, String example, String exampleVi, String usage) {
        this.emoji = emoji;
        this.word = word;
        this.ipa = ipa;
        this.meaning = meaning;
        this.example = example;
        this.exampleVi = exampleVi != null ? exampleVi : "";
        this.usage = usage != null ? usage : "";
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

    public String getExampleVi() {
        return exampleVi;
    }

    public String getUsage() {
        return usage;
    }
}
