package com.example.englishflow.data;

public class TopicItem {
    public static final String STATUS_NOT_STARTED = "Chưa học";
    public static final String STATUS_LEARNING = "Đang học";
    public static final String STATUS_COMPLETED = "Hoàn thành";

    private final String title;
    private final String status;

    public TopicItem(String title, String status) {
        this.title = title;
        this.status = status;
    }

    public String getTitle() {
        return title;
    }

    public String getStatus() {
        return status;
    }
}
