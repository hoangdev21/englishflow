package com.example.englishflow.ui;

import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.englishflow.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class IpaActivity extends AppCompatActivity {

    private TextToSpeech tts;
    private final Handler playbackHandler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ipa);

        setupHeader();
        setupIpaGrids();
        setupTts();
    }

    private void setupHeader() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    private void setupIpaGrids() {
        // Vowels
        RecyclerView vowelGrid = findViewById(R.id.gridVowels);
        vowelGrid.setLayoutManager(new GridLayoutManager(this, 3));
        vowelGrid.setNestedScrollingEnabled(false);
        vowelGrid.setAdapter(new IpaAdapter(getVowels(), this::onSymbolClicked));

        // Consonants
        RecyclerView consonantGrid = findViewById(R.id.gridConsonants);
        consonantGrid.setLayoutManager(new GridLayoutManager(this, 3));
        consonantGrid.setNestedScrollingEnabled(false);
        consonantGrid.setAdapter(new IpaAdapter(getConsonants(), this::onSymbolClicked));
    }

    private void setupTts() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.US);
            }
        });
    }

    private void onSymbolClicked(IpaItem item) {
        if (tts != null) {
            // Get a speakable version of the symbol (some IPA chars are silent in TTS)
            String speakable = getSpeakablePhoneme(item.symbol);
            
            // First speak the phoneme
            tts.speak(speakable, TextToSpeech.QUEUE_FLUSH, null, "phoneme");
            
            // Wait brief moment then speak the example word
            playbackHandler.postDelayed(() -> {
                tts.speak(item.word, TextToSpeech.QUEUE_ADD, null, "example_word");
            }, 1000); // Increased delay for better clarity
        }
    }

    /**
     * Maps complex IPA symbols to strings that standard TTS engines can pronounce accurately.
     */
    private String getSpeakablePhoneme(String ipa) {
        String clean = ipa.trim().toLowerCase();
        
        // Vowels mapping
        if (clean.equals("ɑ:")) return "ah";
        if (clean.equals("æ")) return "aah";
        if (clean.equals("ʌ")) return "uh";
        if (clean.equals("ɛ")) return "eh";
        if (clean.equals("eɪ")) return "ay";
        if (clean.equals("ɜ:")) return "er";
        if (clean.equals("ɪ")) return "ee";
        if (clean.equals("i:")) return "eee";
        if (clean.equals("ə")) return "uh";
        if (clean.equals("oʊ")) return "oh";
        if (clean.equals("ʊ")) return "uuh";
        if (clean.equals("u:")) return "ooo";
        if (clean.equals("aʊ")) return "ow";
        if (clean.equals("aɪ")) return "eye";
        if (clean.equals("ɔɪ")) return "oy";
        if (clean.equals("ɔ:")) return "aw";

        // Consonants mapping
        if (clean.equals("tʃ")) return "chah";
        if (clean.equals("dʒ")) return "jah";
        if (clean.equals("ŋ")) return "ng";
        if (clean.equals("ʒ")) return "zh";
        if (clean.equals("ʃ")) return "sh";
        if (clean.equals("ð")) return "th";
        if (clean.equals("θ")) return "th";
        if (clean.equals("j")) return "yah";
        
        return clean;
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        playbackHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private List<IpaItem> getVowels() {
        List<IpaItem> list = new ArrayList<>();
        list.add(new IpaItem("ɑ:", "father"));
        list.add(new IpaItem("æ", "cat"));
        list.add(new IpaItem("ʌ", "cup"));
        list.add(new IpaItem("ɛ", "bed"));
        list.add(new IpaItem("eɪ", "say"));
        list.add(new IpaItem("ɜ:", "her"));
        list.add(new IpaItem("ɪ", "sit"));
        list.add(new IpaItem("i:", "sheep"));
        list.add(new IpaItem(" ə ", "about"));
        list.add(new IpaItem("oʊ", "go"));
        list.add(new IpaItem("ʊ", "book"));
        list.add(new IpaItem("u:", "blue"));
        list.add(new IpaItem("aʊ", "now"));
        list.add(new IpaItem("aɪ", "my"));
        list.add(new IpaItem("ɔɪ", "boy"));
        list.add(new IpaItem("ɔ:", "law"));
        return list;
    }

    private List<IpaItem> getConsonants() {
        List<IpaItem> list = new ArrayList<>();
        list.add(new IpaItem("b", "bat"));
        list.add(new IpaItem("tʃ", "check"));
        list.add(new IpaItem("d", "do"));
        list.add(new IpaItem("f", "fish"));
        list.add(new IpaItem("g", "go"));
        list.add(new IpaItem("h", "house"));
        list.add(new IpaItem("dʒ", "just"));
        list.add(new IpaItem("k", "come"));
        list.add(new IpaItem("l", "lion"));
        list.add(new IpaItem("m", "man"));
        list.add(new IpaItem("n", "no"));
        list.add(new IpaItem("ŋ", "sing"));
        list.add(new IpaItem("p", "pen"));
        list.add(new IpaItem("r", "red"));
        list.add(new IpaItem("s", "sea"));
        list.add(new IpaItem("ʒ", "measure"));
        list.add(new IpaItem("ʃ", "she"));
        list.add(new IpaItem("t", "two"));
        list.add(new IpaItem("ð", "this"));
        list.add(new IpaItem("θ", "think"));
        list.add(new IpaItem("v", "voice"));
        list.add(new IpaItem("w", "we"));
        list.add(new IpaItem("j", "yes"));
        list.add(new IpaItem("z", "zero"));
        return list;
    }

    public static class IpaItem {
        final String symbol;
        final String word;
        public IpaItem(String symbol, String word) {
            this.symbol = symbol;
            this.word = word;
        }
    }

    private interface OnSymbolClickListener {
        void onClicked(IpaItem item);
    }

    private static class IpaAdapter extends RecyclerView.Adapter<IpaAdapter.ViewHolder> {
        private final List<IpaItem> items;
        private final OnSymbolClickListener listener;

        IpaAdapter(List<IpaItem> items, OnSymbolClickListener listener) {
            this.items = items;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_ipa_card, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            IpaItem item = items.get(position);
            holder.txtSymbol.setText(item.symbol);
            holder.txtWord.setText(item.word);
            holder.itemView.setOnClickListener(v -> listener.onClicked(item));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            final TextView txtSymbol;
            final TextView txtWord;
            ViewHolder(View itemView) {
                super(itemView);
                txtSymbol = itemView.findViewById(R.id.ipaSymbol);
                txtWord = itemView.findViewById(R.id.ipaWord);
            }
        }
    }
}
