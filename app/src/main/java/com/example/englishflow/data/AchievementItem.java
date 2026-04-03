package com.example.englishflow.data;

public class AchievementItem {
    private final String title;
    private final String description;
    private final boolean unlocked;

    public AchievementItem(String title, String description, boolean unlocked) {
        this.title = title;
        this.description = description;
        this.unlocked = unlocked;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public boolean isUnlocked() {
        return unlocked;
    }
}
