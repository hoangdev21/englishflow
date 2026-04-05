package com.example.englishflow.ui.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class MagicLensOverlayView extends View {

    public enum LabelMode {
        TRANSLATION,
        IPA,
        TRANSLATION_IPA
    }

    public interface OnWordTapListener {
        void onWordTapped(@NonNull WordOverlay overlay);
    }

    public static class WordOverlay {
        public final String word;
        public final RectF bounds;
        public final String ipa;
        public final String meaning;
        public final float confidence;

        public WordOverlay(@NonNull String word,
                           @NonNull RectF bounds,
                           @Nullable String ipa,
                           @Nullable String meaning,
                           float confidence) {
            this.word = word;
            this.bounds = new RectF(bounds);
            this.ipa = ipa == null ? "" : ipa.trim();
            this.meaning = meaning == null ? "" : meaning.trim();
            this.confidence = Math.max(0f, Math.min(1f, confidence));
        }
    }

    private final Paint framePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectedFramePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF drawRect = new RectF();

    private final List<WordOverlay> overlays = new ArrayList<>();
    private LabelMode labelMode = LabelMode.TRANSLATION;
    private OnWordTapListener onWordTapListener;
    private String selectedWord = "";
    private boolean lensActive = false;
    private WordOverlay pressedOverlay;

    public MagicLensOverlayView(Context context) {
        super(context);
        init();
    }

    public MagicLensOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MagicLensOverlayView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setWillNotDraw(false);

        framePaint.setStyle(Paint.Style.STROKE);
        framePaint.setStrokeWidth(dp(1.75f));
        framePaint.setColor(Color.parseColor("#FFF5B74A"));

        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(Color.parseColor("#33F59E0B"));

        selectedFramePaint.setStyle(Paint.Style.STROKE);
        selectedFramePaint.setStrokeWidth(dp(2.4f));
        selectedFramePaint.setColor(Color.parseColor("#FF22C55E"));

        labelBgPaint.setStyle(Paint.Style.FILL);
        labelBgPaint.setColor(Color.parseColor("#D91E293B"));

        labelTextPaint.setColor(Color.WHITE);
        labelTextPaint.setTextSize(sp(11f));
        labelTextPaint.setFakeBoldText(true);
    }

    public void setLensActive(boolean active) {
        if (lensActive == active) {
            return;
        }
        lensActive = active;
        invalidate();
    }

    public void setLabelMode(@NonNull LabelMode mode) {
        if (labelMode == mode) {
            return;
        }
        labelMode = mode;
        invalidate();
    }

    public void setOnWordTapListener(@Nullable OnWordTapListener listener) {
        this.onWordTapListener = listener;
    }

    public void setWordOverlays(@Nullable List<WordOverlay> values) {
        overlays.clear();
        if (values != null) {
            overlays.addAll(values);
        }
        invalidate();
    }

    public void clearOverlays() {
        overlays.clear();
        selectedWord = "";
        invalidate();
    }

    public void setSelectedWord(@Nullable String word) {
        selectedWord = word == null ? "" : word.trim().toLowerCase();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!lensActive || overlays.isEmpty()) {
            return;
        }

        float cornerRadius = dp(4.5f);
        float labelPaddingX = dp(3.5f);
        float labelPaddingY = dp(2.4f);
        float labelGap = dp(3.2f);

        for (WordOverlay overlay : overlays) {
            drawRect.set(overlay.bounds);

            if (drawRect.width() < dp(16f) || drawRect.height() < dp(8f)) {
                continue;
            }

            canvas.drawRoundRect(drawRect, cornerRadius, cornerRadius, fillPaint);

            boolean selected = !selectedWord.isEmpty() && selectedWord.equalsIgnoreCase(overlay.word);
            canvas.drawRoundRect(drawRect, cornerRadius, cornerRadius, selected ? selectedFramePaint : framePaint);

            String label = buildLabelText(overlay);
            if (TextUtils.isEmpty(label)) {
                continue;
            }
            label = trimLabel(label, 28);

            float textWidth = labelTextPaint.measureText(label);
            float labelLeft = drawRect.left;
            float labelTop = drawRect.top - dp(17f);
            float minTop = dp(2f);
            if (labelTop < minTop) {
                labelTop = drawRect.bottom + labelGap;
            }

            float labelWidth = textWidth + (labelPaddingX * 2f);
            float maxRight = getWidth() - dp(2f);
            float labelRight = labelLeft + labelWidth;
            if (labelRight > maxRight) {
                float delta = labelRight - maxRight;
                labelLeft = Math.max(dp(2f), labelLeft - delta);
                labelRight = labelLeft + labelWidth;
            }

            float labelBottom = labelTop + dp(15f);
            RectF labelRect = new RectF(labelLeft, labelTop, labelRight, labelBottom);
            canvas.drawRoundRect(labelRect, dp(6f), dp(6f), labelBgPaint);

            float textX = labelRect.left + labelPaddingX;
            float textY = labelRect.bottom - labelPaddingY;
            canvas.drawText(label, textX, textY, labelTextPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!lensActive) {
            return false;
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pressedOverlay = findTappedOverlay(event.getX(), event.getY());
                return pressedOverlay != null;
            case MotionEvent.ACTION_UP:
                WordOverlay tapped = findTappedOverlay(event.getX(), event.getY());
                if (pressedOverlay != null && tapped != null && tapped == pressedOverlay) {
                    selectedWord = tapped.word.toLowerCase();
                    invalidate();
                    performClick();
                    if (onWordTapListener != null) {
                        onWordTapListener.onWordTapped(tapped);
                    }
                    pressedOverlay = null;
                    return true;
                }
                pressedOverlay = null;
                return false;
            case MotionEvent.ACTION_CANCEL:
                pressedOverlay = null;
                return false;
            default:
                return false;
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    @Nullable
    private WordOverlay findTappedOverlay(float x, float y) {
        float tapPadding = dp(7f);
        for (int i = overlays.size() - 1; i >= 0; i--) {
            WordOverlay overlay = overlays.get(i);
            RectF hitRect = new RectF(overlay.bounds);
            hitRect.inset(-tapPadding, -tapPadding);
            if (hitRect.contains(x, y)) {
                return overlay;
            }
        }
        return null;
    }

    @NonNull
    private String buildLabelText(@NonNull WordOverlay overlay) {
        String ipa = overlay.ipa;
        String meaning = overlay.meaning;
        switch (labelMode) {
            case IPA:
                return !ipa.isEmpty() ? ipa : overlay.word;
            case TRANSLATION_IPA:
                if (!meaning.isEmpty() && !ipa.isEmpty()) {
                    return meaning + " • " + ipa;
                }
                if (!meaning.isEmpty()) {
                    return meaning;
                }
                if (!ipa.isEmpty()) {
                    return ipa;
                }
                return overlay.word;
            case TRANSLATION:
            default:
                return !meaning.isEmpty() ? meaning : overlay.word;
        }
    }

    @NonNull
    private String trimLabel(@NonNull String value, int maxLength) {
        String text = value.trim();
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, Math.max(0, maxLength - 3)).trim() + "...";
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}