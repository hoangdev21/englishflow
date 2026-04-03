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
    private final int minExchanges;
    private final Status status;

    public MapNodeItem(String nodeId, String title, String emoji, String promptKey, int minExchanges, Status status) {
        this.nodeId = nodeId;
        this.title = title;
        this.emoji = emoji;
        this.promptKey = promptKey;
        this.minExchanges = minExchanges;
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

    public int getMinExchanges() {
        return minExchanges;
    }

    public Status getStatus() {
        return status;
    }
}
