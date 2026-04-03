package com.example.englishflow.ui.views;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.englishflow.R;

public class LearningGaugeView extends View {

    private static final float START_ANGLE = 160f;
    private static final float SWEEP_ANGLE = 220f;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint needlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hubPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hubInnerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF arcRect = new RectF();

    private float progress = 0f;
    private ValueAnimator progressAnimator;

    public LearningGaugeView(Context context) {
        this(context, null);
    }

    public LearningGaugeView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LearningGaugeView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initPaints();
    }

    private void initPaints() {
        float stroke = dp(12f);

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(stroke);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setColor(ContextCompat.getColor(getContext(), R.color.ef_outline_light));

        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(stroke);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint.setColor(ContextCompat.getColor(getContext(), R.color.ef_primary));

        tickPaint.setStyle(Paint.Style.STROKE);
        tickPaint.setStrokeWidth(dp(2f));
        tickPaint.setStrokeCap(Paint.Cap.ROUND);
        tickPaint.setColor(ContextCompat.getColor(getContext(), R.color.ef_outline));

        needlePaint.setStyle(Paint.Style.STROKE);
        needlePaint.setStrokeWidth(dp(3f));
        needlePaint.setStrokeCap(Paint.Cap.ROUND);
        needlePaint.setColor(ContextCompat.getColor(getContext(), R.color.white));

        hubPaint.setStyle(Paint.Style.FILL);
        hubPaint.setColor(ContextCompat.getColor(getContext(), R.color.white));

        hubInnerPaint.setStyle(Paint.Style.FILL);
        hubInnerPaint.setColor(ContextCompat.getColor(getContext(), R.color.ef_primary_dark));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float left = getPaddingLeft();
        float top = getPaddingTop();
        float right = getWidth() - getPaddingRight();
        float bottom = getHeight() - getPaddingBottom();

        float contentWidth = right - left;
        float contentHeight = bottom - top;

        float centerX = left + (contentWidth / 2f);
        float centerY = top + (contentHeight * 0.95f);
        float radius = Math.min(contentWidth * 0.45f, contentHeight * 0.9f) - dp(8f);
        radius = Math.max(radius, dp(20f));

        arcRect.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius);

        canvas.drawArc(arcRect, START_ANGLE, SWEEP_ANGLE, false, trackPaint);

        float progressSweep = SWEEP_ANGLE * (progress / 100f);
        canvas.drawArc(arcRect, START_ANGLE, progressSweep, false, progressPaint);

        int ticks = 10;
        for (int i = 0; i <= ticks; i++) {
            float angle = START_ANGLE + (SWEEP_ANGLE * i / ticks);
            double radians = Math.toRadians(angle);
            float outer = radius + dp(2f);
            float inner = radius - dp(8f);
            float x1 = centerX + (float) (Math.cos(radians) * outer);
            float y1 = centerY + (float) (Math.sin(radians) * outer);
            float x2 = centerX + (float) (Math.cos(radians) * inner);
            float y2 = centerY + (float) (Math.sin(radians) * inner);
            canvas.drawLine(x1, y1, x2, y2, tickPaint);
        }

        float needleAngle = START_ANGLE + progressSweep;
        double needleRadians = Math.toRadians(needleAngle);
        float needleLength = radius - dp(20f);
        float needleX = centerX + (float) (Math.cos(needleRadians) * needleLength);
        float needleY = centerY + (float) (Math.sin(needleRadians) * needleLength);

        canvas.drawLine(centerX, centerY, needleX, needleY, needlePaint);
        canvas.drawCircle(centerX, centerY, dp(8f), hubPaint);
        canvas.drawCircle(centerX, centerY, dp(3.6f), hubInnerPaint);
    }

    public void setProgress(float value, boolean animated) {
        float clamped = Math.max(0f, Math.min(100f, value));
        if (!animated) {
            progress = clamped;
            invalidate();
            return;
        }

        if (progressAnimator != null) {
            progressAnimator.cancel();
        }

        progressAnimator = ValueAnimator.ofFloat(progress, clamped);
        progressAnimator.setDuration(600L);
        progressAnimator.setInterpolator(new DecelerateInterpolator());
        progressAnimator.addUpdateListener(animation -> {
            progress = (float) animation.getAnimatedValue();
            invalidate();
        });
        progressAnimator.start();
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onDetachedFromWindow() {
        if (progressAnimator != null) {
            progressAnimator.cancel();
            progressAnimator = null;
        }
        super.onDetachedFromWindow();
    }
}
