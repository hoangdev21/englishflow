package com.example.englishflow.data;

public class WordEntry {
    private final String word;
    private final String ipa;
    private final String meaning;
    private final String wordType;
    private final String example;
    private final String exampleVi;
    private final String usage;
    private final String category;
    private final String note;
    private final long learnedAt;

    public WordEntry(String word, String ipa, String meaning, String wordType, String example, 
                     String exampleVi, String usage, String category, String note) {
        this(word, ipa, meaning, wordType, example, exampleVi, usage, category, note, System.currentTimeMillis());
    }

    public WordEntry(String word, String ipa, String meaning, String wordType, String example,
                     String exampleVi, String usage, String category, String note, long learnedAt) {
        this.word = word;
        this.ipa = ipa;
        this.meaning = meaning;
        this.wordType = wordType;
        this.example = example;
        this.exampleVi = exampleVi;
        this.usage = usage;
        this.category = category;
        this.note = note;
        this.learnedAt = learnedAt > 0L ? learnedAt : System.currentTimeMillis();
    }

    public String getWord() { return word; }
    public String getIpa() { return ipa; }
    public String getMeaning() { return meaning; }
    public String getWordType() { return wordType; }
    public String getExample() { return example; }
    public String getExampleVi() { return exampleVi; }
    public String getUsage() { return usage; }
    public String getCategory() { return category; }
    public String getNote() { return note; }
    public long getLearnedAt() { return learnedAt; }
}
