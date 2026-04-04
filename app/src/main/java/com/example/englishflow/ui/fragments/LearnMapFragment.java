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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.englishflow.R;
import com.example.englishflow.data.AppRepository;
import com.example.englishflow.data.LessonVocabulary;
import com.example.englishflow.data.MapNodeItem;
import com.example.englishflow.ui.MapConversationActivity;
import com.example.englishflow.ui.views.MapPathView;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LearnMapFragment extends Fragment {

    private final List<Animator> runningAnimators = new ArrayList<>();
    private final List<View> nodeCircleViews = new ArrayList<>();

    private LinearLayout mapNodesContainer;
    private MapPathView mapPathView;

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

        buildNodeRows(getMockJourney());
        mapNodesContainer.post(this::drawPath);

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
            buildNodeRows(getMockJourney());
            mapNodesContainer.post(this::drawPath);
        }
    }

    private List<MapNodeItem> getMockJourney() {
        AppRepository repo = AppRepository.getInstance(requireContext());
        List<MapNodeItem> mockNodes = Arrays.asList(
            new MapNodeItem("greetings_01", "Greetings", "Hi", "greetings_basic", 
                    "You are a friendly neighbor meeting the user on a sidewalk. Start by saying Hi and ask for their name.", 3, 
                    Arrays.asList(new com.example.englishflow.data.LessonVocabulary("Hello", "/həˈloʊ/", "Xin chào", "Hello, how are you?"), new com.example.englishflow.data.LessonVocabulary("Meet", "/miːt/", "Gặp gỡ", "Nice to meet you.")),
                    MapNodeItem.Status.AVAILABLE),
            new MapNodeItem("greetings_02", "Formal Hello", "GM", "greetings_formal", 
                    "You are an interviewer at a big tech company. Start by greeting the user formally for their interview.", 4, 
                     Arrays.asList(new com.example.englishflow.data.LessonVocabulary("Formal", "/ˈfɔːrml/", "Trang trọng", "This is a formal meeting."), new com.example.englishflow.data.LessonVocabulary("Appointment", "/əˈpɔɪntmənt/", "Cuộc hẹn", "I have an appointment.")),
                    MapNodeItem.Status.LOCKED),
            new MapNodeItem("greetings_03", "Time-based", "Time", "greetings_time", 
                    "You are a barista early in the morning. Greet the user and ask what kind of coffee they'd like to start their day.", 4, 
                    Arrays.asList(new com.example.englishflow.data.LessonVocabulary("Morning", "/ˈmɔːrnɪŋ/", "Buổi sáng", "Good morning!")),
                    MapNodeItem.Status.LOCKED),
            new MapNodeItem("greetings_04", "Phone Talk", "Call", "greetings_phone", 
                    "You are a hotel receptionist answering a call. Greet the customer and offer your assistance.", 4, 
                    Arrays.asList(new com.example.englishflow.data.LessonVocabulary("Reception", "/rɪˈsepʃn/", "Quầy lễ tân", "Go to the reception.")),
                    MapNodeItem.Status.LOCKED),
            new MapNodeItem("greetings_05", "Goodbye", "Bye", "greetings_goodbye", 
                    "You are a teacher at the end of class. Say goodbye to the user and remind them about their homework.", 3, 
                    Arrays.asList(new com.example.englishflow.data.LessonVocabulary("Class", "/klæs/", "Lớp học", "The class is over.")),
                    MapNodeItem.Status.LOCKED),
            new MapNodeItem("greetings_06", "How are you", "Mood", "greetings_howru", 
                    "You are an old friend who hasn't seen the user for years. Greet them excitedly and ask how their life is going.", 5, 
                    Arrays.asList(new com.example.englishflow.data.LessonVocabulary("Exciting", "/ɪkˈsaɪtɪŋ/", "Thú vị/Hứng thú", "This is exciting.")),
                    MapNodeItem.Status.LOCKED)
        );

        boolean prevCompleted = true; // First node is always available if previous is "completed"
        for (int i = 0; i < mockNodes.size(); i++) {
            MapNodeItem node = mockNodes.get(i);
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
        return mockNodes;
    }

    private void buildNodeRows(List<MapNodeItem> nodes) {
        clearRunningAnimators();
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
        TextView emoji = nodeView.findViewById(R.id.nodeEmoji);
        TextView title = nodeView.findViewById(R.id.nodeTitle);
        ImageView stateIcon = nodeView.findViewById(R.id.nodeStateIcon);

        emoji.setText(node.getEmoji());
        title.setText(node.getTitle());

        int strokeColor = ContextCompat.getColor(requireContext(), R.color.transparent);

        switch (node.getStatus()) {
            case COMPLETED:
                nodeContent.setBackgroundResource(R.drawable.bg_map_node_completed);
                stateIcon.setVisibility(View.VISIBLE);
                stateIcon.setImageResource(android.R.drawable.checkbox_on_background);
                title.setAlpha(1f);
                break;
            case AVAILABLE:
                nodeContent.setBackgroundResource(R.drawable.bg_map_node_available);
                stateIcon.setVisibility(View.GONE);
                title.setAlpha(1f);
                startPulse(circle);
                break;
            case IN_PROGRESS:
                nodeContent.setBackgroundResource(R.drawable.bg_map_node_progress);
                stateIcon.setVisibility(View.VISIBLE);
                stateIcon.setImageResource(android.R.drawable.presence_away);
                title.setAlpha(1f);
                break;
            case LOCKED:
            default:
                nodeContent.setBackgroundResource(R.drawable.bg_map_node_locked);
                stateIcon.setVisibility(View.VISIBLE);
                stateIcon.setImageResource(R.drawable.ic_lock_closed);
                title.setAlpha(0.7f);
                emoji.setAlpha(0.75f);
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

    private void openConversation(MapNodeItem node) {
        Intent intent = new Intent(requireContext(), MapConversationActivity.class);
        intent.putExtra(MapConversationActivity.EXTRA_NODE_ID, node.getNodeId());
        intent.putExtra(MapConversationActivity.EXTRA_TITLE, node.getTitle());
        intent.putExtra(MapConversationActivity.EXTRA_PROMPT_KEY, node.getPromptKey());
        intent.putExtra(MapConversationActivity.EXTRA_ROLE_CONTEXT, node.getRoleDescription());
        intent.putExtra(MapConversationActivity.EXTRA_VOCAB_LIST, new java.util.ArrayList<>(node.getVocabList()));
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
    }

    private void clearRunningAnimators() {
        for (Animator animator : runningAnimators) {
            animator.cancel();
        }
        runningAnimators.clear();
    }
}
