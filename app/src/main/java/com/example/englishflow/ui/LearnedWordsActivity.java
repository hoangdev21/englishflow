package com.example.englishflow.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.englishflow.R;
import com.example.englishflow.data.AppRepository;
import com.example.englishflow.data.WordEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class LearnedWordsActivity extends AppCompatActivity {

    private AppRepository repository;
    private RecyclerView rvLearnedWords;
    private LearnedWordsAdapter adapter;
    private TextView txtTotalWords, txtTotalDomains;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_learned_words);

        repository = AppRepository.getInstance(this);
        initViews();
        loadData();
    }

    private void initViews() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        
        rvLearnedWords = findViewById(R.id.rvLearnedWords);
        rvLearnedWords.setLayoutManager(new LinearLayoutManager(this));
        adapter = new LearnedWordsAdapter(new ArrayList<>());
        rvLearnedWords.setAdapter(adapter);

        txtTotalWords = findViewById(R.id.txtTotalWords);
        txtTotalDomains = findViewById(R.id.txtTotalDomains);
    }

    private void loadData() {
        repository.getSavedWordsAsync(words -> {
            if (words == null || words.isEmpty()) {
                Toast.makeText(this, "Chưa có từ nào đã học", Toast.LENGTH_SHORT).show();
                return;
            }

            txtTotalWords.setText(String.valueOf(words.size()));
            
            // Group and sort
            List<Object> displayItems = groupWordsByDomain(words);
            adapter.updateData(displayItems);
        });
    }

    private List<Object> groupWordsByDomain(List<WordEntry> words) {
        // Grouping by domain
        Map<String, List<WordEntry>> grouped = new TreeMap<>();
        for (WordEntry word : words) {
            String category = word.getCategory();
            String domain = category != null && !category.trim().isEmpty() ? category : "Khác";
            if (!grouped.containsKey(domain)) {
                grouped.put(domain, new ArrayList<>());
            }
            grouped.get(domain).add(word);
        }

        txtTotalDomains.setText(String.valueOf(grouped.size()));

        List<Object> items = new ArrayList<>();
        for (Map.Entry<String, List<WordEntry>> entry : grouped.entrySet()) {
            items.add("Domain: " + entry.getKey()); // Header string token
            items.addAll(entry.getValue());
        }
        return items;
    }
}
