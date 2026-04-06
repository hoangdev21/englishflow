package com.example.englishflow.admin;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.englishflow.R;
import com.example.englishflow.data.AppRepository;
import com.example.englishflow.data.DomainItem;
import com.example.englishflow.data.FirebaseSeeder;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Source;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class AdminLessonDetailActivity extends AppCompatActivity {

    private interface LessonWriteAction {
        void run();
    }

    public static final String EXTRA_DOCUMENT_ID = "extra_admin_lesson_document_id";
    public static final String EXTRA_LESSON_ID = "extra_admin_lesson_id";

    private static final String COLLECTION_MAP_LESSONS = "map_lessons";
    private static final String COLLECTION_USERS = "users";
    private static final String STATUS_PUBLISHED = "published";
    private static final String STATUS_AVAILABLE = "available";

    private static final List<String> DEFAULT_FLASHCARD_DOMAINS = Arrays.asList(
            "Ẩm thực",
            "Du lịch",
            "Công việc",
            "Sức khoẻ",
            "Học tập",
            "Nhà cửa",
            "Công nghệ",
            "Kinh doanh",
            "Môi trường",
            "Nghệ thuật",
            "Thể thao",
            "Pháp luật",
            "Khoa học",
            "Tài chính",
            "Gia đình",
            "Văn hoá"
    );

    private static class LessonTaskRef {
        final String documentId;
        final String lessonId;
        final String title;
        final String domain;
        final String minLevel;
        final String status;
        final int order;

        LessonTaskRef(@NonNull String documentId,
                      @NonNull String lessonId,
                      @NonNull String title,
                      @NonNull String domain,
                      @NonNull String minLevel,
                      @NonNull String status,
                      int order) {
            this.documentId = documentId;
            this.lessonId = lessonId;
            this.title = title;
            this.domain = domain;
            this.minLevel = minLevel;
            this.status = status;
            this.order = Math.max(1, order);
        }
    }

    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();
    private AppRepository repository;

    private String documentId = "";
    private String lessonId = "";

    private View topBar;
    private View scrollView;
    private ProgressBar loadingView;
    private TextView errorView;
    private TextView emojiView;
    private TextView titleView;
    private TextView lessonIdView;
    private TextView statusChip;
    private TextView levelChip;
    private TextView metaPrimaryView;
    private TextView metaSecondaryView;
    private TextView roleDescriptionView;
    private TextView promptKeyView;
    private AutoCompleteTextView domainFilterInput;
    private TextView taskCountView;
    private LinearLayout taskContainer;
    private TextView taskEmptyView;
    private LinearLayout keywordContainer;
    private TextView keywordEmptyView;
    private LinearLayout vocabularyContainer;
    private TextView vocabularyEmptyView;
    private LinearLayout flowContainer;
    private TextView flowEmptyView;
    private TextInputEditText inputEditTitle;
    private TextInputEditText inputEditLevel;
    private TextInputEditText inputEditOrder;
    private TextInputEditText inputEditDomain;
    private TextInputEditText inputEditStatus;
    private TextInputEditText inputEditMinExchanges;
    private TextInputEditText inputEditKeywords;
    private TextInputEditText inputEditPromptKey;
    private TextInputEditText inputEditRole;
    private MaterialButton saveChangesButton;
    private MaterialButton addVocabularyButton;
    private MaterialButton addFlowStepButton;
    private ArrayAdapter<String> domainFilterAdapter;

    private int currentOrder = 1;
    private int currentMinExchanges = 4;
    private String currentStatus = STATUS_PUBLISHED;
    private String currentMinLevel = "A1";
    private String currentLessonDomain = "";
    private String selectedDomain = "";
    private String allDomainLabel = "";
    private final List<LessonTaskRef> lessonTaskRefs = new ArrayList<>();
    private ListenerRegistration taskIndexRegistration;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_lesson_detail);

        repository = AppRepository.getInstance(this);
        allDomainLabel = getString(R.string.admin_lesson_detail_domain_all);

        documentId = safeText(getIntent().getStringExtra(EXTRA_DOCUMENT_ID));
        lessonId = safeText(getIntent().getStringExtra(EXTRA_LESSON_ID));

        bindViews();
        setupInsets();
        setupDomainFilter();
        setupActions();
        loadDomainAndTaskData();

        if (documentId.isEmpty() && lessonId.isEmpty()) {
            showLoadError(getString(R.string.admin_lesson_detail_not_found));
            return;
        }

        loadLessonDetail();
    }

    @Override
    protected void onDestroy() {
        stopTaskIndexListener();
        super.onDestroy();
    }

    private void bindViews() {
        topBar = findViewById(R.id.adminLessonDetailTopBar);
        scrollView = findViewById(R.id.adminLessonDetailScroll);
        loadingView = findViewById(R.id.adminLessonDetailLoading);
        errorView = findViewById(R.id.adminLessonDetailError);
        emojiView = findViewById(R.id.adminLessonDetailEmoji);
        titleView = findViewById(R.id.adminLessonDetailTitle);
        lessonIdView = findViewById(R.id.adminLessonDetailLessonId);
        statusChip = findViewById(R.id.adminLessonDetailStatusChip);
        levelChip = findViewById(R.id.adminLessonDetailLevelChip);
        metaPrimaryView = findViewById(R.id.adminLessonDetailMetaPrimary);
        metaSecondaryView = findViewById(R.id.adminLessonDetailMetaSecondary);
        roleDescriptionView = findViewById(R.id.adminLessonDetailRoleDescription);
        promptKeyView = findViewById(R.id.adminLessonDetailPromptKey);
        domainFilterInput = findViewById(R.id.adminLessonDetailDomainFilterInput);
        taskCountView = findViewById(R.id.adminLessonDetailTaskCount);
        taskContainer = findViewById(R.id.adminLessonDetailTaskContainer);
        taskEmptyView = findViewById(R.id.adminLessonDetailTaskEmpty);
        keywordContainer = findViewById(R.id.adminLessonDetailKeywordContainer);
        keywordEmptyView = findViewById(R.id.adminLessonDetailKeywordEmpty);
        vocabularyContainer = findViewById(R.id.adminLessonDetailVocabularyContainer);
        vocabularyEmptyView = findViewById(R.id.adminLessonDetailVocabularyEmpty);
        flowContainer = findViewById(R.id.adminLessonDetailFlowContainer);
        flowEmptyView = findViewById(R.id.adminLessonDetailFlowEmpty);
        inputEditTitle = findViewById(R.id.inputAdminLessonEditTitle);
        inputEditLevel = findViewById(R.id.inputAdminLessonEditLevel);
        inputEditOrder = findViewById(R.id.inputAdminLessonEditOrder);
        inputEditDomain = findViewById(R.id.inputAdminLessonEditDomain);
        inputEditStatus = findViewById(R.id.inputAdminLessonEditStatus);
        inputEditMinExchanges = findViewById(R.id.inputAdminLessonEditMinExchanges);
        inputEditKeywords = findViewById(R.id.inputAdminLessonEditKeywords);
        inputEditPromptKey = findViewById(R.id.inputAdminLessonEditPromptKey);
        inputEditRole = findViewById(R.id.inputAdminLessonEditRole);
        saveChangesButton = findViewById(R.id.btnAdminLessonDetailSaveChanges);
        addVocabularyButton = findViewById(R.id.btnAdminLessonDetailAddVocabulary);
        addFlowStepButton = findViewById(R.id.btnAdminLessonDetailAddFlowStep);
    }

    private void setupInsets() {
        if (topBar != null) {
            final int initialTop = topBar.getPaddingTop();
            ViewCompat.setOnApplyWindowInsetsListener(topBar, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(v.getPaddingLeft(), initialTop + systemBars.top, v.getPaddingRight(), v.getPaddingBottom());
                return insets;
            });
            ViewCompat.requestApplyInsets(topBar);
        }

        if (scrollView != null) {
            final int initialBottom = scrollView.getPaddingBottom();
            ViewCompat.setOnApplyWindowInsetsListener(scrollView, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), initialBottom + systemBars.bottom);
                return insets;
            });
            ViewCompat.requestApplyInsets(scrollView);
        }

        if (topBar != null && scrollView != null) {
            final int extraTopSpacing = Math.round(18f * getResources().getDisplayMetrics().density);
            topBar.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                    applyContentTopSpacing(scrollView, v.getHeight() + extraTopSpacing)
            );
            topBar.post(() -> applyContentTopSpacing(scrollView, topBar.getHeight() + extraTopSpacing));
        }
    }

    private void applyContentTopSpacing(@NonNull View contentView, int topMargin) {
        ViewGroup.LayoutParams params = contentView.getLayoutParams();
        if (!(params instanceof ViewGroup.MarginLayoutParams)) {
            return;
        }

        ViewGroup.MarginLayoutParams marginLayoutParams = (ViewGroup.MarginLayoutParams) params;
        if (marginLayoutParams.topMargin == topMargin) {
            return;
        }

        marginLayoutParams.topMargin = topMargin;
        contentView.setLayoutParams(marginLayoutParams);
    }

    private void setupActions() {
        View backButton = findViewById(R.id.btnBackAdminLessonDetail);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        if (saveChangesButton != null) {
            saveChangesButton.setOnClickListener(v -> saveLessonChanges());
        }

        if (addVocabularyButton != null) {
            addVocabularyButton.setOnClickListener(v -> addVocabularyRow(null));
        }

        if (addFlowStepButton != null) {
            addFlowStepButton.setOnClickListener(v -> addFlowStepRow(null));
        }
    }

    private void setupDomainFilter() {
        if (domainFilterInput == null) {
            return;
        }

        domainFilterAdapter = new ArrayAdapter<>(
                this,
                R.layout.item_admin_dropdown_option,
                new ArrayList<>()
        );
        domainFilterInput.setAdapter(domainFilterAdapter);
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
            renderTaskListForSelectedDomain();
        });
    }

    private void loadDomainAndTaskData() {
        List<String> fallbackDomains = new ArrayList<>(DEFAULT_FLASHCARD_DOMAINS);
        if (repository == null) {
            fetchLessonTaskIndex(fallbackDomains);
            return;
        }

        repository.getDomainsAsync(domains -> {
            List<String> domainNames = extractDomainNames(domains);
            if (domainNames.isEmpty()) {
                domainNames.addAll(fallbackDomains);
            }
            fetchLessonTaskIndex(domainNames);
        });
    }

    private List<String> extractDomainNames(@Nullable List<DomainItem> domains) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        if (domains != null) {
            for (DomainItem item : domains) {
                if (item == null) {
                    continue;
                }
                String normalized = normalizeDomainName(item.getName());
                if (!normalized.isEmpty()) {
                    names.add(normalized);
                }
            }
        }

        if (names.isEmpty()) {
            names.addAll(DEFAULT_FLASHCARD_DOMAINS);
        }
        return new ArrayList<>(names);
    }

    private void fetchLessonTaskIndex(@NonNull List<String> preferredDomains) {
        stopTaskIndexListener();
        taskIndexRegistration = firestore.collection(COLLECTION_MAP_LESSONS)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        if (!lessonTaskRefs.isEmpty()) {
                            return;
                        }
                        firestore.collection(COLLECTION_MAP_LESSONS)
                                .get(Source.CACHE)
                                .addOnSuccessListener(cacheSnapshot -> bindTaskIndex(cacheSnapshot, preferredDomains))
                                .addOnFailureListener(cacheError -> {
                                    lessonTaskRefs.clear();
                                    bindDomainOptions(preferredDomains);
                                    renderTaskListForSelectedDomain();
                                });
                        return;
                    }

                    bindTaskIndex(snapshot, preferredDomains);
                });
    }

    private void stopTaskIndexListener() {
        if (taskIndexRegistration != null) {
            taskIndexRegistration.remove();
            taskIndexRegistration = null;
        }
    }

    private void bindTaskIndex(@Nullable QuerySnapshot snapshot, @NonNull List<String> preferredDomains) {
        lessonTaskRefs.clear();
        if (snapshot != null) {
            for (DocumentSnapshot document : snapshot.getDocuments()) {
                LessonTaskRef task = toLessonTaskRef(document);
                if (task != null) {
                    lessonTaskRefs.add(task);
                }
            }
        }

        lessonTaskRefs.sort(Comparator
                .comparing((LessonTaskRef item) -> item.domain, String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(item -> item.order)
                .thenComparing(item -> item.title, String.CASE_INSENSITIVE_ORDER));

        bindDomainOptions(preferredDomains);
        renderTaskListForSelectedDomain();
    }

    @Nullable
    private LessonTaskRef toLessonTaskRef(@NonNull DocumentSnapshot document) {
        String docId = safeText(document.getId());
        String taskLessonId = nonEmpty(safeText(document.get("lesson_id")), docId);
        if (taskLessonId.isEmpty()) {
            return null;
        }

        String title = nonEmpty(safeText(document.get("title")), taskLessonId);
        List<String> keywords = toStringList(document.get("keywords"));
        String domain = normalizeDomainName(nonEmpty(
                safeText(document.get("domain")),
                inferDomainFromLesson(title, keywords, taskLessonId)
        ));
        String minLevel = nonEmpty(safeText(document.get("min_level")), "A1").toUpperCase(Locale.US);
        String status = normalizeJourneyStatus(nonEmpty(safeText(document.get("status")), STATUS_PUBLISHED));
        int order = Math.max(1, safeInt(document.get("order")));

        return new LessonTaskRef(docId, taskLessonId, title, domain, minLevel, status, order);
    }

    private void bindDomainOptions(@NonNull List<String> preferredDomains) {
        if (domainFilterAdapter == null || domainFilterInput == null) {
            return;
        }

        LinkedHashSet<String> options = new LinkedHashSet<>();
        options.add(allDomainLabel);

        for (String domain : preferredDomains) {
            String normalized = normalizeDomainName(domain);
            if (!normalized.isEmpty()) {
                options.add(normalized);
            }
        }

        for (LessonTaskRef task : lessonTaskRefs) {
            if (!task.domain.isEmpty()) {
                options.add(task.domain);
            }
        }

        List<String> optionList = new ArrayList<>(options);
        domainFilterAdapter.clear();
        domainFilterAdapter.addAll(optionList);
        domainFilterAdapter.notifyDataSetChanged();

        if (selectedDomain.isEmpty() && !currentLessonDomain.isEmpty()) {
            selectedDomain = currentLessonDomain;
        }
        if (!selectedDomain.isEmpty() && !options.contains(selectedDomain)) {
            selectedDomain = "";
        }

        String shownValue = selectedDomain.isEmpty() ? allDomainLabel : selectedDomain;
        domainFilterInput.setText(shownValue, false);
    }

    private void renderTaskListForSelectedDomain() {
        if (taskContainer == null || taskEmptyView == null) {
            return;
        }

        String activeDomain = selectedDomain.isEmpty() ? allDomainLabel : selectedDomain;
        List<LessonTaskRef> filtered = new ArrayList<>();
        for (LessonTaskRef item : lessonTaskRefs) {
            if (!selectedDomain.isEmpty() && !selectedDomain.equalsIgnoreCase(item.domain)) {
                continue;
            }
            filtered.add(item);
        }

        if (taskCountView != null) {
            taskCountView.setText(getString(
                    R.string.admin_lesson_detail_task_count,
                    activeDomain,
                    filtered.size()
            ));
        }

        taskContainer.removeAllViews();
        if (filtered.isEmpty()) {
            taskEmptyView.setVisibility(View.VISIBLE);
            return;
        }

        taskEmptyView.setVisibility(View.GONE);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (LessonTaskRef item : filtered) {
            View row = inflater.inflate(R.layout.item_admin_lesson_task, taskContainer, false);

            MaterialCardView card = row.findViewById(R.id.adminLessonTaskCard);
            TextView title = row.findViewById(R.id.adminLessonTaskTitle);
            TextView meta = row.findViewById(R.id.adminLessonTaskMeta);

            title.setText(item.title);
            meta.setText(getString(
                    R.string.admin_lesson_detail_task_meta,
                    item.lessonId,
                    item.minLevel,
                    item.order,
                    item.status.toUpperCase(Locale.US)
            ));

            boolean selected = isCurrentTask(item);
            card.setStrokeColor(ContextCompat.getColor(this, selected ? R.color.ef_primary : R.color.ef_outline));
            card.setCardBackgroundColor(ContextCompat.getColor(this, selected
                    ? R.color.ef_primary_container
                    : R.color.ef_surface));

            row.setOnClickListener(v -> openTask(item));
            taskContainer.addView(row);
        }
    }

    private boolean isCurrentTask(@NonNull LessonTaskRef task) {
        if (!documentId.isEmpty()) {
            return documentId.equalsIgnoreCase(task.documentId);
        }
        return !lessonId.isEmpty() && lessonId.equalsIgnoreCase(task.lessonId);
    }

    private void openTask(@NonNull LessonTaskRef task) {
        if (isCurrentTask(task)) {
            return;
        }

        documentId = task.documentId;
        lessonId = task.lessonId;
        selectedDomain = task.domain;
        currentLessonDomain = task.domain;
        if (domainFilterInput != null) {
            domainFilterInput.setText(selectedDomain, false);
        }
        renderTaskListForSelectedDomain();
        loadLessonDetail();
    }

    private void loadLessonDetail() {
        setLoading(true);

        if (!documentId.isEmpty()) {
            firestore.collection(COLLECTION_MAP_LESSONS)
                    .document(documentId)
                    .get(Source.SERVER)
                    .addOnSuccessListener(snapshot -> {
                        if (snapshot != null && snapshot.exists()) {
                            bindLesson(snapshot);
                            return;
                        }
                        fetchLessonByLessonId();
                    })
                    .addOnFailureListener(error -> {
                        if (!lessonId.isEmpty()) {
                            fetchLessonByLessonId();
                            return;
                        }
                        showLoadError(formatLoadError(error));
                    });
            return;
        }

        fetchLessonByLessonId();
    }

    private void fetchLessonByLessonId() {
        if (lessonId.isEmpty()) {
            showLoadError(getString(R.string.admin_lesson_detail_not_found));
            return;
        }

        firestore.collection(COLLECTION_MAP_LESSONS)
                .whereEqualTo("lesson_id", lessonId)
                .limit(1)
                .get(Source.SERVER)
                .addOnSuccessListener(this::bindLessonFromQuery)
                .addOnFailureListener(error -> showLoadError(formatLoadError(error)));
    }

    private void bindLessonFromQuery(@NonNull QuerySnapshot snapshot) {
        if (snapshot.isEmpty()) {
            showLoadError(getString(R.string.admin_lesson_detail_not_found));
            return;
        }

        DocumentSnapshot document = snapshot.getDocuments().get(0);
        bindLesson(document);
    }

    private void bindLesson(@NonNull DocumentSnapshot document) {
        setLoading(false);

        documentId = safeText(document.getId());
        lessonId = nonEmpty(safeText(document.get("lesson_id")), lessonId, documentId);

        String title = nonEmpty(safeText(document.get("title")), lessonId, documentId);
        String emoji = nonEmpty(safeText(document.get("emoji")), "📘");
        String status = normalizeJourneyStatus(safeText(document.get("status")));
        String minLevel = nonEmpty(safeText(document.get("min_level")), "A1").toUpperCase(Locale.US);
        int order = Math.max(1, safeInt(document.get("order")));
        int minExchanges = Math.max(2, safeInt(document.get("min_exchanges")));
        String roleDescription = safeText(document.get("role_description"));
        String promptKey = safeText(document.get("prompt_key"));

        List<String> keywords = toStringList(document.get("keywords"));
        String lessonDomain = normalizeDomainName(nonEmpty(
            safeText(document.get("domain")),
            inferDomainFromLesson(title, keywords, lessonId)
        ));
        List<Map<String, Object>> vocabulary = toMapList(document.get("vocabulary"));
        List<Map<String, Object>> flowSteps = toMapList(document.get("flow_steps"));

        currentOrder = order;
        currentMinExchanges = minExchanges;
        currentStatus = status;
        currentMinLevel = minLevel;
        currentLessonDomain = lessonDomain;

        if (emojiView != null) {
            emojiView.setText(emoji);
        }
        if (titleView != null) {
            titleView.setText(title);
        }
        if (lessonIdView != null) {
            lessonIdView.setText(lessonId);
        }

        bindStatusChip(status);
        bindLevelChip(minLevel);

        if (metaPrimaryView != null) {
            metaPrimaryView.setText(getString(
                    R.string.admin_lesson_detail_meta_primary,
                    lessonId,
                    minLevel,
                    order
            ));
        }
        if (metaSecondaryView != null) {
            metaSecondaryView.setText(getString(
                    R.string.admin_lesson_detail_meta_secondary,
                    vocabulary.size(),
                    minExchanges
            ));
        }

        if (roleDescriptionView != null) {
            roleDescriptionView.setText(nonEmpty(roleDescription, getString(R.string.admin_lesson_detail_role_empty)));
        }
        if (promptKeyView != null) {
            promptKeyView.setText(nonEmpty(promptKey, "-"));
        }

        bindEditableFields(
            title,
            minLevel,
            order,
            lessonDomain,
            status,
            minExchanges,
            joinKeywords(keywords),
            promptKey,
            nonEmpty(roleDescription, getString(R.string.admin_content_journey_default_role))
        );

        renderKeywords(keywords);
        renderVocabulary(vocabulary);
        renderFlowSteps(flowSteps);
        if (selectedDomain.isEmpty()) {
            selectedDomain = lessonDomain;
        }
        if (domainFilterInput != null && containsDomainOption(lessonDomain)) {
            selectedDomain = lessonDomain;
            domainFilterInput.setText(lessonDomain, false);
        }
        renderTaskListForSelectedDomain();

        if (errorView != null) {
            errorView.setVisibility(View.GONE);
        }
    }

    private void bindEditableFields(@NonNull String title,
                                    @NonNull String minLevel,
                                    int order,
                                    @NonNull String domain,
                                    @NonNull String status,
                                    int minExchanges,
                                    @NonNull String keywords,
                                    @NonNull String promptKey,
                                    @NonNull String roleDescription) {
        setEditTextValue(inputEditTitle, title);
        setEditTextValue(inputEditLevel, minLevel);
        setEditTextValue(inputEditOrder, String.valueOf(order));
        setEditTextValue(inputEditDomain, domain);
        setEditTextValue(inputEditStatus, status);
        setEditTextValue(inputEditMinExchanges, String.valueOf(minExchanges));
        setEditTextValue(inputEditKeywords, keywords);
        setEditTextValue(inputEditPromptKey, promptKey);
        setEditTextValue(inputEditRole, roleDescription);
    }

    private void setEditTextValue(@Nullable TextInputEditText input, @NonNull String value) {
        if (input == null) {
            return;
        }
        String currentValue = safeText(input.getText());
        if (currentValue.equals(value)) {
            return;
        }
        input.setText(value);
    }

    private void saveLessonChanges() {
        if (documentId.isEmpty()) {
            showLoadError(getString(R.string.admin_lesson_detail_not_found));
            return;
        }

        String title = safeText(inputEditTitle == null ? null : inputEditTitle.getText());
        if (title.isEmpty()) {
            showLoadError(getString(R.string.admin_content_required_fields));
            return;
        }

        String minLevel = nonEmpty(
                safeText(inputEditLevel == null ? null : inputEditLevel.getText()),
                currentMinLevel,
                "A1"
        ).toUpperCase(Locale.US);
        String domain = normalizeDomainName(nonEmpty(
            safeText(inputEditDomain == null ? null : inputEditDomain.getText()),
            currentLessonDomain,
            inferDomainFromLesson(title, parseKeywords(safeText(inputEditKeywords == null ? null : inputEditKeywords.getText())), lessonId)
        ));
        int order = parsePositiveInt(
                safeText(inputEditOrder == null ? null : inputEditOrder.getText()),
                currentOrder
        );
        String status = normalizeJourneyStatus(nonEmpty(
                safeText(inputEditStatus == null ? null : inputEditStatus.getText()),
                currentStatus,
            STATUS_PUBLISHED
        ));
        int minExchanges = Math.max(
                2,
                parsePositiveInt(
                        safeText(inputEditMinExchanges == null ? null : inputEditMinExchanges.getText()),
                        currentMinExchanges
                )
        );
        List<String> keywords = parseKeywords(safeText(
                inputEditKeywords == null ? null : inputEditKeywords.getText()
        ));

        List<Map<String, Object>> editedVocabulary = collectVocabularyFromEditors();
        if (editedVocabulary == null) {
            return;
        }
        List<Map<String, Object>> editedFlowSteps = collectFlowStepsFromEditors();

        String promptKey = safeText(inputEditPromptKey == null ? null : inputEditPromptKey.getText());
        if (promptKey.isEmpty()) {
            promptKey = buildPromptKey(lessonId, title);
        }

        String roleDescription = nonEmpty(
                safeText(inputEditRole == null ? null : inputEditRole.getText()),
                getString(R.string.admin_content_journey_default_role)
        );

        FirebaseUser actor = FirebaseAuth.getInstance().getCurrentUser();
        String actorUid = actor == null ? "" : safeText(actor.getUid());
        String actorEmail = actor == null ? "" : safeText(actor.getEmail());

        Map<String, Object> payload = new HashMap<>();
        payload.put("title", title);
        payload.put("min_level", minLevel);
        payload.put("domain", domain);
        payload.put("order", order);
        payload.put("status", status);
        payload.put("min_exchanges", minExchanges);
        payload.put("keywords", keywords);
        payload.put("prompt_key", promptKey);
        payload.put("role_description", roleDescription);
        payload.put("vocabulary", editedVocabulary);
        payload.put("flow_steps", editedFlowSteps);
        payload.put("updatedByUid", actorUid);
        payload.put("updatedByEmail", actorEmail);
        payload.put("updatedAt", FieldValue.serverTimestamp());

        setSavingState(true);
        ensureAdminForLessonWrite(() -> firestore.collection(COLLECTION_MAP_LESSONS)
                .document(documentId)
                .set(payload, SetOptions.merge())
                .addOnSuccessListener(unused -> {
                    setSavingState(false);
                    if (errorView != null) {
                        errorView.setVisibility(View.GONE);
                    }
                    Toast.makeText(this, R.string.admin_lesson_detail_updated, Toast.LENGTH_SHORT).show();
                    currentLessonDomain = domain;
                    selectedDomain = domain;
                    loadDomainAndTaskData();
                    loadLessonDetail();
                })
                .addOnFailureListener(error -> {
                    setSavingState(false);
                    showLoadError(formatLoadError(error));
                }), () -> setSavingState(false));
    }

    private void setSavingState(boolean saving) {
        if (saveChangesButton == null) {
            return;
        }

        saveChangesButton.setEnabled(!saving);
        saveChangesButton.setText(saving
                ? getString(R.string.admin_lesson_detail_saving)
                : getString(R.string.admin_lesson_detail_save_changes));
        saveChangesButton.setAlpha(saving ? 0.75f : 1f);

        if (addVocabularyButton != null) {
            addVocabularyButton.setEnabled(!saving);
            addVocabularyButton.setAlpha(saving ? 0.75f : 1f);
        }
        if (addFlowStepButton != null) {
            addFlowStepButton.setEnabled(!saving);
            addFlowStepButton.setAlpha(saving ? 0.75f : 1f);
        }
    }

    private void ensureAdminForLessonWrite(@NonNull LessonWriteAction action, @Nullable Runnable onDenied) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            showLoadError(getString(R.string.admin_content_action_error_auth_required));
            if (onDenied != null) {
                onDenied.run();
            }
            return;
        }

        String uid = safeText(currentUser.getUid());
        String email = safeText(currentUser.getEmail());
        if (uid.isEmpty()) {
            showLoadError(getString(R.string.admin_content_action_error_auth_required));
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
                        Map<String, Object> patch = new HashMap<>();
                        patch.put("role", "admin");
                        patch.put("updatedAt", FieldValue.serverTimestamp());
                        firestore.collection(COLLECTION_USERS)
                                .document(uid)
                                .set(patch, SetOptions.merge())
                                .addOnSuccessListener(unused -> action.run())
                                .addOnFailureListener(error -> {
                                    showLoadError(formatLoadError(error));
                                    if (onDenied != null) {
                                        onDenied.run();
                                    }
                                });
                        return;
                    }

                    showLoadError(getString(R.string.admin_content_action_error_not_admin_server));
                    if (onDenied != null) {
                        onDenied.run();
                    }
                })
                .addOnFailureListener(error -> {
                    showLoadError(formatLoadError(error));
                    if (onDenied != null) {
                        onDenied.run();
                    }
                });
    }

    private void bindStatusChip(@NonNull String normalizedStatus) {
        if (statusChip == null) {
            return;
        }

        statusChip.setText(normalizedStatus.toUpperCase(Locale.US));
        if (isVisibleStatus(normalizedStatus)) {
            statusChip.setBackgroundResource(R.drawable.bg_tag_blue);
            statusChip.setTextColor(ContextCompat.getColor(this, R.color.ef_card_blue_text));
            return;
        }

        statusChip.setBackgroundResource(R.drawable.bg_tag_amber);
        statusChip.setTextColor(ContextCompat.getColor(this, R.color.ef_warning_text));
    }

    private void bindLevelChip(@NonNull String minLevel) {
        if (levelChip == null) {
            return;
        }

        levelChip.setText(minLevel.toUpperCase(Locale.US));
        levelChip.setBackgroundResource(R.drawable.bg_tag_emerald);
        levelChip.setTextColor(ContextCompat.getColor(this, R.color.ef_primary));
    }

    private void renderKeywords(@NonNull List<String> keywords) {
        if (keywordContainer == null || keywordEmptyView == null) {
            return;
        }

        keywordContainer.removeAllViews();
        if (keywords.isEmpty()) {
            keywordEmptyView.setVisibility(View.VISIBLE);
            return;
        }

        keywordEmptyView.setVisibility(View.GONE);
        for (int i = 0; i < keywords.size(); i++) {
            String keyword = safeText(keywords.get(i));
            if (keyword.isEmpty()) {
                continue;
            }

            TextView chip = new TextView(this);
            chip.setText(keyword);
            chip.setTextColor(ContextCompat.getColor(this, R.color.ef_text_primary));
            chip.setTextSize(12f);
            chip.setBackgroundResource(R.drawable.bg_chip_soft);
            int horizontal = dpToPx(12);
            int vertical = dpToPx(6);
            chip.setPadding(horizontal, vertical, horizontal, vertical);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            if (i > 0) {
                params.setMarginStart(dpToPx(8));
            }
            chip.setLayoutParams(params);
            keywordContainer.addView(chip);
        }

        keywordEmptyView.setVisibility(keywordContainer.getChildCount() == 0 ? View.VISIBLE : View.GONE);
    }

    private void renderVocabulary(@NonNull List<Map<String, Object>> vocabulary) {
        if (vocabularyContainer == null || vocabularyEmptyView == null) {
            return;
        }

        vocabularyContainer.removeAllViews();
        if (vocabulary.isEmpty()) {
            updateVocabularyEditorState();
            return;
        }

        for (Map<String, Object> item : vocabulary) {
            addVocabularyRow(item);
        }
        updateVocabularyEditorState();
    }

    private void addVocabularyRow(@Nullable Map<String, Object> vocabularyItem) {
        if (vocabularyContainer == null) {
            return;
        }

        View row = LayoutInflater.from(this)
                .inflate(R.layout.item_admin_lesson_detail_vocabulary, vocabularyContainer, false);

        TextView indexView = row.findViewById(R.id.adminLessonDetailVocabIndex);
        TextInputEditText wordInput = row.findViewById(R.id.inputAdminLessonDetailVocabWord);
        TextInputEditText ipaInput = row.findViewById(R.id.inputAdminLessonDetailVocabIpa);
        TextInputEditText meaningInput = row.findViewById(R.id.inputAdminLessonDetailVocabMeaning);
        TextInputEditText exampleInput = row.findViewById(R.id.inputAdminLessonDetailVocabExample);
        MaterialButton removeButton = row.findViewById(R.id.btnAdminLessonDetailRemoveVocabulary);

        if (vocabularyItem != null) {
            setEditTextValue(wordInput, safeText(vocabularyItem.get("word")));
            setEditTextValue(ipaInput, safeText(vocabularyItem.get("ipa")));
            setEditTextValue(meaningInput, safeText(vocabularyItem.get("meaning")));
            setEditTextValue(exampleInput, safeText(vocabularyItem.get("example")));
        }

        removeButton.setOnClickListener(v -> {
            vocabularyContainer.removeView(row);
            updateVocabularyEditorState();
        });

        vocabularyContainer.addView(row);
        indexView.setText(getString(
                R.string.admin_lesson_detail_vocabulary_item,
                vocabularyContainer.getChildCount()
        ));
        updateVocabularyEditorState();
    }

    private void updateVocabularyEditorState() {
        if (vocabularyContainer != null) {
            for (int i = 0; i < vocabularyContainer.getChildCount(); i++) {
                View row = vocabularyContainer.getChildAt(i);
                TextView index = row.findViewById(R.id.adminLessonDetailVocabIndex);
                if (index != null) {
                    index.setText(getString(R.string.admin_lesson_detail_vocabulary_item, i + 1));
                }
            }
        }

        if (vocabularyEmptyView != null) {
            boolean isEmpty = vocabularyContainer == null || vocabularyContainer.getChildCount() == 0;
            vocabularyEmptyView.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        }
    }

    @Nullable
    private List<Map<String, Object>> collectVocabularyFromEditors() {
        List<Map<String, Object>> values = new ArrayList<>();
        if (vocabularyContainer == null) {
            return values;
        }

        for (int i = 0; i < vocabularyContainer.getChildCount(); i++) {
            View row = vocabularyContainer.getChildAt(i);
            TextInputEditText wordInput = row.findViewById(R.id.inputAdminLessonDetailVocabWord);
            TextInputEditText ipaInput = row.findViewById(R.id.inputAdminLessonDetailVocabIpa);
            TextInputEditText meaningInput = row.findViewById(R.id.inputAdminLessonDetailVocabMeaning);
            TextInputEditText exampleInput = row.findViewById(R.id.inputAdminLessonDetailVocabExample);

            String word = safeText(wordInput == null ? null : wordInput.getText());
            String ipa = safeText(ipaInput == null ? null : ipaInput.getText());
            String meaning = safeText(meaningInput == null ? null : meaningInput.getText());
            String example = safeText(exampleInput == null ? null : exampleInput.getText());

            if (word.isEmpty() && ipa.isEmpty() && meaning.isEmpty() && example.isEmpty()) {
                continue;
            }

            if (word.isEmpty()) {
                showLoadError(getString(R.string.admin_lesson_detail_vocab_word_required, i + 1));
                return null;
            }

            Map<String, Object> item = new HashMap<>();
            item.put("word", word);
            item.put("ipa", ipa);
            item.put("meaning", meaning);
            item.put("example", example);
            values.add(item);
        }
        return values;
    }

    private void renderFlowSteps(@NonNull List<Map<String, Object>> steps) {
        if (flowContainer == null || flowEmptyView == null) {
            return;
        }

        flowContainer.removeAllViews();
        if (steps.isEmpty()) {
            updateFlowEditorState();
            return;
        }

        for (Map<String, Object> step : steps) {
            addFlowStepRow(step);
        }
        updateFlowEditorState();
    }

    private void addFlowStepRow(@Nullable Map<String, Object> flowStep) {
        if (flowContainer == null) {
            return;
        }

        View row = LayoutInflater.from(this)
                .inflate(R.layout.item_admin_lesson_detail_flow_step, flowContainer, false);

        TextView indexView = row.findViewById(R.id.adminLessonDetailFlowIndex);
        TextInputEditText typeInput = row.findViewById(R.id.inputAdminLessonDetailFlowType);
        TextInputEditText contentInput = row.findViewById(R.id.inputAdminLessonDetailFlowContent);
        TextInputEditText questionInput = row.findViewById(R.id.inputAdminLessonDetailFlowQuestion);
        TextInputEditText instructionInput = row.findViewById(R.id.inputAdminLessonDetailFlowInstruction);
        TextInputEditText wordInput = row.findViewById(R.id.inputAdminLessonDetailFlowWord);
        TextInputEditText ipaInput = row.findViewById(R.id.inputAdminLessonDetailFlowIpa);
        TextInputEditText meaningInput = row.findViewById(R.id.inputAdminLessonDetailFlowMeaning);
        TextInputEditText expectedKeywordInput = row.findViewById(R.id.inputAdminLessonDetailFlowExpectedKeyword);
        TextInputEditText hintInput = row.findViewById(R.id.inputAdminLessonDetailFlowHint);
        TextInputEditText roleContextInput = row.findViewById(R.id.inputAdminLessonDetailFlowRoleContext);
        TextInputEditText acceptedAnswersInput = row.findViewById(R.id.inputAdminLessonDetailFlowAcceptedAnswers);
        MaterialButton removeButton = row.findViewById(R.id.btnAdminLessonDetailRemoveFlowStep);

        if (flowStep != null) {
            setEditTextValue(typeInput, safeText(flowStep.get("type")));
            setEditTextValue(contentInput, safeText(flowStep.get("content")));
            setEditTextValue(questionInput, safeText(flowStep.get("question")));
            setEditTextValue(instructionInput, safeText(flowStep.get("instruction")));
            setEditTextValue(wordInput, safeText(flowStep.get("word")));
            setEditTextValue(ipaInput, safeText(flowStep.get("ipa")));
            setEditTextValue(meaningInput, safeText(flowStep.get("meaning")));
            setEditTextValue(expectedKeywordInput, safeText(flowStep.get("expected_keyword")));
            setEditTextValue(hintInput, safeText(flowStep.get("hint")));
            setEditTextValue(roleContextInput, safeText(flowStep.get("role_context")));
            setEditTextValue(acceptedAnswersInput, joinKeywords(toStringList(flowStep.get("accepted_answers"))));
        }

        removeButton.setOnClickListener(v -> {
            flowContainer.removeView(row);
            updateFlowEditorState();
        });

        flowContainer.addView(row);
        indexView.setText(getString(R.string.admin_lesson_detail_flow_item, flowContainer.getChildCount()));
        updateFlowEditorState();
    }

    private void updateFlowEditorState() {
        if (flowContainer != null) {
            for (int i = 0; i < flowContainer.getChildCount(); i++) {
                View row = flowContainer.getChildAt(i);
                TextView index = row.findViewById(R.id.adminLessonDetailFlowIndex);
                if (index != null) {
                    index.setText(getString(R.string.admin_lesson_detail_flow_item, i + 1));
                }
            }
        }

        if (flowEmptyView != null) {
            boolean isEmpty = flowContainer == null || flowContainer.getChildCount() == 0;
            flowEmptyView.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        }
    }

    private List<Map<String, Object>> collectFlowStepsFromEditors() {
        List<Map<String, Object>> values = new ArrayList<>();
        if (flowContainer == null) {
            return values;
        }

        for (int i = 0; i < flowContainer.getChildCount(); i++) {
            View row = flowContainer.getChildAt(i);
            String type = safeText(((TextInputEditText) row.findViewById(R.id.inputAdminLessonDetailFlowType)).getText());
            String content = safeText(((TextInputEditText) row.findViewById(R.id.inputAdminLessonDetailFlowContent)).getText());
            String question = safeText(((TextInputEditText) row.findViewById(R.id.inputAdminLessonDetailFlowQuestion)).getText());
            String instruction = safeText(((TextInputEditText) row.findViewById(R.id.inputAdminLessonDetailFlowInstruction)).getText());
            String word = safeText(((TextInputEditText) row.findViewById(R.id.inputAdminLessonDetailFlowWord)).getText());
            String ipa = safeText(((TextInputEditText) row.findViewById(R.id.inputAdminLessonDetailFlowIpa)).getText());
            String meaning = safeText(((TextInputEditText) row.findViewById(R.id.inputAdminLessonDetailFlowMeaning)).getText());
            String expectedKeyword = safeText(((TextInputEditText) row.findViewById(R.id.inputAdminLessonDetailFlowExpectedKeyword)).getText());
            String hint = safeText(((TextInputEditText) row.findViewById(R.id.inputAdminLessonDetailFlowHint)).getText());
            String roleContext = safeText(((TextInputEditText) row.findViewById(R.id.inputAdminLessonDetailFlowRoleContext)).getText());
            String acceptedAnswersRaw = safeText(((TextInputEditText) row.findViewById(R.id.inputAdminLessonDetailFlowAcceptedAnswers)).getText());

            if (isAllBlank(
                    type,
                    content,
                    question,
                    instruction,
                    word,
                    ipa,
                    meaning,
                    expectedKeyword,
                    hint,
                    roleContext,
                    acceptedAnswersRaw
            )) {
                continue;
            }

            Map<String, Object> item = new HashMap<>();
            item.put("type", nonEmpty(type, "intro").toLowerCase(Locale.US));
            item.put("content", content);
            item.put("word", word);
            item.put("ipa", ipa);
            item.put("meaning", meaning);
            item.put("instruction", instruction);
            item.put("question", question);
            item.put("expected_keyword", expectedKeyword);
            item.put("hint", hint);
            item.put("role_context", roleContext);
            item.put("accepted_answers", parseKeywords(acceptedAnswersRaw));
            values.add(item);
        }

        return values;
    }

    private boolean isAllBlank(String... values) {
        if (values == null) {
            return true;
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private boolean containsDomainOption(@Nullable String domain) {
        if (domain == null || domain.trim().isEmpty() || domainFilterAdapter == null) {
            return false;
        }

        for (int i = 0; i < domainFilterAdapter.getCount(); i++) {
            String option = domainFilterAdapter.getItem(i);
            if (domain.equalsIgnoreCase(safeText(option))) {
                return true;
            }
        }
        return false;
    }

    private void showLoadError(@NonNull String message) {
        setLoading(false);
        if (errorView != null) {
            errorView.setText(message);
            errorView.setVisibility(View.VISIBLE);
        }
    }

    private void setLoading(boolean loading) {
        if (loadingView != null) {
            loadingView.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        if (errorView != null && loading) {
            errorView.setVisibility(View.GONE);
        }
    }

    private String formatLoadError(@Nullable Exception error) {
        if (error == null || error.getMessage() == null) {
            return getString(R.string.admin_lesson_detail_load_failed);
        }

        String message = error.getMessage().trim();
        if (message.isEmpty()) {
            return getString(R.string.admin_lesson_detail_load_failed);
        }

        String lower = message.toLowerCase(Locale.US);
        if (lower.contains("permission_denied") || lower.contains("insufficient permissions")) {
            return getString(R.string.admin_content_action_error_permission);
        }
        if (lower.contains("unavailable") || lower.contains("network")) {
            return getString(R.string.admin_content_action_error_network);
        }
        return getString(R.string.admin_content_action_failed_with_reason, message);
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private boolean isVisibleStatus(@NonNull String normalizedStatus) {
        return !("draft".equals(normalizedStatus)
                || "archived".equals(normalizedStatus)
                || "hidden".equals(normalizedStatus)
                || "disabled".equals(normalizedStatus));
    }

    private String normalizeJourneyStatus(String status) {
        String value = safeText(status).toLowerCase(Locale.US);
        if (value.isEmpty()) {
            return STATUS_PUBLISHED;
        }

        if ("active".equals(value) || "live".equals(value)) {
            return STATUS_PUBLISHED;
        }

        if (STATUS_PUBLISHED.equals(value)
                || STATUS_AVAILABLE.equals(value)
                || "draft".equals(value)
                || "archived".equals(value)
                || "hidden".equals(value)
                || "disabled".equals(value)) {
            return value;
        }

        return STATUS_PUBLISHED;
    }

    private String normalizeDomainName(@Nullable String rawDomain) {
        String normalized = safeText(rawDomain);
        if (normalized.isEmpty()) {
            return "Học tập";
        }

        for (String domain : DEFAULT_FLASHCARD_DOMAINS) {
            if (domain.equalsIgnoreCase(normalized)) {
                return domain;
            }
        }
        return normalized;
    }

    private String inferDomainFromLesson(@Nullable String title,
                                         @Nullable List<String> keywords,
                                         @Nullable String lessonId) {
        String combined = normalizeTextForMatch(
                safeText(title) + " " + joinKeywords(keywords) + " " + safeText(lessonId)
        );

        if (containsAnyToken(combined,
                "am thuc", "food", "drink", "restaurant", "breakfast", "lunch", "dinner", "cook", "kitchen", "coffee", "tea", "beverage", "recipe", "snack", "fruit")) {
            return "Ẩm thực";
        }
        if (containsAnyToken(combined,
                "du lich", "travel", "trip", "tour", "airport", "hotel", "flight", "passport", "ticket", "journey", "station", "booking")) {
            return "Du lịch";
        }
        if (containsAnyToken(combined,
                "cong viec", "work", "job", "office", "career", "interview", "meeting", "colleague", "boss", "resume", "company", "project")) {
            return "Công việc";
        }
        if (containsAnyToken(combined,
                "suc khoe", "health", "doctor", "hospital", "medicine", "exercise", "workout", "fitness", "diet", "sleep", "stress")) {
            return "Sức khoẻ";
        }
        if (containsAnyToken(combined,
                "gia dinh", "family", "father", "mother", "parent", "child", "brother", "sister", "wedding", "marriage")) {
            return "Gia đình";
        }
        if (containsAnyToken(combined,
                "nha cua", "home", "house", "room", "bedroom", "living room", "furniture", "bathroom", "bed", "chair", "table")) {
            return "Nhà cửa";
        }
        if (containsAnyToken(combined,
                "cong nghe", "technology", "tech", "computer", "internet", "mobile", "phone", "software", "app", "digital", "code")) {
            return "Công nghệ";
        }
        if (containsAnyToken(combined,
                "kinh doanh", "business", "marketing", "sales", "startup", "customer", "revenue", "profit", "brand", "commerce", "negotiation")) {
            return "Kinh doanh";
        }
        if (containsAnyToken(combined,
                "moi truong", "environment", "nature", "weather", "climate", "green", "recycle", "earth", "pollution", "rain", "sunny")) {
            return "Môi trường";
        }
        if (containsAnyToken(combined,
                "nghe thuat", "art", "music", "painting", "movie", "film", "fashion", "design", "dance", "theater", "photography", "hobby")) {
            return "Nghệ thuật";
        }
        if (containsAnyToken(combined,
                "the thao", "sport", "football", "soccer", "basketball", "tennis", "gym", "run", "swim", "training")) {
            return "Thể thao";
        }
        if (containsAnyToken(combined,
                "phap luat", "law", "legal", "court", "police", "rights", "justice", "regulation")) {
            return "Pháp luật";
        }
        if (containsAnyToken(combined,
                "khoa hoc", "science", "research", "experiment", "lab", "physics", "chemistry", "biology", "innovation")) {
            return "Khoa học";
        }
        if (containsAnyToken(combined,
                "tai chinh", "finance", "money", "bank", "budget", "investment", "loan", "stock", "tax", "salary", "payment")) {
            return "Tài chính";
        }
        if (containsAnyToken(combined,
                "van hoa", "culture", "festival", "tradition", "history", "custom", "heritage", "community", "society")) {
            return "Văn hoá";
        }
        if (containsAnyToken(combined,
                "hoc tap", "study", "school", "class", "lesson", "exam", "homework", "teacher", "student", "learn", "education")) {
            return "Học tập";
        }
        return "Học tập";
    }

    private boolean containsAnyToken(@NonNull String source, String... tokens) {
        if (tokens == null || source.isEmpty()) {
            return false;
        }
        for (String token : tokens) {
            String normalized = normalizeTextForMatch(token);
            if (!normalized.isEmpty() && source.contains(normalized)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeTextForMatch(@Nullable String raw) {
        String normalized = Normalizer.normalize(safeText(raw), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.US);
        return normalized
                .replaceAll("[^a-z0-9]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
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

    private List<Map<String, Object>> toMapList(Object value) {
        if (!(value instanceof List)) {
            return new ArrayList<>();
        }

        List<Map<String, Object>> values = new ArrayList<>();
        for (Object item : (List<?>) value) {
            if (!(item instanceof Map)) {
                continue;
            }

            Map<?, ?> rawMap = (Map<?, ?>) item;
            Map<String, Object> normalized = new HashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                String key = safeText(entry.getKey());
                if (!key.isEmpty()) {
                    normalized.put(key, entry.getValue());
                }
            }
            values.add(normalized);
        }
        return values;
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

    private int parsePositiveInt(@Nullable String raw, int fallback) {
        try {
            int parsed = Integer.parseInt(nonEmpty(raw, String.valueOf(fallback)));
            return Math.max(1, parsed);
        } catch (Exception ignored) {
            return Math.max(1, fallback);
        }
    }

    private List<String> parseKeywords(@Nullable String raw) {
        String normalized = safeText(raw);
        if (normalized.isEmpty()) {
            return new ArrayList<>();
        }

        String[] parts = normalized.split("[,|/\\n]");
        List<String> values = new ArrayList<>();
        for (String part : parts) {
            String keyword = safeText(part);
            if (!keyword.isEmpty() && !values.contains(keyword)) {
                values.add(keyword);
            }
        }

        if (values.isEmpty()) {
            values.add(normalized);
        }
        return values;
    }

    private String joinKeywords(@Nullable List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return "";
        }
        return TextUtils.join(", ", keywords);
    }

    private String buildPromptKey(@NonNull String lessonId, @NonNull String title) {
        String fromTitle = slugify(title);
        if (!fromTitle.isEmpty()) {
            return fromTitle;
        }
        return slugify(lessonId);
    }

    private String slugify(@Nullable String raw) {
        return safeText(raw)
                .toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_", "")
                .replaceAll("_$", "");
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
