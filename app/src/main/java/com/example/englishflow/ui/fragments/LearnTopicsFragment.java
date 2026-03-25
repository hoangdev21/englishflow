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
import com.example.englishflow.data.DomainItem;
import com.example.englishflow.data.TopicItem;
import com.example.englishflow.ui.adapters.TopicAdapter;

public class LearnTopicsFragment extends Fragment {

    private static final String ARG_DOMAIN_NAME = "arg_domain_name";
    private DomainItem domainItem;

    public static LearnTopicsFragment newInstance(DomainItem domainItem) {
        LearnTopicsFragment fragment = new LearnTopicsFragment();
        Bundle bundle = new Bundle();
        bundle.putString(ARG_DOMAIN_NAME, domainItem.getName());
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
            domainItem = new DomainItem("📚", domainName, 0, "#1A7A5E", "#2AAE84", java.util.Collections.emptyList());
        }

        view.findViewById(R.id.btnBackTopics).setOnClickListener(v -> {
            getParentFragmentManager().popBackStack();
        });

        TextView title = view.findViewById(R.id.topicTitle);
        title.setText(domainItem.getName());
        
        TextView emoji = view.findViewById(R.id.topicDomainEmoji);
        emoji.setText(domainItem.getEmoji());

        RecyclerView recyclerView = view.findViewById(R.id.topicRecycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        
        final TopicAdapter adapter = new TopicAdapter(domainItem.getTopics(), topicItem -> {
            Fragment parent = getParentFragment();
            if (parent instanceof LearnFlowNavigator) {
                ((LearnFlowNavigator) parent).openFlashcards(domainItem, topicItem);
            }
        });
        recyclerView.setAdapter(adapter);

        android.widget.EditText search = view.findViewById(R.id.topicSearch);
        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s.toString());
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
    }
}
