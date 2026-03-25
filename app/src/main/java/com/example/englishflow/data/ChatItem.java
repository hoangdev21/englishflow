package com.example.englishflow.data;

public class ChatItem {
    public static final int ROLE_USER = 0;
    public static final int ROLE_AI = 1;
    public static final int ROLE_TYPING = 2;

    private final int role;
    private String message;
    private final String correction;
    private final String explanation;

    public ChatItem(int role, String message, String correction, String explanation) {
        this.role = role;
        this.message = message;
        this.correction = correction;
        this.explanation = explanation;
    }

    public int getRole() {
        return role;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getCorrection() {
        return correction;
    }

    public String getExplanation() {
        return explanation;
    }
}
