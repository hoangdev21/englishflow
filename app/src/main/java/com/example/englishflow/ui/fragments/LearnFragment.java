package com.example.englishflow.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.englishflow.R;
import com.example.englishflow.data.AppRepository;
import com.example.englishflow.data.DomainItem;
import com.example.englishflow.data.TopicItem;

public class LearnFragment extends Fragment implements LearnFlowNavigator {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_learn, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        AppRepository repo = AppRepository.getInstance(requireContext());
        if (savedInstanceState == null) {
            if (repo.hasPendingTopicRequest()) {
                String domain = repo.getPendingTopicDomain();
                String topic = repo.getPendingTopicTitle();
                repo.clearPendingTopicRequest();
                
                // Set the base domains view
                getChildFragmentManager()
                        .beginTransaction()
                        .replace(R.id.learnContainer, new LearnDomainsFragment())
                        .commitNow();
                
                // Immediately push the flashcards on top
                getChildFragmentManager()
                        .beginTransaction()
                        .replace(R.id.learnContainer, LearnFlashcardFragment.newInstance(domain, topic))
                        .addToBackStack("flashcards")
                        .commit();
            } else {
                getChildFragmentManager()
                        .beginTransaction()
                        .replace(R.id.learnContainer, new LearnDomainsFragment())
                        .commit();
            }
        }
    }

    @Override
    public void openTopics(DomainItem domainItem) {
        getChildFragmentManager()
                .beginTransaction()
                .replace(R.id.learnContainer, LearnTopicsFragment.newInstance(domainItem))
                .addToBackStack("topics")
                .commit();
    }

    @Override
    public void openFlashcards(DomainItem domainItem, TopicItem topicItem) {
        getChildFragmentManager()
                .beginTransaction()
                .replace(R.id.learnContainer, LearnFlashcardFragment.newInstance(domainItem.getName(), topicItem.getTitle()))
                .addToBackStack("flashcards")
                .commit();
    }

    @Override
    public void openCelebration(int earnedXp) {
        getChildFragmentManager()
                .beginTransaction()
                .replace(R.id.learnContainer, LearnCelebrationFragment.newInstance(earnedXp))
                .addToBackStack("celebration")
                .commit();
    }
}
