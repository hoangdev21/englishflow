package com.example.englishflow.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.englishflow.R;
import com.example.englishflow.data.AppRepository;
import com.example.englishflow.database.entity.CustomVocabularyEntity;
import com.example.englishflow.ui.adapters.CustomVocabularyManageAdapter;
import com.example.englishflow.ui.adapters.FailedLabelLogAdapter;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class CustomVocabularyManagerActivity extends AppCompatActivity {

    private AppRepository repository;
    private CustomVocabularyManageAdapter customAdapter;
    private FailedLabelLogAdapter failedLabelAdapter;
    private TextView txtNotebookEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_custom_vocabulary_manager);

        repository = AppRepository.getInstance(this);

        RecyclerView customRecycler = findViewById(R.id.recyclerCustomVocabulary);
        RecyclerView failedRecycler = findViewById(R.id.recyclerFailedLabels);
        txtNotebookEmpty = findViewById(R.id.txtNotebookEmpty);

        customAdapter = new CustomVocabularyManageAdapter(new CustomVocabularyManageAdapter.ActionListener() {
            @Override
            public void onEdit(CustomVocabularyEntity item) {
                showEditMeaningDialog(item);
            }

            @Override
            public void onDelete(CustomVocabularyEntity item) {
                repository.deleteLearnedWordAsync(item.word, deleted -> Toast.makeText(
                        CustomVocabularyManagerActivity.this,
                        deleted ? "Da xoa: " + item.word : "Khong xoa duoc tu",
                        Toast.LENGTH_SHORT
                ).show());
            }

            @Override
            public void onToggleLock(CustomVocabularyEntity item) {
                Toast.makeText(CustomVocabularyManagerActivity.this,
                        "Nguon: " + formatSource(item.source) + " / " + safeText(item.domain, "general"),
                        Toast.LENGTH_SHORT).show();
            }
        });
        failedLabelAdapter = new FailedLabelLogAdapter();

        customRecycler.setLayoutManager(new LinearLayoutManager(this));
        customRecycler.setHasFixedSize(true);
        customRecycler.setItemAnimator(null);
        customRecycler.setAdapter(customAdapter);

        failedRecycler.setLayoutManager(new LinearLayoutManager(this));
        failedRecycler.setAdapter(failedLabelAdapter);

        findViewById(R.id.btnBackAction).setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        setupEdgeToEdge();

        repository.getUserNotebookVocabularyLive().observe(this, notebookItems -> {
            List<CustomVocabularyEntity> safeItems = notebookItems == null ? Collections.emptyList() : notebookItems;
            if (customAdapter != null) {
                customAdapter.submitList(safeItems);
            }
            bindNotebookEmptyState(safeItems);
        });

        repository.getTopFailedLabelsLive(20).observe(this, failedList -> {
            if (failedLabelAdapter != null) failedLabelAdapter.submitList(failedList);
        });
    }

    private void setupEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);

        View root = findViewById(R.id.rootCustomVocabulary);
        View hero = findViewById(R.id.heroHeader);
        View contentScroll = findViewById(R.id.contentScroll);

        if (root == null || hero == null || contentScroll == null) {
            return;
        }

        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), root);
        if (controller != null) {
            controller.setAppearanceLightStatusBars(false);
        }

        final int heroBaseTop = hero.getPaddingTop();
        final int contentBaseBottom = contentScroll.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            hero.setPadding(
                    hero.getPaddingLeft(),
                    heroBaseTop + bars.top,
                    hero.getPaddingRight(),
                    hero.getPaddingBottom()
            );
            contentScroll.setPadding(
                    contentScroll.getPaddingLeft(),
                    contentScroll.getPaddingTop(),
                    contentScroll.getPaddingRight(),
                    contentBaseBottom + bars.bottom
            );
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    private void bindNotebookEmptyState(List<CustomVocabularyEntity> notebookItems) {
        boolean isEmpty = notebookItems == null || notebookItems.isEmpty();
        if (txtNotebookEmpty != null) {
            txtNotebookEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        }
    }

    private void showEditMeaningDialog(@NonNull CustomVocabularyEntity item) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setText(item.meaning == null ? "" : item.meaning);

        new AlertDialog.Builder(this)
                .setTitle("Sua nghia: " + item.word)
                .setView(input)
                .setNegativeButton("Huy", null)
                .setPositiveButton("Luu", (dialog, which) -> {
                    String newMeaning = input.getText() == null ? "" : input.getText().toString().trim();
                    if (newMeaning.isEmpty()) {
                        Toast.makeText(this, "Nghia khong duoc de trong", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    repository.updateLearnedWordMeaningAsync(item.word, newMeaning, updated ->
                            Toast.makeText(
                                    this,
                                    updated ? "Da cap nhat" : "Khong cap nhat duoc",
                                    Toast.LENGTH_SHORT
                            ).show());
                })
                .show();
    }

    private String safeText(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim();
    }

    private String formatSource(String source) {
        String normalized = source == null ? "" : source.trim().toLowerCase(Locale.US);
        switch (normalized) {
            case "chat":
                return "chat";
            case "scan":
                return "scan";
            case "journey":
                return "hanh trinh";
            case "dictionary":
                return "tu dien";
            default:
                return "saved";
        }
    }
}
