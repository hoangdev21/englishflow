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
    private final String minLevel;
    private final java.util.List<MapLessonFlowStep> flowSteps;
    private final java.util.List<String> lessonKeywords;
    private Status status;

    public void setStatus(Status status) {
        this.status = status;
    }

        public MapNodeItem(String nodeId, String title, String emoji, String promptKey, String roleDescription, int minExchanges, java.util.List<LessonVocabulary> vocabList, Status status) {
        this(nodeId,
            title,
            emoji,
            promptKey,
            roleDescription,
            minExchanges,
            vocabList,
            status,
            "A1",
            new java.util.ArrayList<>(),
            new java.util.ArrayList<>());
        }

        public MapNodeItem(String nodeId,
                   String title,
                   String emoji,
                   String promptKey,
                   String roleDescription,
                   int minExchanges,
                   java.util.List<LessonVocabulary> vocabList,
                   Status status,
                   String minLevel,
                   java.util.List<MapLessonFlowStep> flowSteps,
                   java.util.List<String> lessonKeywords) {
        this.nodeId = nodeId;
        this.title = title;
        this.emoji = emoji;
        this.promptKey = promptKey;
        this.roleDescription = roleDescription;
        this.minExchanges = minExchanges;
        this.vocabList = vocabList == null
            ? java.util.Collections.emptyList()
            : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(vocabList));
        this.minLevel = (minLevel == null || minLevel.trim().isEmpty()) ? "A1" : minLevel.trim();
        this.flowSteps = flowSteps == null
            ? java.util.Collections.emptyList()
            : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(flowSteps));
        this.lessonKeywords = lessonKeywords == null
            ? java.util.Collections.emptyList()
            : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(lessonKeywords));
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

    public String getMinLevel() {
        return minLevel;
    }

    public java.util.List<MapLessonFlowStep> getFlowSteps() {
        return flowSteps;
    }

    public java.util.List<String> getLessonKeywords() {
        return lessonKeywords;
    }

    public Status getStatus() {
        return status;
    }
}
