package com.example.englishflow.data;

import java.util.ArrayList;
import java.util.List;

public class DictionaryResult {
    private String word;
    private String ipa;
    private String audioUrl;
    private List<Definition> definitions;
    private List<String> synonyms;

    public DictionaryResult() {
        this.definitions = new ArrayList<>();
        this.synonyms = new ArrayList<>();
    }

    public DictionaryResult(String word, String ipa, String audioUrl, List<Definition> definitions, List<String> synonyms) {
        this.word = word;
        this.ipa = ipa;
        this.audioUrl = audioUrl;
        this.definitions = definitions != null ? definitions : new ArrayList<>();
        this.synonyms = synonyms != null ? synonyms : new ArrayList<>();
    }

    public String getWord() {
        return word;
    }

    public void setWord(String word) {
        this.word = word;
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

    public static class Definition {
        private String partOfSpeech;
        private String meaning;
        private String example;

        public Definition() {
        }

        public Definition(String partOfSpeech, String meaning, String example) {
            this.partOfSpeech = partOfSpeech;
            this.meaning = meaning;
            this.example = example;
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

        public String getExample() {
            return example;
        }

        public void setExample(String example) {
            this.example = example;
        }
    }
}
