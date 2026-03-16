package com.example.englishflow.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.englishflow.R;
import com.example.englishflow.data.AppRepository;
import com.example.englishflow.data.DomainItem;
import com.example.englishflow.ui.adapters.DomainAdapter;

import java.util.List;

public class LearnDomainsFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_learn_domains, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        RecyclerView recyclerView = view.findViewById(R.id.domainRecycler);
        recyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 2));

        List<DomainItem> domains = AppRepository.getInstance(requireContext()).getDomains();
        DomainAdapter adapter = new DomainAdapter(domains, domainItem -> {
            Fragment parent = getParentFragment();
            if (parent instanceof LearnFlowNavigator) {
                ((LearnFlowNavigator) parent).openTopics(domainItem);
            }
        });
        recyclerView.setAdapter(adapter);
    }
}
