package com.example.englishflow.data;

import java.util.ArrayList;
import java.util.List;

public class DictionaryResult {
    private String word;
    private String translatedWord;
    private String ipa;
    private String audioUrl;
    private String queryWord;
    private boolean isVietnameseSearch;
    private List<Definition> definitions;
    private List<String> synonyms;

    public DictionaryResult() {
        this.definitions = new ArrayList<>();
        this.synonyms = new ArrayList<>();
    }

    public DictionaryResult(String word, String translatedWord, String ipa, String audioUrl, List<Definition> definitions, List<String> synonyms) {
        this.word = word;
        this.translatedWord = translatedWord;
        this.ipa = ipa;
        this.audioUrl = audioUrl;
        this.definitions = definitions != null ? definitions : new ArrayList<>();
        this.synonyms = synonyms != null ? synonyms : new ArrayList<>();
    }

    public DictionaryResult(String word, String ipa, String audioUrl, List<Definition> definitions, List<String> synonyms) {
        this(word, "", ipa, audioUrl, definitions, synonyms);
    }

    public String getWord() {
        return word;
    }

    public void setWord(String word) {
        this.word = word;
    }

    public String getTranslatedWord() {
        return translatedWord;
    }

    public void setTranslatedWord(String translatedWord) {
        this.translatedWord = translatedWord;
    }

    public String getIpa() {
        return ipa;
    }

    public void setIpa(String ipa) {
        this.ipa = ipa;
    }

    public String getAudioUrl() {
        return audioUrl;
    }

    public void setAudioUrl(String audioUrl) {
        this.audioUrl = audioUrl;
    }

    public List<Definition> getDefinitions() {
        return definitions;
    }

    public void setDefinitions(List<Definition> definitions) {
        this.definitions = definitions != null ? definitions : new ArrayList<>();
    }

    public List<String> getSynonyms() {
        return synonyms;
    }

    public void setSynonyms(List<String> synonyms) {
        this.synonyms = synonyms != null ? synonyms : new ArrayList<>();
    }

    public String getQueryWord() {
        return queryWord;
    }

    public void setQueryWord(String queryWord) {
        this.queryWord = queryWord;
    }

    public boolean isVietnameseSearch() {
        return isVietnameseSearch;
    }

    public void setIsVietnameseSearch(boolean vietnameseSearch) {
        isVietnameseSearch = vietnameseSearch;
    }

    public static class Definition {
        private String partOfSpeech;
        private String meaning;
        private String translatedMeaning;
        private String example;
        private String usageNote;

        public Definition() {
        }

        public Definition(String partOfSpeech, String meaning, String example) {
            this.partOfSpeech = partOfSpeech;
            this.meaning = meaning;
            this.example = example;
        }

        public Definition(String partOfSpeech, String meaning, String translatedMeaning, String example, String usageNote) {
            this.partOfSpeech = partOfSpeech;
            this.meaning = meaning;
            this.translatedMeaning = translatedMeaning;
            this.example = example;
            this.usageNote = usageNote;
        }

        public String getPartOfSpeech() {
            return partOfSpeech;
        }

        public void setPartOfSpeech(String partOfSpeech) {
            this.partOfSpeech = partOfSpeech;
        }

        public String getMeaning() {
            return meaning;
        }

        public void setMeaning(String meaning) {
            this.meaning = meaning;
        }

        public String getTranslatedMeaning() {
            return translatedMeaning;
        }

        public void setTranslatedMeaning(String translatedMeaning) {
            this.translatedMeaning = translatedMeaning;
        }

        public String getExample() {
            return example;
        }

        public void setExample(String example) {
            this.example = example;
        }

        public String getUsageNote() {
            return usageNote;
        }

        public void setUsageNote(String usageNote) {
            this.usageNote = usageNote;
        }
    }
}
