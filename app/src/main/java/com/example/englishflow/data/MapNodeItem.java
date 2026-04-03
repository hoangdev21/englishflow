package com.example.englishflow.data;

public class MapNodeItem {

    public enum Status {
        LOCKED,
        AVAILABLE,
        IN_PROGRESS,
        COMPLETED
    }

    private final String nodeId;
    private final String title;
    private final String emoji;
    private final String promptKey;
    private final String roleDescription;
    private final int minExchanges;
    private final java.util.List<LessonVocabulary> vocabList;
    private Status status;

    public void setStatus(Status status) {
        this.status = status;
    }

    public MapNodeItem(String nodeId, String title, String emoji, String promptKey, String roleDescription, int minExchanges, java.util.List<LessonVocabulary> vocabList, Status status) {
        this.nodeId = nodeId;
        this.title = title;
        this.emoji = emoji;
        this.promptKey = promptKey;
        this.roleDescription = roleDescription;
        this.minExchanges = minExchanges;
        this.vocabList = vocabList;
        this.status = status;
    }

    public String getNodeId() {
        return nodeId;
    }

    public String getTitle() {
        return title;
    }

    public String getEmoji() {
        return emoji;
    }

    public String getPromptKey() {
        return promptKey;
    }

    public String getRoleDescription() {
        return roleDescription;
    }

    public int getMinExchanges() {
        return minExchanges;
    }

    public java.util.List<LessonVocabulary> getVocabList() {
        return vocabList;
    }

    public Status getStatus() {
        return status;
    }
}
