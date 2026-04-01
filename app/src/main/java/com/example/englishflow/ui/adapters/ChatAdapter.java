package com.example.englishflow.ui.adapters;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.englishflow.R;
import com.example.englishflow.data.ChatItem;
import com.example.englishflow.database.EnglishFlowDatabase;
import com.example.englishflow.database.entity.CustomVocabularyEntity;
import com.google.android.material.button.MaterialButton;

import java.util.List;
import java.util.Locale;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final List<ChatItem> messages;
    private TextToSpeech tts;
    private boolean ttsReady = false;

    public ChatAdapter(List<ChatItem> messages) {
        this.messages = messages;
    }

    /** Call this from Fragment/Activity when ready. TTS is initialized lazily on first use. */
    public void initTts(Context context) {
        if (tts != null) return;
        tts = new TextToSpeech(context.getApplicationContext(), status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(Locale.US);
                ttsReady = (result != TextToSpeech.LANG_MISSING_DATA
                        && result != TextToSpeech.LANG_NOT_SUPPORTED);
            }
        });
    }

    /** Release TTS engine — call from Fragment.onDestroyView() */
    public void shutdownTts() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
            ttsReady = false;
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
            ((UserViewHolder) holder).message.setText(item.getMessage());

        } else if (holder instanceof AiViewHolder) {
            AiViewHolder h = (AiViewHolder) holder;

            // ── Render message with basic Markdown + HTML ──────────────────
            String rawMessage = item.getMessage() != null ? item.getMessage() : "";
            String htmlMessage = rawMessage
                    .replaceAll("\\*\\*(.*?)\\*\\*", "<b>$1</b>")
                    .replaceAll("\\*(.*?)\\*", "<i>$1</i>")
                    .replaceAll("\\[color:(.*?)\\](.*?)\\[/color\\]", "<font color='$1'>$2</font>")
                    .replace("\n", "<br>");

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                h.message.setText(android.text.Html.fromHtml(htmlMessage, android.text.Html.FROM_HTML_MODE_COMPACT));
            } else {
                h.message.setText(android.text.Html.fromHtml(htmlMessage));
            }

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

                h.tvVocabWord.setText(word != null ? word : "");
                h.tvVocabIpa.setText(ipa != null ? "/" + ipa + "/" : "");
                h.tvVocabIpa.setVisibility(ipa != null ? View.VISIBLE : View.GONE);

                // 🔊 Speak button
                h.btnSpeak.setOnClickListener(v -> speakWord(word, v.getContext()));

                // 📚 Save to dictionary button
                h.btnSaveVocab.setOnClickListener(v -> saveVocabToDict(item, v.getContext(), h.btnSaveVocab));

            } else {
                h.vocabActionBar.setVisibility(View.GONE);
            }
        }
    }

    // ── TTS Speaker ───────────────────────────────────────────────────────────
    private void speakWord(String word, Context context) {
        if (word == null || word.isEmpty()) return;
        if (tts == null) {
            initTts(context);
            // Give TTS time to init, then speak
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                if (ttsReady) tts.speak(word, TextToSpeech.QUEUE_FLUSH, null, null);
            }, 600);
            return;
        }
        if (ttsReady) {
            tts.speak(word, TextToSpeech.QUEUE_FLUSH, null, null);
        } else {
            Toast.makeText(context, "TTS chưa sẵn sàng, thử lại sau", Toast.LENGTH_SHORT).show();
        }
    }

    // ── Save to Custom Vocabulary (Dictionary) ───────────────────────────────
    private void saveVocabToDict(ChatItem item, Context context, MaterialButton saveBtn) {
        String word = item.getVocabWord();
        if (word == null || word.isEmpty()) return;

        new Thread(() -> {
            EnglishFlowDatabase db = EnglishFlowDatabase.getInstance(context.getApplicationContext());

            // Check if already saved
            CustomVocabularyEntity existing = db.customVocabularyDao().findByWord(word);
            if (existing != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                        Toast.makeText(context, "\"" + word + "\" đã có trong từ điển!", Toast.LENGTH_SHORT).show());
                return;
            }

            // Build entity from vocab data
            CustomVocabularyEntity entity = new CustomVocabularyEntity(
                    word,
                    item.getVocabMeaning(),
                    item.getVocabIpa(),
                    item.getVocabExample(),
                    item.getVocabExampleVi(),
                    null   // usage note — not parsed separately, stays null
            );
            entity.source = "chat";
            entity.domain = "general";
            entity.updatedAt = System.currentTimeMillis();

            db.customVocabularyDao().upsert(entity);

            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                // Visual feedback: turn button green-checked
                saveBtn.setBackgroundResource(R.drawable.bg_icon_circle_emerald);
                saveBtn.setIconResource(R.drawable.ic_bookmark);
                Toast.makeText(context,
                        "Đã lưu \"" + word + "\" vào từ điển!",
                        Toast.LENGTH_SHORT).show();
            });
        }).start();
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    // ── ViewHolders ───────────────────────────────────────────────────────────

    static class UserViewHolder extends RecyclerView.ViewHolder {
        final TextView message;

        UserViewHolder(@NonNull View itemView) {
            super(itemView);
            message = itemView.findViewById(R.id.chatUserMessage);
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
        }
    }

    static class TypingViewHolder extends RecyclerView.ViewHolder {
        TypingViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
