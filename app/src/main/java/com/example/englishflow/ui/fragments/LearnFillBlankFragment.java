package com.example.englishflow.ui.fragments;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.englishflow.R;
import com.example.englishflow.data.AppRepository;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class LearnFillBlankFragment extends Fragment {

    private static final String ARG_PREFERRED_TOPIC = "arg_preferred_topic";
    private static final int XP_PER_CORRECT_ANSWER = 10;
    private static final int COMBO_STREAK_TARGET = 5;
    private static final int COMBO_BONUS_XP = 20;
    private static final int TOPIC_BOOTSTRAP_MAX_RETRY = 3;
    private static final int QUESTION_BOOTSTRAP_MAX_RETRY = 2;
    private static final long BOOTSTRAP_RETRY_DELAY_MS = 280L;

    private AppRepository repository;

    private View loadingView;
    private View emptyStateView;
    private ScrollView questionScroll;
    private TextView emptyMessageText;

    private TextView topicTitleText;
    private TextView topicMetaText;
    private TextView progressText;
    private TextView sentenceText;
    private TextView sentenceViText;
    private TextView meaningText;
    private TextView hintText;
    private TextView sessionXpText;
    private TextView comboIndicatorText;
    private HorizontalScrollView slotsScroll;
    private LinearLayout slotsContainer;
    private View rootContainer;
    private View slotsInputCard;
    private View hintContainer;


    private EditText answerInput;

    private MaterialButton changeTopicButton;
    private MaterialButton hintFirstButton;
    private MaterialButton hintLastButton;
    private MaterialButton checkButton;
    private MaterialButton nextButton;
    private MaterialButton goFlashcardButton;
    private MaterialButton solveAnswerButton;

    private final List<AppRepository.FillBlankTopicItem> availableTopics = new ArrayList<>();
    private final List<AppRepository.FillBlankQuestionItem> questions = new ArrayList<>();
    private final Set<Integer> rewardedQuestions = new HashSet<>();
    private final List<TextView> slotViews = new ArrayList<>();

    private int currentQuestionIndex = 0;
    private int sessionXp = 0;
    private int consecutiveCorrectStreak = 0;
    private int sessionBestCombo = 0;
    private int sessionCorrectCount = 0;
    private int sessionWrongAttempts = 0;
    private boolean hasRecordedSessionCompletion = false;
    private boolean isCurrentAnswered = false;
    private boolean isHintFirstShown = false;
    private boolean isHintLastShown = false;
    private String currentTopic = "";
    private String expectedAnswerPattern = "";

    private MediaPlayer feedbackPlayer;
    private boolean autoAdvanceScheduled = false;
    private final Runnable autoAdvanceRunnable = this::runAutoAdvanceIfReady;

    public static LearnFillBlankFragment newInstance(@Nullable String preferredTopic) {
        LearnFillBlankFragment fragment = new LearnFillBlankFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PREFERRED_TOPIC, preferredTopic);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_learn_fill_blank, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repository = AppRepository.getInstance(requireContext());

        bindViews(view);
        applyStatusBarInsets(view);
        setupInteractions(view);
        refreshSessionXpText();
        refreshComboIndicator();

        String preferredTopic = getArguments() != null
                ? getArguments().getString(ARG_PREFERRED_TOPIC)
                : null;
        loadTopics(preferredTopic);
    }

    @Override
    public void onDestroyView() {
        cancelAutoAdvance();
        releaseFeedbackPlayer();
        super.onDestroyView();
    }

    private void bindViews(@NonNull View view) {
        loadingView = view.findViewById(R.id.fillBlankLoading);
        emptyStateView = view.findViewById(R.id.fillBlankEmptyState);
        questionScroll = view.findViewById(R.id.fillBlankScroll);
        emptyMessageText = view.findViewById(R.id.fillBlankEmptyMessage);

        topicTitleText = view.findViewById(R.id.fillBlankTopicTitle);
        topicMetaText = view.findViewById(R.id.fillBlankTopicMeta);
        progressText = view.findViewById(R.id.fillBlankProgress);
        sentenceText = view.findViewById(R.id.fillBlankSentence);
        sentenceViText = view.findViewById(R.id.fillBlankSentenceVi);
        meaningText = view.findViewById(R.id.fillBlankMeaning);
        hintText = view.findViewById(R.id.fillBlankHint);
        sessionXpText = view.findViewById(R.id.fillBlankSessionXp);
        comboIndicatorText = view.findViewById(R.id.fillBlankComboIndicator);
        slotsScroll = view.findViewById(R.id.fillBlankSlotsScroll);
        slotsContainer = view.findViewById(R.id.fillBlankSlotsContainer);
        rootContainer = view.findViewById(R.id.fillBlankRootContainer);
        slotsInputCard = view.findViewById(R.id.fillBlankSlotsInputCard);
        hintContainer = view.findViewById(R.id.fillBlankHintContainer);


        answerInput = view.findViewById(R.id.fillBlankAnswerInput);

        changeTopicButton = view.findViewById(R.id.btnChangeFillBlankTopic);
        hintFirstButton = view.findViewById(R.id.btnHintFirst);
        hintLastButton = view.findViewById(R.id.btnHintLast);
        checkButton = view.findViewById(R.id.btnCheckFillBlank);
        nextButton = view.findViewById(R.id.btnNextFillBlank);
        goFlashcardButton = view.findViewById(R.id.btnFillBlankGoFlashcard);
        solveAnswerButton = view.findViewById(R.id.btnSolveAnswer);
    }

    private void setupInteractions(@NonNull View view) {
        view.findViewById(R.id.btnBackFillBlank).setOnClickListener(v ->
                getParentFragmentManager().popBackStack());

        if (changeTopicButton != null) {
            changeTopicButton.setOnClickListener(v -> showTopicPicker());
        }

        if (rootContainer != null) {
            rootContainer.setOnClickListener(v -> dismissKeyboardAndClearFocus());
        }

        View.OnClickListener focusSlotInput = v -> requestKeyboardForSlots();
        if (slotsInputCard != null) {
            slotsInputCard.setOnClickListener(focusSlotInput);
        }
        if (slotsScroll != null) {
            slotsScroll.setOnClickListener(focusSlotInput);
        }
        if (slotsContainer != null) {
            slotsContainer.setOnClickListener(focusSlotInput);
        }

        if (hintFirstButton != null) {
            hintFirstButton.setOnClickListener(v -> {
                isHintFirstShown = true;
                updateHintText();
            });
        }

        if (hintLastButton != null) {
            hintLastButton.setOnClickListener(v -> {
                isHintLastShown = true;
                updateHintText();
            });
        }

        if (checkButton != null) {
            checkButton.setOnClickListener(v -> checkCurrentAnswer());
        }

        if (nextButton != null) {
            nextButton.setOnClickListener(v -> moveToNextQuestion());
        }

        if (answerInput != null) {
            answerInput.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    updateAnswerSlots(s != null ? s.toString() : "");
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            });

            answerInput.setOnEditorActionListener((v, actionId, event) -> {
                boolean isImeDone = actionId == EditorInfo.IME_ACTION_DONE;
                boolean isEnter = event != null
                        && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                        && event.getAction() == KeyEvent.ACTION_DOWN;
                if (isImeDone || isEnter) {
                    dismissKeyboardAndClearFocus();
                    checkCurrentAnswer();
                    return true;
                }
                return false;
            });
        }

        if (goFlashcardButton != null) {
            goFlashcardButton.setOnClickListener(v -> openDomainsFlow());
        }

        if (solveAnswerButton != null) {
            solveAnswerButton.setOnClickListener(v -> showSolveConfirmationDialog());
        }

        attachRaisedButtonMotion(changeTopicButton);
        attachRaisedButtonMotion(hintFirstButton);
        attachRaisedButtonMotion(hintLastButton);
        attachRaisedButtonMotion(checkButton);
        attachRaisedButtonMotion(nextButton);
        attachRaisedButtonMotion(goFlashcardButton);
        attachRaisedButtonMotion(solveAnswerButton);
        attachRaisedButtonMotion(slotsInputCard);
    }

    private void loadTopics(@Nullable String preferredTopic) {
        loadTopics(preferredTopic, 0);
    }

    private void loadTopics(@Nullable String preferredTopic, int attempt) {
        showLoading(true);
        repository.getFillBlankTopicsAsync(result -> {
            if (!isAdded()) {
                return;
            }

            availableTopics.clear();
            if (result != null) {
                availableTopics.addAll(result);
            }

            if (availableTopics.isEmpty()) {
                if (attempt < TOPIC_BOOTSTRAP_MAX_RETRY) {
                    postBootstrapRetry(() -> loadTopics(preferredTopic, attempt + 1));
                    return;
                }
                showEmptyState(getString(R.string.fill_blank_empty_message));
                return;
            }

            AppRepository.FillBlankTopicItem selected = findTopicByName(preferredTopic);
            if (selected == null) {
                selected = availableTopics.get(0);
            }
            loadQuestionsForTopic(selected.topic, 0);
        });
    }

    @Nullable
    private AppRepository.FillBlankTopicItem findTopicByName(@Nullable String topicName) {
        if (topicName == null || topicName.trim().isEmpty()) {
            return null;
        }
        for (AppRepository.FillBlankTopicItem item : availableTopics) {
            if (item != null && topicName.equalsIgnoreCase(item.topic)) {
                return item;
            }
        }
        return null;
    }

    private void loadQuestionsForTopic(@NonNull String topic) {
        loadQuestionsForTopic(topic, 0);
    }

    private void loadQuestionsForTopic(@NonNull String topic, int attempt) {
        if (attempt == 0) {
            currentTopic = topic;
            sessionXp = 0;
            currentQuestionIndex = 0;
            consecutiveCorrectStreak = 0;
            sessionBestCombo = 0;
            sessionCorrectCount = 0;
            sessionWrongAttempts = 0;
            hasRecordedSessionCompletion = false;
            cancelAutoAdvance();
            rewardedQuestions.clear();
            refreshSessionXpText();
            refreshComboIndicator();
        }

        AppRepository.FillBlankTopicItem selectedTopicSnapshot = findTopicByName(topic);
        setTopicBadgeText(topic);
        if (topicMetaText != null && selectedTopicSnapshot != null) {
            topicMetaText.setText(getString(
                    R.string.fill_blank_topic_meta_format,
                    selectedTopicSnapshot.domain,
                    selectedTopicSnapshot.questionCount
            ));
        }

        showLoading(true);

        repository.getFillBlankQuestionsAsync(topic, result -> {
            if (!isAdded()) {
                return;
            }

            questions.clear();
            if (result != null) {
                questions.addAll(result);
            }

            AppRepository.FillBlankTopicItem selectedTopic = findTopicByName(topic);
            if (topicMetaText != null && selectedTopic != null) {
                topicMetaText.setText(getString(
                        R.string.fill_blank_topic_meta_format,
                        selectedTopic.domain,
                        selectedTopic.questionCount
                ));
            }

            if (questions.isEmpty()) {
                boolean shouldRetry = attempt < QUESTION_BOOTSTRAP_MAX_RETRY
                        && selectedTopicSnapshot != null
                        && selectedTopicSnapshot.questionCount > 0;
                if (shouldRetry) {
                    postBootstrapRetry(() -> loadQuestionsForTopic(topic, attempt + 1));
                    return;
                }
                showEmptyState(getString(R.string.fill_blank_no_questions));
                return;
            }

            showQuestionState();
            bindQuestion();
        });
    }

    private void postBootstrapRetry(@NonNull Runnable action) {
        View anchor = rootContainer != null ? rootContainer : (loadingView != null ? loadingView : getView());
        if (anchor == null) {
            return;
        }

        anchor.postDelayed(() -> {
            if (isAdded()) {
                action.run();
            }
        }, BOOTSTRAP_RETRY_DELAY_MS);
    }

    private void setTopicBadgeText(@Nullable String topic) {
        if (topicTitleText == null) {
            return;
        }

        String safeTopic = topic == null ? "" : topic.trim();
        if (safeTopic.isEmpty()) {
            topicTitleText.setText(R.string.fill_blank_topic_default);
            return;
        }

        topicTitleText.setText(getString(R.string.fill_blank_topic_inline_format, safeTopic));
    }

    private void bindQuestion() {
        if (questions.isEmpty() || currentQuestionIndex < 0 || currentQuestionIndex >= questions.size()) {
            return;
        }

        cancelAutoAdvance();

        AppRepository.FillBlankQuestionItem question = questions.get(currentQuestionIndex);

        if (progressText != null) {
            progressText.setText(getString(
                    R.string.fill_blank_progress_format,
                    currentQuestionIndex + 1,
                    questions.size()
            ));
        }

        if (sentenceText != null) {
            sentenceText.setText(question.maskedSentence);
        }
        if (sentenceViText != null) {
            sentenceViText.setText(getString(R.string.fill_blank_translation_format, question.sentenceVi));
        }
        if (meaningText != null) {
            meaningText.setText(getString(R.string.fill_blank_meaning_format, question.meaningVi));
        }

        if (answerInput != null) {
            answerInput.setText("");
        }
        configureAnswerSlots(question.expectedAnswer);

        isCurrentAnswered = false;
        isHintFirstShown = false;
        isHintLastShown = false;
        updateHintText();
        if (hintContainer != null) {
            hintContainer.setVisibility(View.GONE);
        }


        dismissKeyboardAndClearFocus();

        if (checkButton != null) {
            checkButton.setEnabled(true);
        }
        if (nextButton != null) {
            nextButton.setEnabled(false);
        }
        if (hintFirstButton != null) {
            hintFirstButton.setEnabled(true);
        }
        if (hintLastButton != null) {
            hintLastButton.setEnabled(true);
        }

        if (questionScroll != null) {
            questionScroll.post(() -> questionScroll.smoothScrollTo(0, 0));
        }

        animateQuestionReveal();
    }

    private void checkCurrentAnswer() {
        if (questions.isEmpty() || currentQuestionIndex >= questions.size()) {
            return;
        }

        if (isCurrentAnswered) {
            return;
        }

        AppRepository.FillBlankQuestionItem question = questions.get(currentQuestionIndex);
        String rawAnswer = answerInput != null && answerInput.getText() != null
                ? answerInput.getText().toString()
                : "";
        String normalizedAnswer = normalizeAnswer(rawAnswer);

        if (normalizedAnswer.isEmpty()) {
            animateWrongFeedback();
            return;
        }


        if (question.acceptedAnswers.contains(normalizedAnswer)) {
            onCorrectAnswer(question);
        } else {
            onWrongAnswer();
        }
    }

    private void onCorrectAnswer(@NonNull AppRepository.FillBlankQuestionItem question) {
        dismissKeyboardAndClearFocus();
        playFeedbackSound(true);

        if (!isCurrentAnswered && !rewardedQuestions.contains(currentQuestionIndex)) {
            rewardedQuestions.add(currentQuestionIndex);

            consecutiveCorrectStreak++;
            sessionBestCombo = Math.max(sessionBestCombo, consecutiveCorrectStreak);
            sessionCorrectCount++;

            int xpAwarded = XP_PER_CORRECT_ANSWER;
            boolean hitCombo = consecutiveCorrectStreak > 0 && consecutiveCorrectStreak % COMBO_STREAK_TARGET == 0;
            if (hitCombo) {
                xpAwarded += COMBO_BONUS_XP;
            }

            sessionXp += xpAwarded;
            repository.addXp(xpAwarded);
            repository.recordFillBlankAnswerResult(true, xpAwarded, consecutiveCorrectStreak);
            repository.markFillBlankQuestionCompleted(question.topic, question.sourceWord);



            refreshSessionXpText();
            refreshComboIndicator();
            if (hitCombo) {
                Toast.makeText(
                        requireContext(),
                        getString(R.string.fill_blank_combo_bonus_toast, consecutiveCorrectStreak, COMBO_BONUS_XP),
                        Toast.LENGTH_SHORT
                ).show();
            } else {
                Toast.makeText(requireContext(), R.string.fill_blank_reward_toast, Toast.LENGTH_SHORT).show();
            }
        }

        isCurrentAnswered = true;
        if (hintText != null) {
            hintText.setText(getString(R.string.fill_blank_feedback_correct, question.expectedAnswer));
        }
        if (checkButton != null) {
            checkButton.setEnabled(false);
        }
        if (nextButton != null) {
            nextButton.setEnabled(false);
        }
        if (hintFirstButton != null) {
            hintFirstButton.setEnabled(false);
        }
        if (hintLastButton != null) {
            hintLastButton.setEnabled(false);
        }

        animateCorrectFeedback();
        scheduleAutoAdvance();
    }

    private void onWrongAnswer() {
        consecutiveCorrectStreak = 0;
        sessionWrongAttempts++;
        refreshComboIndicator();
        playFeedbackSound(false);
        repository.recordFillBlankAnswerResult(false, 0, 0);


        if (hintText != null) {
            hintText.setText(R.string.fill_blank_feedback_try_again);
        }
        animateWrongFeedback();
        if (answerInput != null) {
            answerInput.selectAll();
        }
    }

    private void moveToNextQuestion() {
        cancelAutoAdvance();

        if (!isCurrentAnswered) {
            if (hintText != null) {
                hintText.setText(R.string.fill_blank_feedback_try_again);
            }
            animateWrongFeedback();
            return;
        }

        if (currentQuestionIndex >= questions.size() - 1) {
            showCompletionDialog();
            return;
        }

        currentQuestionIndex++;
        bindQuestion();
    }

    private void showCompletionDialog() {
        if (!isAdded()) {
            return;
        }

        if (!hasRecordedSessionCompletion) {
            hasRecordedSessionCompletion = true;
            repository.recordFillBlankSessionResult(
                currentTopic,
                sessionXp,
                sessionBestCombo,
                sessionCorrectCount,
                sessionWrongAttempts
            );
        }

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext(), R.style.ef_dialog_rounded)
                .create();

        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_fill_blank_completion, null);
        dialog.setView(dialogView);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.setCancelable(false);

        TextView messageText = dialogView.findViewById(R.id.dialogDoneMessage);
        TextView xpText = dialogView.findViewById(R.id.tvDoneXp);
        TextView comboText = dialogView.findViewById(R.id.tvDoneCombo);
        MaterialButton btnRetry = dialogView.findViewById(R.id.btnDoneRetry);
        MaterialButton btnChangeTopic = dialogView.findViewById(R.id.btnDoneChangeTopic);

        if (messageText != null) {
            messageText.setText(getString(R.string.fill_blank_done_message, questions.size(), sessionXp, sessionBestCombo));
        }
        if (xpText != null) {
            xpText.setText(String.valueOf(sessionXp));
        }
        if (comboText != null) {
            comboText.setText(String.valueOf(sessionBestCombo));
        }

        btnRetry.setOnClickListener(v -> {
            dialog.dismiss();
            repository.resetFillBlankProgressForTopic(currentTopic);
            loadQuestionsForTopic(currentTopic);
        });

        btnChangeTopic.setOnClickListener(v -> {
            dialog.dismiss();
            showTopicPicker();
        });

        dialog.show();
    }

    private void showTopicPicker() {
        if (!isAdded()) {
            return;
        }

        List<AppRepository.FillBlankTopicItem> cachedTopics = new ArrayList<>(availableTopics);
        if (!cachedTopics.isEmpty()) {
            showTopicPickerDialog(cachedTopics);

            // Refresh in background so next open reflects latest status updates.
            repository.getFillBlankTopicsAsync(result -> {
                if (!isAdded()) {
                    return;
                }
                availableTopics.clear();
                if (result != null) {
                    availableTopics.addAll(result);
                }
            });
            return;
        }

        repository.getFillBlankTopicsAsync(result -> {
            if (!isAdded()) {
                return;
            }

            availableTopics.clear();
            if (result != null) {
                availableTopics.addAll(result);
            }

            if (availableTopics.isEmpty()) {
                Toast.makeText(requireContext(), R.string.fill_blank_empty_message, Toast.LENGTH_SHORT).show();
                return;
            }

            showTopicPickerDialog(new ArrayList<>(availableTopics));
        });
    }

    private void showTopicPickerDialog(@NonNull List<AppRepository.FillBlankTopicItem> sourceTopics) {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View sheetView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_fill_blank_topic_picker, null, false);

        List<AppRepository.FillBlankTopicItem> latestTopics = new ArrayList<>(sourceTopics);

        TextView subtitle = sheetView.findViewById(R.id.tvFillBlankTopicSubtitle);
        if (subtitle != null) {
            subtitle.setText(getString(
                    R.string.fill_blank_topic_picker_subtitle_format,
                    latestTopics.size()
            ));
        }

        RecyclerView recyclerView = sheetView.findViewById(R.id.rvFillBlankTopics);
        if (recyclerView != null) {
            recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
            List<AppRepository.FillBlankTopicItem> filtered = getFillBlankTopicsByStatus(
                    latestTopics,
                    AppRepository.FillBlankTopicItem.STATUS_NOT_STARTED
            );

            final FillBlankTopicPickerAdapter pickerAdapter = new FillBlankTopicPickerAdapter(
                    filtered,
                    currentTopic,
                    selected -> {
                        dialog.dismiss();
                        if (selected != null) {
                            if (selected.status == AppRepository.FillBlankTopicItem.STATUS_COMPLETED) {
                                  showTopicDetailReviewDialog(selected.topic);
                            } else {
                                  loadQuestionsForTopic(selected.topic);
                            }
                        }
                    }
            );
            recyclerView.setAdapter(pickerAdapter);

            com.google.android.material.button.MaterialButtonToggleGroup toggle = sheetView.findViewById(R.id.toggleFillBlankTabs);
            if (toggle != null) {
                 toggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                      if (!isChecked) return;
                      int targetStatus = AppRepository.FillBlankTopicItem.STATUS_NOT_STARTED;
                      if (checkedId == R.id.tabFillBlankInProgress) {
                          targetStatus = AppRepository.FillBlankTopicItem.STATUS_IN_PROGRESS;
                      } else if (checkedId == R.id.tabFillBlankCompleted) {
                          targetStatus = AppRepository.FillBlankTopicItem.STATUS_COMPLETED;
                      }

                      pickerAdapter.submitItems(getFillBlankTopicsByStatus(latestTopics, targetStatus));
                 });
            }
        }

        dialog.setContentView(sheetView);
        dialog.show();
    }

    @NonNull
    private List<AppRepository.FillBlankTopicItem> getFillBlankTopicsByStatus(
            @NonNull List<AppRepository.FillBlankTopicItem> topics,
            int status
    ) {
        List<AppRepository.FillBlankTopicItem> filtered = new ArrayList<>();
        for (AppRepository.FillBlankTopicItem topic : topics) {
            if (topic != null && topic.status == status) {
                filtered.add(topic);
            }
        }
        return filtered;
    }

    private void showTopicDetailReviewDialog(String topic) {
        if (!isAdded()) return;

        BottomSheetDialog reviewDialog = new BottomSheetDialog(requireContext());
        View reviewView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_fill_blank_topic_review, null);

        TextView title = reviewView.findViewById(R.id.tvReviewTitle);
        title.setText("Ôn tập: " + topic);

        RecyclerView rv = reviewView.findViewById(R.id.rvReviewQuestions);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));

        repository.executeAsync(() -> {
            List<AppRepository.FillBlankQuestionItem> allQuestions = 
                repository.buildFillBlankQuestionsForTopic(topic, false, true, true);
            
            if (isAdded()) {
                 requireActivity().runOnUiThread(() -> {
                      rv.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                          @NonNull
                          @Override
                          public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                              View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_fill_blank_review_question, parent, false);
                              return new RecyclerView.ViewHolder(v) {};
                          }

                          @Override
                          public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                              AppRepository.FillBlankQuestionItem q = allQuestions.get(position);
                              TextView tvQ = holder.itemView.findViewById(R.id.tvReviewQuestion);
                              TextView tvA = holder.itemView.findViewById(R.id.tvReviewAnswer);
                              TextView tvVi = holder.itemView.findViewById(R.id.tvReviewVi);
                              
                              tvQ.setText(q.maskedSentence);
                              tvA.setText(q.expectedAnswer);
                              tvVi.setText(buildHighlightedVietnameseReview(
                                      holder.itemView.getContext(),
                                      q.sentenceVi,
                                      q.meaningVi
                              ));
                          }

                          @Override
                          public int getItemCount() {
                              return allQuestions.size();
                          }
                      });
                 });
            }
        });

        reviewDialog.setContentView(reviewView);
        reviewDialog.show();
    }

    @NonNull
    private CharSequence buildHighlightedVietnameseReview(@NonNull Context context,
                                                          @Nullable String sentenceVi,
                                                          @Nullable String meaningVi) {
        String translation = sentenceVi == null ? "" : sentenceVi.trim();
        if (translation.isEmpty()) {
            translation = getString(R.string.fill_blank_review_translation_missing);
        }

        String translationLabel = getString(R.string.fill_blank_review_translation_label);
        String translationPrefix = translationLabel.endsWith(" ") ? translationLabel : translationLabel + " ";

        String answerVi = meaningVi == null ? "" : meaningVi.trim();
        SpannableStringBuilder builder = new SpannableStringBuilder(translationPrefix).append(translation);

        if (answerVi.isEmpty()) {
            return builder;
        }

        int foundIndex = translation.toLowerCase(Locale.ROOT)
                .indexOf(answerVi.toLowerCase(Locale.ROOT));

        if (foundIndex >= 0) {
            int start = translationPrefix.length() + foundIndex;
            applyVietnameseAnswerSpan(context, builder, start, start + answerVi.length());
            return builder;
        }

        String label = getString(R.string.fill_blank_review_vi_answer_label);
        builder.append("  ").append(label).append(" ");
        int start = builder.length();
        builder.append(answerVi);
        applyVietnameseAnswerSpan(context, builder, start, builder.length());
        return builder;
    }

    private void applyVietnameseAnswerSpan(@NonNull Context context,
                                           @NonNull SpannableStringBuilder text,
                                           int start,
                                           int end) {
        if (start < 0 || end <= start || end > text.length()) {
            return;
        }

        text.setSpan(new ForegroundColorSpan(ContextCompat.getColor(context, R.color.ef_orange_dark)),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new StyleSpan(Typeface.BOLD),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new UnderlineSpan(),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private void updateHintText() {
        if (hintText == null || questions.isEmpty() || currentQuestionIndex >= questions.size()) {
            return;
        }

        AppRepository.FillBlankQuestionItem question = questions.get(currentQuestionIndex);
        String expected = question.expectedAnswer != null ? question.expectedAnswer.trim() : "";
        if (expected.isEmpty()) {
            hintText.setText(R.string.fill_blank_hint_default);
            return;
        }

        if (!isHintFirstShown && !isHintLastShown) {
            hintText.setText(R.string.fill_blank_hint_default);
            return;
        }

        List<String> hintSegments = new ArrayList<>();
        if (isHintFirstShown) {
            hintSegments.add(getString(
                    R.string.fill_blank_hint_first_format,
                    expected.substring(0, 1).toUpperCase(Locale.US)
            ));
        }
        if (isHintLastShown) {
            hintSegments.add(getString(
                    R.string.fill_blank_hint_last_format,
                    expected.substring(expected.length() - 1).toLowerCase(Locale.US)
            ));
        }
        hintSegments.add(getString(R.string.fill_blank_hint_length_format, expected.length()));

        // Changed separator to "-" as requested
        hintText.setText(TextUtils.join(" - ", hintSegments));

        if (hintContainer != null && hintContainer.getVisibility() != View.VISIBLE) {
            hintContainer.setVisibility(View.VISIBLE);
            hintContainer.setAlpha(0f);
            hintContainer.setTranslationY(dp(10));
            hintContainer.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(300L)
                    .start();
        }
    }

    private void showSolveConfirmationDialog() {
        if (!isAdded() || questions.isEmpty() || currentQuestionIndex >= questions.size()) {
            return;
        }

        int solveCost = 50;

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext(), R.style.ef_dialog_rounded)
                .create();

        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_confirm_solve_hint, null);
        dialog.setView(dialogView);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        MaterialButton btnCancel = dialogView.findViewById(R.id.btnCancelSolve);
        MaterialButton btnConfirm = dialogView.findViewById(R.id.btnConfirmSolve);

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnConfirm.setOnClickListener(v -> {
            dialog.dismiss();
            processSolveAnswer(solveCost);
        });

        dialog.show();
    }

    private void processSolveAnswer(int cost) {
        repository.spendXpAsync(cost, result -> {
            if (!isAdded()) return;

            if (result.success) {
                revealAnswer();
                Toast.makeText(requireContext(), "Đã dùng 50 XP để mở giải đáp!", Toast.LENGTH_SHORT).show();
                refreshSessionXpAfterSpend(); // Optional: refresh UI if needed
            } else {
                Toast.makeText(requireContext(), result.message != null ? result.message : "Không đủ XP rùi!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void revealAnswer() {
        if (questions.isEmpty() || currentQuestionIndex >= questions.size()) return;
        AppRepository.FillBlankQuestionItem question = questions.get(currentQuestionIndex);
        
        if (answerInput != null) {
            answerInput.setText(question.expectedAnswer);
            updateAnswerSlots(question.expectedAnswer);
        }
        
        onCorrectAnswer(question);
    }

    private void refreshSessionXpAfterSpend() {
        // Since we spent XP, we might want to update the local session display if it shows total XP
        // But the session XP usually tracks earnings in this session.
        // For clarity, we'll just keep it as is or update if it's total XP.
    }


    private void refreshSessionXpText() {
        if (sessionXpText != null) {
            sessionXpText.setText(getString(R.string.fill_blank_session_xp_format, sessionXp));
        }
    }

    private void refreshComboIndicator() {
        if (comboIndicatorText != null) {
            comboIndicatorText.setText(getString(
                    R.string.fill_blank_combo_format,
                    Math.max(0, consecutiveCorrectStreak),
                    COMBO_STREAK_TARGET
            ));
        }
    }





    private void showLoading(boolean show) {
        if (loadingView != null) {
            loadingView.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (questionScroll != null) {
            questionScroll.setVisibility(show ? View.GONE : View.VISIBLE);
        }
        if (emptyStateView != null) {
            emptyStateView.setVisibility(View.GONE);
        }
    }

    private void showEmptyState(@NonNull String message) {
        if (loadingView != null) {
            loadingView.setVisibility(View.GONE);
        }
        if (questionScroll != null) {
            questionScroll.setVisibility(View.GONE);
        }
        if (emptyStateView != null) {
            emptyStateView.setVisibility(View.VISIBLE);
        }
        if (emptyMessageText != null) {
            emptyMessageText.setText(message);
        }
    }

    private void showQuestionState() {
        if (loadingView != null) {
            loadingView.setVisibility(View.GONE);
        }
        if (emptyStateView != null) {
            emptyStateView.setVisibility(View.GONE);
        }
        if (questionScroll != null) {
            questionScroll.setVisibility(View.VISIBLE);
        }
    }

    private void animateWrongFeedback() {
        View shakeTarget = slotsInputCard != null ? slotsInputCard : (slotsContainer != null ? slotsContainer : answerInput);
        if (shakeTarget == null) {
            return;
        }

        // Show wrong state on slots
        for (TextView slot : slotViews) {
            if (slot.getText().length() > 0) {
                slot.setBackgroundResource(R.drawable.bg_fill_blank_char_slot_wrong);
            }
        }

        ObjectAnimator shake = ObjectAnimator.ofFloat(
                shakeTarget,
                View.TRANSLATION_X,
                0f, -20f, 20f, -15f, 15f, -10f, 10f, -5f, 5f, 0f
        );
        shake.setDuration(450L);
        shake.start();

        shakeTarget.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);

        // Reset slots background after animation
        shakeTarget.postDelayed(() -> {
            if (!isAdded() || isCurrentAnswered) return;
            String current = answerInput != null && answerInput.getText() != null ? answerInput.getText().toString() : "";
            updateAnswerSlots(current);
        }, 600L);
    }


    private void animateCorrectFeedback() {
        View target = slotsInputCard != null ? slotsInputCard : (slotsContainer != null ? slotsContainer : answerInput);
        if (target == null) {
            return;
        }

        target.animate()
                .scaleX(1.03f)
                .scaleY(1.03f)
                .setDuration(120L)
                .withEndAction(() -> target.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(140L)
                        .start())
                .start();
    }

    private void animateQuestionReveal() {
        if (sentenceText != null) {
            sentenceText.setAlpha(0f);
            sentenceText.setTranslationY(dp(8));
            sentenceText.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(240L)
                    .start();
        }

        if (sentenceViText != null) {
            sentenceViText.setAlpha(0f);
            sentenceViText.setTranslationY(dp(6));
            sentenceViText.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(70L)
                    .setDuration(240L)
                    .start();
        }
    }

    private void scheduleAutoAdvance() {
        if (autoAdvanceScheduled) {
            return;
        }

        View anchor = questionScroll != null ? questionScroll : rootContainer;
        if (anchor == null) {
            return;
        }

        autoAdvanceScheduled = true;
        anchor.removeCallbacks(autoAdvanceRunnable);
        anchor.postDelayed(autoAdvanceRunnable, 750L);
    }

    private void runAutoAdvanceIfReady() {
        autoAdvanceScheduled = false;
        if (!isAdded() || !isCurrentAnswered) {
            return;
        }
        moveToNextQuestion();
    }

    private void cancelAutoAdvance() {
        View anchor = questionScroll != null ? questionScroll : rootContainer;
        if (anchor != null) {
            anchor.removeCallbacks(autoAdvanceRunnable);
        }
        autoAdvanceScheduled = false;
    }

    private void playFeedbackSound(boolean isCorrect) {
        if (!isAdded()) {
            return;
        }

        int soundRes = isCorrect ? R.raw.am_thanh_dung : R.raw.am_thanh_sai;
        releaseFeedbackPlayer();

        feedbackPlayer = MediaPlayer.create(requireContext(), soundRes);
        if (feedbackPlayer == null) {
            return;
        }

        feedbackPlayer.setOnCompletionListener(mp -> releaseFeedbackPlayer());
        feedbackPlayer.setOnErrorListener((mp, what, extra) -> {
            releaseFeedbackPlayer();
            return true;
        });
        feedbackPlayer.start();
    }

    private void releaseFeedbackPlayer() {
        if (feedbackPlayer == null) {
            return;
        }

        try {
            if (feedbackPlayer.isPlaying()) {
                feedbackPlayer.stop();
            }
        } catch (IllegalStateException ignored) {
        }

        feedbackPlayer.reset();
        feedbackPlayer.release();
        feedbackPlayer = null;
    }

    private void attachRaisedButtonMotion(@Nullable View target) {
        if (target == null) {
            return;
        }

        target.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate()
                            .scaleX(0.97f)
                            .scaleY(0.97f)
                            .translationY(dp(1))
                            .setDuration(90L)
                            .start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .translationY(0f)
                            .setDuration(130L)
                            .start();
                    break;
                default:
                    break;
            }
            return false;
        });
    }

    private void applyStatusBarInsets(@NonNull View rootView) {
        View root = rootView.findViewById(R.id.fillBlankRootContainer);
        if (root == null) {
            return;
        }

        int initialLeft = root.getPaddingLeft();
        int initialRight = root.getPaddingRight();
        int initialBottom = root.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets statusBars = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars());
            v.setPadding(initialLeft, statusBars.top + dp(2), initialRight, initialBottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    private void configureAnswerSlots(@Nullable String expectedAnswer) {
        expectedAnswerPattern = expectedAnswer == null ? "" : expectedAnswer.trim();
        slotViews.clear();
        if (slotsContainer == null) {
            return;
        }

        slotsContainer.removeAllViews();
        if (expectedAnswerPattern.isEmpty()) {
            return;
        }

        for (int i = 0; i < expectedAnswerPattern.length(); i++) {
            char c = expectedAnswerPattern.charAt(i);
            if (c == ' ') {
                View spacer = new View(requireContext());
                LinearLayout.LayoutParams spacerParams = new LinearLayout.LayoutParams(dp(10), dp(1));
                spacerParams.setMargins(dp(2), 0, dp(2), 0);
                spacer.setLayoutParams(spacerParams);
                slotsContainer.addView(spacer);
                continue;
            }

            TextView slot = new TextView(requireContext());
            LinearLayout.LayoutParams slotParams = new LinearLayout.LayoutParams(dp(24), dp(34));
            slotParams.setMarginEnd(dp(4));
            slot.setLayoutParams(slotParams);
            slot.setBackgroundResource(R.drawable.bg_fill_blank_char_slot);
            slot.setGravity(Gravity.CENTER);
            slot.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            slot.setTextColor(ContextCompat.getColor(requireContext(), R.color.ef_text_primary));
            slot.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
            slot.setIncludeFontPadding(false);
            slot.setAlpha(0.55f);
            slot.setOnClickListener(v -> requestKeyboardForSlots());

            slotsContainer.addView(slot);
            slotViews.add(slot);
        }

        if (answerInput != null) {
            int maxLength = Math.max(1, slotViews.size());
            answerInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxLength)});
            CharSequence currentText = answerInput.getText();
            updateAnswerSlots(currentText != null ? currentText.toString() : "");
        }

        if (slotsScroll != null) {
            slotsScroll.post(() -> slotsScroll.smoothScrollTo(0, 0));
        }
    }

    private void updateAnswerSlots(@Nullable String input) {
        if (slotViews.isEmpty()) {
            return;
        }

        String compactInput = input == null ? "" : input.replace(" ", "");
        int max = Math.min(compactInput.length(), slotViews.size());

        for (int i = 0; i < slotViews.size(); i++) {
            TextView slot = slotViews.get(i);
            if (i < max) {
                String typed = String.valueOf(compactInput.charAt(i)).toUpperCase(Locale.US);
                slot.setText(typed);
                slot.setAlpha(1f);
                slot.setBackgroundResource(R.drawable.bg_fill_blank_char_slot_filled);
            } else if (i == max) {
                slot.setText("");
                slot.setAlpha(1f);
                slot.setBackgroundResource(R.drawable.bg_fill_blank_char_slot_active);
            } else {
                slot.setText("");
                slot.setAlpha(0.55f);
                slot.setBackgroundResource(R.drawable.bg_fill_blank_char_slot);
            }
        }
    }

    private void requestKeyboardForSlots() {
        if (answerInput == null || !isAdded()) {
            return;
        }
        answerInput.requestFocus();
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(answerInput, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void dismissKeyboardAndClearFocus() {
        if (answerInput == null || !isAdded()) {
            return;
        }
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(answerInput.getWindowToken(), 0);
        }
        answerInput.clearFocus();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String getTopicEmoji(@Nullable String domain, @Nullable String topic) {
        String value = ((domain == null ? "" : domain) + " " + (topic == null ? "" : topic)).toLowerCase(Locale.US);
        if (value.contains("ẩm thực") || value.contains("am thuc") || value.contains("food") || value.contains("cook")) return "🍜";
        if (value.contains("du lịch") || value.contains("du lich") || value.contains("travel")) return "✈️";
        if (value.contains("nhà cửa") || value.contains("nha cua") || value.contains("home")) return "🏠";
        if (value.contains("công nghệ") || value.contains("cong nghe") || value.contains("tech")) return "💻";
        if (value.contains("học tập") || value.contains("hoc tap") || value.contains("study")) return "🎓";
        if (value.contains("sức khỏe") || value.contains("suc khoe") || value.contains("health")) return "🏥";
        if (value.contains("kinh doanh") || value.contains("business")) return "📈";
        if (value.contains("môi trường") || value.contains("moi truong")) return "🌿";
        if (value.contains("nghệ thuật") || value.contains("nghe thuat") || value.contains("art")) return "🎨";
        if (value.contains("thể thao") || value.contains("the thao") || value.contains("sport")) return "⚽";
        if (value.contains("khoa học") || value.contains("khoa hoc") || value.contains("science")) return "🧪";
        if (value.contains("tài chính") || value.contains("tai chinh") || value.contains("finance")) return "💰";
        if (value.contains("gia đình") || value.contains("gia dinh") || value.contains("family")) return "👨‍👩‍👧‍👦";
        return "📚";
    }

    private interface TopicPickerSelectionListener {
        void onSelect(@Nullable AppRepository.FillBlankTopicItem item);
    }

    private class FillBlankTopicPickerAdapter
            extends RecyclerView.Adapter<FillBlankTopicPickerAdapter.TopicOptionViewHolder> {

        private final List<AppRepository.FillBlankTopicItem> items;
        private final String selectedTopic;
        private final TopicPickerSelectionListener listener;

        FillBlankTopicPickerAdapter(@NonNull List<AppRepository.FillBlankTopicItem> source,
                                    @Nullable String selectedTopic,
                                    @NonNull TopicPickerSelectionListener listener) {
            this.items = new ArrayList<>(source);
            this.selectedTopic = selectedTopic == null ? "" : selectedTopic.trim();
            this.listener = listener;
        }

        @NonNull
        @Override
        public TopicOptionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View itemView = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_fill_blank_topic_option, parent, false);
            TopicOptionViewHolder holder = new TopicOptionViewHolder(itemView);
            attachRaisedButtonMotion(holder.itemView);
            return holder;
        }

        @Override
        public void onBindViewHolder(@NonNull TopicOptionViewHolder holder, int position) {
            AppRepository.FillBlankTopicItem item = items.get(position);

            holder.emojiText.setText(getTopicEmoji(item.domain, item.topic));
            holder.topicNameText.setText(item.topic);
            holder.topicDomainText.setText(getString(
                    R.string.fill_blank_topic_domain_format,
                    item.domain,
                    item.learnedWords
            ));
            holder.topicCountText.setText(getString(
                    R.string.fill_blank_topic_question_count_short,
                    item.questionCount
            ));

            boolean isCurrent = item.topic != null && item.topic.equalsIgnoreCase(selectedTopic);
            holder.currentBadgeText.setVisibility(isCurrent ? View.VISIBLE : View.GONE);
            holder.cardView.setStrokeColor(ContextCompat.getColor(
                    holder.itemView.getContext(),
                    isCurrent ? R.color.ef_primary : R.color.ef_outline
            ));
            holder.cardView.setStrokeWidth(dp(isCurrent ? 2 : 1));

            if (item.status == AppRepository.FillBlankTopicItem.STATUS_IN_PROGRESS) {
                holder.statusBadgeText.setVisibility(View.VISIBLE);
                holder.statusBadgeText.setText("ĐANG HỌC");
                holder.statusBadgeText.setBackgroundResource(R.drawable.bg_tag_amber);
                holder.statusBadgeText.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.ef_orange_dark));
            } else if (item.status == AppRepository.FillBlankTopicItem.STATUS_COMPLETED) {
                holder.statusBadgeText.setVisibility(View.VISIBLE);
                holder.statusBadgeText.setText("HOÀN THÀNH");
                holder.statusBadgeText.setBackgroundResource(R.drawable.bg_tag_emerald);
                holder.statusBadgeText.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.ef_primary_dark));
            } else {
                holder.statusBadgeText.setVisibility(View.GONE);
            }

            holder.itemView.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                listener.onSelect(item);
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        void submitItems(@NonNull List<AppRepository.FillBlankTopicItem> source) {
            items.clear();
            items.addAll(source);
            notifyDataSetChanged();
        }

        private class TopicOptionViewHolder extends RecyclerView.ViewHolder {
            final MaterialCardView cardView;
            final TextView emojiText;
            final TextView topicNameText;
            final TextView topicDomainText;
            final TextView topicCountText;
            final TextView currentBadgeText;
            final TextView statusBadgeText;

            TopicOptionViewHolder(@NonNull View itemView) {
                super(itemView);
                cardView = itemView.findViewById(R.id.fillBlankTopicOptionCard);
                emojiText = itemView.findViewById(R.id.fillBlankTopicEmoji);
                topicNameText = itemView.findViewById(R.id.fillBlankTopicName);
                topicDomainText = itemView.findViewById(R.id.fillBlankTopicDomain);
                topicCountText = itemView.findViewById(R.id.fillBlankTopicCount);
                currentBadgeText = itemView.findViewById(R.id.fillBlankTopicCurrentBadge);
                statusBadgeText = itemView.findViewById(R.id.fillBlankTopicStatusBadge);
            }
        }
    }

    private void openDomainsFlow() {
        Fragment parent = getParentFragment();
        if (parent instanceof LearnFlowNavigator) {
            ((LearnFlowNavigator) parent).openDomains();
        }
    }

    private String normalizeAnswer(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.US);
        normalized = normalized.replace('’', '\'');
        normalized = normalized.replaceAll("^[^a-z0-9']+|[^a-z0-9']+$", "");
        normalized = normalized.replaceAll("\\s+", " ");
        return normalized;
    }
}
