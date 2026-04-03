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
    }

    private List<MapNodeItem> getMockJourney() {
        return Arrays.asList(
                new MapNodeItem("greetings_01", "Greetings", "Hi", "greetings_basic", 3, MapNodeItem.Status.COMPLETED),
                new MapNodeItem("greetings_02", "Formal Hello", "GM", "greetings_formal", 4, MapNodeItem.Status.AVAILABLE),
                new MapNodeItem("greetings_03", "Time-based", "Time", "greetings_time", 4, MapNodeItem.Status.LOCKED),
                new MapNodeItem("greetings_04", "Phone Talk", "Call", "greetings_phone", 4, MapNodeItem.Status.LOCKED),
                new MapNodeItem("greetings_05", "Goodbye", "Bye", "greetings_goodbye", 3, MapNodeItem.Status.LOCKED),
                new MapNodeItem("greetings_06", "How are you", "Mood", "greetings_howru", 5, MapNodeItem.Status.LOCKED)
        );
    }

    private void buildNodeRows(List<MapNodeItem> nodes) {
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
        for (Animator animator : runningAnimators) {
            animator.cancel();
        }
        runningAnimators.clear();
        nodeCircleViews.clear();
    }
}
