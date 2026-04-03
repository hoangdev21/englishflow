package com.example.englishflow.data;

public class AchievementItem {
    private final String title;
    private final String description;
    private final boolean unlocked;
    private final String icon;
    private final int currentValue;
    private final int targetValue;

    public AchievementItem(String title, String description, String icon, boolean unlocked) {
        this(title, description, icon, unlocked, unlocked ? 1 : 0, 1);
    }

    public AchievementItem(String title, String description, String icon, boolean unlocked, int currentValue, int targetValue) {
        this.title = title;
        this.description = description;
        this.icon = icon;
        this.unlocked = unlocked;
        this.currentValue = Math.max(currentValue, 0);
        this.targetValue = Math.max(targetValue, 1);
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

    public int getCurrentValue() {
        return currentValue;
    }

    public int getTargetValue() {
        return targetValue;
    }
}
