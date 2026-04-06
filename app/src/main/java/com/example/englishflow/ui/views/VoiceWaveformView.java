package com.example.englishflow.ui.views;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Random;

public class VoiceWaveformView extends View {

    public enum Mode {
        IDLE,
        LISTENING,
        PROCESSING,
        SPEAKING
    }

    private static final int BAR_COUNT = 11;

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF barRect = new RectF();
    private final float[] phaseOffsets = new float[BAR_COUNT];
    private final Random random = new Random();

    private ValueAnimator animator;
    private float animationPhase = 0f;
    private float micLevel = 0f;
    private float smoothedMicLevel = 0f;
    private Mode mode = Mode.IDLE;
    private long lastFrameNanos = 0L;

    public VoiceWaveformView(Context context) {
        super(context);
        init();
    }

    public VoiceWaveformView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public VoiceWaveformView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        barPaint.setStyle(Paint.Style.FILL);
        updateColorForMode();

        for (int i = 0; i < BAR_COUNT; i++) {
            phaseOffsets[i] = random.nextFloat() * (float) (Math.PI * 2f);
        }
    }

    public void setMode(@NonNull Mode nextMode) {
        if (mode == nextMode) {
            // Keep subtle ambient motion alive even when remaining in IDLE.
            startAnimationIfNeeded();
            return;
        }

        mode = nextMode;
        updateColorForMode();

        if (mode == Mode.IDLE) {
            resetMicLevel();
        }
        startAnimationIfNeeded();
        invalidate();
    }

    public void setMicLevel(float rmsDb) {
        float normalized = clamp((rmsDb + 2f) / 12f);
        micLevel = normalized;
    }

    public void resetMicLevel() {
        micLevel = 0f;
        smoothedMicLevel = 0f;
    }

    private void startAnimationIfNeeded() {
        if (animator != null) {
            return;
        }

        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(700L);
        animator.setInterpolator(new LinearInterpolator());
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.addUpdateListener(animation -> {
            long nowNanos = System.nanoTime();
            if (lastFrameNanos <= 0L) {
                lastFrameNanos = nowNanos;
            }
            float deltaSeconds = (nowNanos - lastFrameNanos) / 1_000_000_000f;
            lastFrameNanos = nowNanos;

            float speedCyclesPerSecond;
            switch (mode) {
                case IDLE:
                    speedCyclesPerSecond = 0.45f;
                    break;
                case PROCESSING:
                    speedCyclesPerSecond = 0.85f;
                    break;
                case LISTENING:
                case SPEAKING:
                default:
                    speedCyclesPerSecond = 1.15f;
                    break;
            }
            animationPhase += deltaSeconds * speedCyclesPerSecond * (float) (Math.PI * 2f);
            invalidate();
        });
        animator.start();
    }

    private void stopAnimation() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        lastFrameNanos = 0L;
    }

    private void updateColorForMode() {
        switch (mode) {
            case LISTENING:
                barPaint.setColor(Color.parseColor("#EF4444"));
                break;
            case PROCESSING:
                barPaint.setColor(Color.parseColor("#3B82F6"));
                break;
            case SPEAKING:
                barPaint.setColor(Color.parseColor("#10B981"));
                break;
            case IDLE:
            default:
                barPaint.setColor(Color.parseColor("#80D7E9FF"));
                break;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float width = getWidth();
        float height = getHeight();
        if (width <= 0f || height <= 0f) {
            return;
        }

        smoothedMicLevel += (micLevel - smoothedMicLevel) * 0.18f;

        float barWidth = width / (BAR_COUNT * 1.75f);
        float barSpacing = barWidth * 0.75f;
        float totalWidth = (BAR_COUNT * barWidth) + ((BAR_COUNT - 1) * barSpacing);
        float startX = (width - totalWidth) / 2f;
        float centerY = height / 2f;

        float minHalfHeight = Math.max(2f, height * 0.10f);
        float maxHalfHeight = height * 0.46f;
        boolean silentListening = mode == Mode.LISTENING && smoothedMicLevel < 0.06f;

        for (int i = 0; i < BAR_COUNT; i++) {
            float localPhase = animationPhase + phaseOffsets[i];
            float wave = 0.5f + 0.5f * (float) Math.sin(localPhase);
            float pulse = 0.5f + 0.5f * (float) Math.sin((localPhase * 0.65f) + (i * 0.45f));
            float ambient = 0.5f + 0.5f * (float) Math.sin((localPhase * 0.42f) + (i * 0.20f));

            float intensity;
            switch (mode) {
                case LISTENING:
                    if (silentListening) {
                        // Silence scanning effect while still in listening mode.
                        intensity = 0.10f + (0.20f * ambient);
                    } else {
                        intensity = 0.15f + (0.50f * ((0.35f * wave) + (0.65f * smoothedMicLevel)));
                    }
                    break;
                case PROCESSING:
                    intensity = 0.18f + (0.18f * pulse);
                    break;
                case SPEAKING:
                    intensity = 0.22f + (0.42f * ((0.55f * wave) + (0.45f * pulse)));
                    break;
                case IDLE:
                default:
                    // Ambient breathing bars when everyone is silent.
                    intensity = 0.08f + (0.16f * ambient);
                    break;
            }

            float centerDistance = Math.abs(i - ((BAR_COUNT - 1) / 2f));
            float centerBias = 1f - (centerDistance / ((BAR_COUNT - 1) / 2f)) * 0.18f;

            float halfHeight = minHalfHeight + ((maxHalfHeight - minHalfHeight) * clamp(intensity));
            halfHeight *= centerBias;

            float left = startX + i * (barWidth + barSpacing);
            float right = left + barWidth;
            float top = centerY - halfHeight;
            float bottom = centerY + halfHeight;

            barRect.set(left, top, right, bottom);
            canvas.drawRoundRect(barRect, barWidth, barWidth, barPaint);
        }
    }

    private float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopAnimation();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility != VISIBLE) {
            stopAnimation();
        } else {
            startAnimationIfNeeded();
        }
    }
}
