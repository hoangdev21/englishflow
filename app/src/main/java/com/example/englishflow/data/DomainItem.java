package com.example.englishflow.data;

import java.util.List;

public class DomainItem {
    private final String emoji;
    private final String name;
    private final int progress;
    private final String gradientStart;
    private final String gradientEnd;
    private final int backgroundImageRes;
    private final List<TopicItem> topics;

    public DomainItem(String emoji, String name, int progress, String gradientStart, String gradientEnd, int backgroundImageRes, List<TopicItem> topics) {
        this.emoji = emoji;
        this.name = name;
        this.progress = progress;
        this.gradientStart = gradientStart;
        this.gradientEnd = gradientEnd;
        this.backgroundImageRes = backgroundImageRes;
        this.topics = topics;
    }

    public String getEmoji() {
        return emoji;
    }

    public String getName() {
        return name;
    }

    public int getProgress() {
        return progress;
    }

    public String getGradientStart() {
        return gradientStart;
    }

    public String getGradientEnd() {
        return gradientEnd;
    }

    public int getBackgroundImageRes() {
        return backgroundImageRes;
    }

    public List<TopicItem> getTopics() {
        return topics;
    }
}
