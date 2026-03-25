package com.example.englishflow.data;

public class AchievementItem {
    private final String title;
    private final String description;
    private final boolean unlocked;
    private final String icon;

    public AchievementItem(String title, String description, String icon, boolean unlocked) {
        this.title = title;
        this.description = description;
        this.icon = icon;
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

    public String getIcon() {
        return icon;
    }
}
