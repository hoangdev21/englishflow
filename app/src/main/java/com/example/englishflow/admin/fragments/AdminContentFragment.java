package com.example.englishflow.admin.fragments;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.englishflow.R;
import com.example.englishflow.admin.AdminLessonDetailActivity;
import com.example.englishflow.admin.AdminJourneyLessonAdapter;
import com.example.englishflow.admin.AdminJourneyLessonItem;
import com.example.englishflow.admin.AdminVocabularyAdapter;
import com.example.englishflow.data.AppRepository;
import com.example.englishflow.data.DomainItem;
import com.example.englishflow.data.FirebaseSeeder;
import com.example.englishflow.data.JourneyLessonRepository;
import com.example.englishflow.data.LessonVocabulary;
import com.example.englishflow.data.MapLessonFlowStep;
import com.example.englishflow.data.MapNodeItem;
import com.example.englishflow.database.entity.CustomVocabularyEntity;
import com.example.englishflow.ui.CustomVocabularyManagerActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.WriteBatch;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AdminContentFragment extends Fragment {

    private interface LessonWriteAction {
        void run();
    }

    private static final String COLLECTION_MAP_LESSONS = "map_lessons";
    private static final String COLLECTION_USERS = "users";
    private static final String STATUS_PUBLISHED = "published";
    private static final String STATUS_DRAFT = "draft";
    private static final long TOPIC_COUNT_REFRESH_INTERVAL_MS = 20_000L;
    private static final long SYSTEM_VOCAB_COUNT_REFRESH_INTERVAL_MS = 20_000L;
    private static final long SEARCH_FILTER_DEBOUNCE_MS = 120L;
    private static final float RECYCLER_HEIGHT_SCREEN_RATIO = 0.58f;
    private static final int RECYCLER_MIN_HEIGHT_DP = 280;

    private enum SectionMode {
        VOCABULARY,
        JOURNEY
    }

    private AppRepository repository;
    private FirebaseFirestore firestore;
    private JourneyLessonRepository journeyLessonRepository;
    private AdminVocabularyAdapter vocabularyAdapter;
    private AdminJourneyLessonAdapter journeyAdapter;
    private TextInputEditText searchInput;
    private AutoCompleteTextView domainFilterInput;
    private View domainFilterLayout;
    private TextView resultCaption;
    private TextView emptyState;
    private TextView journeyResultCaption;
    private TextView journeyEmptyState;
    private TextView journeySummaryChip;
    private TextView statVocabularyCount;
    private TextView statDomainCount;
    private TextView statTopicCount;
    private TextView statLockedCount;
    private View vocabularyPanel;
    private View journeyPanel;
    private com.google.android.material.button.MaterialButton sectionVocabularyButton;
    private com.google.android.material.button.MaterialButton sectionJourneyButton;
    private MaterialButton primaryActionButton;
    private ArrayAdapter<String> domainFilterAdapter;
    private ListenerRegistration journeyRegistration;

    private final List<CustomVocabularyEntity> allVocabulary = new ArrayList<>();
    private final List<AdminJourneyLessonItem> allJourneyLessons = new ArrayList<>();
    private SectionMode activeSection = SectionMode.VOCABULARY;
    private boolean hasFirestoreJourneyData = false;
    private boolean hasRequestedJourneyFallback = false;
    private boolean journeySeedWriteInFlight = false;
    private long lastTopicCountRefreshAt = 0L;
    private long lastSystemVocabCountRefreshAt = 0L;
    private int cachedSystemVocabularyCount = -1;
    private String selectedDomain = "";
    private String searchQuery = "";
    private String allDomainLabel = "";
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable applyFiltersRunnable = this::applyFilters;
    private final ExecutorService filterExecutor = Executors.newSingleThreadExecutor();
    private int vocabularyFilterGeneration = 0;
    private int journeyFilterGeneration = 0;
    private int vocabularyCatalogGeneration = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_admin_content, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        repository = AppRepository.getInstance(requireContext());
        firestore = FirebaseFirestore.getInstance();
        journeyLessonRepository = new JourneyLessonRepository();
        allDomainLabel = getString(R.string.admin_content_domain_filter_all);

        statVocabularyCount = view.findViewById(R.id.adminStatVocabularyCount);
        statDomainCount = view.findViewById(R.id.adminStatDomainCount);
        statTopicCount = view.findViewById(R.id.adminStatTopicCount);
        statLockedCount = view.findViewById(R.id.adminStatLockedCount);
        journeySummaryChip = view.findViewById(R.id.adminJourneySummaryChip);

        resultCaption = view.findViewById(R.id.adminVocabResultCaption);
        emptyState = view.findViewById(R.id.adminVocabEmptyState);
        journeyResultCaption = view.findViewById(R.id.adminJourneyResultCaption);
        journeyEmptyState = view.findViewById(R.id.adminJourneyEmptyState);

        vocabularyPanel = view.findViewById(R.id.adminVocabularyPanel);
        journeyPanel = view.findViewById(R.id.adminJourneyPanel);

        sectionVocabularyButton = view.findViewById(R.id.adminSectionVocabulary);
        sectionJourneyButton = view.findViewById(R.id.adminSectionJourney);
        primaryActionButton = view.findViewById(R.id.adminBtnPrimaryAction);

        searchInput = view.findViewById(R.id.adminVocabSearchInput);
        domainFilterInput = view.findViewById(R.id.adminDomainFilterInput);
        domainFilterLayout = view.findViewById(R.id.adminDomainFilterLayout);

        setupRecyclerViews(view);
        setupSectionControls();
        setupPrimaryActions(view);
        setupShortcutActions(view);

        setupSearchInput();
        setupDomainFilter();



        setActiveSection(SectionMode.VOCABULARY);
        refreshTopicCount(true);
        refreshSystemVocabularyCount(true);
        startJourneyRealtimeListener();
        refreshAdminVocabularyCatalog();

        repository.getAllCustomVocabularyLive().observe(getViewLifecycleOwner(), items -> refreshAdminVocabularyCatalog());
    }

    @Override
    public void onDestroyView() {
        mainHandler.removeCallbacks(applyFiltersRunnable);
        stopJourneyRealtimeListener();
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        filterExecutor.shutdownNow();
        super.onDestroy();
    }

    private void setupRecyclerViews(@NonNull View view) {
        RecyclerView vocabRecycler = view.findViewById(R.id.adminVocabRecycler);
        vocabRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        vocabRecycler.setHasFixedSize(true);
        vocabRecycler.setItemViewCacheSize(12);
        configureRecyclerScrollBehavior(vocabRecycler);
        vocabularyAdapter = new AdminVocabularyAdapter(new AdminVocabularyAdapter.ActionListener() {
            @Override
            public void onEdit(@NonNull CustomVocabularyEntity item) {
                showEditVocabularyDialog(item);
            }

            @Override
            public void onToggleLock(@NonNull CustomVocabularyEntity item) {
                toggleVocabularyLock(item);
            }
        });
        vocabRecycler.setAdapter(vocabularyAdapter);

        RecyclerView journeyRecycler = view.findViewById(R.id.adminJourneyRecycler);
        journeyRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        journeyRecycler.setHasFixedSize(true);
        journeyRecycler.setItemViewCacheSize(8);
        configureRecyclerScrollBehavior(journeyRecycler);
        journeyAdapter = new AdminJourneyLessonAdapter(new AdminJourneyLessonAdapter.ActionListener() {
            @Override
            public void onViewDetails(@NonNull AdminJourneyLessonItem item) {
                openLessonDetail(item);
            }

            @Override
            public void onEdit(@NonNull AdminJourneyLessonItem item) {
                showEditJourneyDialog(item);
            }

            @Override
            public void onToggleVisibility(@NonNull AdminJourneyLessonItem item) {
                toggleJourneyVisibility(item);
            }

            @Override
            public void onDelete(@NonNull AdminJourneyLessonItem item) {
                confirmDeleteJourneyLesson(item);
            }
        });
        journeyRecycler.setAdapter(journeyAdapter);
    }

    private void configureRecyclerScrollBehavior(@NonNull RecyclerView recyclerView) {
        recyclerView.setNestedScrollingEnabled(true);
        recyclerView.setItemAnimator(null);
        recyclerView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        recyclerView.getRecycledViewPool().setMaxRecycledViews(0, 40);

        ViewGroup.LayoutParams params = recyclerView.getLayoutParams();
        if (params != null) {
            int targetHeight = Math.max(
                    dpToPx(RECYCLER_MIN_HEIGHT_DP),
                    Math.round(getResources().getDisplayMetrics().heightPixels * RECYCLER_HEIGHT_SCREEN_RATIO)
            );
            if (params.height != targetHeight) {
                params.height = targetHeight;
                recyclerView.setLayoutParams(params);
            }
        }
    }

    private void setupSectionControls() {
        if (sectionVocabularyButton != null) {
            sectionVocabularyButton.setOnClickListener(v -> setActiveSection(SectionMode.VOCABULARY));
        }
        if (sectionJourneyButton != null) {
            sectionJourneyButton.setOnClickListener(v -> setActiveSection(SectionMode.JOURNEY));
        }
    }

    private void setupPrimaryActions(@NonNull View view) {
        if (primaryActionButton != null) {
            primaryActionButton.setOnClickListener(v -> {
                if (activeSection == SectionMode.JOURNEY) {
                    showAddJourneyDialog();
                } else {
                    showAddVocabularyDialog();
                }
            });
        }

        MaterialButton btnContent = view.findViewById(R.id.adminBtnContent);
        if (btnContent != null) {
            btnContent.setOnClickListener(v -> openAdvancedManager());
        }
    }

    private void setupShortcutActions(@NonNull View view) {
        MaterialButton addWordShortcut = view.findViewById(R.id.adminShortcutAddWord);
        MaterialButton addJourneyShortcut = view.findViewById(R.id.adminShortcutAddJourney);
        MaterialButton openManagerShortcut = view.findViewById(R.id.adminShortcutOpenManager);

        if (addWordShortcut != null) {
            addWordShortcut.setOnClickListener(v -> {
                setActiveSection(SectionMode.VOCABULARY);
                showAddVocabularyDialog();
            });
        }

        if (addJourneyShortcut != null) {
            addJourneyShortcut.setOnClickListener(v -> {
                setActiveSection(SectionMode.JOURNEY);
                showAddJourneyDialog();
            });
        }

        if (openManagerShortcut != null) {
            openManagerShortcut.setOnClickListener(v -> openAdvancedManager());
        }
    }

    private void openAdvancedManager() {
        startActivity(new Intent(requireContext(), CustomVocabularyManagerActivity.class));
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
                scheduleApplyFilters();
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
                R.layout.item_admin_dropdown_option,
                new ArrayList<>()
        );
        domainFilterInput.setAdapter(domainFilterAdapter);
        domainFilterInput.setDropDownBackgroundResource(R.drawable.bg_admin_dropdown_popup);
        domainFilterInput.setDropDownHeight(dpToPx(260));
        domainFilterInput.setDropDownVerticalOffset(dpToPx(6));
        domainFilterInput.setThreshold(0);
        domainFilterInput.setOnClickListener(v -> domainFilterInput.showDropDown());
        domainFilterInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                domainFilterInput.showDropDown();
            }
        });
        domainFilterInput.setOnItemClickListener((parent, view, position, id) -> {
            String selected = domainFilterAdapter.getItem(position);
            if (selected == null || selected.equals(allDomainLabel)) {
                selectedDomain = "";
            } else {
                selectedDomain = selected;
            }
            applyFiltersNow();
        });
        domainFilterInput.setText(allDomainLabel, false);
    }

    private void scheduleApplyFilters() {
        mainHandler.removeCallbacks(applyFiltersRunnable);
        mainHandler.postDelayed(applyFiltersRunnable, SEARCH_FILTER_DEBOUNCE_MS);
    }

    private void applyFiltersNow() {
        mainHandler.removeCallbacks(applyFiltersRunnable);
        applyFilters();
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
        sortedDomains.sort(String.CASE_INSENSITIVE_ORDER);
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
        int localWords = allVocabulary.size();
        int totalWords = cachedSystemVocabularyCount > 0 ? cachedSystemVocabularyCount : localWords;
        int lockedWords = 0;
        Set<String> domains = new LinkedHashSet<>();

        for (CustomVocabularyEntity item : allVocabulary) {
            domains.add(normalizeDomain(item.domain));
            if (item.isLocked) {
                lockedWords++;
            }
        }

        if (statVocabularyCount != null) {
            statVocabularyCount.setText(String.valueOf(totalWords));
        }
        if (statDomainCount != null) {
            statDomainCount.setText(String.valueOf(domains.size()));
        }
        if (statLockedCount != null) {
            statLockedCount.setText(String.valueOf(lockedWords));
        }
    }

    private void refreshSystemVocabularyCount(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && (now - lastSystemVocabCountRefreshAt) < SYSTEM_VOCAB_COUNT_REFRESH_INTERVAL_MS) {
            return;
        }
        lastSystemVocabCountRefreshAt = now;

        repository.getSystemVocabularyCountAsync(totalVocabulary -> {
            if (!isAdded()) {
                return;
            }
            cachedSystemVocabularyCount = Math.max(0, totalVocabulary);
            if (statVocabularyCount != null) {
                statVocabularyCount.setText(String.valueOf(cachedSystemVocabularyCount));
            }
        });
    }

    private void refreshTopicCount(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && (now - lastTopicCountRefreshAt) < TOPIC_COUNT_REFRESH_INTERVAL_MS) {
            return;
        }
        lastTopicCountRefreshAt = now;

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
            if (statTopicCount != null) {
                statTopicCount.setText(String.valueOf(topicCount));
            }
        });
    }

    private void refreshAdminVocabularyCatalog() {
        final int requestGeneration = ++vocabularyCatalogGeneration;
        repository.getAdminVocabularyCatalogAsync(items -> {
            if (!isAdded() || requestGeneration != vocabularyCatalogGeneration) {
                return;
            }

            allVocabulary.clear();
            if (items != null) {
                allVocabulary.addAll(items);
            }

            updateSummaryStats();
            rebuildDomainFilterOptions();
            refreshTopicCount(false);
            refreshSystemVocabularyCount(false);
            applyFiltersNow();
        });
    }

    private void applyFilters() {
        if (activeSection == SectionMode.JOURNEY) {
            applyJourneyFilters();
        } else {
            applyVocabularyFilters();
        }
    }

    private void applyVocabularyFilters() {
        if (vocabularyAdapter == null) {
            return;
        }

        final int requestGeneration = ++vocabularyFilterGeneration;
        final List<CustomVocabularyEntity> source = new ArrayList<>(allVocabulary);
        final String domainFilter = selectedDomain;
        final String query = searchQuery;

        filterExecutor.execute(() -> {
            List<CustomVocabularyEntity> filtered = new ArrayList<>();
            for (CustomVocabularyEntity item : source) {
                String domain = normalizeDomain(item.domain);
                if (!domainFilter.isEmpty() && !domain.equalsIgnoreCase(domainFilter)) {
                    continue;
                }

                if (!query.isEmpty()) {
                    String haystack = (safeText(item.word) + " "
                            + safeText(item.meaning) + " "
                            + domain + " "
                            + safeText(item.source)).toLowerCase(Locale.US);
                    if (!haystack.contains(query)) {
                        continue;
                    }
                }
                filtered.add(item);
            }

            filtered.sort((left, right) -> {
                int domainCompare = normalizeDomain(left.domain).compareToIgnoreCase(normalizeDomain(right.domain));
                if (domainCompare != 0) {
                    return domainCompare;
                }
                return safeText(left.word).compareToIgnoreCase(safeText(right.word));
            });

            int totalCount = source.size();
            mainHandler.post(() -> {
                if (!isAdded()
                        || vocabularyAdapter == null
                        || activeSection != SectionMode.VOCABULARY
                        || requestGeneration != vocabularyFilterGeneration) {
                    return;
                }

                vocabularyAdapter.submitList(filtered);
                if (emptyState != null) {
                    emptyState.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
                }
                if (resultCaption != null) {
                    resultCaption.setText(getString(R.string.admin_content_result_count, filtered.size(), totalCount));
                }
            });
        });
    }

    private void applyJourneyFilters() {
        if (journeyAdapter == null) {
            return;
        }

        final int requestGeneration = ++journeyFilterGeneration;
        final List<AdminJourneyLessonItem> source = new ArrayList<>(allJourneyLessons);
        final String query = searchQuery;

        filterExecutor.execute(() -> {
            List<AdminJourneyLessonItem> filtered = new ArrayList<>();
            for (AdminJourneyLessonItem item : source) {
                if (!query.isEmpty()) {
                    String haystack = (safeText(item.getLessonId()) + " "
                            + safeText(item.getTitle()) + " "
                            + safeText(item.getStatus()) + " "
                            + safeText(item.getMinLevel()) + " "
                            + safeText(joinKeywords(item.getKeywords()))).toLowerCase(Locale.US);
                    if (!haystack.contains(query)) {
                        continue;
                    }
                }
                filtered.add(item);
            }

            filtered.sort((left, right) -> {
                int byOrder = Integer.compare(left.getOrder(), right.getOrder());
                if (byOrder != 0) {
                    return byOrder;
                }
                return safeText(left.getTitle()).compareToIgnoreCase(safeText(right.getTitle()));
            });

            int totalCount = source.size();
            mainHandler.post(() -> {
                if (!isAdded()
                        || journeyAdapter == null
                        || activeSection != SectionMode.JOURNEY
                        || requestGeneration != journeyFilterGeneration) {
                    return;
                }

                journeyAdapter.submitList(filtered);
                if (journeyEmptyState != null) {
                    journeyEmptyState.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
                }
                if (journeyResultCaption != null) {
                    journeyResultCaption.setText(getString(
                            R.string.admin_content_journey_result_count,
                            filtered.size(),
                            totalCount
                    ));
                }
            });
        });
    }

    private void updateJourneySummaryChip() {
        if (journeySummaryChip == null) {
            return;
        }

        int total = allJourneyLessons.size();
        int visible = 0;
        for (AdminJourneyLessonItem item : allJourneyLessons) {
            if (item.isVisibleToLearner()) {
                visible++;
            }
        }
        int hidden = Math.max(0, total - visible);

        journeySummaryChip.setText(getString(
                R.string.admin_content_journey_summary,
                total,
                visible,
                hidden
        ));
    }

    private void setActiveSection(@NonNull SectionMode mode) {
        activeSection = mode;

        if (vocabularyPanel != null) {
            vocabularyPanel.setVisibility(mode == SectionMode.VOCABULARY ? View.VISIBLE : View.GONE);
        }
        if (journeyPanel != null) {
            journeyPanel.setVisibility(mode == SectionMode.JOURNEY ? View.VISIBLE : View.GONE);
        }
        if (domainFilterLayout != null) {
            domainFilterLayout.setVisibility(mode == SectionMode.VOCABULARY ? View.VISIBLE : View.GONE);
        }
        if (resultCaption != null) {
            resultCaption.setVisibility(mode == SectionMode.VOCABULARY ? View.VISIBLE : View.GONE);
        }
        if (journeyResultCaption != null) {
            journeyResultCaption.setVisibility(mode == SectionMode.JOURNEY ? View.VISIBLE : View.GONE);
        }

        styleSectionButton(sectionVocabularyButton, mode == SectionMode.VOCABULARY);
        styleSectionButton(sectionJourneyButton, mode == SectionMode.JOURNEY);

        if (primaryActionButton != null) {
            primaryActionButton.setText(mode == SectionMode.VOCABULARY
                    ? getString(R.string.admin_content_add_word)
                    : getString(R.string.admin_content_add_journey));
        }

        if (searchInput != null) {
            searchInput.setHint(mode == SectionMode.VOCABULARY
                    ? getString(R.string.admin_content_search_hint)
                    : getString(R.string.admin_content_journey_search_hint));
        }

        applyFiltersNow();
    }

    private void styleSectionButton(@Nullable com.google.android.material.button.MaterialButton button, boolean selected) {
        if (button == null || !isAdded()) {
            return;
        }

        button.setBackgroundResource(selected ? R.drawable.bg_admin_3d_primary : R.drawable.bg_admin_3d_white);
        button.setTextColor(ContextCompat.getColor(requireContext(), selected ? R.color.white : R.color.ef_text_primary));
        button.setIconTint(ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), selected ? R.color.white : R.color.ef_primary)
        ));
    }

    private void toggleVocabularyLock(@NonNull CustomVocabularyEntity item) {
        boolean nextState = !item.isLocked;
        boolean success = repository.upsertCustomVocabularyByAdmin(
                item.word,
                nonEmpty(item.meaning, "-"),
                normalizeDomain(item.domain),
                nonEmpty(item.source, "seed"),
                nextState
        );
        if (isAdded()) {
            Toast.makeText(
                    requireContext(),
                    success
                            ? getString(nextState
                            ? R.string.admin_content_word_locked
                            : R.string.admin_content_word_unlocked)
                            : getString(R.string.admin_content_action_failed),
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void showAddVocabularyDialog() {
        LinearLayout container = createDialogContainer();

        addDialogLabel(container, getString(R.string.admin_content_word_label));
        EditText wordInput = createDialogInput(
                "e.g. blossom",
                "",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        );
        addDialogField(container, wordInput);

        addDialogLabel(container, getString(R.string.admin_content_meaning_label));
        EditText meaningInput = createDialogInput(
                "e.g. nở hoa",
                "",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        );
        addDialogField(container, meaningInput);

        addDialogLabel(container, getString(R.string.admin_content_domain_label));
        EditText domainInput = createDialogInput(
                "e.g. nature",
                selectedDomain.isEmpty() ? "general" : selectedDomain,
                InputType.TYPE_CLASS_TEXT
        );
        addDialogField(container, domainInput);

        addDialogLabel(container, getString(R.string.admin_content_source_label));
        EditText sourceInput = createDialogInput(
                "Source of data",
                "admin",
                InputType.TYPE_CLASS_TEXT
        );
        addDialogField(container, sourceInput);

        CheckBox lockCheck = createDialogCheck(getString(R.string.admin_content_lock_now), false);
        addDialogField(container, lockCheck);

        ScrollView scrollWrapper = new ScrollView(requireContext());
        scrollWrapper.addView(container);

        AlertDialog dialog = new AlertDialog.Builder(requireContext(), R.style.ef_dialog_rounded)
                .setTitle(R.string.admin_content_add_word_title)
                .setView(scrollWrapper)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.admin_content_save, null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String word = safeText(wordInput.getText());
            String meaning = safeText(meaningInput.getText());
            String domain = safeText(domainInput.getText());
            String source = safeText(sourceInput.getText());

            if (word.isEmpty() || meaning.isEmpty()) {
                Toast.makeText(requireContext(), R.string.admin_content_required_fields, Toast.LENGTH_SHORT).show();
                return;
            }

            boolean success = repository.upsertCustomVocabularyByAdmin(
                    word,
                    meaning,
                    domain,
                    source,
                    lockCheck.isChecked()
            );

            if (success) {
                Toast.makeText(requireContext(), R.string.admin_content_word_added, Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            } else {
                Toast.makeText(requireContext(), R.string.admin_content_action_failed, Toast.LENGTH_SHORT).show();
            }
        }));

        dialog.show();
    }

    private void showEditVocabularyDialog(@NonNull CustomVocabularyEntity item) {
        LinearLayout container = createDialogContainer();

        addDialogLabel(container, getString(R.string.admin_content_word_label));
        EditText wordInput = createDialogInput(
                "Word",
                safeText(item.word),
                InputType.TYPE_CLASS_TEXT
        );
        wordInput.setEnabled(false);
        addDialogField(container, wordInput);

        addDialogLabel(container, getString(R.string.admin_content_meaning_label));
        EditText meaningInput = createDialogInput(
                "Meaning",
                safeText(item.meaning),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        );
        addDialogField(container, meaningInput);

        addDialogLabel(container, getString(R.string.admin_content_domain_label));
        EditText domainInput = createDialogInput(
                "Domain",
                normalizeDomain(item.domain),
                InputType.TYPE_CLASS_TEXT
        );
        addDialogField(container, domainInput);

        addDialogLabel(container, getString(R.string.admin_content_source_label));
        EditText sourceInput = createDialogInput(
                "Source",
                safeText(item.source),
                InputType.TYPE_CLASS_TEXT
        );
        addDialogField(container, sourceInput);

        CheckBox lockCheck = createDialogCheck(getString(R.string.admin_content_lock_now), item.isLocked);
        addDialogField(container, lockCheck);

        ScrollView scrollWrapper = new ScrollView(requireContext());
        scrollWrapper.addView(container);

        AlertDialog dialog = new AlertDialog.Builder(requireContext(), R.style.ef_dialog_rounded)
                .setTitle(R.string.admin_content_edit_word_title)
                .setView(scrollWrapper)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.admin_content_save, null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String meaning = safeText(meaningInput.getText());
            String domain = safeText(domainInput.getText());
            String source = safeText(sourceInput.getText());
            if (meaning.isEmpty()) {
                Toast.makeText(requireContext(), R.string.admin_content_required_fields, Toast.LENGTH_SHORT).show();
                return;
            }

            boolean success = repository.updateCustomVocabularyByAdmin(
                    item.word,
                    meaning,
                    domain,
                    source,
                    lockCheck.isChecked()
            );

            if (success) {
                Toast.makeText(requireContext(), R.string.admin_content_word_updated, Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            } else {
                Toast.makeText(requireContext(), R.string.admin_content_action_failed, Toast.LENGTH_SHORT).show();
            }
        }));

        dialog.show();
    }

    private void showAddJourneyDialog() {
        int suggestedOrder = getNextJourneyOrder();

        LinearLayout container = createDialogContainer();

        addDialogLabel(container, getString(R.string.admin_content_journey_title_label));
        EditText titleInput = createDialogInput(
                "e.g. Job Interview",
                "",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        );
        addDialogField(container, titleInput);

        addDialogLabel(container, getString(R.string.admin_content_journey_level_label));
        EditText levelInput = createDialogInput(
                "A1, A2, B1...",
                "A1",
                InputType.TYPE_CLASS_TEXT
        );
        addDialogField(container, levelInput);

        addDialogLabel(container, getString(R.string.admin_content_journey_order_label));
        EditText orderInput = createDialogInput(
                "Display order",
                String.valueOf(suggestedOrder),
                InputType.TYPE_CLASS_NUMBER
        );
        addDialogField(container, orderInput);

        addDialogLabel(container, getString(R.string.admin_content_journey_status_label));
        EditText statusInput = createDialogInput(
                "Status (published/draft)",
                STATUS_PUBLISHED,
                InputType.TYPE_CLASS_TEXT
        );
        addDialogField(container, statusInput);

        addDialogLabel(container, getString(R.string.admin_content_domain_label));
        EditText domainInput = createDialogInput(
            "e.g. Học tập",
            "Học tập",
            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS
        );
        addDialogField(container, domainInput);

        addDialogLabel(container, getString(R.string.admin_content_journey_keywords_label));
        EditText keywordsInput = createDialogInput(
                "Separated by commas",
                "",
                InputType.TYPE_CLASS_TEXT
        );
        addDialogField(container, keywordsInput);

        addDialogLabel(container, getString(R.string.admin_content_journey_role_label));
        EditText roleInput = createDialogInput(
                "AI Assistant role",
                getString(R.string.admin_content_journey_default_role),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        );
        addDialogField(container, roleInput);

        ScrollView scrollWrapper = new ScrollView(requireContext());
        scrollWrapper.addView(container);

        AlertDialog dialog = new AlertDialog.Builder(requireContext(), R.style.ef_dialog_rounded)
                .setTitle(R.string.admin_content_add_journey_title)
                .setView(scrollWrapper)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.admin_content_save, null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            View saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            String title = safeText(titleInput.getText());
            String minLevel = safeText(levelInput.getText());
            String status = normalizeJourneyStatus(safeText(statusInput.getText()));
            String keywordsRaw = safeText(keywordsInput.getText());
            String roleDescription = safeText(roleInput.getText());

            int order = safeInt(orderInput.getText());
            if (order <= 0) {
                order = suggestedOrder;
            }

            if (title.isEmpty()) {
                Toast.makeText(requireContext(), R.string.admin_content_required_fields, Toast.LENGTH_SHORT).show();
                return;
            }

            List<String> keywords = parseKeywords(keywordsRaw);
            String lessonId = generateLessonId(title, order);
            String domain = resolveJourneyDomain(
                    safeText(domainInput.getText()),
                    title,
                    keywords,
                    lessonId
            );
            FirebaseUser actor = FirebaseAuth.getInstance().getCurrentUser();
            String actorUid = actor == null ? "" : safeText(actor.getUid());
            String actorEmail = actor == null ? "" : safeText(actor.getEmail());

            Map<String, Object> payload = new HashMap<>();
            payload.put("lesson_id", lessonId);
            payload.put("title", title);
            payload.put("domain", domain);
            payload.put("emoji", pickEmojiForOrder(order));
            payload.put("prompt_key", buildPromptKey(lessonId, title));
            payload.put("role_description", nonEmpty(roleDescription, getString(R.string.admin_content_journey_default_role)));
            payload.put("min_level", nonEmpty(minLevel, "A1"));
            payload.put("min_exchanges", 4);
            payload.put("order", order);
            payload.put("status", status);
            payload.put("keywords", keywords);
            payload.put("flow_steps", buildDefaultFlowSteps(title, keywords, roleDescription));
            payload.put("vocabulary", buildDefaultVocabulary(keywords));
            payload.put("createdByUid", actorUid);
            payload.put("createdByEmail", actorEmail);
            payload.put("updatedByUid", actorUid);
            payload.put("updatedByEmail", actorEmail);
            payload.put("updatedAt", FieldValue.serverTimestamp());
            payload.put("createdAt", FieldValue.serverTimestamp());

            saveButton.setEnabled(false);
            ensureAdminForLessonWrite(
                    () -> writeNewLessonDocument(lessonId, payload, saveButton, dialog, 1),
                    () -> saveButton.setEnabled(true)
            );
        }));

        dialog.show();
    }

    private void showEditJourneyDialog(@NonNull AdminJourneyLessonItem item) {
        LinearLayout container = createDialogContainer();

        addDialogLabel(container, getString(R.string.admin_content_journey_title_label));
        EditText titleInput = createDialogInput(
                "Title",
                safeText(item.getTitle()),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        );
        addDialogField(container, titleInput);

        addDialogLabel(container, getString(R.string.admin_content_journey_level_label));
        EditText levelInput = createDialogInput(
                "Level",
                nonEmpty(item.getMinLevel(), "A1"),
                InputType.TYPE_CLASS_TEXT
        );
        addDialogField(container, levelInput);

        addDialogLabel(container, getString(R.string.admin_content_journey_order_label));
        EditText orderInput = createDialogInput(
                "Order",
                String.valueOf(item.getOrder()),
                InputType.TYPE_CLASS_NUMBER
        );
        addDialogField(container, orderInput);

        addDialogLabel(container, getString(R.string.admin_content_journey_status_label));
        EditText statusInput = createDialogInput(
                "Status (published/draft)",
                nonEmpty(item.getStatus(), STATUS_PUBLISHED),
                InputType.TYPE_CLASS_TEXT
        );
        addDialogField(container, statusInput);

        addDialogLabel(container, getString(R.string.admin_content_domain_label));
        EditText domainInput = createDialogInput(
                "Domain",
                resolveJourneyDomain("", item.getTitle(), item.getKeywords(), item.getLessonId()),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS
        );
        addDialogField(container, domainInput);

        addDialogLabel(container, getString(R.string.admin_content_journey_keywords_label));
        EditText keywordsInput = createDialogInput(
                "Keywords",
                joinKeywords(item.getKeywords()),
                InputType.TYPE_CLASS_TEXT
        );
        addDialogField(container, keywordsInput);

        ScrollView scrollWrapper = new ScrollView(requireContext());
        scrollWrapper.addView(container);

        AlertDialog dialog = new AlertDialog.Builder(requireContext(), R.style.ef_dialog_rounded)
                .setTitle(R.string.admin_content_edit_journey_title)
                .setView(scrollWrapper)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.admin_content_save, null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String title = safeText(titleInput.getText());
            String minLevel = safeText(levelInput.getText());
            String status = normalizeJourneyStatus(safeText(statusInput.getText()));
            List<String> keywords = parseKeywords(safeText(keywordsInput.getText()));
            String domain = resolveJourneyDomain(
                    safeText(domainInput.getText()),
                    title,
                    keywords,
                    item.getLessonId()
            );

            int order = safeInt(orderInput.getText());
            if (order <= 0) {
                order = item.getOrder();
            }

            if (title.isEmpty()) {
                Toast.makeText(requireContext(), R.string.admin_content_required_fields, Toast.LENGTH_SHORT).show();
                return;
            }

            Map<String, Object> payload = new HashMap<>();
            FirebaseUser actor = FirebaseAuth.getInstance().getCurrentUser();
            String actorUid = actor == null ? "" : safeText(actor.getUid());
            String actorEmail = actor == null ? "" : safeText(actor.getEmail());
            payload.put("title", title);
            payload.put("min_level", nonEmpty(minLevel, "A1"));
            payload.put("order", order);
            payload.put("status", status);
            payload.put("domain", domain);
            payload.put("keywords", keywords);
            payload.put("prompt_key", buildPromptKey(item.getLessonId(), title));
            payload.put("updatedByUid", actorUid);
            payload.put("updatedByEmail", actorEmail);
            payload.put("updatedAt", FieldValue.serverTimestamp());

            ensureAdminForLessonWrite(() -> firestore.collection(COLLECTION_MAP_LESSONS)
                    .document(item.getDocumentId())
                    .set(payload, SetOptions.merge())
                    .addOnSuccessListener(unused -> {
                        if (!isAdded()) {
                            return;
                        }
                        Toast.makeText(requireContext(), R.string.admin_content_journey_updated, Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    })
                    .addOnFailureListener(error -> {
                        if (!isAdded()) {
                            return;
                        }
                        Toast.makeText(
                                requireContext(),
                                getString(R.string.admin_content_action_failed_with_reason, humanizeFirestoreError(error)),
                                Toast.LENGTH_LONG
                        ).show();
                    }), null);
        }));

        dialog.show();
    }

    private void openLessonDetail(@NonNull AdminJourneyLessonItem item) {
        Intent intent = new Intent(requireContext(), AdminLessonDetailActivity.class);
        intent.putExtra(
                AdminLessonDetailActivity.EXTRA_DOCUMENT_ID,
                nonEmpty(item.getDocumentId(), item.getLessonId())
        );
        intent.putExtra(AdminLessonDetailActivity.EXTRA_LESSON_ID, safeText(item.getLessonId()));
        startActivity(intent);
    }

    private void confirmDeleteJourneyLesson(@NonNull AdminJourneyLessonItem item) {
        if (!isAdded()) {
            return;
        }

        String lessonName = nonEmpty(item.getTitle(), item.getLessonId(), item.getDocumentId());
        new AlertDialog.Builder(requireContext(), R.style.ef_dialog_rounded)
                .setTitle(R.string.admin_content_delete_journey_title)
                .setMessage(getString(R.string.admin_content_delete_journey_message, lessonName))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.admin_content_delete, (dialog, which) -> deleteJourneyLesson(item))
                .show();
    }

    private void deleteJourneyLesson(@NonNull AdminJourneyLessonItem item) {
        String documentId = nonEmpty(item.getDocumentId(), item.getLessonId());
        if (documentId.isEmpty()) {
            if (isAdded()) {
                Toast.makeText(requireContext(), R.string.admin_content_action_failed, Toast.LENGTH_SHORT).show();
            }
            return;
        }

        ensureAdminForLessonWrite(() -> firestore.collection(COLLECTION_MAP_LESSONS)
                .document(documentId)
                .delete()
                .addOnSuccessListener(unused -> {
                    if (!isAdded()) {
                        return;
                    }
                    removeJourneyLessonLocally(item);
                    Toast.makeText(requireContext(), R.string.admin_content_journey_deleted, Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(error -> {
                    if (!isAdded()) {
                        return;
                    }
                    Toast.makeText(
                            requireContext(),
                            getString(R.string.admin_content_action_failed_with_reason, humanizeFirestoreError(error)),
                            Toast.LENGTH_LONG
                    ).show();
                }), null);
    }

    private void removeJourneyLessonLocally(@NonNull AdminJourneyLessonItem removedItem) {
        List<AdminJourneyLessonItem> remaining = new ArrayList<>();
        for (AdminJourneyLessonItem item : allJourneyLessons) {
            if (!isSameJourneyLesson(item, removedItem)) {
                remaining.add(item);
            }
        }

        if (remaining.size() == allJourneyLessons.size()) {
            return;
        }
        updateJourneyItems(remaining);
    }

    private boolean isSameJourneyLesson(@NonNull AdminJourneyLessonItem left,
                                        @NonNull AdminJourneyLessonItem right) {
        String leftDocId = safeText(left.getDocumentId());
        String rightDocId = safeText(right.getDocumentId());
        if (!leftDocId.isEmpty() && leftDocId.equalsIgnoreCase(rightDocId)) {
            return true;
        }

        String leftLessonId = safeText(left.getLessonId());
        String rightLessonId = safeText(right.getLessonId());
        return !leftLessonId.isEmpty() && leftLessonId.equalsIgnoreCase(rightLessonId);
    }

    private void toggleJourneyVisibility(@NonNull AdminJourneyLessonItem item) {
        String nextStatus = item.isVisibleToLearner() ? STATUS_DRAFT : STATUS_PUBLISHED;

        Map<String, Object> payload = new HashMap<>();
        FirebaseUser actor = FirebaseAuth.getInstance().getCurrentUser();
        String actorUid = actor == null ? "" : safeText(actor.getUid());
        String actorEmail = actor == null ? "" : safeText(actor.getEmail());
        payload.put("status", nextStatus);
        payload.put("updatedByUid", actorUid);
        payload.put("updatedByEmail", actorEmail);
        payload.put("updatedAt", FieldValue.serverTimestamp());

        ensureAdminForLessonWrite(() -> firestore.collection(COLLECTION_MAP_LESSONS)
                .document(item.getDocumentId())
                .set(payload, SetOptions.merge())
                .addOnSuccessListener(unused -> {
                    if (!isAdded()) {
                        return;
                    }
                    Toast.makeText(
                            requireContext(),
                            nextStatus.equals(STATUS_PUBLISHED)
                                    ? R.string.admin_content_journey_visible
                                    : R.string.admin_content_journey_hidden,
                            Toast.LENGTH_SHORT
                    ).show();
                })
                .addOnFailureListener(error -> {
                    if (!isAdded()) {
                        return;
                    }
                    Toast.makeText(
                            requireContext(),
                            getString(R.string.admin_content_action_failed_with_reason, humanizeFirestoreError(error)),
                            Toast.LENGTH_LONG
                    ).show();
                }), null);
    }

    private void ensureAdminForLessonWrite(@NonNull LessonWriteAction action, @Nullable Runnable onDenied) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            if (isAdded()) {
                Toast.makeText(requireContext(), R.string.admin_content_action_error_auth_required, Toast.LENGTH_LONG).show();
            }
            if (onDenied != null) {
                onDenied.run();
            }
            return;
        }

        String uid = safeText(currentUser.getUid());
        String email = safeText(currentUser.getEmail());
        if (uid.isEmpty()) {
            if (isAdded()) {
                Toast.makeText(requireContext(), R.string.admin_content_action_error_auth_required, Toast.LENGTH_LONG).show();
            }
            if (onDenied != null) {
                onDenied.run();
            }
            return;
        }

        firestore.collection(COLLECTION_USERS)
                .document(uid)
                .get(Source.SERVER)
                .addOnSuccessListener(snapshot -> {
                    String role = safeText(snapshot.getString("role"));
                    if ("admin".equalsIgnoreCase(role)) {
                        action.run();
                        return;
                    }

                    if (FirebaseSeeder.isAdminEmail(email)) {
                        // Promote seed-admin account if role is missing on server.
                        Map<String, Object> patch = new HashMap<>();
                        patch.put("role", "admin");
                        patch.put("updatedAt", FieldValue.serverTimestamp());
                        firestore.collection(COLLECTION_USERS)
                                .document(uid)
                                .set(patch, SetOptions.merge())
                                .addOnSuccessListener(unused -> action.run())
                                .addOnFailureListener(error -> {
                                    if (isAdded()) {
                                        Toast.makeText(
                                                requireContext(),
                                                getString(R.string.admin_content_action_failed_with_reason, humanizeFirestoreError(error)),
                                                Toast.LENGTH_LONG
                                        ).show();
                                    }
                                    if (onDenied != null) {
                                        onDenied.run();
                                    }
                                });
                        return;
                    }

                    if (isAdded()) {
                        Toast.makeText(requireContext(), R.string.admin_content_action_error_not_admin_server, Toast.LENGTH_LONG).show();
                    }
                    if (onDenied != null) {
                        onDenied.run();
                    }
                })
                .addOnFailureListener(error -> {
                    if (isAdded()) {
                        Toast.makeText(
                                requireContext(),
                                getString(R.string.admin_content_action_failed_with_reason, humanizeFirestoreError(error)),
                                Toast.LENGTH_LONG
                        ).show();
                    }
                    if (onDenied != null) {
                        onDenied.run();
                    }
                });
    }

    private void writeNewLessonDocument(@NonNull String lessonId,
                                        @NonNull Map<String, Object> payload,
                                        @NonNull View saveButton,
                                        @NonNull AlertDialog dialog,
                                        int retriesLeft) {
        firestore.collection(COLLECTION_MAP_LESSONS)
                .document(lessonId)
                .get(Source.SERVER)
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        if (retriesLeft > 0) {
                            String retryLessonId = lessonId + "_" + (System.currentTimeMillis() % 100000L);
                            Map<String, Object> retryPayload = new HashMap<>(payload);
                            retryPayload.put("lesson_id", retryLessonId);
                            writeNewLessonDocument(retryLessonId, retryPayload, saveButton, dialog, retriesLeft - 1);
                            return;
                        }

                        saveButton.setEnabled(true);
                        if (isAdded()) {
                            Toast.makeText(requireContext(), R.string.admin_content_journey_id_conflict, Toast.LENGTH_LONG).show();
                        }
                        return;
                    }

                    firestore.collection(COLLECTION_MAP_LESSONS)
                            .document(lessonId)
                            .set(payload)
                            .addOnSuccessListener(unused -> {
                                if (!isAdded()) {
                                    return;
                                }
                                Toast.makeText(requireContext(), R.string.admin_content_journey_added, Toast.LENGTH_SHORT).show();
                                dialog.dismiss();
                            })
                            .addOnFailureListener(error -> {
                                if (!isAdded()) {
                                    return;
                                }
                                saveButton.setEnabled(true);
                                Toast.makeText(
                                        requireContext(),
                                        getString(R.string.admin_content_action_failed_with_reason, humanizeFirestoreError(error)),
                                        Toast.LENGTH_LONG
                                ).show();
                            });
                })
                .addOnFailureListener(error -> {
                    if (!isAdded()) {
                        return;
                    }
                    saveButton.setEnabled(true);
                    Toast.makeText(
                            requireContext(),
                            getString(R.string.admin_content_action_failed_with_reason, humanizeFirestoreError(error)),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    private void startJourneyRealtimeListener() {
        stopJourneyRealtimeListener();
        hasFirestoreJourneyData = false;
        hasRequestedJourneyFallback = false;

        journeyRegistration = firestore.collection(COLLECTION_MAP_LESSONS)
                .addSnapshotListener((snapshot, error) -> {
                    if (!isAdded()) {
                        return;
                    }

                    if (error != null) {
                        loadJourneyFallbackIfNeeded();
                        return;
                    }

                    List<AdminJourneyLessonItem> parsed = parseJourneySnapshot(snapshot);
                    if (!parsed.isEmpty()) {
                        hasFirestoreJourneyData = true;
                        updateJourneyItems(parsed);
                        return;
                    }

                    if (!hasFirestoreJourneyData) {
                        loadJourneyFallbackIfNeeded();
                    } else {
                        updateJourneyItems(new ArrayList<>());
                    }
                });
    }

    private void stopJourneyRealtimeListener() {
        if (journeyRegistration != null) {
            journeyRegistration.remove();
            journeyRegistration = null;
        }
    }

    private List<AdminJourneyLessonItem> parseJourneySnapshot(@Nullable QuerySnapshot snapshot) {
        List<AdminJourneyLessonItem> result = new ArrayList<>();
        if (snapshot == null) {
            return result;
        }

        int fallbackIndex = 0;
        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            fallbackIndex++;

            String lessonId = nonEmpty(safeText(doc.get("lesson_id")), safeText(doc.getId()));
            if (lessonId.isEmpty()) {
                continue;
            }

            String title = nonEmpty(safeText(doc.get("title")), lessonId);
            String minLevel = nonEmpty(safeText(doc.get("min_level")), "A1");
            String status = normalizeJourneyStatus(nonEmpty(safeText(doc.get("status")), STATUS_PUBLISHED));
            int order = resolveJourneyOrder(doc, lessonId, fallbackIndex);
            int minExchanges = Math.max(2, safeInt(doc.get("min_exchanges")));
            int vocabCount = listSize(doc.get("vocabulary"));
            List<String> keywords = toStringList(doc.get("keywords"));

            result.add(new AdminJourneyLessonItem(
                    doc.getId(),
                    lessonId,
                    title,
                    minLevel,
                    status,
                    order,
                    minExchanges,
                    vocabCount,
                    keywords
            ));
        }

        result.sort((left, right) -> {
            int byOrder = Integer.compare(left.getOrder(), right.getOrder());
            if (byOrder != 0) {
                return byOrder;
            }
            return safeText(left.getTitle()).compareToIgnoreCase(safeText(right.getTitle()));
        });

        return result;
    }

    private void loadJourneyFallbackIfNeeded() {
        if (journeyLessonRepository == null || hasRequestedJourneyFallback) {
            return;
        }
        hasRequestedJourneyFallback = true;

        journeyLessonRepository.fetchLessons(nodes -> {
            if (!isAdded() || hasFirestoreJourneyData) {
                return;
            }

            List<AdminJourneyLessonItem> fallbackItems = mapFallbackNodesToAdminItems(nodes);
            updateJourneyItems(fallbackItems);
            seedJourneyCollectionIfMissing(nodes);
        });
    }

    private void updateJourneyItems(@NonNull List<AdminJourneyLessonItem> items) {
        allJourneyLessons.clear();
        allJourneyLessons.addAll(items);
        updateJourneySummaryChip();

        if (activeSection == SectionMode.JOURNEY) {
            applyJourneyFilters();
        }
    }

    private List<AdminJourneyLessonItem> mapFallbackNodesToAdminItems(@Nullable List<MapNodeItem> nodes) {
        List<AdminJourneyLessonItem> items = new ArrayList<>();
        if (nodes == null || nodes.isEmpty()) {
            return items;
        }

        int fallbackIndex = 0;
        for (MapNodeItem node : nodes) {
            if (node == null) {
                continue;
            }
            fallbackIndex++;

            String lessonId = nonEmpty(safeText(node.getNodeId()), String.format(Locale.US, "lesson_%02d", fallbackIndex));
            int order = extractOrderFromLessonId(lessonId, fallbackIndex);
            List<String> keywords = new ArrayList<>(node.getLessonKeywords());
            if (keywords.isEmpty() && node.getVocabList() != null) {
                for (LessonVocabulary vocab : node.getVocabList()) {
                    if (vocab == null) {
                        continue;
                    }
                    String word = safeText(vocab.getWord());
                    if (!word.isEmpty()) {
                        keywords.add(word);
                    }
                }
            }

            items.add(new AdminJourneyLessonItem(
                    lessonId,
                    lessonId,
                    nonEmpty(safeText(node.getTitle()), lessonId),
                    nonEmpty(safeText(node.getMinLevel()), "A1"),
                    STATUS_PUBLISHED,
                    order,
                    Math.max(2, node.getMinExchanges()),
                    node.getVocabList() == null ? 0 : node.getVocabList().size(),
                    keywords
            ));
        }

        items.sort((left, right) -> {
            int byOrder = Integer.compare(left.getOrder(), right.getOrder());
            if (byOrder != 0) {
                return byOrder;
            }
            return safeText(left.getTitle()).compareToIgnoreCase(safeText(right.getTitle()));
        });
        return items;
    }

    private void seedJourneyCollectionIfMissing(@Nullable List<MapNodeItem> nodes) {
        if (nodes == null || nodes.isEmpty() || journeySeedWriteInFlight || hasFirestoreJourneyData) {
            return;
        }

        journeySeedWriteInFlight = true;
        WriteBatch batch = firestore.batch();

        int fallbackIndex = 0;
        for (MapNodeItem node : nodes) {
            if (node == null) {
                continue;
            }
            fallbackIndex++;

            String lessonId = nonEmpty(safeText(node.getNodeId()), String.format(Locale.US, "lesson_%02d", fallbackIndex));
            int order = extractOrderFromLessonId(lessonId, fallbackIndex);
            List<String> keywords = new ArrayList<>(node.getLessonKeywords());
            if (keywords.isEmpty() && node.getVocabList() != null) {
                for (LessonVocabulary vocab : node.getVocabList()) {
                    if (vocab == null) {
                        continue;
                    }
                    String keyword = safeText(vocab.getWord());
                    if (!keyword.isEmpty()) {
                        keywords.add(keyword);
                    }
                }
            }
            String domain = resolveJourneyDomain("", node.getTitle(), keywords, lessonId);

            Map<String, Object> payload = new HashMap<>();
            payload.put("lesson_id", lessonId);
            payload.put("title", nonEmpty(safeText(node.getTitle()), lessonId));
            payload.put("domain", domain);
            payload.put("emoji", nonEmpty(safeText(node.getEmoji()), pickEmojiForOrder(order)));
            payload.put("prompt_key", nonEmpty(safeText(node.getPromptKey()), buildPromptKey(lessonId, node.getTitle())));
            payload.put("role_description", nonEmpty(safeText(node.getRoleDescription()), getString(R.string.admin_content_journey_default_role)));
            payload.put("min_level", nonEmpty(safeText(node.getMinLevel()), "A1"));
            payload.put("min_exchanges", Math.max(2, node.getMinExchanges()));
            payload.put("order", order);
            payload.put("status", STATUS_PUBLISHED);
            payload.put("keywords", keywords);
            payload.put("vocabulary", serializeLessonVocabulary(node.getVocabList()));
            payload.put("flow_steps", serializeFlowSteps(node.getFlowSteps()));
            payload.put("updatedAt", FieldValue.serverTimestamp());
            payload.put("createdAt", FieldValue.serverTimestamp());

            batch.set(
                    firestore.collection(COLLECTION_MAP_LESSONS).document(lessonId),
                    payload,
                    SetOptions.merge()
            );
        }

        batch.commit()
                .addOnCompleteListener(task -> journeySeedWriteInFlight = false);
    }

    private List<Map<String, Object>> serializeLessonVocabulary(@Nullable List<LessonVocabulary> vocabList) {
        List<Map<String, Object>> values = new ArrayList<>();
        if (vocabList == null || vocabList.isEmpty()) {
            return values;
        }

        for (LessonVocabulary vocab : vocabList) {
            if (vocab == null) {
                continue;
            }
            String word = safeText(vocab.getWord());
            if (word.isEmpty()) {
                continue;
            }

            Map<String, Object> item = new HashMap<>();
            item.put("word", word);
            item.put("ipa", safeText(vocab.getIpa()));
            item.put("meaning", safeText(vocab.getMeaning()));
            item.put("example", safeText(vocab.getExample()));
            values.add(item);
        }
        return values;
    }

    private List<Map<String, Object>> serializeFlowSteps(@Nullable List<MapLessonFlowStep> flowSteps) {
        List<Map<String, Object>> values = new ArrayList<>();
        if (flowSteps == null || flowSteps.isEmpty()) {
            return values;
        }

        for (MapLessonFlowStep step : flowSteps) {
            if (step == null || safeText(step.getType()).isEmpty()) {
                continue;
            }

            Map<String, Object> item = new HashMap<>();
            item.put("type", safeText(step.getType()));
            item.put("content", safeText(step.getContent()));
            item.put("word", safeText(step.getWord()));
            item.put("ipa", safeText(step.getIpa()));
            item.put("meaning", safeText(step.getMeaning()));
            item.put("instruction", safeText(step.getInstruction()));
            item.put("question", safeText(step.getQuestion()));
            item.put("expected_keyword", safeText(step.getExpectedKeyword()));
            item.put("hint", safeText(step.getHint()));
            item.put("role_context", safeText(step.getRoleContext()));
            item.put("accepted_answers", new ArrayList<>(step.getAcceptedAnswers()));
            values.add(item);
        }
        return values;
    }

    private int extractOrderFromLessonId(@NonNull String lessonId, int fallbackIndex) {
        String digits = lessonId.replaceAll("[^0-9]", "");
        if (!digits.isEmpty()) {
            try {
                return Integer.parseInt(digits);
            } catch (Exception ignored) {
            }
        }
        return Math.max(1, fallbackIndex);
    }

    private int resolveJourneyOrder(@NonNull DocumentSnapshot doc, @NonNull String lessonId, int fallbackIndex) {
        int direct = safeInt(doc.get("order"));
        if (direct > 0) {
            return direct;
        }

        int legacyIndex = safeInt(doc.get("index"));
        if (legacyIndex > 0) {
            return legacyIndex;
        }

        String digits = lessonId.replaceAll("[^0-9]", "");
        if (!digits.isEmpty()) {
            try {
                return Integer.parseInt(digits);
            } catch (Exception ignored) {
            }
        }

        return Math.max(1, fallbackIndex);
    }

    private int getNextJourneyOrder() {
        int maxOrder = 0;
        for (AdminJourneyLessonItem item : allJourneyLessons) {
            maxOrder = Math.max(maxOrder, item.getOrder());
        }
        return maxOrder + 1;
    }

    private String generateLessonId(@NonNull String title, int order) {
        String slug = slugify(title);
        if (slug.isEmpty()) {
            slug = "journey";
        }

        String base = String.format(Locale.US, "lesson_%02d_%s", Math.max(1, order), slug);
        if (base.length() > 64) {
            base = base.substring(0, 64);
        }

        String candidate = base;
        int suffix = 1;
        while (containsLessonId(candidate)) {
            candidate = base + "_" + suffix;
            suffix++;
        }
        return candidate;
    }

    private boolean containsLessonId(@NonNull String lessonId) {
        for (AdminJourneyLessonItem item : allJourneyLessons) {
            if (lessonId.equalsIgnoreCase(item.getLessonId())
                    || lessonId.equalsIgnoreCase(item.getDocumentId())) {
                return true;
            }
        }
        return false;
    }

    private String buildPromptKey(@NonNull String lessonId, @NonNull String title) {
        String fromTitle = slugify(title);
        if (!fromTitle.isEmpty()) {
            return fromTitle;
        }
        return slugify(lessonId);
    }

    private String slugify(String raw) {
        return safeText(raw)
                .toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_", "")
                .replaceAll("_$", "");
    }

    private String pickEmojiForOrder(int order) {
        String[] options = {"👋", "😊", "👨‍👩‍👧", "☕", "💼", "👕", "🌤️", "🏠", "🎬", "👋"};
        int index = Math.max(0, order - 1) % options.length;
        return options[index];
    }

    private List<Map<String, Object>> buildDefaultFlowSteps(@NonNull String title,
                                                             @NonNull List<String> keywords,
                                                             @Nullable String roleDescription) {
        List<Map<String, Object>> flow = new ArrayList<>();

        String mainKeyword = keywords.isEmpty()
                ? "hello"
                : safeText(keywords.get(0)).toLowerCase(Locale.US);
        if (mainKeyword.isEmpty()) {
            mainKeyword = "hello";
        }

        String safeRole = nonEmpty(roleDescription, getString(R.string.admin_content_journey_default_role));

        Map<String, Object> intro = new HashMap<>();
        intro.put("type", "intro");
        intro.put("content", getString(R.string.admin_content_journey_intro_template, title));
        intro.put("role_context", safeRole);
        flow.add(intro);

        Map<String, Object> vocab = new HashMap<>();
        vocab.put("type", "vocabulary");
        vocab.put("word", mainKeyword);
        vocab.put("ipa", "");
        vocab.put("meaning", getString(R.string.admin_content_journey_meaning_template, mainKeyword));
        vocab.put("instruction", getString(R.string.admin_content_journey_instruction_template, mainKeyword));
        vocab.put("hint", getString(R.string.admin_content_journey_hint_template, mainKeyword));
        flow.add(vocab);

        Map<String, Object> quiz = new HashMap<>();
        quiz.put("type", "situational_quiz");
        quiz.put("question", getString(R.string.admin_content_journey_question_template, mainKeyword));
        quiz.put("expected_keyword", mainKeyword);
        quiz.put("hint", getString(R.string.admin_content_journey_quiz_hint_template));
        List<String> accepted = new ArrayList<>();
        accepted.add(mainKeyword);
        quiz.put("accepted_answers", accepted);
        flow.add(quiz);

        return flow;
    }

    private List<Map<String, Object>> buildDefaultVocabulary(@NonNull List<String> keywords) {
        List<String> source = new ArrayList<>(keywords);
        if (source.isEmpty()) {
            source.add("hello");
        }

        List<Map<String, Object>> vocabulary = new ArrayList<>();
        int limit = Math.min(3, source.size());
        for (int i = 0; i < limit; i++) {
            String word = safeText(source.get(i)).toLowerCase(Locale.US);
            if (word.isEmpty()) {
                continue;
            }
            Map<String, Object> item = new HashMap<>();
            item.put("word", word);
            item.put("ipa", "");
            item.put("meaning", getString(R.string.admin_content_journey_meaning_template, word));
            item.put("example", getString(R.string.admin_content_journey_example_template, word));
            vocabulary.add(item);
        }

        if (vocabulary.isEmpty()) {
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("word", "hello");
            fallback.put("ipa", "");
            fallback.put("meaning", getString(R.string.admin_content_journey_meaning_template, "hello"));
            fallback.put("example", getString(R.string.admin_content_journey_example_template, "hello"));
            vocabulary.add(fallback);
        }

        return vocabulary;
    }

    private List<String> parseKeywords(@Nullable String raw) {
        String normalized = safeText(raw);
        if (normalized.isEmpty()) {
            return new ArrayList<>();
        }

        String[] parts = normalized.split("[,|/\\n]");
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String part : parts) {
            String value = safeText(part);
            if (!value.isEmpty()) {
                unique.add(value);
            }
        }

        if (unique.isEmpty()) {
            unique.add(normalized);
        }

        return new ArrayList<>(unique);
    }

    private String joinKeywords(@Nullable List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String keyword : keywords) {
            String value = safeText(keyword);
            if (value.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(value);
        }
        return builder.toString();
    }

    private String normalizeJourneyStatus(String status) {
        String value = safeText(status).toLowerCase(Locale.US);
        if (value.isEmpty()) {
            return STATUS_PUBLISHED;
        }

        if ("active".equals(value) || "live".equals(value)) {
            return STATUS_PUBLISHED;
        }

        if ("published".equals(value)
                || "draft".equals(value)
                || "archived".equals(value)
                || "hidden".equals(value)
                || "disabled".equals(value)) {
            return value;
        }

        return STATUS_PUBLISHED;
    }

    private String humanizeFirestoreError(@Nullable Exception error) {
        if (error == null || error.getMessage() == null) {
            return getString(R.string.admin_content_action_failed_generic);
        }

        String message = error.getMessage().trim();
        String lower = message.toLowerCase(Locale.US);
        if (lower.contains("permission_denied") || lower.contains("insufficient permissions")) {
            return getString(R.string.admin_content_action_error_permission);
        }
        if (lower.contains("unavailable") || lower.contains("network")) {
            return getString(R.string.admin_content_action_error_network);
        }
        return message;
    }

    private List<String> toStringList(Object value) {
        if (!(value instanceof List)) {
            return new ArrayList<>();
        }

        List<String> values = new ArrayList<>();
        for (Object item : (List<?>) value) {
            String text = safeText(item);
            if (!text.isEmpty()) {
                values.add(text);
            }
        }
        return values;
    }

    private int listSize(Object value) {
        if (!(value instanceof List)) {
            return 0;
        }
        return ((List<?>) value).size();
    }

    private int safeInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(safeText(value));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private LinearLayout createDialogContainer() {
        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        int horizontalPadding = dpToPx(24);
        int verticalPadding = dpToPx(20);
        container.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding);
        container.setBackgroundResource(R.drawable.bg_admin_dialog);
        return container;
    }

    private void addDialogLabel(@NonNull LinearLayout container, String labelText) {
        TextView label = new TextView(requireContext());
        label.setText(labelText);
        label.setTextColor(ContextCompat.getColor(requireContext(), R.color.ef_text_primary));
        label.setTextSize(13);
        label.setTypeface(null, Typeface.BOLD);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = dpToPx(16);
        label.setLayoutParams(params);
        container.addView(label);
    }

    private EditText createDialogInput(String hint, String value, int inputType) {
        EditText input = new EditText(requireContext());
        input.setHint(hint);
        input.setText(value);
        input.setInputType(inputType);
        input.setBackgroundResource(R.drawable.bg_input_field);
        input.setTextColor(ContextCompat.getColor(requireContext(), R.color.ef_text_primary));
        input.setHintTextColor(ContextCompat.getColor(requireContext(), R.color.ef_text_tertiary));
        input.setTextSize(15);
        int horizontal = dpToPx(16);
        int vertical = dpToPx(12);
        input.setPadding(horizontal, vertical, horizontal, vertical);
        return input;
    }

    private CheckBox createDialogCheck(String label, boolean checked) {
        CheckBox checkBox = new CheckBox(requireContext());
        checkBox.setText(label);
        checkBox.setChecked(checked);
        checkBox.setTextColor(ContextCompat.getColor(requireContext(), R.color.ef_text_primary));
        checkBox.setTextSize(14);
        return checkBox;
    }

    private void addDialogField(@NonNull LinearLayout container, @NonNull View field) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = dpToPx(8);
        field.setLayoutParams(params);
        container.addView(field);
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private String normalizeDomain(String domain) {
        String normalized = safeText(domain);
        return normalized.isEmpty() ? "general" : normalized;
    }

    private String resolveJourneyDomain(@Nullable String rawDomain,
                                        @Nullable String title,
                                        @Nullable List<String> keywords,
                                        @Nullable String lessonId) {
        String providedDomain = safeText(rawDomain);
        if (!providedDomain.isEmpty()) {
            return providedDomain;
        }
        return inferJourneyDomain(title, keywords, lessonId);
    }

    private String inferJourneyDomain(@Nullable String title,
                                      @Nullable List<String> keywords,
                                      @Nullable String lessonId) {
        String searchableText = normalizeJourneyTextForMatching(
                nonEmpty(safeText(title), "") + " " + joinKeywords(keywords) + " " + safeText(lessonId)
        );

        if (containsAnyJourneyToken(searchableText,
                "am thuc", "food", "drink", "restaurant", "breakfast", "lunch", "dinner", "cook", "kitchen", "coffee", "tea", "beverage", "recipe", "snack", "fruit")) {
            return "Ẩm thực";
        }
        if (containsAnyJourneyToken(searchableText,
                "du lich", "travel", "trip", "tour", "airport", "hotel", "flight", "passport", "ticket", "journey", "station", "booking")) {
            return "Du lịch";
        }
        if (containsAnyJourneyToken(searchableText,
                "cong viec", "work", "job", "office", "career", "interview", "meeting", "colleague", "boss", "resume", "company", "project")) {
            return "Công việc";
        }
        if (containsAnyJourneyToken(searchableText,
                "suc khoe", "health", "doctor", "hospital", "medicine", "exercise", "workout", "fitness", "diet", "sleep", "stress")) {
            return "Sức khoẻ";
        }
        if (containsAnyJourneyToken(searchableText,
                "gia dinh", "family", "father", "mother", "parent", "child", "brother", "sister", "wedding", "marriage")) {
            return "Gia đình";
        }
        if (containsAnyJourneyToken(searchableText,
                "nha cua", "home", "house", "room", "bedroom", "living room", "furniture", "bathroom", "bed", "chair", "table")) {
            return "Nhà cửa";
        }
        if (containsAnyJourneyToken(searchableText,
                "cong nghe", "technology", "tech", "computer", "internet", "mobile", "phone", "software", "app", "digital", "code")) {
            return "Công nghệ";
        }
        if (containsAnyJourneyToken(searchableText,
                "kinh doanh", "business", "marketing", "sales", "startup", "customer", "revenue", "profit", "brand", "commerce", "negotiation")) {
            return "Kinh doanh";
        }
        if (containsAnyJourneyToken(searchableText,
                "moi truong", "environment", "nature", "weather", "climate", "green", "recycle", "earth", "pollution", "rain", "sunny")) {
            return "Môi trường";
        }
        if (containsAnyJourneyToken(searchableText,
                "nghe thuat", "art", "music", "painting", "movie", "film", "fashion", "design", "dance", "theater", "photography", "hobby")) {
            return "Nghệ thuật";
        }
        if (containsAnyJourneyToken(searchableText,
                "the thao", "sport", "football", "soccer", "basketball", "tennis", "gym", "run", "swim", "training")) {
            return "Thể thao";
        }
        if (containsAnyJourneyToken(searchableText,
                "phap luat", "law", "legal", "court", "police", "rights", "justice", "regulation")) {
            return "Pháp luật";
        }
        if (containsAnyJourneyToken(searchableText,
                "khoa hoc", "science", "research", "experiment", "lab", "physics", "chemistry", "biology", "innovation")) {
            return "Khoa học";
        }
        if (containsAnyJourneyToken(searchableText,
                "tai chinh", "finance", "money", "bank", "budget", "investment", "loan", "stock", "tax", "salary", "payment")) {
            return "Tài chính";
        }
        if (containsAnyJourneyToken(searchableText,
                "van hoa", "culture", "festival", "tradition", "history", "custom", "heritage", "community", "society")) {
            return "Văn hoá";
        }
        if (containsAnyJourneyToken(searchableText,
                "hoc tap", "study", "school", "class", "lesson", "exam", "homework", "teacher", "student", "learn", "education")) {
            return "Học tập";
        }

        return "Học tập";
    }

    private String normalizeJourneyTextForMatching(@Nullable String raw) {
        String normalized = Normalizer.normalize(safeText(raw), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.US);
        return normalized
                .replaceAll("[^a-z0-9]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private boolean containsAnyJourneyToken(@NonNull String source, String... tokens) {
        if (tokens == null || source.isEmpty()) {
            return false;
        }
        for (String token : tokens) {
            String normalizedToken = normalizeJourneyTextForMatching(token);
            if (!normalizedToken.isEmpty() && source.contains(normalizedToken)) {
                return true;
            }
        }
        return false;
    }

    private String safeText(Object text) {
        return text == null ? "" : String.valueOf(text).trim();
    }

    private String nonEmpty(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }
}
