package com.aeriq.app.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.provider.Settings;
import android.view.View;
import android.view.animation.LinearInterpolator;
import com.aeriq.app.R;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Animación de apertura (HU34): el logo de AERIQ gira como un ventilador —acelera, va rápido y frena— y suelta
 * partículas de «aire» por las puntas de las aspas, que salen girando hacia los lados y se desvanecen. Debajo aparece
 * la palabra AERIQ. Dura {@link #DURATION} ms y termina sola (también se puede tocar para saltarla).
 * Si el móvil tiene las animaciones desactivadas, se ve el logo fijo un momento. Si el logo no se puede cargar, se
 * termina al instante: la app nunca se queda bloqueada en esta pantalla.
 */
public final class SplashView extends View {
    public static final long DURATION = 1800, STATIC_DURATION = 600;
    private static final int[] PARTICLE_COLORS = {0xff0e6b5e, 0xff219c72, 0xff62d2b3, 0xff9fd9c6};

    private static final class Particle {
        float angle, radius, speed, spin, size, born;
        int color;
    }

    private final Bitmap logo;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Particle> particles = new ArrayList<>();
    private final Random random = new Random(7);
    private final boolean reduced;
    private ValueAnimator animator;
    private float progress, lastEmit, rotation;
    private Runnable done;
    private boolean finished;

    public SplashView(Context context) {
        super(context);
        setBackgroundColor(Views.BACKGROUND);
        Bitmap image = null;
        try { image = BitmapFactory.decodeResource(getResources(), R.drawable.aeriq_mark); } catch (RuntimeException ignored) { }
        logo = image;
        float scale = Settings.Global.getFloat(context.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
        reduced = scale == 0f;
        text.setColor(Views.INK);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        text.setLetterSpacing(0.12f);
        setContentDescription("AERIQ");
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setOnClickListener(view -> finish());
    }

    /** Arranca la animación; {@code onDone} se llama una sola vez al terminar (o al saltarla). */
    /**
     * Diseño lógico: (Runnable onDone) → start() → void; según doc/android_design.md.
     */
    public void start(Runnable onDone) {
        done = onDone;
        if (logo == null) { post(this::finish); return; }
        if (reduced) {
            progress = 1;
            invalidate();
            postDelayed(this::finish, STATIC_DURATION);
            return;
        }
        animator = ValueAnimator.ofFloat(0, 1);
        animator.setDuration(DURATION);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            step();
            invalidate();
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            /**
             * Atiende el evento animation end.
             * Diseño lógico: (android.animation.Animator animation) → onAnimationEnd() → void; según doc/android_design.md.
             */
            @Override public void onAnimationEnd(android.animation.Animator animation) { finish(); }
        });
        animator.start();
    }

    /**
     * Ángulo del logo según el avance: velocidad en forma de campana (arranca, gira rápido y frena del todo).
     * Es la integral de sin²(πt): da 3,5 vueltas en total y termina en reposo.
     */
    /**
     * Diseño lógico: (float t) → angleAt() → float; según doc/android_design.md.
     */
    private static float angleAt(float t) {
        double turns = 3.5;
        return (float) (360 * turns * (t - Math.sin(2 * Math.PI * t) / (2 * Math.PI)));
    }

    /**
     * Gestiona step.
     * Diseño lógico: (sin parámetros) → step() → void; según doc/android_design.md.
     */
    private void step() {
        rotation = angleAt(progress);
        // Las partículas salen mientras el ventilador va rápido (de 15 % a 80 % del tiempo), 3 por aspa cada 2 %.
        if (progress > 0.15f && progress < 0.8f && progress - lastEmit > 0.02f) {
            lastEmit = progress;
            for (int blade = 0; blade < 3; blade++) {
                for (int k = 0; k < 2; k++) {
                    Particle p = new Particle();
                    p.angle = (float) Math.toRadians(rotation + blade * 120 + 20 + random.nextInt(30));
                    p.radius = 0.42f;
                    p.speed = 0.55f + random.nextFloat() * 0.5f;
                    p.spin = 2.2f + random.nextFloat() * 1.5f;
                    p.size = 2.5f + random.nextFloat() * 4f;
                    p.color = PARTICLE_COLORS[random.nextInt(PARTICLE_COLORS.length)];
                    p.born = progress;
                    particles.add(p);
                }
            }
        }
    }

    /**
     * Atiende el evento draw.
     * Diseño lógico: (Canvas canvas) → onDraw() → void; según doc/android_design.md.
     */
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight(), cx = w / 2f, cy = h * 0.44f;
        float size = Math.min(w, h) * 0.34f, density = getResources().getDisplayMetrics().density;
        // Partículas: salen girando (como el aire de un ventilador) y se desvanecen al alejarse.
        for (Particle p : particles) {
            float age = progress - p.born;
            if (age < 0 || age > 0.45f) continue;
            float r = (p.radius + p.speed * age) * size;
            float a = p.angle + p.spin * age;
            float alpha = 1f - age / 0.45f;
            paint.setColor(p.color);
            paint.setAlpha((int) (230 * alpha));
            canvas.drawCircle(cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r, p.size * density * (0.6f + alpha * 0.4f), paint);
        }
        if (logo != null) {
            paint.setAlpha(255);
            float appear = Math.min(1f, progress / 0.12f);
            float s = size * (0.85f + 0.15f * appear);
            canvas.save();
            canvas.rotate(rotation, cx, cy);
            paint.setAlpha((int) (255 * appear));
            android.graphics.RectF box = new android.graphics.RectF(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2);
            canvas.drawBitmap(logo, null, box, paint);
            canvas.restore();
        }
        // La palabra AERIQ aparece cuando el ventilador ya frena.
        float word = reduced ? 1f : Math.max(0f, Math.min(1f, (progress - 0.55f) / 0.25f));
        if (word > 0) {
            text.setTextSize(30 * density);
            text.setAlpha((int) (255 * word));
            canvas.drawText("AERIQ", cx, cy + size * 0.5f + 64 * density - 10 * density * (1 - word), text);
        }
    }

    /**
     * Gestiona finish.
     * Diseño lógico: (sin parámetros) → finish() → void; según doc/android_design.md.
     */
    private void finish() {
        if (finished) return;
        finished = true;
        if (animator != null) animator.cancel();
        animate().alpha(0f).setDuration(reduced ? 0 : 220).withEndAction(() -> { if (done != null) done.run(); }).start();
    }

    /**
     * Atiende el evento detached from window.
     * Diseño lógico: (sin parámetros) → onDetachedFromWindow() → void; según doc/android_design.md.
     */
    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animator != null) animator.cancel();
    }
}
