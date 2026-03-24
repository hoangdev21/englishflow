package com.example.englishflow.ui;

import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.englishflow.R;
import com.example.englishflow.data.AppRepository;
import com.example.englishflow.database.entity.CustomVocabularyEntity;
import com.example.englishflow.ui.adapters.CustomVocabularyManageAdapter;
import com.example.englishflow.ui.adapters.FailedLabelLogAdapter;

public class CustomVocabularyManagerActivity extends AppCompatActivity {

    private AppRepository repository;
    private CustomVocabularyManageAdapter customAdapter;
    private FailedLabelLogAdapter failedLabelAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_custom_vocabulary_manager);

        repository = AppRepository.getInstance(this);

        RecyclerView customRecycler = findViewById(R.id.recyclerCustomVocabulary);
        RecyclerView failedRecycler = findViewById(R.id.recyclerFailedLabels);

        customAdapter = new CustomVocabularyManageAdapter(new CustomVocabularyManageAdapter.ActionListener() {
            @Override
            public void onEdit(CustomVocabularyEntity item) {
                showEditMeaningDialog(item);
            }

            @Override
            public void onDelete(CustomVocabularyEntity item) {
                boolean deleted = repository.deleteCustomVocabulary(item.word);
                Toast.makeText(CustomVocabularyManagerActivity.this,
                        deleted ? "Da xoa: " + item.word : "Khong xoa duoc tu",
                        Toast.LENGTH_SHORT).show();
                loadData();
            }

            @Override
            public void onToggleLock(CustomVocabularyEntity item) {
                boolean ok = repository.setCustomVocabularyLocked(item.word, !item.isLocked);
                Toast.makeText(CustomVocabularyManagerActivity.this,
                        ok ? (!item.isLocked ? "Da khoa" : "Da mo khoa") : "Cap nhat that bai",
                        Toast.LENGTH_SHORT).show();
                loadData();
            }
        });
        failedLabelAdapter = new FailedLabelLogAdapter();

        customRecycler.setLayoutManager(new LinearLayoutManager(this));
        customRecycler.setAdapter(customAdapter);

        failedRecycler.setLayoutManager(new LinearLayoutManager(this));
        failedRecycler.setAdapter(failedLabelAdapter);

        loadData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }

    private void loadData() {
        customAdapter.submitList(repository.getAllCustomVocabulary());
        failedLabelAdapter.submitList(repository.getTopFailedLabels(20));
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

                    boolean updated = repository.updateCustomMeaning(item.word, newMeaning, false);
                    if (!updated && item.isLocked) {
                        showForceUpdateDialog(item.word, newMeaning);
                        return;
                    }

                    Toast.makeText(this, updated ? "Da cap nhat" : "Khong cap nhat duoc", Toast.LENGTH_SHORT).show();
                    loadData();
                })
                .show();
    }

    private void showForceUpdateDialog(@NonNull String word, @NonNull String meaning) {
        new AlertDialog.Builder(this)
                .setTitle("Tu dang bi khoa")
                .setMessage("Tu nay dang bi khoa. Ban co muon cap nhat bat chap khoa khong?")
                .setNegativeButton("Khong", null)
                .setPositiveButton("Cap nhat", (dialog, which) -> {
                    boolean forced = repository.updateCustomMeaning(word, meaning, true);
                    Toast.makeText(this, forced ? "Da cap nhat (force)" : "Cap nhat that bai", Toast.LENGTH_SHORT).show();
                    loadData();
                })
                .show();
    }
}
