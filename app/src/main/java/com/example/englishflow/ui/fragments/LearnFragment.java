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
import com.google.android.material.tabs.TabLayout;

public class LearnFragment extends Fragment implements LearnFlowNavigator {

    private static final int TAB_FLASHCARDS = 0;
    private static final int TAB_MAP = 1;

    private TabLayout learnTabs;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_learn, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        learnTabs = view.findViewById(R.id.learnTabs);
        setupTabs();

        AppRepository repo = AppRepository.getInstance(requireContext());
        if (savedInstanceState == null) {
            if (repo.hasPendingTopicRequest()) {
                String domain = repo.getPendingTopicDomain();
                String topic = repo.getPendingTopicTitle();
                repo.clearPendingTopicRequest();

                switchRootTab(TAB_FLASHCARDS, false);
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
                switchRootTab(TAB_FLASHCARDS, false);
            }
        }
    }

    private void setupTabs() {
        if (learnTabs.getTabCount() == 0) {
            learnTabs.addTab(learnTabs.newTab().setText(getString(R.string.learn_flashcard_tab)));
            learnTabs.addTab(learnTabs.newTab().setText(getString(R.string.learn_map_tab)));
        }
        learnTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                switchRootTab(tab.getPosition(), true);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                // No-op.
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                // No-op.
            }
        });
    }

    private void switchRootTab(int tabPosition, boolean clearBackStack) {
        if (!isAdded()) {
            return;
        }
        if (clearBackStack) {
            getChildFragmentManager().popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE);
        }
        Fragment root = tabPosition == TAB_MAP ? new LearnMapFragment() : new LearnDomainsFragment();
        getChildFragmentManager()
                .beginTransaction()
                .replace(R.id.learnContainer, root)
                .commit();
        TabLayout.Tab tab = learnTabs.getTabAt(tabPosition);
        if (tab != null && !tab.isSelected()) {
            tab.select();
        }
    }

    @Override
    public void openTopics(DomainItem domainItem) {
        switchRootTab(TAB_FLASHCARDS, false);
        getChildFragmentManager()
                .beginTransaction()
                .replace(R.id.learnContainer, LearnTopicsFragment.newInstance(domainItem))
                .addToBackStack("topics")
                .commit();
    }

    @Override
    public void openFlashcards(DomainItem domainItem, TopicItem topicItem) {
        switchRootTab(TAB_FLASHCARDS, false);
        getChildFragmentManager()
                .beginTransaction()
                .replace(R.id.learnContainer, LearnFlashcardFragment.newInstance(domainItem.getName(), topicItem.getTitle()))
                .addToBackStack("flashcards")
                .commit();
    }

    @Override
    public void openCelebration(int earnedXp) {
        switchRootTab(TAB_FLASHCARDS, false);
        getChildFragmentManager()
                .beginTransaction()
                .replace(R.id.learnContainer, LearnCelebrationFragment.newInstance(earnedXp))
                .addToBackStack("celebration")
                .commit();
    }
}
