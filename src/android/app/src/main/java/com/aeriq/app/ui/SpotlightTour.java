package com.aeriq.app.ui;
/*
 * Fichero: SpotlightTour.java
 * Autor: Codigo Norte
 * Descripción: Archivo fuente de src/android/app/src/main/java/com/aeriq/app/ui.
 * Fecha: 2026-09-25
 * Copyright (c) 2026 Codigo Norte
 */
import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.animation.DecelerateInterpolator;
import android.widget.*;
import java.util.List;
import java.util.function.Function;

import static com.aeriq.app.ui.Views.*;

/**
 * Tour guiado con foco (HU33, «coach marks»): oscurece toda la app y deja iluminado el elemento que se explica, con
 * una tarjeta al lado («Paso X de N», título, texto, «Saltar tutorial», «Atrás» y «Siguiente»). Los elementos se
 * buscan por etiqueta ({@code View.setTag("tour:…")}) en la pantalla real; si uno no está (por ejemplo, una pestaña
 * que esa cuenta no tiene), ese paso se salta. Mientras dura, no se puede tocar la app de debajo.
 */
public final class SpotlightTour extends FrameLayout {
    /** Un paso: qué elemento iluminar (etiqueta) y qué decir. */
    public static final class Step {
        final String tag, title, text;
        public Step(String tag, String title, String text) { this.tag = tag; this.title = title; this.text = text; }
    }

    private static final int SCRIM = 0xba000000, RING = 0xff79d9b6;
    private final Views ui;
    private final List<Step> steps;
    private final Function<String, View> finder;
    private final Runnable onClose;
    private final Paint scrim = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG),
        arrow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF hole = new RectF(), from = new RectF(), to = new RectF();
    private final LinearLayout card;
    private final TextView counter, title, text;
    private final Button back, next;
    private ValueAnimator move;
    private int index = -1, retries;
    private boolean above, closing;



    public SpotlightTour(Activity activity, Views ui, List<Step> steps, Function<String, View> finder, Runnable onClose) {
        super(activity);
        this.ui = ui;
        this.steps = steps;
        this.finder = finder;
        this.onClose = onClose;
        setWillNotDraw(false);
        setClickable(true); // la app de debajo no recibe toques mientras dura el tour
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        scrim.setColor(SCRIM);
        path.setFillType(Path.FillType.EVEN_ODD);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(ui.dp(2));
        ring.setColor(RING);
        arrow.setColor(WHITE);

        card = ui.column();
        card.setBackground(ui.shape(WHITE, 16, 0));
        card.setElevation(ui.dp(8));
        card.setPadding(ui.dp(18), ui.dp(16), ui.dp(18), ui.dp(12));
        card.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        counter = ui.text("", 12, true, GREEN);
        counter.setLetterSpacing(0.06f);
        card.addView(counter);
        title = ui.text("", 18, true, INK);
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        title.setPadding(0, ui.dp(6), 0, ui.dp(6));
        title.setAccessibilityHeading(true);
        card.addView(title);
        text = ui.text("", 15, false, MUTED);
        text.setLineSpacing(ui.dp(3), 1);
        card.addView(text);
        LinearLayout buttons = ui.row();
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        buttons.setPadding(0, ui.dp(12), 0, 0);
        TextView skip = ui.text("Saltar tutorial", 14, true, MUTED);
        skip.setGravity(Gravity.CENTER_VERTICAL);
        skip.setMinHeight(ui.dp(44));
        skip.setPadding(0, 0, ui.dp(12), 0);
        skip.setFocusable(true);
        skip.setOnClickListener(view -> close());
        skip.setContentDescription("Saltar el tutorial");
        buttons.addView(skip, new LinearLayout.LayoutParams(0, -2, 1));
        back = ui.action("Atrás", WHITE, INK, 12, this::previous);
        back.setContentDescription("Paso anterior del tutorial");
        buttons.addView(back, new LinearLayout.LayoutParams(-2, ui.dp(44)));
        View gap = new View(activity);
        buttons.addView(gap, new LinearLayout.LayoutParams(ui.dp(8), 1));
        next = ui.action("Siguiente", GREEN, WHITE, 12, this::next);
        buttons.addView(next, new LinearLayout.LayoutParams(-2, ui.dp(44)));
        card.addView(buttons, new LinearLayout.LayoutParams(-1, -2));
        card.setAlpha(0f);
        addView(card, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START));
    }

    private final android.view.ViewTreeObserver.OnGlobalLayoutListener follow = this::follow;

    /**
     * Atiende el evento attached to window.
     * Diseño lógico: (sin parámetros) → onAttachedToWindow() → void; según doc/android_design.md.
     */
    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnGlobalLayoutListener(follow);
    }

    /**
     * Atiende el evento detached from window.
     * Diseño lógico: (sin parámetros) → onDetachedFromWindow() → void; según doc/android_design.md.
     */
    @Override protected void onDetachedFromWindow() {
        getViewTreeObserver().removeOnGlobalLayoutListener(follow);
        if (move != null) move.cancel();
        super.onDetachedFromWindow();
    }

    /** Si el elemento iluminado se ha movido (la pantalla aún se estaba colocando), el foco y la tarjeta lo siguen. */
    /**
     * Diseño lógico: (sin parámetros) → follow() → void; según doc/android_design.md.
     */
    private void follow() {
        if (index < 0 || closing || (move != null && move.isRunning())) return;
        View view = target(steps.get(index));
        if (view == null) return;
        RectF now = new RectF();
        locate(view, now);
        if (Math.abs(now.top - to.top) < 1 && Math.abs(now.left - to.left) < 1 && Math.abs(now.bottom - to.bottom) < 1) return;
        to.set(now);
        hole.set(now);
        placeCard();
    }

    /** Empieza por el primer paso cuyo elemento esté en pantalla. */
    /**
     * Diseño lógico: (sin parámetros) → start() → void; según doc/android_design.md.
     */
    public void start() {
        setAlpha(0f);
        animate().alpha(1f).setDuration(200).start();
        show(0, 1);
    }

    /** Botón atrás del móvil: paso anterior; en el primero no hace nada (para salir está «Saltar tutorial»). */
    /**
     * Diseño lógico: (sin parámetros) → back() → void; según doc/android_design.md.
     */
    public void back() { previous(); }

    /**
     * Gestiona next.
     * Diseño lógico: (sin parámetros) → next() → void; según doc/android_design.md.
     */
    private void next() {
        if (nextAvailable(index + 1, 1) < 0) close(); else show(index + 1, 1);
    }

    /**
     * Gestiona previous.
     * Diseño lógico: (sin parámetros) → previous() → void; según doc/android_design.md.
     */
    private void previous() {
        if (nextAvailable(index - 1, -1) >= 0) show(index - 1, -1);
    }

    /** Índice del siguiente paso con elemento visible en esa dirección, o -1. */
    /**
     * Diseño lógico: (int from, int direction) → nextAvailable() → int; según doc/android_design.md.
     */
    private int nextAvailable(int from, int direction) {
        for (int i = from; i >= 0 && i < steps.size(); i += direction) if (target(steps.get(i)) != null) return i;
        return -1;
    }

    /**
     * Gestiona target.
     * Diseño lógico: (Step step) → target() → View; según doc/android_design.md.
     */
    private View target(Step step) {
        View view = finder.apply(step.tag);
        return view != null && view.isShown() && view.getWidth() > 0 ? view : null;
    }

    /**
     * Muestra.
     * Diseño lógico: (int wanted, int direction) → show() → void; según doc/android_design.md.
     */
    private void show(int wanted, int direction) {
        int found = nextAvailable(wanted, direction);
        if (found < 0) {
            // La pantalla aún se está dibujando (p. ej. el mapa recién abierto): se reintenta un momento.
            if (index < 0 && retries++ < 20) { postDelayed(() -> show(wanted, direction), 100); return; }
            if (index < 0) close();
            return;
        }
        index = found;
        Step step = steps.get(index);
        int position = 1, total = 0;
        for (int i = 0; i < steps.size(); i++) {
            if (target(steps.get(i)) == null) continue;
            total++;
            if (i < index) position++;
        }
        boolean last = nextAvailable(index + 1, 1) < 0;
        counter.setText("PASO " + position + " DE " + total);
        title.setText(step.title);
        text.setText(step.text);
        back.setVisibility(nextAvailable(index - 1, -1) >= 0 ? VISIBLE : GONE);
        next.setText(last ? "Empezar" : "Siguiente");
        next.setContentDescription(last ? "Terminar el tutorial" : "Paso siguiente del tutorial");
        card.setContentDescription("Paso " + position + " de " + total + ". " + step.title + ". " + step.text);

        locate(target(step), to);
        if (hole.isEmpty()) {
            hole.set(to);
        } else {
            from.set(hole);
            if (move != null) move.cancel();
            move = ValueAnimator.ofFloat(0, 1);
            move.setDuration(280);
            move.setInterpolator(new DecelerateInterpolator());
            move.addUpdateListener(a -> {
                float t = (float) a.getAnimatedValue();
                hole.set(from.left + (to.left - from.left) * t, from.top + (to.top - from.top) * t,
                    from.right + (to.right - from.right) * t, from.bottom + (to.bottom - from.bottom) * t);
                invalidate();
            });
            move.start();
        }
        card.animate().cancel();
        card.setAlpha(0f);
        requestLayout();
        post(() -> {
            placeCard();
            card.animate().alpha(1f).setDuration(180).start();
            card.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED);
            announceForAccessibility(card.getContentDescription());
        });
        invalidate();
    }

    /** Rectángulo del elemento en coordenadas de esta capa, con un margen alrededor. */
    /**
     * Diseño lógico: (View view, RectF out) → locate() → void; según doc/android_design.md.
     */
    private void locate(View view, RectF out) {
        if (view == null) return;
        int[] mine = new int[2], theirs = new int[2];
        getLocationInWindow(mine);
        view.getLocationInWindow(theirs);
        float pad = ui.dp(6);
        out.set(theirs[0] - mine[0] - pad, theirs[1] - mine[1] - pad,
            theirs[0] - mine[0] + view.getWidth() + pad, theirs[1] - mine[1] + view.getHeight() + pad);
    }

    /**
     * Gestiona inset top.
     * Diseño lógico: (sin parámetros) → insetTop() → int; según doc/android_design.md.
     */
    private int insetTop() {
        WindowInsets insets = getRootWindowInsets();
        return insets == null ? 0 : insets.getSystemWindowInsetTop();
    }

    /**
     * Gestiona inset bottom.
     * Diseño lógico: (sin parámetros) → insetBottom() → int; según doc/android_design.md.
     */
    private int insetBottom() {
        WindowInsets insets = getRootWindowInsets();
        return insets == null ? 0 : insets.getSystemWindowInsetBottom();
    }

    /**
     * Atiende el evento measure.
     * Diseño lógico: (int widthSpec, int heightSpec) → onMeasure() → void; según doc/android_design.md.
     */
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        card.getLayoutParams().width = Math.min(width - ui.dp(32), ui.dp(360));
        super.onMeasure(widthSpec, heightSpec);
    }

    /**
     * Atiende el evento layout.
     * Diseño lógico: (boolean changed, int left, int top, int right, int bottom) → onLayout() → void; según doc/android_design.md.
     */
    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        placeCard();
    }

    /** Debajo del elemento si cabe; si no, encima; y si tampoco, centrada. Siempre dentro de la pantalla. */
    /**
     * Diseño lógico: (sin parámetros) → placeCard() → void; según doc/android_design.md.
     */
    private void placeCard() {
        if (to.isEmpty() || card.getHeight() == 0) return;
        float gap = ui.dp(16), margin = ui.dp(16), w = getWidth(), h = getHeight(), cw = card.getWidth(), ch = card.getHeight();
        float y;
        if (to.bottom + gap + ch <= h - insetBottom() - margin) { y = to.bottom + gap; above = false; }
        else if (to.top - gap - ch >= insetTop() + margin) { y = to.top - gap - ch; above = true; }
        else { y = Math.max(insetTop() + margin, (h - ch) / 2f); above = false; }
        float x = Math.max(margin, Math.min(w - margin - cw, to.centerX() - cw / 2f));
        card.setTranslationX(x);
        card.setTranslationY(y);
        invalidate();
    }

    /**
     * Atiende el evento draw.
     * Diseño lógico: (Canvas canvas) → onDraw() → void; según doc/android_design.md.
     */
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float radius = ui.dp(14);
        path.reset();
        path.addRect(0, 0, getWidth(), getHeight(), Path.Direction.CW);
        if (!hole.isEmpty()) path.addRoundRect(hole, radius, radius, Path.Direction.CW);
        canvas.drawPath(path, scrim);
        if (hole.isEmpty()) return;
        canvas.drawRoundRect(hole, radius, radius, ring);
        // Flecha blanca de la tarjeta hacia el elemento iluminado.
        if (card.getAlpha() > 0 && (move == null || !move.isRunning())) {
            float cx = Math.max(card.getTranslationX() + ui.dp(24),
                Math.min(card.getTranslationX() + card.getWidth() - ui.dp(24), hole.centerX()));
            float size = ui.dp(9), edge = above ? card.getTranslationY() + card.getHeight() : card.getTranslationY();
            boolean touches = above ? edge < hole.top : edge > hole.bottom;
            if (touches) {
                Path tip = new Path();
                tip.moveTo(cx - size, edge);
                tip.lineTo(cx, above ? edge + size : edge - size);
                tip.lineTo(cx + size, edge);
                tip.close();
                arrow.setAlpha((int) (255 * card.getAlpha()));
                canvas.drawPath(tip, arrow);
            }
        }
    }

    /**
     * Gestiona dispatch draw.
     * Diseño lógico: (Canvas canvas) → dispatchDraw() → void; según doc/android_design.md.
     */
    @Override protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        invalidateIfCardFading();
    }

    /**
     * Gestiona invalidate if card fading.
     * Diseño lógico: (sin parámetros) → invalidateIfCardFading() → void; según doc/android_design.md.
     */
    private void invalidateIfCardFading() {
        if (card.getAlpha() > 0 && card.getAlpha() < 1) postInvalidateOnAnimation();
    }

    /**
     * Cierra.
     * Diseño lógico: (sin parámetros) → close() → void; según doc/android_design.md.
     */
    private void close() {
        if (closing) return;
        closing = true;
        if (move != null) move.cancel();
        animate().alpha(0f).setDuration(180).withEndAction(onClose).start();
    }
}
