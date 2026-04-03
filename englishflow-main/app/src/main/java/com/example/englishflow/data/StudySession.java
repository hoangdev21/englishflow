package com.example.englishflow.data;

public class StudySession {
    private long startTime;
    private long endTime;
    private int wordsLearned;
    private String domainName;
    private String topicName;
    private int xpEarned;

    public StudySession(long startTime, long endTime, int wordsLearned, 
                       String domainName, String topicName, int xpEarned) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.wordsLearned = wordsLearned;
        this.domainName = domainName;
        this.topicName = topicName;
        this.xpEarned = xpEarned;
    }

    // Getters
    public long getStartTime() { return startTime; }
    public long getEndTime() { return endTime; }
    public int getWordsLearned() { return wordsLearned; }
    public String getDomainName() { return domainName; }
    public String getTopicName() { return topicName; }
    public int getXpEarned() { return xpEarned; }

    public long getDurationMinutes() {
        return (endTime - startTime) / (1000 * 60);
    }

    public String getFormattedDate() {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault());
        return sdf.format(new java.util.Date(startTime));
    }
}
