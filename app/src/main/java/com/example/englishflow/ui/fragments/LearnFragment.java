package com.example.englishflow.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.example.englishflow.R;
import com.example.englishflow.data.AppRepository;
import com.example.englishflow.data.DomainItem;
import com.example.englishflow.data.TopicItem;

public class LearnFragment extends Fragment implements LearnFlowNavigator {

    private OnBackPressedCallback backPressedCallback;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_learn, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupBackHandling();

        AppRepository repo = AppRepository.getInstance(requireContext());
        if (savedInstanceState == null) {
            if (repo.hasPendingTopicRequest()) {
                String domain = repo.getPendingTopicDomain();
                String topic = repo.getPendingTopicTitle();
                repo.clearPendingTopicRequest();

                getChildFragmentManager()
                        .beginTransaction()
                        .replace(R.id.learnContainer, new LearnDomainsFragment())
                        .commitNow();

                getChildFragmentManager()
                        .beginTransaction()
                        .replace(R.id.learnContainer, LearnFlashcardFragment.newInstance(domain, topic))
                        .addToBackStack("flashcards")
                        .commit();
            } else {
                showOverview(false);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (backPressedCallback != null) {
            backPressedCallback.setEnabled(true);
        }
    }

    @Override
    public void onPause() {
        if (backPressedCallback != null) {
            backPressedCallback.setEnabled(false);
        }
        super.onPause();
    }

    private void setupBackHandling() {
        backPressedCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                FragmentManager childManager = getChildFragmentManager();
                if (childManager.getBackStackEntryCount() > 0) {
                    childManager.popBackStack();
                    return;
                }
                setEnabled(false);
                requireActivity().getOnBackPressedDispatcher().onBackPressed();
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), backPressedCallback);
    }

    private void showOverview(boolean clearBackStack) {
        if (!isAdded()) {
            return;
        }
        if (clearBackStack) {
            getChildFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        }
        getChildFragmentManager()
                .beginTransaction()
                .replace(R.id.learnContainer, new LearnOverviewFragment())
                .commit();
    }

    private void showDomains(boolean addToBackStack) {
        if (!isAdded()) {
            return;
        }
        androidx.fragment.app.FragmentTransaction transaction = getChildFragmentManager()
                .beginTransaction()
                .replace(R.id.learnContainer, new LearnDomainsFragment());
        if (addToBackStack) {
            transaction.addToBackStack("domains");
        }
        transaction.commit();
    }

    private void showJourney(boolean addToBackStack) {
        if (!isAdded()) {
            return;
        }
        androidx.fragment.app.FragmentTransaction transaction = getChildFragmentManager()
                .beginTransaction()
                .replace(R.id.learnContainer, new LearnMapFragment());
        if (addToBackStack) {
            transaction.addToBackStack("journey");
        }
        transaction.commit();
    }

    private void showFillBlank(String preferredTopic, boolean addToBackStack) {
        if (!isAdded()) {
            return;
        }
        androidx.fragment.app.FragmentTransaction transaction = getChildFragmentManager()
                .beginTransaction()
                .replace(R.id.learnContainer, LearnFillBlankFragment.newInstance(preferredTopic));
        if (addToBackStack) {
            transaction.addToBackStack("fill_blank");
        }
        transaction.commit();
    }

    @Override
    public void openDomains() {
        showDomains(true);
    }

    @Override
    public void openJourney() {
        showJourney(true);
    }

    @Override
    public void openFillBlank(String preferredTopic) {
        Fragment current = getChildFragmentManager().findFragmentById(R.id.learnContainer);
        boolean launchedFromFlashcards = current instanceof LearnFlashcardFragment;
        showFillBlank(preferredTopic, !launchedFromFlashcards);
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
    public void openCelebration(int earnedXp, String completedTopic, String completedDomain, int learnedWords) {
        getChildFragmentManager()
                .beginTransaction()
                .replace(R.id.learnContainer, LearnCelebrationFragment.newInstance(
                        earnedXp,
                        completedTopic,
                        completedDomain,
                        learnedWords
                ))
                .addToBackStack("celebration")
                .commit();
    }
}
