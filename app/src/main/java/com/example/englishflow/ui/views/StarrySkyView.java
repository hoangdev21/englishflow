package com.example.englishflow.ui.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class StarrySkyView extends View {

    private final Paint paint;
    private final List<Star> stars;
    private final Random random;
    private int width;
    private int height;
    private boolean isAnimating = false;

    private static final int STAR_COUNT = 80;

    public StarrySkyView(Context context) {
        this(context, null);
    }

    public StarrySkyView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public StarrySkyView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        stars = new ArrayList<>();
        random = new Random();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        width = w;
        height = h;
        initStars();
    }

    private void initStars() {
        stars.clear();
        for (int i = 0; i < STAR_COUNT; i++) {
            stars.add(new Star(
                    random.nextFloat() * width,
                    random.nextFloat() * height,
                    (random.nextFloat() * 3f) + 1f, // Size
                    (random.nextFloat() * 2f) + 0.5f, // Speed
                    random.nextInt(155) + 100 // Alpha
            ));
        }
        if (!isAnimating) {
            isAnimating = true;
            postInvalidateOnAnimation();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        for (Star star : stars) {
            paint.setAlpha(star.alpha);
            // Twinkle effect
            if (random.nextFloat() < 0.05f) {
                star.alpha = random.nextInt(155) + 100;
            }
            
            canvas.drawCircle(star.x, star.y, star.radius, paint);

            // Move star horizontally to create moving sky effect
            star.x -= star.speed;

            // Reset star to right side if it moves off screen
            if (star.x < -star.radius) {
                star.x = width + star.radius;
                star.y = random.nextFloat() * height;
            }
        }

        if (isAnimating) {
            postInvalidateOnAnimation();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        isAnimating = false;
    }

    private static class Star {
        float x;
        float y;
        float radius;
        float speed;
        int alpha;

        Star(float x, float y, float radius, float speed, int alpha) {
            this.x = x;
            this.y = y;
            this.radius = radius;
            this.speed = speed;
            this.alpha = alpha;
        }
    }
}
