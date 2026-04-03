package com.example.englishflow.ui.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.englishflow.R;

import java.util.ArrayList;
import java.util.List;

public class MapPathView extends View {

    private final Paint routePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    private final List<PointF> nodeCenters = new ArrayList<>();

    public MapPathView(Context context) {
        super(context);
        init();
    }

    public MapPathView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MapPathView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        routePaint.setColor(ContextCompat.getColor(getContext(), R.color.ef_map_route));
        routePaint.setStyle(Paint.Style.STROKE);
        routePaint.setStrokeWidth(dpToPx(5f));
        routePaint.setStrokeCap(Paint.Cap.ROUND);

        glowPaint.setColor(ContextCompat.getColor(getContext(), R.color.ef_map_route_glow));
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(dpToPx(11f));
        glowPaint.setStrokeCap(Paint.Cap.ROUND);

        dotPaint.setColor(ContextCompat.getColor(getContext(), R.color.ef_map_route));
        dotPaint.setStyle(Paint.Style.FILL);
    }

    public void setNodeCenters(List<PointF> centers) {
        nodeCenters.clear();
        nodeCenters.addAll(centers);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (nodeCenters.size() < 2) {
            return;
        }

        for (int i = 0; i < nodeCenters.size() - 1; i++) {
            PointF start = nodeCenters.get(i);
            PointF end = nodeCenters.get(i + 1);

            float middleY = (start.y + end.y) / 2f;
            path.reset();
            path.moveTo(start.x, start.y);
            path.cubicTo(start.x, middleY, end.x, middleY, end.x, end.y);

            canvas.drawPath(path, glowPaint);
            canvas.drawPath(path, routePaint);
        }

        for (PointF point : nodeCenters) {
            canvas.drawCircle(point.x, point.y, dpToPx(4f), dotPaint);
        }
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }
}
