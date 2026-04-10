package com.example.englishflow.ui;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.os.Bundle;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.englishflow.R;
import com.example.englishflow.data.AppRepository;
import com.example.englishflow.data.EcoCompanionStore;
import com.example.englishflow.ui.views.EcoMascot3DView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class EcoSanctuaryActivity extends AppCompatActivity {

    private AppRepository repository;
    private EcoCompanionStore ecoStore;

    private TextView levelBadgeText;
    private TextView stageText;
    private TextView moodText;
    private TextView bondText;
    private TextView walletXpText;
    private TextView walletSeedsText;
    private TextView missionSummaryText;
    private EcoMascot3DView mascotView;
    private LinearProgressIndicator bondProgress;

    private MissionAdapter missionAdapter;
    private SkinAdapter skinAdapter;

    private AppRepository.DashboardSnapshot latestSnapshot;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_eco_sanctuary);

        repository = AppRepository.getInstance(this);
        ecoStore = new EcoCompanionStore(this);

        bindViews();
        setupInsets();
        setupRecyclerViews();
        setupActions();
        observeData();

        refreshDashboardSnapshot();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshDashboardSnapshot();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private void bindViews() {
        levelBadgeText = findViewById(R.id.ecoLevelBadge);
        stageText = findViewById(R.id.ecoStageText);
        moodText = findViewById(R.id.ecoMoodText);
        bondText = findViewById(R.id.ecoBondText);
        walletXpText = findViewById(R.id.ecoWalletXp);
        walletSeedsText = findViewById(R.id.ecoWalletSeeds);
        missionSummaryText = findViewById(R.id.ecoMissionSummary);
        mascotView = findViewById(R.id.ecoMascotView);
        bondProgress = findViewById(R.id.ecoBondProgress);
    }

    private void setupInsets() {
        View topBar = findViewById(R.id.ecoTopBar);
        if (topBar != null) {
            final int initialTop = topBar.getPaddingTop();
            ViewCompat.setOnApplyWindowInsetsListener(topBar, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(v.getPaddingLeft(), initialTop + systemBars.top, v.getPaddingRight(), v.getPaddingBottom());
                return insets;
            });
            ViewCompat.requestApplyInsets(topBar);
        }

        View scroll = findViewById(R.id.ecoScroll);
        if (scroll != null) {
            final int initialBottom = scroll.getPaddingBottom();
            ViewCompat.setOnApplyWindowInsetsListener(scroll, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), initialBottom + systemBars.bottom);
                return insets;
            });
            ViewCompat.requestApplyInsets(scroll);
        }
    }

    private void setupRecyclerViews() {
        RecyclerView missionRecycler = findViewById(R.id.ecoMissionRecycler);
        RecyclerView skinRecycler = findViewById(R.id.ecoSkinRecycler);

        missionAdapter = new MissionAdapter(new MissionAdapter.Listener() {
            @Override
            public void onClaimMission(@NonNull EcoCompanionStore.EcoMission mission) {
                claimMission(mission);
            }
        });
        missionRecycler.setLayoutManager(new LinearLayoutManager(this));
        missionRecycler.setAdapter(missionAdapter);

        skinAdapter = new SkinAdapter(new SkinAdapter.Listener() {
            @Override
            public void onSkinAction(@NonNull EcoCompanionStore.EcoSkin skin, boolean owned, boolean selected) {
                handleSkinAction(skin, owned, selected);
            }
        });
        LinearLayoutManager horizontalManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        skinRecycler.setLayoutManager(horizontalManager);
        skinRecycler.setAdapter(skinAdapter);
    }

    private void setupActions() {
        View back = findViewById(R.id.ecoBtnBack);
        if (back != null) {
            back.setOnClickListener(v -> {
                v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).withEndAction(() -> {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).withEndAction(this::finish);
                }).start();
            });
        }
        setupMascotInteraction();
    }

    private void setupMascotInteraction() {
        if (mascotView != null) {
            mascotView.setOnClickListener(v -> mascotView.startFlying());
        }
    }

    private void observeData() {
        repository.getLiveUserStats().observe(this, stats -> refreshDashboardSnapshot());
    }

    private void refreshDashboardSnapshot() {
        repository.getDashboardSnapshotAsync(snapshot -> runOnUiThread(() -> {
            latestSnapshot = snapshot;
            renderHero(snapshot);
            renderMissions(snapshot);
            renderSkins(snapshot);
        }));
    }

    private void renderHero(@NonNull AppRepository.DashboardSnapshot snapshot) {
        int level = ecoStore.getLevel();
        int bondXp = ecoStore.getBondXp();
        int levelStartXp = ecoStore.getCurrentLevelStartXp();
        int nextLevelXp = ecoStore.getNextLevelTargetXp();

        levelBadgeText.setText(String.format(Locale.getDefault(), "Lv.%d", level));
        stageText.setText(ecoStore.getEvolutionStageLabel());
        moodText.setText(ecoStore.resolveMoodLabel(snapshot));

        int progressPercent = ecoStore.getLevelProgressPercent();
        bondProgress.setProgressCompat(progressPercent, true);

        bondText.setText(String.format(
                Locale.getDefault(),
                "Bond: %d / %d (%d%%)",
                Math.max(0, bondXp - levelStartXp),
                Math.max(1, nextLevelXp - levelStartXp),
                progressPercent
        ));

        int xpBalance = snapshot.userProgress != null ? Math.max(0, snapshot.userProgress.totalXpEarned) : 0;
        if (walletXpText != null) walletXpText.setText(String.format(Locale.getDefault(), "XP: %d", xpBalance));
        if (walletSeedsText != null) walletSeedsText.setText(String.format(Locale.getDefault(), "Hạt giống: %d", ecoStore.getSeeds()));

        // The 3D view is self-rendering and handles its own appearance natively.
    }

    private void renderMissions(@NonNull AppRepository.DashboardSnapshot snapshot) {
        List<EcoCompanionStore.EcoMission> missions = ecoStore.getTodayMissions(snapshot);
        missionAdapter.submit(missions);

        int claimed = 0;
        for (EcoCompanionStore.EcoMission mission : missions) {
            if (mission.claimed) {
                claimed++;
            }
        }

        missionSummaryText.setText(String.format(
                Locale.getDefault(),
                "Hoàn thành %d/%d nhiệm vụ • Tổng claim: %d",
                claimed,
                missions.size(),
                ecoStore.getTotalClaimedMissions()
        ));
    }

    private void renderSkins(@NonNull AppRepository.DashboardSnapshot snapshot) {
        List<EcoCompanionStore.EcoSkin> skins = ecoStore.getSkinCatalog();
        Set<String> owned = new HashSet<>();
        for (EcoCompanionStore.EcoSkin skin : skins) {
            if (ecoStore.isSkinOwned(skin.key)) {
                owned.add(skin.key);
            }
        }
        int xpBalance = snapshot.userProgress != null ? Math.max(0, snapshot.userProgress.totalXpEarned) : 0;

        skinAdapter.submit(
                skins,
                owned,
                ecoStore.getSelectedSkinKey(),
                xpBalance,
                ecoStore.getSeeds(),
                ecoStore.getLevel()
        );
    }

    private void claimMission(@NonNull EcoCompanionStore.EcoMission mission) {
        if (latestSnapshot == null) {
            return;
        }

        EcoCompanionStore.ClaimResult result = ecoStore.claimMission(mission.id, latestSnapshot);
        if (!result.success) {
            Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show();
            return;
        }

        if (result.rewardXp > 0) {
            repository.addXp(result.rewardXp);
        }

        if (!result.unlockedSkinKey.isEmpty()) {
            ecoStore.setSelectedSkin(result.unlockedSkinKey);
        }

        Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show();
        refreshDashboardSnapshot();
    }

    private void handleSkinAction(@NonNull EcoCompanionStore.EcoSkin skin, boolean owned, boolean selected) {
        if (selected) {
            return;
        }

        if (owned) {
            ecoStore.setSelectedSkin(skin.key);
            Toast.makeText(this, "Đã trang bị skin", Toast.LENGTH_SHORT).show();
            refreshDashboardSnapshot();
            return;
        }

        if (ecoStore.getLevel() < skin.requiredLevel) {
            Toast.makeText(
                    this,
                    String.format(Locale.getDefault(), "Cần đạt Lv.%d để mở skin này", skin.requiredLevel),
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        if (skin.seedCost > 0) {
            EcoCompanionStore.PurchaseResult purchaseResult = ecoStore.purchaseSkinWithSeeds(skin.key);
            Toast.makeText(this, purchaseResult.message, Toast.LENGTH_SHORT).show();
            if (purchaseResult.success) {
                refreshDashboardSnapshot();
            }
            return;
        }

        if (skin.xpCost > 0) {
            repository.spendXpAsync(skin.xpCost, spendXpResult -> runOnUiThread(() -> {
                if (!spendXpResult.success) {
                    Toast.makeText(
                            this,
                            spendXpResult.message == null || spendXpResult.message.trim().isEmpty()
                                    ? "Không thể mua skin bằng XP"
                                    : spendXpResult.message,
                            Toast.LENGTH_SHORT
                    ).show();
                    return;
                }

                ecoStore.unlockSkinAndEquip(skin.key);
                Toast.makeText(this, "Mua skin thành công", Toast.LENGTH_SHORT).show();
                refreshDashboardSnapshot();
            }));
            return;
        }

        Toast.makeText(this, "Skin này mở qua nhiệm vụ đặc biệt", Toast.LENGTH_SHORT).show();
    }

    private static class MissionAdapter extends RecyclerView.Adapter<MissionAdapter.MissionViewHolder> {

        interface Listener {
            void onClaimMission(@NonNull EcoCompanionStore.EcoMission mission);
        }

        private final List<EcoCompanionStore.EcoMission> items = new ArrayList<>();
        private final Listener listener;
        private int expandedPosition = -1;

        MissionAdapter(@NonNull Listener listener) {
            this.listener = listener;
        }

        void submit(@NonNull List<EcoCompanionStore.EcoMission> nextItems) {
            items.clear();
            items.addAll(nextItems);
            expandedPosition = -1; // Reset expansion on refresh
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public MissionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_eco_mission, parent, false);
            return new MissionViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull MissionViewHolder holder, int position) {
            final int pos = holder.getBindingAdapterPosition();
            EcoCompanionStore.EcoMission mission = items.get(pos);

            holder.title.setText(mission.title);
            holder.description.setText(mission.description);

            String rewardText = String.format(Locale.getDefault(), "+%d XP • +%d Seeds", mission.rewardXp, mission.rewardSeeds);
            if (!mission.rewardSkinKey.isEmpty()) {
                rewardText += " • Skin hiếm";
            }
            holder.rewardRow.setText(rewardText);

            holder.progressBar.setMax(mission.target);
            holder.progressBar.setProgressCompat(Math.min(mission.progress, mission.target), false);
            holder.progressText.setText(String.format(Locale.getDefault(), "%d/%d", mission.progress, mission.target));

            boolean isExpanded = (pos == expandedPosition);
            holder.detailContainer.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
            holder.toggleIcon.setRotation(isExpanded ? 90f : 270f);

            if (mission.claimed) {
                holder.statusIcon.setImageResource(R.drawable.ic_check_circle);
                holder.statusIcon.setColorFilter(ContextCompat.getColor(holder.itemView.getContext(), R.color.ef_success));
                holder.statusBg.setBackgroundResource(R.drawable.bg_eco_chip_success);
                holder.statusBg.setAlpha(0.2f);
                holder.claimButton.setEnabled(false);
                holder.claimButton.setText("Đã nhận thưởng");
            } else if (mission.isCompleted()) {
                holder.statusIcon.setImageResource(R.drawable.ic_check_circle);
                holder.statusIcon.setColorFilter(ContextCompat.getColor(holder.itemView.getContext(), R.color.ef_primary));
                holder.statusBg.setBackgroundResource(R.drawable.bg_eco_chip);
                holder.statusBg.setAlpha(0.2f);
                holder.claimButton.setEnabled(true);
                holder.claimButton.setText("Nhận thưởng ngay");
            } else {
                holder.statusIcon.setImageResource(R.drawable.ic_check_circle);
                holder.statusIcon.setColorFilter(ContextCompat.getColor(holder.itemView.getContext(), R.color.ef_text_secondary), android.graphics.PorterDuff.Mode.SRC_IN);
                holder.statusBg.setBackgroundResource(R.drawable.bg_eco_chip_locked);
                holder.statusBg.setAlpha(0.1f);
                holder.claimButton.setEnabled(false);
                holder.claimButton.setText("Chưa hoàn thành");
            }

            holder.container.setOnClickListener(v -> {
                int previousExpanded = expandedPosition;
                if (expandedPosition == pos) {
                    expandedPosition = -1;
                } else {
                    expandedPosition = pos;
                }

                ViewGroup parent = (ViewGroup) holder.itemView.getParent();
                if (parent != null) {
                    TransitionManager.beginDelayedTransition(parent, new AutoTransition().setDuration(300));
                }

                if (previousExpanded != -1) notifyItemChanged(previousExpanded);
                notifyItemChanged(pos);
            });

            holder.claimButton.setOnClickListener(v -> listener.onClaimMission(mission));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class MissionViewHolder extends RecyclerView.ViewHolder {
            final LinearLayout container;
            final LinearLayout detailContainer;
            final ImageView statusIcon;
            final View statusBg;
            final ImageView toggleIcon;
            final TextView title;
            final TextView description;
            final TextView rewardRow;
            final LinearProgressIndicator progressBar;
            final TextView progressText;
            final MaterialButton claimButton;

            MissionViewHolder(@NonNull View itemView) {
                super(itemView);
                container = itemView.findViewById(R.id.missionContainer);
                detailContainer = itemView.findViewById(R.id.missionDetailContainer);
                statusIcon = itemView.findViewById(R.id.missionStatusIcon);
                statusBg = itemView.findViewById(R.id.missionStatusBg);
                toggleIcon = itemView.findViewById(R.id.missionToggleIcon);
                title = itemView.findViewById(R.id.missionTitle);
                description = itemView.findViewById(R.id.missionDesc);
                rewardRow = itemView.findViewById(R.id.missionRewardRow);
                progressBar = itemView.findViewById(R.id.missionProgressBar);
                progressText = itemView.findViewById(R.id.missionProgressText);
                claimButton = itemView.findViewById(R.id.missionClaimButton);
            }
        }
    }

    private static class SkinAdapter extends RecyclerView.Adapter<SkinAdapter.SkinViewHolder> {

        interface Listener {
            void onSkinAction(@NonNull EcoCompanionStore.EcoSkin skin, boolean owned, boolean selected);
        }

        private final List<EcoCompanionStore.EcoSkin> skins = new ArrayList<>();
        private final Set<String> ownedSkinKeys = new HashSet<>();
        private final Listener listener;

        private String selectedSkinKey = "";
        private int xpBalance = 0;
        private int seedBalance = 0;
        private int level = 1;

        SkinAdapter(@NonNull Listener listener) {
            this.listener = listener;
        }

        void submit(@NonNull List<EcoCompanionStore.EcoSkin> nextSkins,
                    @NonNull Set<String> owned,
                    @NonNull String selected,
                    int xpBalance,
                    int seedBalance,
                    int level) {
            skins.clear();
            skins.addAll(nextSkins);

            ownedSkinKeys.clear();
            ownedSkinKeys.addAll(owned);

            this.selectedSkinKey = selected;
            this.xpBalance = Math.max(0, xpBalance);
            this.seedBalance = Math.max(0, seedBalance);
            this.level = Math.max(1, level);

            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public SkinViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_eco_skin, parent, false);
            return new SkinViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull SkinViewHolder holder, int position) {
            EcoCompanionStore.EcoSkin skin = skins.get(position);
            boolean owned = ownedSkinKeys.contains(skin.key);
            boolean selected = selectedSkinKey.equals(skin.key);
            boolean levelLocked = level < skin.requiredLevel;

            holder.preview.setImageResource(skin.drawableRes);
            holder.name.setText(skin.name);
            holder.rarity.setText(skin.rarity);
            holder.bonus.setText(skin.bonus);
            holder.price.setText(resolvePriceText(skin));

            if (selected) {
                holder.actionButton.setEnabled(false);
                holder.actionButton.setText("Đang dùng");
                holder.actionButton.setBackgroundResource(R.drawable.bg_eco_button_primary);
                holder.actionButton.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), android.R.color.white));
            } else if (owned) {
                holder.actionButton.setEnabled(true);
                holder.actionButton.setText("Trang bị");
                holder.actionButton.setBackgroundResource(R.drawable.bg_eco_button_outline);
                holder.actionButton.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.ef_text_primary));
            } else if (levelLocked) {
                holder.actionButton.setEnabled(false);
                holder.actionButton.setText(String.format(Locale.getDefault(), "Mở Lv.%d", skin.requiredLevel));
                holder.actionButton.setBackgroundResource(R.drawable.bg_eco_button_outline);
                holder.actionButton.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.ef_text_secondary));
            } else if (skin.seedCost > 0) {
                holder.actionButton.setEnabled(seedBalance >= skin.seedCost);
                holder.actionButton.setText(seedBalance >= skin.seedCost ? "Mua bằng Seeds" : "Thiếu Seeds");
                holder.actionButton.setBackgroundResource(R.drawable.bg_eco_button_outline);
                holder.actionButton.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.ef_text_primary));
            } else if (skin.xpCost > 0) {
                holder.actionButton.setEnabled(xpBalance >= skin.xpCost);
                holder.actionButton.setText(xpBalance >= skin.xpCost ? "Mua bằng XP" : "Thiếu XP");
                holder.actionButton.setBackgroundResource(R.drawable.bg_eco_button_outline);
                holder.actionButton.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.ef_text_primary));
            } else {
                holder.actionButton.setEnabled(false);
                holder.actionButton.setText("Nhiệm vụ đặc biệt");
                holder.actionButton.setBackgroundResource(R.drawable.bg_eco_button_outline);
                holder.actionButton.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.ef_text_secondary));
            }

            holder.actionButton.setOnClickListener(v -> listener.onSkinAction(skin, owned, selected));
        }

        private String resolvePriceText(@NonNull EcoCompanionStore.EcoSkin skin) {
            if (ownedSkinKeys.contains(skin.key)) {
                return "Đã sở hữu";
            }
            if (skin.seedCost > 0) {
                return String.format(Locale.getDefault(), "%d Seeds", skin.seedCost);
            }
            if (skin.xpCost > 0) {
                return String.format(Locale.getDefault(), "%d XP", skin.xpCost);
            }
            return "Mở từ nhiệm vụ";
        }

        @Override
        public int getItemCount() {
            return skins.size();
        }

        static class SkinViewHolder extends RecyclerView.ViewHolder {
            final LinearLayout container;
            final ImageView preview;
            final TextView name;
            final TextView rarity;
            final TextView bonus;
            final TextView price;
            final MaterialButton actionButton;

            SkinViewHolder(@NonNull View itemView) {
                super(itemView);
                container = itemView.findViewById(R.id.skinContainer);
                preview = itemView.findViewById(R.id.skinPreview);
                name = itemView.findViewById(R.id.skinName);
                rarity = itemView.findViewById(R.id.skinRarity);
                bonus = itemView.findViewById(R.id.skinBonus);
                price = itemView.findViewById(R.id.skinPrice);
                actionButton = itemView.findViewById(R.id.skinActionButton);
            }
        }
    }
}
