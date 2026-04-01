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
import com.example.englishflow.database.entity.LearnedWordEntity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import android.speech.tts.TextToSpeech;

public class LearnedWordsActivity extends AppCompatActivity {

    private AppRepository repository;
    private RecyclerView rvLearnedWords;
    private LearnedWordsAdapter adapter;
    private TextView txtTotalWords, txtTotalDomains;
    private TextToSpeech textToSpeech;

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
        txtTotalWords = findViewById(R.id.txtTotalWords);
        txtTotalDomains = findViewById(R.id.txtTotalDomains);

        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(Locale.US);
                textToSpeech.setSpeechRate(0.9f);
            }
        });
        
        adapter = new LearnedWordsAdapter(new ArrayList<>(), new LearnedWordsAdapter.DictionaryActionListener() {
            @Override
            public void onPronounce(LearnedWordEntity wordEntry) {
                if (textToSpeech != null) {
                    textToSpeech.speak(wordEntry.word, TextToSpeech.QUEUE_FLUSH, null, "dictionary-word");
                }
            }

            @Override
            public void onDelete(LearnedWordEntity wordEntry) {
                repository.removeWord(new com.example.englishflow.data.WordEntry(
                        wordEntry.word, wordEntry.ipa, wordEntry.meaning, wordEntry.wordType,
                        wordEntry.example, wordEntry.exampleVi,
                        wordEntry.usage != null ? wordEntry.usage : "",
                        wordEntry.domain, wordEntry.note
                ));
                loadData();
                Toast.makeText(LearnedWordsActivity.this, "Đã xóa từ: " + wordEntry.word, Toast.LENGTH_SHORT).show();
            }
        });
        rvLearnedWords.setAdapter(adapter);
    }

    private void loadData() {
        repository.getLearnedWordEntitiesAsync(words -> {
            if (words == null || words.isEmpty()) {
                txtTotalWords.setText("0");
                txtTotalDomains.setText("0");
                adapter.updateData(new ArrayList<>());
                Toast.makeText(this, "Chưa có từ nào đã học", Toast.LENGTH_SHORT).show();
                return;
            }

            txtTotalWords.setText(String.valueOf(words.size()));
            
            // Sort descending by time
            Collections.sort(words, (w1, w2) -> Long.compare(w2.learnedAt, w1.learnedAt));
            
            // Group by Date
            List<Object> displayItems = groupWordsByDate(words);
            adapter.updateData(displayItems);
        });
    }

    private List<Object> groupWordsByDate(List<LearnedWordEntity> words) {
        Map<String, List<LearnedWordEntity>> grouped = new LinkedHashMap<>();
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", new Locale("vi", "VN"));
        Calendar cal = Calendar.getInstance();
        String todayString = sdf.format(cal.getTime());
        cal.add(Calendar.DAY_OF_YEAR, -1);
        String yesterdayString = sdf.format(cal.getTime());

        for (LearnedWordEntity word : words) {
            String dateString;
            if (word.learnedAt > 0) {
                dateString = sdf.format(new Date(word.learnedAt));
            } else {
                dateString = todayString; // Default to today if missing timestamp
            }
            
            String groupHeader = dateString;
            if (dateString.equals(todayString)) {
                groupHeader = "Hôm nay, " + dateString;
            } else if (dateString.equals(yesterdayString)) {
                groupHeader = "Hôm qua, " + dateString;
            }
            
            if (!grouped.containsKey(groupHeader)) {
                grouped.put(groupHeader, new ArrayList<>());
            }
            grouped.get(groupHeader).add(word);
        }

        // Count unique domains inside these words to populate txtTotalDomains
        Map<String, Integer> domainCount = new HashMap<>();
        for (LearnedWordEntity w : words) {
            domainCount.put(w.domain != null ? w.domain : "Khác", 1);
        }
        txtTotalDomains.setText(String.valueOf(domainCount.size()));

        List<Object> items = new ArrayList<>();
        for (Map.Entry<String, List<LearnedWordEntity>> entry : grouped.entrySet()) {
            items.add("Ngày lưu: " + entry.getKey()); 
            items.addAll(entry.getValue());
        }
        return items;
    }

    @Override
    protected void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }
}
