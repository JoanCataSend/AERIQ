package com.aeriq.app.ui;
/*
 * Fichero: AppSheet.java
 * Autor: Codigo Norte
 * Descripción: Archivo fuente de src/android/app/src/main/java/com/aeriq/app/ui.
 * Fecha: 2026-09-25
 * Copyright (c) 2026 Codigo Norte
 */
import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.*;

import static com.aeriq.app.ui.Views.*;

/**
 * Hoja inferior con el aspecto de la app (esquinas de 28 dp, asa, tarjetas y botones en cápsula) en lugar del
 * cuadro de diálogo de Android. Se cierra arrastrándola hacia abajo, tocando fuera o con el botón atrás.
 */
final class AppSheet {
    final LinearLayout content;
    private final Dialog dialog;
    private final Views ui;
    private final LinearLayout sheet, actions;
    private float downY;



    AppSheet(Activity activity, Views ui, String title, String subtitle) {
        this.ui = ui;
        dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);

        sheet = ui.column();
        GradientDrawable background = new GradientDrawable();
        background.setColor(WHITE);
        float corner = ui.dp(28);
        background.setCornerRadii(new float[]{corner, corner, corner, corner, 0, 0, 0, 0});
        sheet.setBackground(background);
        sheet.setPadding(ui.dp(20), ui.dp(10), ui.dp(20), ui.dp(20));

        // Asa y cabecera: se arrastran para cerrar.
        LinearLayout head = ui.column();
        View handle = new View(activity);
        handle.setBackground(ui.shape(0xffd5dcd9, 3, 0));
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(ui.dp(44), ui.dp(5));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        handleParams.bottomMargin = ui.dp(16);
        head.addView(handle, handleParams);
        TextView heading = ui.text(title, 22, true, INK);
        heading.setTypeface(android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD));
        head.addView(heading);
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView sub = ui.text(subtitle, 14, false, MUTED);
            sub.setPadding(0, ui.dp(4), 0, 0);
            head.addView(sub);
        }
        head.setPadding(0, 0, 0, ui.dp(16));
        head.setOnTouchListener(this::drag);
        sheet.addView(head, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(activity);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        content = ui.column();
        scroll.addView(content, new FrameLayout.LayoutParams(-1, -2));
        sheet.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        actions = ui.row();
        actions.setPadding(0, ui.dp(16), 0, 0);
        actions.setVisibility(View.GONE);
        sheet.addView(actions, new LinearLayout.LayoutParams(-1, -2));

        // La hoja ocupa como mucho el 85 % de la pantalla; si el contenido es más largo, se desplaza.
        FrameLayout holder = new FrameLayout(activity) {
            /**
             * Atiende el evento measure.
             * Diseño lógico: (int widthSpec, int heightSpec) → onMeasure() → void; según doc/android_design.md.
             */
            @Override protected void onMeasure(int widthSpec, int heightSpec) {
                int max = Math.round(getResources().getDisplayMetrics().heightPixels * 0.85f);
                super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST));
            }
        };
        holder.addView(sheet, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        dialog.setContentView(holder);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(0));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.BOTTOM);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.35f);
            window.setWindowAnimations(android.R.style.Animation_InputMethod);
        }
    }

    /** Título pequeño de sección. */
    void section(String label) {
        TextView text = ui.text(label, 13, true, MUTED);
        text.setPadding(ui.dp(4), content.getChildCount() == 0 ? 0 : ui.dp(18), 0, ui.dp(8));
        content.addView(text);
    }

    /** Tarjeta gris claro donde van las filas de ajustes. */
    LinearLayout group() {
        LinearLayout group = ui.column();
        group.setBackground(ui.shape(0xfff1f4f3, 20, 0));
        group.setPadding(ui.dp(16), ui.dp(4), ui.dp(16), ui.dp(4));
        content.addView(group, new LinearLayout.LayoutParams(-1, -2));
        return group;
    }

    /**
     * Fila con texto y un interruptor de la app; tocar la fila entera también lo cambia. Admite un interruptor ya
     * creado, para que conserve su estado y sus avisos entre una apertura y otra.
     */
    Switch toggle(LinearLayout group, String label, String hint, Switch toggle) {
        if (toggle.getParent() != null) ((ViewGroup) toggle.getParent()).removeView(toggle);
        divider(group);
        LinearLayout row = ui.row();
        row.setMinimumHeight(ui.dp(56));
        row.setPadding(0, ui.dp(8), 0, ui.dp(8));
        LinearLayout texts = ui.column();
        texts.addView(ui.text(label, 15, true, INK));
        if (hint != null) texts.addView(ui.text(hint, 12, false, MUTED));
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        toggle.setContentDescription(label);
        row.addView(toggle);
        row.setOnClickListener(view -> toggle.toggle());
        group.addView(row, new LinearLayout.LayoutParams(-1, -2));
        return toggle;
    }

    /** Fila informativa: icono en círculo, etiqueta y valor. Devuelve el texto del valor. */
    TextView info(LinearLayout group, int icon, String label, String value, int tint) {
        divider(group);
        LinearLayout row = ui.row();
        row.setGravity(Gravity.TOP);
        row.setPadding(0, ui.dp(12), 0, ui.dp(12));
        ImageView picture = ui.image(icon, 36, 36);
        picture.setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8));
        picture.setColorFilter(tint);
        picture.setBackground(ui.shape(WHITE, 18, 0));
        row.addView(picture, new LinearLayout.LayoutParams(ui.dp(36), ui.dp(36)));
        LinearLayout texts = ui.column();
        texts.setPadding(ui.dp(12), 0, 0, 0);
        texts.addView(ui.text(label, 12, true, MUTED));
        TextView text = ui.text(value, 15, false, INK);
        text.setPadding(0, ui.dp(2), 0, 0);
        text.setLineSpacing(ui.dp(2), 1);
        texts.addView(text);
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        group.addView(row, new LinearLayout.LayoutParams(-1, -2));
        return text;
    }

    /**
     * Gestiona divider.
     * Diseño lógico: (LinearLayout group) → divider() → void; según doc/android_design.md.
     */
    private void divider(LinearLayout group) {
        if (group.getChildCount() == 0) return;
        View line = new View(group.getContext());
        line.setBackgroundColor(0xffe1e8e5);
        group.addView(line, new LinearLayout.LayoutParams(-1, 1));
    }

    /** Botón verde principal. Cierra la hoja y después ejecuta la acción. */
    void primary(String label, Runnable click) { action(label, GREEN, WHITE, click); }

    /** Botón blanco con borde. */
    void secondary(String label, Runnable click) { action(label, WHITE, INK, click); }

    /**
     * Gestiona action.
     * Diseño lógico: (String label, int background, int foreground, Runnable click) → action() → void; según doc/android_design.md.
     */
    private void action(String label, int background, int foreground, Runnable click) {
        if (actions.getChildCount() > 0) actions.addView(new View(actions.getContext()), new LinearLayout.LayoutParams(ui.dp(10), 1));
        TextView button = ui.text(label, 15, true, foreground);
        button.setGravity(Gravity.CENTER);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x220e6b5e),
            ui.shape(background, 26, background == WHITE ? 0xffd5dcd9 : 0), null));
        button.setFocusable(true);
        button.setOnClickListener(view -> {
            dialog.dismiss();
            if (click != null) click.run();
        });
        actions.addView(button, new LinearLayout.LayoutParams(0, ui.dp(52), 1));
        actions.setVisibility(View.VISIBLE);
    }



    void show() { dialog.show(); }

    /** Arrastrar hacia abajo la cabecera: sigue al dedo y, pasado un tercio o con un gesto rápido, se cierra. */
    /**
     * Diseño lógico: (View view, MotionEvent event) → drag() → boolean; según doc/android_design.md.
     */
    private boolean drag(View view, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downY = event.getRawY();
                sheet.animate().cancel();
                return true;
            case MotionEvent.ACTION_MOVE:
                sheet.setTranslationY(Math.max(0, event.getRawY() - downY));
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                float moved = sheet.getTranslationY();
                long time = Math.max(1, event.getEventTime() - event.getDownTime());
                if (moved > sheet.getHeight() / 3f || (moved > ui.dp(24) && moved / time > 0.6f)) {
                    sheet.animate().translationY(sheet.getHeight()).setDuration(160).withEndAction(dialog::dismiss).start();
                } else {
                    sheet.animate().translationY(0).setDuration(200)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
                }
                return true;
            }
            default:
                return false;
        }
    }
}
