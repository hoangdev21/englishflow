package com.example.englishflow.ui.views;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.animation.AnimatorListenerAdapter;
import android.animation.Animator;

import androidx.annotation.Nullable;

import java.util.Random;

/**
 * A highly professional progress bar that features an electric lightning bolt effect.
 * It uses Hardware acceleration and randomized paths to simulate electricity flickering
 * around the progress area.
 */
public class LightningProgressBar extends View {

    private int progress = 0; // 0 to 100
    private int max = 100;

    private Paint trackPaint;
    private Paint progressPaint;
    private Paint glowPaint;
    private Paint boltPaint;

    private RectF trackRect = new RectF();
    private RectF progressRect = new RectF();

    private float animationOffset = 0f;
    private ValueAnimator animator;
    private Random random = new Random();

    // Custom coloring
    private int colorStart = Color.parseColor("#00B0FF"); // Deep sea blue
    private int colorEnd = Color.parseColor("#00E5FF");   // Light sea blue
    private int boltColor = Color.parseColor("#FFFFFF");
    private int glowColor = Color.parseColor("#4000B0FF");

    public LightningProgressBar(Context context) {
        super(context);
        init();
    }

    public LightningProgressBar(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setLayerType(LAYER_TYPE_HARDWARE, null);

        trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        trackPaint.setColor(Color.parseColor("#E0E0E0"));
        trackPaint.setStyle(Paint.Style.FILL);

        progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        progressPaint.setStyle(Paint.Style.FILL);

        glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glowPaint.setStyle(Paint.Style.FILL);
        glowPaint.setColor(glowColor);
        glowPaint.setMaskFilter(new BlurMaskFilter(15, BlurMaskFilter.Blur.NORMAL));

        boltPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        boltPaint.setColor(boltColor);
        boltPaint.setStyle(Paint.Style.STROKE);
        boltPaint.setStrokeWidth(3f);
        boltPaint.setStrokeCap(Paint.Cap.ROUND);
        boltPaint.setAlpha(180);
    }

    private void startAnimationBurst() {
        if (!isShown() || getWindowVisibility() != VISIBLE) {
            return;
        }

        stopAnimation();
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(900);
        animator.setInterpolator(new LinearInterpolator());
        animator.setRepeatCount(1);
        animator.addUpdateListener(animation -> {
            animationOffset = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                animationOffset = 0f;
                animator = null;
                invalidate();
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                animationOffset = 0f;
            }
        });
        animator.start();
    }

    private void stopAnimation() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        animationOffset = 0f;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);

        if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.AT_MOST) {
            height = (int) (14 * getResources().getDisplayMetrics().density);
        }

        setMeasuredDimension(width, height);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        trackRect.set(0, 0, w, h);
        updateProgressRect();
    }

    private void updateProgressRect() {
        float width = getWidth();
        float height = getHeight();
        float ratio = (float) progress / max;
        progressRect.set(0, 0, width * ratio, height);

        if (progressRect.width() > 0) {
            progressPaint.setShader(new LinearGradient(0, 0, progressRect.width(), 0,
                    colorStart, colorEnd, Shader.TileMode.CLAMP));
        }
    }

    public void setProgress(int progress) {
        int nextProgress = Math.max(0, Math.min(progress, max));
        if (this.progress == nextProgress) {
            return;
        }

        this.progress = nextProgress;
        updateProgressRect();
        if (this.progress > 0) {
            startAnimationBurst();
        } else {
            stopAnimation();
        }
        invalidate();
    }

    public void setMax(int max) {
        this.max = max;
        updateProgressRect();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float cornerRadius = getHeight() / 2f;

        // 1. Draw Track
        canvas.drawRoundRect(trackRect, cornerRadius, cornerRadius, trackPaint);

        if (progress <= 0) return;

        // 2. Draw Glow for progress
        canvas.drawRoundRect(progressRect, cornerRadius, cornerRadius, glowPaint);

        // 3. Draw Progress Bar
        canvas.drawRoundRect(progressRect, cornerRadius, cornerRadius, progressPaint);

        // 4. Draw electric bolts only while burst animation is active.
        if (animator != null && animator.isRunning()) {
            drawElectricBolts(canvas, cornerRadius);
        }
    }

    private void drawElectricBolts(Canvas canvas, float cornerRadius) {
        float w = progressRect.width();
        float h = progressRect.height();
        if (w < 10) return;

        // Clip slightly larger than progress to allow some electricity to bleed out
        canvas.save();
        RectF clipRect = new RectF(progressRect);
        clipRect.inset(-4, -4); 
        Path clipPath = new Path();
        clipPath.addRoundRect(clipRect, cornerRadius + 4, cornerRadius + 4, Path.Direction.CW);
        canvas.clipPath(clipPath);

        // Logic for "flickering" lines
        int boltCount = 4;
        for (int i = 0; i < boltCount; i++) {
            Path boltPath = new Path();
            float startX = 2 + random.nextFloat() * 10;
            float startY = h / 2f + (random.nextFloat() - 0.5f) * h * 1.2f;
            boltPath.moveTo(startX, startY);

            int segments = 10;
            float segmentWidth = (w - startX) / segments;

            for (int s = 1; s <= segments; s++) {
                float targetX = startX + s * segmentWidth;
                float targetY = h / 2f + (random.nextFloat() - 0.5f) * h * 1.4f;
                // Add zig-zag jitter
                boltPath.lineTo(targetX, targetY);
            }

            // Glow effect for the bolt itself
            boltPaint.setStrokeWidth(4f);
            boltPaint.setAlpha(60);
            canvas.drawPath(boltPath, boltPaint);
            
            // Core of the bolt
            boltPaint.setStrokeWidth(1.5f);
            boltPaint.setAlpha(200 + random.nextInt(55));
            canvas.drawPath(boltPath, boltPaint);
        }

        canvas.restore();
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
        }
    }
}
