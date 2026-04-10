package com.example.englishflow.ui.adapters;

import android.content.Context;
import android.text.Html;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import com.example.englishflow.R;
import com.example.englishflow.data.AppRepository;
import com.example.englishflow.data.AppSettingsStore;
import com.example.englishflow.data.ChatItem;
import com.example.englishflow.data.WordEntry;
import com.google.android.material.button.MaterialButton;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final List<ChatItem> messages;
    private static final ExecutorService IO_EXECUTOR = Executors.newSingleThreadExecutor();
    private final LruCache<String, CharSequence> formattedMessageCache = new LruCache<>(200);
    private AppSettingsStore settingsStore;

    public ChatAdapter(List<ChatItem> messages) {
        this.messages = messages;
    }

    /** Callback for speaking a word — provided by Fragment to route through VoiceFlowEngine */
    public interface SpeakCallback {
        void speak(String text);
    }

    private SpeakCallback speakCallback;

    public void setSpeakCallback(SpeakCallback cb) {
        this.speakCallback = cb;
    }

    /** Legacy TTS kept ONLY as fallback when no engine callback is set */
    private android.speech.tts.TextToSpeech fallbackTts;
    private boolean fallbackTtsReady = false;

    public void initTts(android.content.Context context) {
        if (fallbackTts != null) return;
        fallbackTts = new android.speech.tts.TextToSpeech(context.getApplicationContext(), status -> {
            if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                int r = fallbackTts.setLanguage(java.util.Locale.US);
                fallbackTtsReady = (r != android.speech.tts.TextToSpeech.LANG_MISSING_DATA
                        && r != android.speech.tts.TextToSpeech.LANG_NOT_SUPPORTED);
            }
        });
    }

    public void shutdownTts() {
        if (fallbackTts != null) {
            fallbackTts.stop();
            fallbackTts.shutdown();
            fallbackTts = null;
            fallbackTtsReady = false;
        }
    }

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).getRole();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == ChatItem.ROLE_USER) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_user, parent, false);
            return new UserViewHolder(view);
        }
        if (viewType == ChatItem.ROLE_TYPING) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_typing, parent, false);
            return new TypingViewHolder(view);
        }
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_ai, parent, false);
        return new AiViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatItem item = messages.get(position);

        if (holder instanceof UserViewHolder) {
            UserViewHolder userHolder = (UserViewHolder) holder;
            userHolder.message.setText(item.getMessage());

            // SYNC AVATAR FROM PROFILE
            if (settingsStore == null) {
                settingsStore = new AppSettingsStore(holder.itemView.getContext());
            }
            Glide.with(holder.itemView.getContext())
                 .load(settingsStore.getAvatarResId())
                 .into(userHolder.avatar);

        } else if (holder instanceof AiViewHolder) {
            AiViewHolder h = (AiViewHolder) holder;

            // ── Render message with basic Markdown + HTML ──────────────────
            String rawMessage = item.getMessage() != null ? item.getMessage() : "";
            h.message.setText(getFormattedMessage(rawMessage));

            // ── Correction block ───────────────────────────────────────────
            if (item.getCorrection() != null && !item.getCorrection().isEmpty()) {
                h.correctionBlock.setVisibility(View.VISIBLE);
                h.correctionText.setText("Sửa: " + item.getCorrection());
                h.correctionExplain.setText(item.getExplanation());
            } else {
                h.correctionBlock.setVisibility(View.GONE);
            }

            // ── Vocabulary Action Bar ──────────────────────────────────────
            if (item.hasVocab()) {
                h.vocabActionBar.setVisibility(View.VISIBLE);

                String word   = item.getVocabWord();
                String ipa    = item.getVocabIpa();
                String meaning = item.getVocabMeaning();
                String example = item.getVocabExample();
                String exampleVi = item.getVocabExampleVi();

                h.tvVocabWord.setText(word != null ? word : "");
                h.tvVocabIpa.setText(ipa != null ? "/" + ipa + "/" : "");
                h.tvVocabIpa.setVisibility(ipa != null ? View.VISIBLE : View.GONE);

                boolean hasMeaning = meaning != null && !meaning.trim().isEmpty();
                boolean hasExample = example != null && !example.trim().isEmpty();
                boolean hasExampleVi = exampleVi != null && !exampleVi.trim().isEmpty();

                if (hasMeaning || hasExample || hasExampleVi) {
                    h.vocabDetailBlock.setVisibility(View.VISIBLE);
                    h.tvVocabMeaning.setVisibility(hasMeaning ? View.VISIBLE : View.GONE);
                    h.tvVocabExample.setVisibility(hasExample ? View.VISIBLE : View.GONE);
                    h.tvVocabExampleVi.setVisibility(hasExampleVi ? View.VISIBLE : View.GONE);

                    if (hasMeaning) {
                        h.tvVocabMeaning.setText("Nghĩa: " + meaning);
                    }
                    if (hasExample) {
                        h.tvVocabExample.setText("Ví dụ: " + example);
                    }
                    if (hasExampleVi) {
                        h.tvVocabExampleVi.setText("Dịch: " + exampleVi);
                    }
                } else {
                    h.vocabDetailBlock.setVisibility(View.GONE);
                }

                // 🔊 Speak button
                h.btnSpeak.setOnClickListener(v -> speakWord(word, v.getContext()));

                // 📚 Save to dictionary button
                h.btnSaveVocab.setOnClickListener(v -> saveVocabToDict(item, v.getContext(), h.btnSaveVocab));

            } else {
                h.vocabActionBar.setVisibility(View.GONE);
                h.vocabDetailBlock.setVisibility(View.GONE);
            }
        }
    }

    private CharSequence getFormattedMessage(String rawMessage) {
        CharSequence cached = formattedMessageCache.get(rawMessage);
        if (cached != null) {
            return cached;
        }

        String htmlMessage = rawMessage
                .replaceAll("\\*\\*(.*?)\\*\\*", "<b>$1</b>")
                .replaceAll("\\*(.*?)\\*", "<i>$1</i>")
                .replaceAll("\\[color:(.*?)\\](.*?)\\[/color\\]", "<font color='$1'>$2</font>")
                .replace("\n", "<br>");

        CharSequence formatted;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            formatted = Html.fromHtml(htmlMessage, Html.FROM_HTML_MODE_COMPACT);
        } else {
            formatted = Html.fromHtml(htmlMessage);
        }

        formattedMessageCache.put(rawMessage, formatted);
        return formatted;
    }

    // ── TTS Speaker ───────────────────────────────────────────────────────────
    private void speakWord(String word, Context context) {
        if (word == null || word.isEmpty()) return;
        // Prefer the VoiceFlowEngine route
        if (speakCallback != null) {
            speakCallback.speak(word);
            return;
        }
        // Fallback: standalone TTS
        if (fallbackTts == null) initTts(context);
        if (fallbackTtsReady) {
            fallbackTts.speak(word, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, null);
        } else {
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                if (fallbackTtsReady) fallbackTts.speak(word,
                        android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, null);
            }, 700);
        }
    }

    // ── Save to Custom Vocabulary (Dictionary) ───────────────────────────────
    private void saveVocabToDict(ChatItem item, Context context, MaterialButton saveBtn) {
        String word = item.getVocabWord();
        if (word == null || word.isEmpty()) return;

        String normalizedWord = word.trim().toLowerCase(Locale.US);
        AppRepository repository = AppRepository.getInstance(context.getApplicationContext());

        IO_EXECUTOR.execute(() -> {
            List<WordEntry> existingWords = repository.getSavedWords();
            for (WordEntry existing : existingWords) {
                String existingWord = existing == null ? "" : existing.getWord();
                if (normalizedWord.equals(existingWord == null ? "" : existingWord.trim().toLowerCase(Locale.US))) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                            Toast.makeText(context, "\"" + word + "\" da co trong so tay!", Toast.LENGTH_SHORT).show());
                    return;
                }
            }

            repository.saveWord(new WordEntry(
                    word,
                    item.getVocabIpa(),
                    item.getVocabMeaning(),
                    "noun",
                    item.getVocabExample(),
                    item.getVocabExampleVi(),
                    "",
                    "Chat Flow",
                    "Saved from chat flow"
            ));

            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                // Visual feedback: turn button green-checked
                saveBtn.setBackgroundResource(R.drawable.bg_icon_circle_emerald);
                saveBtn.setIconResource(R.drawable.ic_bookmark);
                Toast.makeText(context,
                        "Da luu \"" + word + "\" vao so tay!",
                        Toast.LENGTH_SHORT).show();
            });
        });
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    // ── ViewHolders ───────────────────────────────────────────────────────────

    static class UserViewHolder extends RecyclerView.ViewHolder {
        final TextView message;
        final ImageView avatar;

        UserViewHolder(@NonNull View itemView) {
            super(itemView);
            message = itemView.findViewById(R.id.chatUserMessage);
            avatar = itemView.findViewById(R.id.chatUserAvatar);
        }
    }

    static class AiViewHolder extends RecyclerView.ViewHolder {
        final TextView message;
        final View correctionBlock;
        final TextView correctionText;
        final TextView correctionExplain;
        // Vocab action bar
        final LinearLayout vocabActionBar;
        final TextView tvVocabWord;
        final TextView tvVocabIpa;
        final MaterialButton btnSpeak;
        final MaterialButton btnSaveVocab;
        final LinearLayout vocabDetailBlock;
        final TextView tvVocabMeaning;
        final TextView tvVocabExample;
        final TextView tvVocabExampleVi;

        AiViewHolder(@NonNull View itemView) {
            super(itemView);
            message         = itemView.findViewById(R.id.chatAiMessage);
            correctionBlock = itemView.findViewById(R.id.correctionBlock);
            correctionText  = itemView.findViewById(R.id.correctionText);
            correctionExplain = itemView.findViewById(R.id.correctionExplain);
            vocabActionBar  = itemView.findViewById(R.id.vocabActionBar);
            tvVocabWord     = itemView.findViewById(R.id.tvVocabWord);
            tvVocabIpa      = itemView.findViewById(R.id.tvVocabIpa);
            btnSpeak        = itemView.findViewById(R.id.btnSpeak);
            btnSaveVocab    = itemView.findViewById(R.id.btnSaveVocab);
            vocabDetailBlock = itemView.findViewById(R.id.vocabDetailBlock);
            tvVocabMeaning = itemView.findViewById(R.id.tvVocabMeaning);
            tvVocabExample = itemView.findViewById(R.id.tvVocabExample);
            tvVocabExampleVi = itemView.findViewById(R.id.tvVocabExampleVi);
        }
    }

    static class TypingViewHolder extends RecyclerView.ViewHolder {
        TypingViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
