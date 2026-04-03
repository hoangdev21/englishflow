package com.example.englishflow.ui.fragments;

import com.example.englishflow.data.DomainItem;
import com.example.englishflow.data.TopicItem;

public interface LearnFlowNavigator {
    void openDomains();
    void openJourney();
    void openTopics(DomainItem domainItem);
    void openFlashcards(DomainItem domainItem, TopicItem topicItem);
    void openCelebration(int earnedXp);
}
