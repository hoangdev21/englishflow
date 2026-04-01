package com.example.englishflow.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.englishflow.R;
import com.example.englishflow.data.AppRepository;
import com.example.englishflow.data.DomainItem;
import com.example.englishflow.data.TopicItem;
import com.example.englishflow.ui.adapters.TopicAdapter;

public class LearnTopicsFragment extends Fragment {

    private static final String ARG_DOMAIN_NAME = "arg_domain_name";
    private static final String ARG_DOMAIN_EMOJI = "arg_domain_emoji";
    private static final String ARG_DOMAIN_IMAGE = "arg_domain_image";
    private DomainItem domainItem;
    private TopicAdapter topicAdapter;

    public static LearnTopicsFragment newInstance(DomainItem domainItem) {
        LearnTopicsFragment fragment = new LearnTopicsFragment();
        Bundle bundle = new Bundle();
        bundle.putString(ARG_DOMAIN_NAME, domainItem.getName());
        bundle.putString(ARG_DOMAIN_EMOJI, domainItem.getEmoji());
        bundle.putInt(ARG_DOMAIN_IMAGE, domainItem.getBackgroundImageRes());
        fragment.setArguments(bundle);
        fragment.domainItem = domainItem;
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_learn_topics, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (domainItem == null && getArguments() != null) {
            String domainName = getArguments().getString(ARG_DOMAIN_NAME, "Lĩnh vực");
            String domainEmoji = getArguments().getString(ARG_DOMAIN_EMOJI, "📚");
            int domainImage = getArguments().getInt(ARG_DOMAIN_IMAGE, 0);
            domainItem = new DomainItem(domainEmoji, domainName, 0, "#1A7A5E", "#2AAE84", domainImage, java.util.Collections.emptyList());
        }

        view.findViewById(R.id.btnBackTopics).setOnClickListener(v -> {
            getParentFragmentManager().popBackStack();
        });

        // Set Header Background
        android.widget.ImageView headerBg = view.findViewById(R.id.topicHeaderBg);
        if (domainItem.getBackgroundImageRes() != 0) {
            headerBg.setImageResource(domainItem.getBackgroundImageRes());
        }

        TextView title = view.findViewById(R.id.topicTitle);
        title.setText(domainItem.getName());
        
        TextView emoji = view.findViewById(R.id.topicDomainEmoji);
        emoji.setText(domainItem.getEmoji());

        RecyclerView recyclerView = view.findViewById(R.id.topicRecycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        
        topicAdapter = new TopicAdapter(domainItem.getTopics(), domainItem.getEmoji(), topicItem -> {
            Fragment parent = getParentFragment();
            if (parent instanceof LearnFlowNavigator) {
                ((LearnFlowNavigator) parent).openFlashcards(domainItem, topicItem);
            }
        });
        recyclerView.setAdapter(topicAdapter);

        android.widget.EditText search = view.findViewById(R.id.topicSearch);
        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (topicAdapter != null) {
                    topicAdapter.filter(s.toString());
                }
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        setupFilters(view);
    }

    private void setupFilters(View view) {
        com.google.android.material.button.MaterialButton btnAll = view.findViewById(R.id.filterAll);
        com.google.android.material.button.MaterialButton btnNotStarted = view.findViewById(R.id.filterNotStarted);
        com.google.android.material.button.MaterialButton btnLearning = view.findViewById(R.id.filterLearning);
        com.google.android.material.button.MaterialButton btnCompleted = view.findViewById(R.id.filterCompleted);

        com.google.android.material.button.MaterialButton[] buttons = {btnAll, btnNotStarted, btnLearning, btnCompleted};

        btnAll.setOnClickListener(v -> handleFilterClick(btnAll, "Tất cả", buttons));
        btnNotStarted.setOnClickListener(v -> handleFilterClick(btnNotStarted, TopicItem.STATUS_NOT_STARTED, buttons));
        btnLearning.setOnClickListener(v -> handleFilterClick(btnLearning, TopicItem.STATUS_LEARNING, buttons));
        btnCompleted.setOnClickListener(v -> handleFilterClick(btnCompleted, TopicItem.STATUS_COMPLETED, buttons));
    }

    private void handleFilterClick(com.google.android.material.button.MaterialButton selected, String status, com.google.android.material.button.MaterialButton[] allButtons) {
        if (topicAdapter != null) {
            topicAdapter.filterStatus(status);
        }

        // Update UI selection state
        for (com.google.android.material.button.MaterialButton btn : allButtons) {
            if (btn == selected) {
                btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getResources().getColor(R.color.ef_primary)));
                btn.setTextColor(android.graphics.Color.WHITE);
            } else {
                btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getResources().getColor(R.color.ef_white_silver)));
                btn.setTextColor(getResources().getColor(R.color.ef_text_secondary));
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isAdded() || domainItem == null || topicAdapter == null) {
            return;
        }
        topicAdapter.submitTopics(AppRepository.getInstance(requireContext()).getTopicsForDomain(domainItem.getName()));
    }
}
