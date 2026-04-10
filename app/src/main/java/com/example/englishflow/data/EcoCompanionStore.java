package com.example.englishflow.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import com.example.englishflow.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class EcoCompanionStore {

    private static final String PREFS = "eco_companion_store";
    private static final String KEY_SEEDS = "eco_seeds";
    private static final String KEY_BOND_XP = "eco_bond_xp";
    private static final String KEY_SELECTED_SKIN = "eco_selected_skin";
    private static final String KEY_OWNED_SKINS = "eco_owned_skins";
    private static final String KEY_TOTAL_MISSIONS_CLAIMED = "eco_total_missions_claimed";
    private static final String KEY_CLAIMED_MISSIONS_PREFIX = "eco_claimed_missions_";

    private static final String SKIN_CLASSIC = "eco_classic";
    private static final String SKIN_RAINFOREST = "eco_rainforest";
    private static final String SKIN_SOLAR = "eco_solar";
    private static final String SKIN_GLACIER = "eco_glacier";
    private static final String SKIN_NEBULA = "eco_nebula";
    private static final String SKIN_ROYAL = "eco_royal";

    private static final String PROGRESS_WORDS = "words";
    private static final String PROGRESS_XP_TODAY = "xp_today";
    private static final String PROGRESS_STREAK = "streak";

    private static final int[] LEVEL_XP_THRESHOLDS = new int[]{0, 120, 280, 520, 860, 1300, 1850, 2500};

    private static final List<EcoSkin> SKIN_CATALOG = Arrays.asList(
            new EcoSkin(SKIN_CLASSIC, "Eco Cổ Điển", "Common", "Khởi đầu dịu dàng", 0, 0, 0, R.drawable.eco_parrot_classic),
            new EcoSkin(SKIN_RAINFOREST, "Rừng Nhiệt Đới", "Rare", "+3% XP nhiệm vụ ngày", 520, 0, 2, R.drawable.eco_parrot_rainforest),
            new EcoSkin(SKIN_SOLAR, "Chiến Binh Hoàng Hôn", "Rare", "+5 Seeds mỗi lần claim", 980, 0, 3, R.drawable.eco_parrot_solar),
            new EcoSkin(SKIN_GLACIER, "Băng Lam", "Epic", "Mở hiệu ứng cánh băng", 0, 170, 4, R.drawable.eco_parrot_glacier),
            new EcoSkin(SKIN_NEBULA, "Tinh Vân", "Epic", "+5% Bond XP", 1850, 0, 5, R.drawable.eco_parrot_nebula),
            new EcoSkin(SKIN_ROYAL, "Hoàng Gia Streak", "Mythic", "Skin sự kiện chỉ mở từ nhiệm vụ", 0, 0, 4, R.drawable.eco_parrot_royal)
    );

    private static final List<MissionTemplate> MISSION_TEMPLATES = Arrays.asList(
            new MissionTemplate(
                    "quick_feed",
                    "Nạp năng lượng nhanh",
                    "Học 5 từ mới trong hôm nay",
                    PROGRESS_WORDS,
                    5,
                    35,
                    18,
                    20,
                    ""
            ),
            new MissionTemplate(
                    "focus_sprint",
                    "Sprint tập trung",
                    "Kiếm 60 XP trong hôm nay",
                    PROGRESS_XP_TODAY,
                    60,
                    45,
                    24,
                    28,
                    ""
            ),
            new MissionTemplate(
                    "streak_guard",
                    "Giữ lửa 3 ngày",
                    "Duy trì streak tối thiểu 3 ngày",
                    PROGRESS_STREAK,
                    3,
                    55,
                    28,
                    36,
                    SKIN_ROYAL
            ),
            new MissionTemplate(
                    "mystery_box",
                    "Hộp bí ẩn Eco",
                    "Đạt 120 XP hôm nay để mở hộp thưởng",
                    PROGRESS_XP_TODAY,
                    120,
                    75,
                    40,
                    52,
                    ""
            )
    );

    private final SharedPreferences preferences;

    public EcoCompanionStore(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        preferences = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        ensureDefaults();
    }

    public static class EcoSkin {
        public final String key;
        public final String name;
        public final String rarity;
        public final String bonus;
        public final int xpCost;
        public final int seedCost;
        public final int requiredLevel;
        @DrawableRes
        public final int drawableRes;

        public EcoSkin(String key,
                       String name,
                       String rarity,
                       String bonus,
                       int xpCost,
                       int seedCost,
                       int requiredLevel,
                       int drawableRes) {
            this.key = safeKey(key);
            this.name = safeText(name);
            this.rarity = safeText(rarity);
            this.bonus = safeText(bonus);
            this.xpCost = Math.max(0, xpCost);
            this.seedCost = Math.max(0, seedCost);
            this.requiredLevel = Math.max(1, requiredLevel);
            this.drawableRes = drawableRes;
        }

        private static String safeKey(String value) {
            return value == null ? "" : value.trim();
        }

        private static String safeText(String value) {
            return value == null ? "" : value.trim();
        }
    }

    public static class EcoMission {
        public final String id;
        public final String title;
        public final String description;
        public final int progress;
        public final int target;
        public final int rewardXp;
        public final int rewardSeeds;
        public final int rewardBondXp;
        public final String rewardSkinKey;
        public final boolean claimed;

        public EcoMission(String id,
                          String title,
                          String description,
                          int progress,
                          int target,
                          int rewardXp,
                          int rewardSeeds,
                          int rewardBondXp,
                          String rewardSkinKey,
                          boolean claimed) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.progress = Math.max(0, progress);
            this.target = Math.max(1, target);
            this.rewardXp = Math.max(0, rewardXp);
            this.rewardSeeds = Math.max(0, rewardSeeds);
            this.rewardBondXp = Math.max(0, rewardBondXp);
            this.rewardSkinKey = rewardSkinKey == null ? "" : rewardSkinKey;
            this.claimed = claimed;
        }

        public boolean isCompleted() {
            return progress >= target;
        }

        public int progressPercent() {
            if (target <= 0) {
                return 0;
            }
            return Math.min(100, Math.max(0, Math.round((progress * 100f) / target)));
        }
    }

    public static class ClaimResult {
        public final boolean success;
        public final String message;
        public final int rewardXp;
        public final int rewardSeeds;
        public final int rewardBondXp;
        public final String unlockedSkinKey;
        public final boolean leveledUp;
        public final int newLevel;

        public ClaimResult(boolean success,
                           String message,
                           int rewardXp,
                           int rewardSeeds,
                           int rewardBondXp,
                           String unlockedSkinKey,
                           boolean leveledUp,
                           int newLevel) {
            this.success = success;
            this.message = message == null ? "" : message;
            this.rewardXp = Math.max(0, rewardXp);
            this.rewardSeeds = Math.max(0, rewardSeeds);
            this.rewardBondXp = Math.max(0, rewardBondXp);
            this.unlockedSkinKey = unlockedSkinKey == null ? "" : unlockedSkinKey;
            this.leveledUp = leveledUp;
            this.newLevel = Math.max(1, newLevel);
        }
    }

    public static class PurchaseResult {
        public final boolean success;
        public final String message;

        public PurchaseResult(boolean success, String message) {
            this.success = success;
            this.message = message == null ? "" : message;
        }
    }

    private static class MissionTemplate {
        final String id;
        final String title;
        final String description;
        final String progressType;
        final int target;
        final int rewardXp;
        final int rewardSeeds;
        final int rewardBondXp;
        final String rewardSkinKey;

        MissionTemplate(String id,
                        String title,
                        String description,
                        String progressType,
                        int target,
                        int rewardXp,
                        int rewardSeeds,
                        int rewardBondXp,
                        String rewardSkinKey) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.progressType = progressType;
            this.target = target;
            this.rewardXp = rewardXp;
            this.rewardSeeds = rewardSeeds;
            this.rewardBondXp = rewardBondXp;
            this.rewardSkinKey = rewardSkinKey;
        }
    }

    private void ensureDefaults() {
        Set<String> owned = preferences.getStringSet(KEY_OWNED_SKINS, null);
        boolean needsInit = owned == null || owned.isEmpty();
        if (!needsInit) {
            String selected = preferences.getString(KEY_SELECTED_SKIN, SKIN_CLASSIC);
            if (selected != null && owned.contains(selected)) {
                return;
            }
        }

        Set<String> defaultOwned = new HashSet<>();
        defaultOwned.add(SKIN_CLASSIC);
        preferences.edit()
                .putStringSet(KEY_OWNED_SKINS, defaultOwned)
                .putString(KEY_SELECTED_SKIN, SKIN_CLASSIC)
                .putInt(KEY_SEEDS, Math.max(25, preferences.getInt(KEY_SEEDS, 25)))
                .apply();
    }

    @NonNull
    public List<EcoSkin> getSkinCatalog() {
        return new ArrayList<>(SKIN_CATALOG);
    }

    @NonNull
    public EcoSkin getSkinByKey(@NonNull String key) {
        for (EcoSkin skin : SKIN_CATALOG) {
            if (skin.key.equals(key)) {
                return skin;
            }
        }
        return SKIN_CATALOG.get(0);
    }

    public boolean isSkinOwned(@NonNull String skinKey) {
        Set<String> owned = getOwnedSkinSet();
        return owned.contains(skinKey);
    }

    @NonNull
    public String getSelectedSkinKey() {
        String selected = preferences.getString(KEY_SELECTED_SKIN, SKIN_CLASSIC);
        if (selected == null || selected.trim().isEmpty()) {
            return SKIN_CLASSIC;
        }
        if (!isSkinOwned(selected)) {
            return SKIN_CLASSIC;
        }
        return selected;
    }

    @NonNull
    public EcoSkin getSelectedSkin() {
        return getSkinByKey(getSelectedSkinKey());
    }

    public void setSelectedSkin(@NonNull String skinKey) {
        if (!isSkinOwned(skinKey)) {
            return;
        }
        preferences.edit().putString(KEY_SELECTED_SKIN, skinKey).apply();
    }

    public void unlockSkin(@NonNull String skinKey) {
        Set<String> owned = getOwnedSkinSet();
        if (owned.add(skinKey)) {
            preferences.edit().putStringSet(KEY_OWNED_SKINS, owned).apply();
        }
    }

    public void unlockSkinAndEquip(@NonNull String skinKey) {
        unlockSkin(skinKey);
        setSelectedSkin(skinKey);
    }

    public int getSeeds() {
        return Math.max(0, preferences.getInt(KEY_SEEDS, 0));
    }

    public int getBondXp() {
        return Math.max(0, preferences.getInt(KEY_BOND_XP, 0));
    }

    public int getTotalClaimedMissions() {
        return Math.max(0, preferences.getInt(KEY_TOTAL_MISSIONS_CLAIMED, 0));
    }

    public int getLevel() {
        int bondXp = getBondXp();
        int level = 1;
        for (int i = 0; i < LEVEL_XP_THRESHOLDS.length; i++) {
            if (bondXp >= LEVEL_XP_THRESHOLDS[i]) {
                level = i + 1;
            } else {
                break;
            }
        }
        return Math.max(1, level);
    }

    public int getCurrentLevelStartXp() {
        int level = getLevel();
        int idx = Math.max(0, Math.min(level - 1, LEVEL_XP_THRESHOLDS.length - 1));
        return LEVEL_XP_THRESHOLDS[idx];
    }

    public int getNextLevelTargetXp() {
        int level = getLevel();
        int nextIdx = Math.min(level, LEVEL_XP_THRESHOLDS.length - 1);
        if (nextIdx == level - 1) {
            return LEVEL_XP_THRESHOLDS[nextIdx] + 500;
        }
        return LEVEL_XP_THRESHOLDS[nextIdx];
    }

    public int getLevelProgressPercent() {
        int start = getCurrentLevelStartXp();
        int next = getNextLevelTargetXp();
        int current = getBondXp();
        int range = Math.max(1, next - start);
        int clamped = Math.max(0, Math.min(range, current - start));
        return Math.min(100, Math.round((clamped * 100f) / range));
    }

    public String getEvolutionStageLabel() {
        int level = getLevel();
        if (level <= 2) {
            return "Mầm cánh";
        }
        if (level <= 4) {
            return "Hộ vệ bầu trời";
        }
        if (level <= 6) {
            return "Phi công tinh anh";
        }
        return "Thần thú Eco";
    }

    public String resolveMoodLabel(@NonNull AppRepository.DashboardSnapshot snapshot) {
        int xpToday = snapshot.userProgress != null ? Math.max(0, snapshot.userProgress.xpTodayEarned) : 0;
        int streak = snapshot.userProgress != null ? Math.max(0, snapshot.userProgress.currentStreak) : 0;

        if (xpToday <= 0) {
            return "Đang đói năng lượng";
        }
        if (streak >= 7) {
            return "Cực phấn khích";
        }
        if (xpToday >= 120) {
            return "Trạng thái bùng nổ";
        }
        if (xpToday >= 60) {
            return "Rất vui vẻ";
        }
        return "Tò mò khám phá";
    }

    @NonNull
    public List<EcoMission> getTodayMissions(@NonNull AppRepository.DashboardSnapshot snapshot) {
        String todayKey = getTodayKey();
        Set<String> claimed = getClaimedMissionSet(todayKey);
        List<EcoMission> missions = new ArrayList<>();

        for (MissionTemplate template : MISSION_TEMPLATES) {
            int progress = resolveMissionProgress(template.progressType, snapshot);
            String rewardSkinKey = shouldOfferRewardSkin(template.rewardSkinKey) ? template.rewardSkinKey : "";
            missions.add(new EcoMission(
                    template.id,
                    template.title,
                    template.description,
                    progress,
                    template.target,
                    template.rewardXp,
                    template.rewardSeeds,
                    template.rewardBondXp,
                    rewardSkinKey,
                    claimed.contains(template.id)
            ));
        }

        return missions;
    }

    @NonNull
    public ClaimResult claimMission(@NonNull String missionId, @NonNull AppRepository.DashboardSnapshot snapshot) {
        EcoMission mission = null;
        for (EcoMission item : getTodayMissions(snapshot)) {
            if (item.id.equals(missionId)) {
                mission = item;
                break;
            }
        }

        if (mission == null) {
            return new ClaimResult(false, "Không tìm thấy nhiệm vụ", 0, 0, 0, "", false, getLevel());
        }
        if (mission.claimed) {
            return new ClaimResult(false, "Nhiệm vụ đã nhận thưởng", 0, 0, 0, "", false, getLevel());
        }
        if (!mission.isCompleted()) {
            return new ClaimResult(false, "Nhiệm vụ chưa hoàn thành", 0, 0, 0, "", false, getLevel());
        }

        int oldLevel = getLevel();
        int bonusSeeds = getSelectedSkin().key.equals(SKIN_SOLAR) ? 5 : 0;
        int finalSeeds = mission.rewardSeeds + bonusSeeds;
        int finalBondXp = mission.rewardBondXp;
        if (getSelectedSkin().key.equals(SKIN_NEBULA)) {
            finalBondXp = Math.round(finalBondXp * 1.05f);
        }

        int updatedSeeds = Math.max(0, getSeeds() + finalSeeds);
        int updatedBondXp = Math.max(0, getBondXp() + finalBondXp);

        String todayKey = getTodayKey();
        Set<String> claimedSet = getClaimedMissionSet(todayKey);
        claimedSet.add(mission.id);

        String unlockedSkinKey = "";
        if (!mission.rewardSkinKey.isEmpty() && !isSkinOwned(mission.rewardSkinKey)) {
            unlockSkin(mission.rewardSkinKey);
            unlockedSkinKey = mission.rewardSkinKey;
        }

        preferences.edit()
                .putInt(KEY_SEEDS, updatedSeeds)
                .putInt(KEY_BOND_XP, updatedBondXp)
                .putInt(KEY_TOTAL_MISSIONS_CLAIMED, getTotalClaimedMissions() + 1)
                .putStringSet(KEY_CLAIMED_MISSIONS_PREFIX + todayKey, claimedSet)
                .apply();

        int newLevel = getLevel();
        boolean leveledUp = newLevel > oldLevel;

        StringBuilder messageBuilder = new StringBuilder();
        messageBuilder.append(String.format(Locale.getDefault(), "+%d XP, +%d Seeds", mission.rewardXp, finalSeeds));
        if (leveledUp) {
            messageBuilder.append(String.format(Locale.getDefault(), " | Lên Lv.%d", newLevel));
        }
        if (!unlockedSkinKey.isEmpty()) {
            messageBuilder.append(" | Mở skin mới");
        }

        return new ClaimResult(
                true,
                messageBuilder.toString(),
                mission.rewardXp,
                finalSeeds,
                finalBondXp,
                unlockedSkinKey,
                leveledUp,
                newLevel
        );
    }

    @NonNull
    public PurchaseResult purchaseSkinWithSeeds(@NonNull String skinKey) {
        EcoSkin skin = getSkinByKey(skinKey);
        if (skin.seedCost <= 0) {
            return new PurchaseResult(false, "Skin này không mua bằng Seeds");
        }
        if (isSkinOwned(skin.key)) {
            return new PurchaseResult(true, "Skin đã sở hữu");
        }
        if (getLevel() < skin.requiredLevel) {
            return new PurchaseResult(false, "Chưa đủ cấp độ để mở skin");
        }

        int currentSeeds = getSeeds();
        if (currentSeeds < skin.seedCost) {
            return new PurchaseResult(false, "Không đủ Seeds để mở skin");
        }

        unlockSkinAndEquip(skin.key);
        preferences.edit().putInt(KEY_SEEDS, Math.max(0, currentSeeds - skin.seedCost)).apply();
        return new PurchaseResult(true, "Đã mở skin bằng Seeds");
    }

    private int resolveMissionProgress(@NonNull String progressType, @NonNull AppRepository.DashboardSnapshot snapshot) {
        if (PROGRESS_WORDS.equals(progressType)) {
            return Math.max(0, snapshot.wordsLearnedToday);
        }
        if (PROGRESS_XP_TODAY.equals(progressType)) {
            return snapshot.userProgress == null ? 0 : Math.max(0, snapshot.userProgress.xpTodayEarned);
        }
        if (PROGRESS_STREAK.equals(progressType)) {
            return snapshot.userProgress == null ? 0 : Math.max(0, snapshot.userProgress.currentStreak);
        }
        return 0;
    }

    private boolean shouldOfferRewardSkin(String rewardSkinKey) {
        if (rewardSkinKey == null || rewardSkinKey.trim().isEmpty()) {
            return false;
        }
        return !isSkinOwned(rewardSkinKey.trim());
    }

    @NonNull
    private Set<String> getOwnedSkinSet() {
        Set<String> set = preferences.getStringSet(KEY_OWNED_SKINS, null);
        Set<String> safeSet = set == null ? new HashSet<>() : new HashSet<>(set);
        safeSet.add(SKIN_CLASSIC);
        return safeSet;
    }

    @NonNull
    private Set<String> getClaimedMissionSet(@NonNull String dayKey) {
        Set<String> set = preferences.getStringSet(KEY_CLAIMED_MISSIONS_PREFIX + dayKey, null);
        return set == null ? new HashSet<>() : new HashSet<>(set);
    }

    @NonNull
    private String getTodayKey() {
        Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int dayOfYear = calendar.get(Calendar.DAY_OF_YEAR);
        return year + "_" + dayOfYear;
    }
}
