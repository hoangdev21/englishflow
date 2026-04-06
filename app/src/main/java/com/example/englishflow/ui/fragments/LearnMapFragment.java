package com.example.englishflow.ui.fragments;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Intent;
import android.graphics.PointF;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.englishflow.R;
import com.example.englishflow.data.AppRepository;
import com.example.englishflow.data.JourneyLessonRepository;
import com.example.englishflow.data.MapNodeItem;
import com.example.englishflow.ui.MapConversationActivity;
import com.example.englishflow.ui.views.MapPathView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LearnMapFragment extends Fragment {

    private final List<Animator> runningAnimators = new ArrayList<>();
    private final List<Animator> runningHeroAnimators = new ArrayList<>();
    private final List<View> nodeCircleViews = new ArrayList<>();
    private final List<MapNodeItem> currentJourneyNodes = new ArrayList<>();

    private LinearLayout mapNodesContainer;
    private MapPathView mapPathView;
    private JourneyLessonRepository journeyLessonRepository;
    private TextView mapStreakChip;
    private MaterialButton mapSpeakQuickButton;
    private ImageView mapHeroAvatar;
    private View mapHeroGlowBase;
    private View mapHeroPulseRingOuter;
    private View mapHeroPulseRingInner;
    private MapNodeItem heroSpeakTarget;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_learn_map, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mapNodesContainer = view.findViewById(R.id.mapNodesContainer);
        mapPathView = view.findViewById(R.id.mapPathView);
        mapStreakChip = view.findViewById(R.id.mapStreakChip);
        mapSpeakQuickButton = view.findViewById(R.id.mapSpeakQuickButton);
        mapHeroAvatar = view.findViewById(R.id.mapHeroAvatar);
        mapHeroGlowBase = view.findViewById(R.id.mapHeroGlowBase);
        mapHeroPulseRingOuter = view.findViewById(R.id.mapHeroPulseRingOuter);
        mapHeroPulseRingInner = view.findViewById(R.id.mapHeroPulseRingInner);
        journeyLessonRepository = new JourneyLessonRepository();

        if (mapSpeakQuickButton != null) {
            mapSpeakQuickButton.setOnClickListener(v -> onSpeakQuickClicked());
        }
        configureSpeakQuickButton();
        startHeroAnimations();
        refreshHeroSummary();

        loadJourneyNodes();

        // Back Button Logic
        View btnBack = view.findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> {
                requireActivity().getOnBackPressedDispatcher().onBackPressed();
            });
        }

        // Apply Insets for Status Bar (Edge-to-Edge compatibility)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            androidx.core.graphics.Insets systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, systemBars.top, 0, 0);
            return insets;
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mapNodesContainer != null) {
            loadJourneyNodes();
        }
        refreshHeroSummary();
    }

    private void loadJourneyNodes() {
        if (!isAdded()) {
            return;
        }

        if (journeyLessonRepository == null) {
            journeyLessonRepository = new JourneyLessonRepository();
        }

        journeyLessonRepository.fetchLessons(lessons -> {
            if (!isAdded() || mapNodesContainer == null) {
                return;
            }

            List<MapNodeItem> nodes = applyProgressStatuses(lessons);
            currentJourneyNodes.clear();
            currentJourneyNodes.addAll(nodes);
            AppRepository.getInstance(requireContext()).setTotalMapNodeCount(nodes.size());
            buildNodeRows(nodes);
            mapNodesContainer.post(this::drawPath);
            configureSpeakQuickButton();
        });
    }

    private void refreshHeroSummary() {
        if (!isAdded()) {
            return;
        }

        AppRepository.getInstance(requireContext()).getDashboardSnapshotAsync(snapshot -> {
            if (!isAdded()) {
                return;
            }

            int streak = 0;
            if (snapshot != null && snapshot.userProgress != null) {
                streak = Math.max(0, snapshot.userProgress.currentStreak);
            }

            if (mapStreakChip != null) {
                mapStreakChip.setText(getString(R.string.learn_map_streak_chip_format, streak));
            }
        });
    }

    private void configureSpeakQuickButton() {
        heroSpeakTarget = pickHeroSpeakTarget();
        if (mapSpeakQuickButton == null) {
            return;
        }

        boolean enabled = heroSpeakTarget != null;
        mapSpeakQuickButton.setEnabled(enabled);
        mapSpeakQuickButton.setAlpha(enabled ? 1f : 0.6f);
    }

    private MapNodeItem pickHeroSpeakTarget() {
        MapNodeItem completedFallback = null;
        MapNodeItem availableFallback = null;

        for (MapNodeItem node : currentJourneyNodes) {
            if (node == null || node.getStatus() == null) {
                continue;
            }

            switch (node.getStatus()) {
                case IN_PROGRESS:
                    return node;
                case AVAILABLE:
                    if (availableFallback == null) {
                        availableFallback = node;
                    }
                    break;
                case COMPLETED:
                    if (completedFallback == null) {
                        completedFallback = node;
                    }
                    break;
                case LOCKED:
                default:
                    break;
            }
        }

        if (availableFallback != null) {
            return availableFallback;
        }
        return completedFallback;
    }

    private void onSpeakQuickClicked() {
        if (heroSpeakTarget == null) {
            if (isAdded()) {
                Toast.makeText(requireContext(), R.string.learn_map_speak_unavailable, Toast.LENGTH_SHORT).show();
            }
            return;
        }
        openConversation(heroSpeakTarget);
    }

    private void startHeroAnimations() {
        clearHeroAnimators();

        if (mapHeroGlowBase != null) {
            ObjectAnimator glowBreath = ObjectAnimator.ofPropertyValuesHolder(
                    mapHeroGlowBase,
                    PropertyValuesHolder.ofFloat(View.SCALE_X, 0.96f, 1.08f, 0.96f),
                    PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.96f, 1.08f, 0.96f),
                    PropertyValuesHolder.ofFloat(View.ALPHA, 0.40f, 0.78f, 0.40f)
            );
            glowBreath.setDuration(3200L);
            glowBreath.setRepeatCount(ObjectAnimator.INFINITE);
            glowBreath.setRepeatMode(ObjectAnimator.RESTART);
            glowBreath.start();
            runningHeroAnimators.add(glowBreath);
        }

        startHeroPulseRing(mapHeroPulseRingOuter, 0L, 2600L);
        startHeroPulseRing(mapHeroPulseRingInner, 1200L, 2600L);

        if (mapHeroAvatar != null) {
            ObjectAnimator avatarFloat = ObjectAnimator.ofPropertyValuesHolder(
                    mapHeroAvatar,
                    PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 0f, -8f, 0f),
                    PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.02f, 1f),
                    PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.02f, 1f)
            );
            avatarFloat.setDuration(3600L);
            avatarFloat.setRepeatCount(ObjectAnimator.INFINITE);
            avatarFloat.setRepeatMode(ObjectAnimator.RESTART);
            avatarFloat.start();
            runningHeroAnimators.add(avatarFloat);
        }

        if (mapSpeakQuickButton != null) {
            ObjectAnimator ctaBreath = ObjectAnimator.ofPropertyValuesHolder(
                    mapSpeakQuickButton,
                    PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.04f, 1f),
                    PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.04f, 1f)
            );
            ctaBreath.setDuration(2200L);
            ctaBreath.setRepeatCount(ObjectAnimator.INFINITE);
            ctaBreath.setRepeatMode(ObjectAnimator.RESTART);
            ctaBreath.start();
            runningHeroAnimators.add(ctaBreath);
        }
    }

    private void startHeroPulseRing(@Nullable View ring, long delayMs, long durationMs) {
        if (ring == null) {
            return;
        }

        ring.setScaleX(0.82f);
        ring.setScaleY(0.82f);
        ring.setAlpha(0f);

        ObjectAnimator ringPulse = ObjectAnimator.ofPropertyValuesHolder(
                ring,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 0.82f, 1.20f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.82f, 1.20f),
                PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 0.55f, 0f)
        );
        ringPulse.setStartDelay(delayMs);
        ringPulse.setDuration(durationMs);
        ringPulse.setRepeatCount(ObjectAnimator.INFINITE);
        ringPulse.setRepeatMode(ObjectAnimator.RESTART);
        ringPulse.start();
        runningHeroAnimators.add(ringPulse);
    }

    private List<MapNodeItem> applyProgressStatuses(List<MapNodeItem> nodes) {
        List<MapNodeItem> safeNodes = nodes == null ? new ArrayList<>() : new ArrayList<>(nodes);
        if (safeNodes.isEmpty()) {
            return safeNodes;
        }

        AppRepository repo = AppRepository.getInstance(requireContext());

        boolean prevCompleted = true; // First node is always available if previous is "completed"
        for (int i = 0; i < safeNodes.size(); i++) {
            MapNodeItem node = safeNodes.get(i);
            boolean isDone = repo.isMapNodeCompleted(node.getNodeId());
            
            if (isDone) {
                node.setStatus(MapNodeItem.Status.COMPLETED);
                prevCompleted = true;
            } else if (prevCompleted) {
                node.setStatus(MapNodeItem.Status.AVAILABLE);
                prevCompleted = false; // Next ones are locked
            } else {
                node.setStatus(MapNodeItem.Status.LOCKED);
            }
        }
        return safeNodes;
    }

    private void buildNodeRows(List<MapNodeItem> nodes) {
        clearNodeAnimators();
        nodeCircleViews.clear();
        mapNodesContainer.removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (int i = 0; i < nodes.size(); i++) {
            MapNodeItem node = nodes.get(i);

            FrameLayout row = new FrameLayout(requireContext());
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(136)
            );
            row.setLayoutParams(rowParams);

            View tile = inflater.inflate(R.layout.item_map_node, row, false);
            FrameLayout.LayoutParams tileParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            tileParams.gravity = getNodeGravityForIndex(i);
            tileParams.leftMargin = dp(18);
            tileParams.rightMargin = dp(18);
            tileParams.topMargin = dp(10);
            tile.setLayoutParams(tileParams);

            bindNode(tile, node);
            row.addView(tile);
            mapNodesContainer.addView(row);
        }
    }

    private void bindNode(View nodeView, MapNodeItem node) {
        MaterialCardView circle = nodeView.findViewById(R.id.nodeCircle);
        View nodeContent = nodeView.findViewById(R.id.nodeContent);
        ImageView nodeIcon = nodeView.findViewById(R.id.nodeIcon);
        TextView title = nodeView.findViewById(R.id.nodeTitle);
        ImageView stateIcon = nodeView.findViewById(R.id.nodeStateIcon);

        nodeIcon.setImageResource(getLessonIconRes(node));
        nodeIcon.setAlpha(1f);
        title.setText(node.getTitle());

        int strokeColor = ContextCompat.getColor(requireContext(), R.color.transparent);

        switch (node.getStatus()) {
            case COMPLETED:
                nodeContent.setBackgroundResource(R.drawable.bg_map_node_completed);
                stateIcon.setVisibility(View.VISIBLE);
                stateIcon.setImageResource(android.R.drawable.checkbox_on_background);
                title.setAlpha(1f);
                nodeIcon.setAlpha(1f);
                break;
            case AVAILABLE:
                nodeContent.setBackgroundResource(R.drawable.bg_map_node_available);
                stateIcon.setVisibility(View.GONE);
                title.setAlpha(1f);
                nodeIcon.setAlpha(1f);
                startPulse(circle);
                break;
            case IN_PROGRESS:
                nodeContent.setBackgroundResource(R.drawable.bg_map_node_progress);
                stateIcon.setVisibility(View.VISIBLE);
                stateIcon.setImageResource(android.R.drawable.presence_away);
                title.setAlpha(1f);
                nodeIcon.setAlpha(1f);
                break;
            case LOCKED:
            default:
                nodeContent.setBackgroundResource(R.drawable.bg_map_node_locked);
                stateIcon.setVisibility(View.VISIBLE);
                stateIcon.setImageResource(R.drawable.ic_lock_closed);
                title.setAlpha(0.7f);
                nodeIcon.setAlpha(0.58f);
                break;
        }

        circle.setStrokeColor(strokeColor);

        boolean clickable = node.getStatus() == MapNodeItem.Status.AVAILABLE
                || node.getStatus() == MapNodeItem.Status.IN_PROGRESS
                || node.getStatus() == MapNodeItem.Status.COMPLETED;

        nodeView.setAlpha(clickable ? 1f : 0.85f);
        nodeView.setOnClickListener(clickable ? v -> openConversation(node) : null);

        nodeCircleViews.add(circle);
    }

    private int getLessonIconRes(MapNodeItem node) {
        if (node == null) {
            return R.drawable.hello;
        }

        int byOrder = iconByOrder(extractOrder(node.getNodeId()));
        if (byOrder != 0) {
            return byOrder;
        }

        int byPrompt = iconByPrompt(node.getPromptKey());
        if (byPrompt != 0) {
            return byPrompt;
        }

        int byTitle = iconByTitle(node.getTitle());
        if (byTitle != 0) {
            return byTitle;
        }

        return R.drawable.hello;
    }

    private int extractOrder(String nodeId) {
        if (nodeId == null || nodeId.trim().isEmpty()) {
            return -1;
        }
        String digits = nodeId.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return -1;
        }
        try {
            return Integer.parseInt(digits);
        } catch (Exception ignored) {
            return -1;
        }
    }

    private int iconByOrder(int order) {
        switch (order) {
            case 1:
                return R.drawable.hello;
            case 2:
                return R.drawable.happy;
            case 3:
                return R.drawable.family;
            case 4:
                return R.drawable.coffee;
            case 5:
                return R.drawable.work;
            case 6:
                return R.drawable.fashion;
            case 7:
                return R.drawable.weather;
            case 8:
                return R.drawable.house;
            case 9:
                return R.drawable.hobby;
            case 10:
                return R.drawable.goodbye;
            default:
                return 0;
        }
    }

    private int iconByPrompt(String promptKey) {
        String key = promptKey == null ? "" : promptKey.trim().toLowerCase(Locale.US);
        if (key.isEmpty()) {
            return 0;
        }
        if (key.contains("greeting")) return R.drawable.hello;
        if (key.contains("mood")) return R.drawable.happy;
        if (key.contains("family")) return R.drawable.family;
        if (key.contains("breakfast")) return R.drawable.coffee;
        if (key.contains("job") || key.contains("dream") || key.contains("work")) return R.drawable.work;
        if (key.contains("fashion")) return R.drawable.fashion;
        if (key.contains("weather")) return R.drawable.weather;
        if (key.contains("home") || key.contains("house")) return R.drawable.house;
        if (key.contains("hobby") || key.contains("weekend")) return R.drawable.hobby;
        if (key.contains("farewell") || key.contains("goodbye")) return R.drawable.goodbye;
        return 0;
    }

    private int iconByTitle(String title) {
        String text = title == null ? "" : title.trim().toLowerCase(Locale.US);
        if (text.isEmpty()) {
            return 0;
        }
        if (text.contains("chào")) return R.drawable.hello;
        if (text.contains("cảm xúc")) return R.drawable.happy;
        if (text.contains("gia đình")) return R.drawable.family;
        if (text.contains("bữa sáng")) return R.drawable.coffee;
        if (text.contains("công việc") || text.contains("ước mơ")) return R.drawable.work;
        if (text.contains("thời trang") || text.contains("màu sắc")) return R.drawable.fashion;
        if (text.contains("thời tiết")) return R.drawable.weather;
        if (text.contains("ngôi nhà")) return R.drawable.house;
        if (text.contains("sở thích")) return R.drawable.hobby;
        if (text.contains("hẹn gặp lại")) return R.drawable.goodbye;
        return 0;
    }

    private void openConversation(MapNodeItem node) {
        Intent intent = new Intent(requireContext(), MapConversationActivity.class);
        intent.putExtra(MapConversationActivity.EXTRA_NODE_ID, node.getNodeId());
        intent.putExtra(MapConversationActivity.EXTRA_TITLE, node.getTitle());
        intent.putExtra(MapConversationActivity.EXTRA_PROMPT_KEY, node.getPromptKey());
        intent.putExtra(MapConversationActivity.EXTRA_ROLE_CONTEXT, node.getRoleDescription());
        intent.putExtra(MapConversationActivity.EXTRA_VOCAB_LIST, new java.util.ArrayList<>(node.getVocabList()));
        intent.putExtra(MapConversationActivity.EXTRA_FLOW_STEPS, new java.util.ArrayList<>(node.getFlowSteps()));
        intent.putStringArrayListExtra(MapConversationActivity.EXTRA_LESSON_KEYWORDS, new java.util.ArrayList<>(node.getLessonKeywords()));
        intent.putExtra(MapConversationActivity.EXTRA_MIN_LEVEL, node.getMinLevel());
        intent.putExtra(MapConversationActivity.EXTRA_MIN_EXCHANGES, node.getMinExchanges());
        startActivity(intent);
    }

    private void drawPath() {
        if (!isAdded()) {
            return;
        }
        int[] pathLocation = new int[2];
        mapPathView.getLocationOnScreen(pathLocation);

        List<PointF> points = new ArrayList<>();
        for (View nodeCircle : nodeCircleViews) {
            int[] nodeLocation = new int[2];
            nodeCircle.getLocationOnScreen(nodeLocation);
            float cx = nodeLocation[0] - pathLocation[0] + (nodeCircle.getWidth() / 2f);
            float cy = nodeLocation[1] - pathLocation[1] + (nodeCircle.getHeight() / 2f);
            points.add(new PointF(cx, cy));
        }
        mapPathView.setNodeCenters(points);
    }

    private int getNodeGravityForIndex(int index) {
        if (index == 0 || index == 5) {
            return Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        }
        return index % 2 == 0 ? Gravity.TOP | Gravity.START : Gravity.TOP | Gravity.END;
    }

    private void startPulse(View view) {
        ObjectAnimator pulse = ObjectAnimator.ofPropertyValuesHolder(
                view,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.08f, 1f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.08f, 1f)
        );
        pulse.setDuration(1500L);
        pulse.setRepeatCount(ObjectAnimator.INFINITE);
        pulse.start();
        runningAnimators.add(pulse);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        clearRunningAnimators();
        nodeCircleViews.clear();
        currentJourneyNodes.clear();
        heroSpeakTarget = null;

        mapNodesContainer = null;
        mapPathView = null;
        mapStreakChip = null;
        mapSpeakQuickButton = null;
        mapHeroAvatar = null;
        mapHeroGlowBase = null;
        mapHeroPulseRingOuter = null;
        mapHeroPulseRingInner = null;
    }

    private void clearRunningAnimators() {
        clearNodeAnimators();
        clearHeroAnimators();
    }

    private void clearNodeAnimators() {
        for (Animator animator : runningAnimators) {
            animator.cancel();
        }
        runningAnimators.clear();
    }

    private void clearHeroAnimators() {
        for (Animator animator : runningHeroAnimators) {
            animator.cancel();
        }
        runningHeroAnimators.clear();
    }
}
