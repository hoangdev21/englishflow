package com.example.englishflow.ui.views;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * A highly immersive realistic 3D-like Mascot View.
 * Renders a stylized 3D parrot on a branch with falling leaves ambient effect.
 * Interaction: Swipe horizontally to rotate the camera. Tap to fly.
 */
public class EcoMascot3DView extends View {

    private final Paint paint;
    private final List<BodyPart> parts;
    private final List<Leaf> leaves;
    private final Random random;

    // View State
    private float angleY = 0f;
    private float targetAngleY = 0f;
    private float wingFlapAngle = 0f;
    private float flyOffset = 0f;
    private boolean isFlying = false;
    private long flightStartTime = 0;

    // Touch Handling
    private float lastTouchX;
    private boolean isDragging = false;

    // Colors - Cinematic grading
    private static final int COLOR_BODY = 0xFF558B2F; // Deep Green
    private static final int COLOR_BELLY = 0xFF8BC34A; // Light Green
    private static final int COLOR_HEAD = 0xFF689F38;
    private static final int COLOR_BEAK = 0xFFFFB300; // Amber
    private static final int COLOR_EYE_BG = 0xFFFAFAFA; // White
    private static final int COLOR_PUPIL = 0xFF212121;
    private static final int COLOR_WING = 0xFF33691E; // Very Deep Green
    private static final int COLOR_WING_HIGHLIGHT = 0xFF7CB342;
    private static final int COLOR_TAIL = 0xFF1B5E20;
    private static final int COLOR_LEG = 0xFF6D4C41; // Brown
    private static final int COLOR_CLAW = 0xFF4E342E;
    private static final int COLOR_BRANCH = 0xFF5D4037; // Dark Wood
    
    public EcoMascot3DView(Context context) {
        this(context, null);
    }

    public EcoMascot3DView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public EcoMascot3DView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        parts = new ArrayList<>();
        leaves = new ArrayList<>();
        random = new Random();
        initScene();
    }

    private void initScene() {
        // Initialize falling leaves
        for (int i = 0; i < 20; i++) {
            leaves.add(new Leaf());
        }

        buildParrotModel();
        
        // Start animation loop
        postInvalidateOnAnimation();
    }

    private void buildParrotModel() {
        parts.clear();
        
        // The branch
        parts.add(new BodyPart(0, 160, 0, 500, 30, COLOR_BRANCH, PartType.BRANCH));

        // Parrot Body & Tail
        parts.add(new BodyPart(0, 50, -40, 40, 100, COLOR_TAIL, PartType.TAIL));
        parts.add(new BodyPart(0, 10, 0, 90, 140, COLOR_BODY, PartType.BODY));
        parts.add(new BodyPart(0, 15, 20, 70, 110, COLOR_BELLY, PartType.BELLY));
        
        // Legs & Claws
        parts.add(new BodyPart(-25, 110, 0, 15, 60, COLOR_LEG, PartType.LEG));
        parts.add(new BodyPart(25, 110, 0, 15, 60, COLOR_LEG, PartType.LEG));
        parts.add(new BodyPart(-25, 150, 10, 10, 25, COLOR_CLAW, PartType.CLAW));
        parts.add(new BodyPart(25, 150, 10, 10, 25, COLOR_CLAW, PartType.CLAW));

        // Wings
        parts.add(new BodyPart(-55, 0, 0, 20, 120, COLOR_WING, PartType.WING_L));
        parts.add(new BodyPart(55, 0, 0, 20, 120, COLOR_WING, PartType.WING_R));

        // Head, Beak & Details
        parts.add(new BodyPart(0, -75, 10, 75, 75, COLOR_HEAD, PartType.HEAD));
        parts.add(new BodyPart(0, -70, 55, 30, 40, COLOR_BEAK, PartType.BEAK));
        
        // Left Eye
        parts.add(new BodyPart(-22, -90, 40, 18, 18, COLOR_EYE_BG, PartType.EYE_BG));
        parts.add(new BodyPart(-22, -90, 46, 8, 8, COLOR_PUPIL, PartType.PUPIL));
        
        // Right Eye
        parts.add(new BodyPart(22, -90, 40, 18, 18, COLOR_EYE_BG, PartType.EYE_BG));
        parts.add(new BodyPart(22, -90, 46, 8, 8, COLOR_PUPIL, PartType.PUPIL));
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX = event.getX();
                isDragging = false;
                return true;

            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - lastTouchX;
                if (Math.abs(dx) > 10) isDragging = true;
                
                // Allow horizontal rotation (limited or infinite)
                targetAngleY -= dx * 0.008f; 
                
                lastTouchX = event.getX();
                break;

            case MotionEvent.ACTION_UP:
                if (!isDragging) {
                    startFlying();
                } else {
                    // Start snapping back to 0 slowly after drag
                    // We can handle this logic in onDraw interpolation
                }
                break;
        }
        return super.onTouchEvent(event);
    }

    public void startFlying() {
        if (!isFlying) {
            isFlying = true;
            flightStartTime = System.currentTimeMillis();
            
            // Re-center rotation quickly for the flight
            ValueAnimator resetRot = ValueAnimator.ofFloat(angleY, 0f);
            resetRot.setDuration(300);
            resetRot.addUpdateListener(a -> {
                angleY = (float) a.getAnimatedValue();
                targetAngleY = angleY;
            });
            resetRot.start();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;
        float focalLength = 1200f; 

        // 1. Draw Environment (Falling Leaves)
        drawLeaves(canvas);

        // 2. Update Physics & Animation States
        updateParrotAnimation();

        // 3. Smooth Camera Rotation
        if (!isDragging && !isFlying) {
            // Smoothly return to 0 (front view) if not dragged
            targetAngleY += (0 - targetAngleY) * 0.05f;
        }
        angleY += (targetAngleY - angleY) * 0.15f;

        float sinY = (float) Math.sin(angleY);
        float cosY = (float) Math.cos(angleY);
        
        // 4. Project and Sort 3D Parts
        for (BodyPart part : parts) {
            float modelX = part.x;
            float modelY = part.y;
            float modelZ = part.z;

            // Apply flight and flap logic to wings
            if (part.type == PartType.WING_L) {
                float flapOffset = (float) Math.sin(wingFlapAngle) * (isFlying ? 60f : 5f);
                modelX -= Math.abs(flapOffset);
                modelY += (flapOffset * 0.5f); // Wing moves up/down too
            } else if (part.type == PartType.WING_R) {
                float flapOffset = (float) Math.sin(wingFlapAngle) * (isFlying ? 60f : 5f);
                modelX += Math.abs(flapOffset);
                modelY += (flapOffset * 0.5f);
            }

            // Apply flight offset to everything EXCEPT the branch
            if (part.type != PartType.BRANCH) {
                modelY += flyOffset;
                
                // Add a subtle idle breathing to the body
                if (!isFlying && (part.type == PartType.BODY || part.type == PartType.BELLY)) {
                   modelY += Math.sin(System.currentTimeMillis() * 0.003f) * 2f;
                }
            }

            // 3D Rotation Math
            float rotX = modelX * cosY - modelZ * sinY;
            float rotZ = modelX * sinY + modelZ * cosY;

            // Perspective Projection
            part.currentProjScale = focalLength / (focalLength + rotZ);
            part.currentProjX = centerX + (rotX * part.currentProjScale);
            part.currentProjY = centerY + (modelY * part.currentProjScale);
            part.currentZOrder = rotZ; 
        }

        Collections.sort(parts, (p1, p2) -> Float.compare(p1.currentZOrder, p2.currentZOrder));

        // 5. Draw the 3D Model
        for (BodyPart part : parts) {
            drawPart(canvas, part);
        }

        // Loop loop
        postInvalidateOnAnimation();
    }

    private void updateParrotAnimation() {
        if (isFlying) {
            wingFlapAngle += 0.7f;
            long flightDuration = System.currentTimeMillis() - flightStartTime;
            if (flightDuration < 800) {
                flyOffset -= 8f; // Take off stronger
            } else if (flightDuration < 2500) {
                // Hovering slightly
                flyOffset += (float) Math.sin(flightDuration * 0.01) * 2f;
            } else if (flightDuration < 3300) {
                flyOffset += 8f; // Landing
            } else {
                isFlying = false;
                flyOffset = 0;
            }
        } else {
            wingFlapAngle += 0.05f; // Gentle flap
            flyOffset = 0;
        }
    }

    private void drawLeaves(Canvas canvas) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xAA8BC34A); 
        
        for (Leaf leaf : leaves) {
            leaf.y += leaf.speedY;
            leaf.x += Math.sin(leaf.y * 0.02f) * leaf.swaySpeed;
            leaf.rotation += leaf.speedRot;

            if (leaf.y > getHeight() + 50) {
                leaf.y = -50;
                leaf.x = random.nextFloat() * getWidth();
            }

            canvas.save();
            canvas.translate(leaf.x, leaf.y);
            canvas.rotate(leaf.rotation);
            canvas.scale(leaf.scale, leaf.scale);

            // Draw a realistic leaf shape
            Path leafPath = new Path();
            leafPath.moveTo(0, -10);
            leafPath.cubicTo(10, -5, 10, 5, 0, 15);
            leafPath.cubicTo(-10, 5, -10, -5, 0, -10);
            canvas.drawPath(leafPath, paint);
            
            canvas.restore();
        }
    }

    private void drawPart(Canvas canvas, BodyPart part) {
        float w = part.width * part.currentProjScale;
        float h = part.height * part.currentProjScale;
        float halfW = w / 2f;
        float halfH = h / 2f;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(part.color);

        canvas.save();
        canvas.translate(part.currentProjX, part.currentProjY);
        
        Path path = new Path();

        switch (part.type) {
            case BRANCH:
                paint.setShader(new LinearGradient(0, -halfH, 0, halfH, 0xFF4E342E, 0xFF3E2723, Shader.TileMode.CLAMP));
                canvas.drawRoundRect(-halfW, -halfH, halfW, halfH, 20f * part.currentProjScale, 20f * part.currentProjScale, paint);
                paint.setShader(null);
                break;
                
            case HEAD:
            case EYE_BG:
            case PUPIL:
                canvas.drawOval(-halfW, -halfH, halfW, halfH, paint);
                break;
                
            case BEAK:
                // Curved sharp beak
                path.moveTo(-halfW, -halfH);
                path.quadTo(halfW*1.5f, -halfH*0.2f, 0, halfH);
                path.quadTo(-halfW*1.5f, -halfH*0.2f, -halfW, -halfH);
                paint.setShader(new LinearGradient(0, -halfH, 0, halfH, 0xFFFFCA28, 0xFFFF8F00, Shader.TileMode.CLAMP));
                canvas.drawPath(path, paint);
                paint.setShader(null);
                break;

            case BODY:
                // Egg-shaped body
                paint.setShader(new RadialGradient(0, -halfH*0.5f, Math.max(w, h), 0xFF689F38, 0xFF33691E, Shader.TileMode.CLAMP));
                path.moveTo(0, -halfH);
                path.cubicTo(halfW, -halfH, halfW*1.2f, halfH, 0, halfH);
                path.cubicTo(-halfW*1.2f, halfH, -halfW, -halfH, 0, -halfH);
                canvas.drawPath(path, paint);
                paint.setShader(null);
                break;

            case BELLY:
                paint.setShader(new RadialGradient(0, -halfH*0.2f, h, 0xFFDCEDC8, 0xFF7CB342, Shader.TileMode.CLAMP));
                path.moveTo(0, -halfH);
                path.cubicTo(halfW*0.9f, -halfH, halfW*1.1f, halfH, 0, halfH);
                path.cubicTo(-halfW*1.1f, halfH, -halfW, -halfH, 0, -halfH);
                canvas.drawPath(path, paint);
                paint.setShader(null);
                break;
                
            case WING_L:
            case WING_R:
                // Elegant wing shape
                paint.setShader(new LinearGradient(0, -halfH, 0, halfH, COLOR_WING_HIGHLIGHT, COLOR_WING, Shader.TileMode.CLAMP));
                path.moveTo(0, -halfH);
                path.cubicTo(halfW*1.5f, 0, halfW, halfH, 0, halfH*1.2f);
                path.cubicTo(-halfW*0.5f, halfH, -halfW, 0, 0, -halfH);
                canvas.drawPath(path, paint);
                paint.setShader(null);
                break;

            case TAIL:
                // Long drooping tail
                path.moveTo(-halfW, -halfH);
                path.lineTo(halfW, -halfH);
                path.cubicTo(halfW*0.8f, 0, halfW*0.2f, halfH, 0, halfH*1.5f);
                path.cubicTo(-halfW*0.2f, halfH, -halfW*0.8f, 0, -halfW, -halfH);
                canvas.drawPath(path, paint);
                break;

            case LEG:
            case CLAW:
                canvas.drawRoundRect(-halfW, -halfH, halfW, halfH, 10f * part.currentProjScale, 10f * part.currentProjScale, paint);
                break;
        }

        canvas.restore();
    }

    private enum PartType { BRANCH, BODY, BELLY, HEAD, BEAK, EYE_BG, PUPIL, WING_L, WING_R, TAIL, LEG, CLAW }

    private static class BodyPart {
        float x, y, z;      
        float width, height; 
        int color;
        PartType type;
        
        float currentProjX, currentProjY, currentProjScale, currentZOrder;

        BodyPart(float x, float y, float z, float width, float height, int color, PartType type) {
            this.x = x; this.y = y; this.z = z;
            this.width = width; this.height = height;
            this.color = color;
            this.type = type;
        }
    }

    private class Leaf {
        float x, y, rotation, scale;
        float speedY, speedRot, swaySpeed;

        Leaf() {
            x = random.nextFloat() * 1000f; // Broad initial X
            y = -(random.nextFloat() * 1000f);
            scale = 0.5f + random.nextFloat() * 1.5f;
            rotation = random.nextFloat() * 360f;
            speedY = 2f + random.nextFloat() * 3f;
            speedRot = -2f + random.nextFloat() * 4f;
            swaySpeed = 1f + random.nextFloat() * 2f;
        }
    }
}
