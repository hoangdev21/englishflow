package com.example.englishflow.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.bumptech.glide.Glide;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.englishflow.R;
import com.example.englishflow.data.AppRepository;
import com.example.englishflow.data.LeaderboardItem;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class LeaderboardActivity extends AppCompatActivity {

    private AppRepository repository;
    private RecyclerView rvLeaderboard;
    private LeaderboardAdapter adapter;
    private List<LeaderboardItem> allItems = new ArrayList<>();
    private static final java.text.DecimalFormat scoreFormatter = new java.text.DecimalFormat("#,###");
    
    private MaterialButton btnDay, btnWeek, btnMonth;
    private View podium1, podium2, podium3;
    private TextView r1Name, r1Score, r1Initial;
    private TextView r2Name, r2Score, r2Initial;
    private TextView r3Name, r3Score, r3Initial;
    private View stickyMyRank;
    private TextView myRankTxt, myNameTxt, myScoreTxt, myInitialTxt;
    private ImageView myAvatarImg, r1AvatarImg, r2AvatarImg, r3AvatarImg;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_leaderboard);

        repository = AppRepository.getInstance(this);
        initViews();
        applyWindowInsets();
        loadData("today");
    }

    private void initViews() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        
        rvLeaderboard = findViewById(R.id.rvLeaderboard);
        rvLeaderboard.setLayoutManager(new LinearLayoutManager(this));
        adapter = new LeaderboardAdapter(new ArrayList<>(), repository.getCurrentEmail());
        rvLeaderboard.setAdapter(adapter);

        // Podium views
        podium1 = findViewById(R.id.podium1);
        podium2 = findViewById(R.id.podium2);
        podium3 = findViewById(R.id.podium3);
        
        r1Name = findViewById(R.id.rank1Name);
        r1Score = findViewById(R.id.rank1Score);
        r1Initial = findViewById(R.id.rank1Initial);
        r1AvatarImg = findViewById(R.id.rank1Avatar);
        
        r2Name = findViewById(R.id.rank2Name);
        r2Score = findViewById(R.id.rank2Score);
        r2Initial = findViewById(R.id.rank2Initial);
        r2AvatarImg = findViewById(R.id.rank2Avatar);
        
        r3Name = findViewById(R.id.rank3Name);
        r3Score = findViewById(R.id.rank3Score);
        r3Initial = findViewById(R.id.rank3Initial);
        r3AvatarImg = findViewById(R.id.rank3Avatar);

        // Period buttons
        btnDay = findViewById(R.id.btnDay);
        btnWeek = findViewById(R.id.btnWeek);
        btnMonth = findViewById(R.id.btnMonth);

        btnDay.setOnClickListener(v -> selectPeriod("today", btnDay));
        btnWeek.setOnClickListener(v -> selectPeriod("all", btnWeek)); // Mock "all" for week for now
        btnMonth.setOnClickListener(v -> selectPeriod("all", btnMonth));

        // Sticky user rank
        stickyMyRank = findViewById(R.id.stickyMyRank);
        myRankTxt = findViewById(R.id.myRank);
        myNameTxt = findViewById(R.id.myName);
        myScoreTxt = findViewById(R.id.myScore);
        myInitialTxt = findViewById(R.id.myInitial);
        myAvatarImg = findViewById(R.id.myAvatar);
    }

    private void applyWindowInsets() {
        View header = findViewById(R.id.leaderboardHeader);
        ViewCompat.setOnApplyWindowInsetsListener(header, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), insets.top + (int)(16 * getResources().getDisplayMetrics().density), v.getPaddingRight(), v.getPaddingBottom());
            return windowInsets;
        });
        
        View footer = findViewById(R.id.stickyMyRank);
        ViewCompat.setOnApplyWindowInsetsListener(footer, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), (int)(16 * getResources().getDisplayMetrics().density), v.getPaddingRight(), insets.bottom + (int)(24 * getResources().getDisplayMetrics().density));
            return windowInsets;
        });
    }

    private void selectPeriod(String period, MaterialButton selectedBtn) {
        // Reset all buttons
        MaterialButton[] btns = {btnDay, btnWeek, btnMonth};
        for (MaterialButton b : btns) {
            b.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.ef_surface));
            b.setTextColor(ContextCompat.getColor(this, R.color.ef_text_secondary));
            b.setStrokeWidth(1);
        }
        
        // Style selected
        selectedBtn.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.ef_primary));
        selectedBtn.setTextColor(ContextCompat.getColor(this, R.color.white));
        selectedBtn.setStrokeWidth(0);

        loadData(period);
    }

    private void loadData(String period) {
        repository.getLeaderboardAsync(period, items -> {
            allItems = items;
            updatePodium(items);
            
            // Show EVERYONE in the list (rank 1 to N) as requested for a clear table look
            adapter.updateData(items);
            updateMyRank(items);

            if (items.isEmpty()) {
                Toast.makeText(this, "Chưa có dữ liệu xếp hạng", Toast.LENGTH_SHORT).show();
                stickyMyRank.setVisibility(View.GONE);
            } else {
                stickyMyRank.setVisibility(View.VISIBLE);
            }
        });
    }

    private void updateMyRank(List<LeaderboardItem> items) {
        String myEmail = repository.getCurrentEmail();
        for (LeaderboardItem item : items) {
            if (myEmail.equals(item.email)) {
                myRankTxt.setText(String.valueOf(item.rank));
                myNameTxt.setText(item.name);
                myScoreTxt.setText(scoreFormatter.format(item.score) + " XP");
                loadAvatar(item.avatarPath, myAvatarImg, myInitialTxt, item.name);
                return;
            }
        }
    }

    private void updatePodium(List<LeaderboardItem> items) {
        podium1.setVisibility(View.INVISIBLE);
        podium2.setVisibility(View.INVISIBLE);
        podium3.setVisibility(View.INVISIBLE);

        if (items.size() >= 1) {
            LeaderboardItem i = items.get(0);
            r1Name.setText(i.name);
            r1Score.setText(scoreFormatter.format(i.score) + " XP");
            loadAvatar(i.avatarPath, r1AvatarImg, r1Initial, i.name);
            podium1.setVisibility(View.VISIBLE);
        }
        
        if (items.size() >= 2) {
            LeaderboardItem i = items.get(1);
            r2Name.setText(i.name);
            r2Score.setText(scoreFormatter.format(i.score) + " XP");
            loadAvatar(i.avatarPath, r2AvatarImg, r2Initial, i.name);
            podium2.setVisibility(View.VISIBLE);
        }

        if (items.size() >= 3) {
            LeaderboardItem i = items.get(2);
            r3Name.setText(i.name);
            r3Score.setText(scoreFormatter.format(i.score) + " XP");
            loadAvatar(i.avatarPath, r3AvatarImg, r3Initial, i.name);
            podium3.setVisibility(View.VISIBLE);
        }
    }

    private void loadAvatar(String path, ImageView img, TextView fallback, String name) {
        if (path != null && !path.isEmpty()) {
            img.setVisibility(View.VISIBLE);
            fallback.setVisibility(View.GONE);
            Glide.with(this)
                .load(path)
                .placeholder(R.drawable.user_avatar)
                .circleCrop()
                .into(img);
        } else {
            img.setVisibility(View.GONE);
            fallback.setVisibility(View.VISIBLE);
            fallback.setText(getInitial(name));
        }
    }

    private String getInitial(String name) {
        if (name == null || name.isEmpty()) return "?";
        return name.substring(0, 1).toUpperCase();
    }
}
