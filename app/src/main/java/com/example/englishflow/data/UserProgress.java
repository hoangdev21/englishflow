package com.example.englishflow.data;

import java.util.ArrayList;
import java.util.List;

public class UserProgress {
    public int totalWordsLearned = 0;
    public int totalWordsScanned = 0;
    public int currentStreak = 0;
    public int bestStreak = 0;
    public int totalXpEarned = 0;
    public int xpTodayEarned = 0;
    public long lastStudyDate = 0;
    public List<StudySession> studySessions = new ArrayList<>();
    public int totalStudyMinutes = 0;
    public String cefrLevel = "A1";

    public UserProgress() {
    }

    // Getters
    public int getTotalWordsLearned() { return totalWordsLearned; }
    public int getTotalWordsScanned() { return totalWordsScanned; }
    public int getCurrentStreak() { return currentStreak; }
    public int getBestStreak() { return bestStreak; }
    public int getTotalXpEarned() { return totalXpEarned; }
    public int getXpTodayEarned() { return xpTodayEarned; }
    public long getLastStudyDate() { return lastStudyDate; }
    public List<StudySession> getStudySessions() { return new ArrayList<>(studySessions); }
    public int getTotalStudyMinutes() { return totalStudyMinutes; }

    // Setters
    public void setStudySessions(List<StudySession> sessions) {
        this.studySessions = new ArrayList<>(sessions);
    }

    public void incrementWordsLearned(int count) {
        this.totalWordsLearned += count;
    }

    public void incrementXpToday(int xp) {
        this.xpTodayEarned += xp;
        this.totalXpEarned += xp;
    }

    public void addStudySession(StudySession session) {
        this.studySessions.add(session);
        this.totalStudyMinutes += session.getDurationMinutes();
        this.lastStudyDate = System.currentTimeMillis();
    }

    public void updateStreak(int newStreak, int newBestStreak) {
        this.currentStreak = newStreak;
        if (newBestStreak > bestStreak) {
            this.bestStreak = newBestStreak;
        }
    }

    public int getWeeklyStudyMinutes(int dayOfWeek) {
        // dayOfWeek: 0 = Sunday, 1 = Monday, etc.
        if (dayOfWeek < studySessions.size()) {
            return (int) studySessions.get(dayOfWeek).getDurationMinutes();
        }
        return 0;
    }

    public List<Integer> getAllWeeklyMinutes() {
        List<Integer> minutes = new ArrayList<>();
        for (StudySession session : studySessions) {
            minutes.add((int) session.getDurationMinutes());
        }
        return minutes;
    }
}
