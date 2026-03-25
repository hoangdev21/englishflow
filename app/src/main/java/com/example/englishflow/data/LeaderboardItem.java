package com.example.englishflow.data;

public class LeaderboardItem {
    public String name;
    public String email;
    public int score;
    public int rank;
    public String avatarPath; // Mock for now

    public LeaderboardItem(String name, String email, int score, int rank) {
        this.name = name;
        this.email = email;
        this.score = score;
        this.rank = rank;
    }
}
