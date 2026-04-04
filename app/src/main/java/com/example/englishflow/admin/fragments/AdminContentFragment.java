package com.example.englishflow.admin.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.englishflow.R;
import com.example.englishflow.admin.AdminVocabularyAdapter;
import com.example.englishflow.data.AppRepository;
import com.example.englishflow.data.DomainItem;
import com.example.englishflow.database.entity.CustomVocabularyEntity;
import com.example.englishflow.ui.CustomVocabularyManagerActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class AdminContentFragment extends Fragment {

    private AppRepository repository;
    private AdminVocabularyAdapter adapter;
    private TextInputEditText searchInput;
    private AutoCompleteTextView domainFilterInput;
    private TextView resultCaption;
    private TextView emptyState;
    private TextView statVocabularyCount;
    private TextView statDomainCount;
    private TextView statTopicCount;
    private TextView statLockedCount;
    private ArrayAdapter<String> domainFilterAdapter;

    private final List<CustomVocabularyEntity> allVocabulary = new ArrayList<>();
    private String selectedDomain = "";
    private String searchQuery = "";
    private String allDomainLabel = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_admin_content, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        repository = AppRepository.getInstance(requireContext());
        allDomainLabel = getString(R.string.admin_content_domain_filter_all);

        statVocabularyCount = view.findViewById(R.id.adminStatVocabularyCount);
        statDomainCount = view.findViewById(R.id.adminStatDomainCount);
        statTopicCount = view.findViewById(R.id.adminStatTopicCount);
        statLockedCount = view.findViewById(R.id.adminStatLockedCount);
        resultCaption = view.findViewById(R.id.adminVocabResultCaption);
        emptyState = view.findViewById(R.id.adminVocabEmptyState);

        searchInput = view.findViewById(R.id.adminVocabSearchInput);
        domainFilterInput = view.findViewById(R.id.adminDomainFilterInput);

        RecyclerView recyclerView = view.findViewById(R.id.adminVocabRecycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new AdminVocabularyAdapter();
        recyclerView.setAdapter(adapter);

        setupSearchInput();
        setupDomainFilter();

        MaterialButton btnContent = view.findViewById(R.id.adminBtnContent);
        if (btnContent != null) {
            btnContent.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), CustomVocabularyManagerActivity.class))
            );
        }

        repository.getAllCustomVocabularyLive().observe(getViewLifecycleOwner(), items -> {
            allVocabulary.clear();
            if (items != null) {
                allVocabulary.addAll(items);
            }
            updateSummaryStats();
            rebuildDomainFilterOptions();
            refreshTopicCount();
            applyFilters();
        });
    }

    private void setupSearchInput() {
        if (searchInput == null) {
            return;
        }
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = safeText(s).toLowerCase(Locale.US);
                applyFilters();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void setupDomainFilter() {
        if (domainFilterInput == null) {
            return;
        }

        domainFilterAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_list_item_1,
                new ArrayList<>()
        );
        domainFilterInput.setAdapter(domainFilterAdapter);
        domainFilterInput.setOnClickListener(v -> domainFilterInput.showDropDown());
        domainFilterInput.setOnItemClickListener((parent, view, position, id) -> {
            String selected = domainFilterAdapter.getItem(position);
            if (selected == null || selected.equals(allDomainLabel)) {
                selectedDomain = "";
            } else {
                selectedDomain = selected;
            }
            applyFilters();
        });
        domainFilterInput.setText(allDomainLabel, false);
    }

    private void rebuildDomainFilterOptions() {
        if (domainFilterAdapter == null || domainFilterInput == null) {
            return;
        }

        Set<String> uniqueDomains = new LinkedHashSet<>();
        for (CustomVocabularyEntity item : allVocabulary) {
            uniqueDomains.add(normalizeDomain(item.domain));
        }

        List<String> options = new ArrayList<>();
        options.add(allDomainLabel);

        List<String> sortedDomains = new ArrayList<>(uniqueDomains);
        Collections.sort(sortedDomains, String.CASE_INSENSITIVE_ORDER);
        options.addAll(sortedDomains);

        domainFilterAdapter.clear();
        domainFilterAdapter.addAll(options);
        domainFilterAdapter.notifyDataSetChanged();

        String target = selectedDomain.isEmpty() ? allDomainLabel : selectedDomain;
        if (!options.contains(target)) {
            selectedDomain = "";
            target = allDomainLabel;
        }
        domainFilterInput.setText(target, false);
    }

    private void updateSummaryStats() {
        int totalWords = allVocabulary.size();
        int lockedWords = 0;
        Set<String> domains = new LinkedHashSet<>();

        for (CustomVocabularyEntity item : allVocabulary) {
            domains.add(normalizeDomain(item.domain));
            if (item.isLocked) {
                lockedWords++;
            }
        }

        statVocabularyCount.setText(String.valueOf(totalWords));
        statDomainCount.setText(String.valueOf(domains.size()));
        statLockedCount.setText(String.valueOf(lockedWords));
    }

    private void refreshTopicCount() {
        repository.getDomainsAsync(domains -> {
            if (!isAdded()) {
                return;
            }
            int topicCount = 0;
            for (DomainItem domain : domains) {
                if (domain.getTopics() != null) {
                    topicCount += domain.getTopics().size();
                }
            }
            statTopicCount.setText(String.valueOf(topicCount));
        });
    }

    private void applyFilters() {
        if (adapter == null) {
            return;
        }

        List<CustomVocabularyEntity> filtered = new ArrayList<>();
        for (CustomVocabularyEntity item : allVocabulary) {
            String domain = normalizeDomain(item.domain);
            if (!selectedDomain.isEmpty() && !domain.equalsIgnoreCase(selectedDomain)) {
                continue;
            }

            if (!searchQuery.isEmpty()) {
                String haystack = (safeText(item.word) + " "
                        + safeText(item.meaning) + " "
                        + domain + " "
                        + safeText(item.source)).toLowerCase(Locale.US);
                if (!haystack.contains(searchQuery)) {
                    continue;
                }
            }
            filtered.add(item);
        }

        Collections.sort(filtered, (left, right) -> {
            int domainCompare = normalizeDomain(left.domain).compareToIgnoreCase(normalizeDomain(right.domain));
            if (domainCompare != 0) {
                return domainCompare;
            }
            return safeText(left.word).compareToIgnoreCase(safeText(right.word));
        });

        adapter.submitList(filtered);
        boolean empty = filtered.isEmpty();
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        resultCaption.setText(getString(R.string.admin_content_result_count, filtered.size(), allVocabulary.size()));
    }

    private String normalizeDomain(String domain) {
        String normalized = safeText(domain);
        return normalized.isEmpty() ? "general" : normalized;
    }

    private String safeText(CharSequence text) {
        return text == null ? "" : text.toString().trim();
    }
}
