package com.aeriq.app.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

public final class Views {
    public static final int BACKGROUND = 0xfff4f7f6, WHITE = 0xffffffff, INK = 0xff17201d;
    public static final int GREEN = 0xff0e6b5e, DARK = 0xff1c2522, MUTED = 0xff5e6b66;
    public static final int SOFT = 0xffe4f0ed, LINE = 0xffe3eae7, RED = 0xffc8433d;
    private final Context context;

    public Views(Context context) { this.context = context; }
    public int dp(float value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }

    public GradientDrawable shape(int color, int radius, int border) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(radius));
        if (border != 0) background.setStroke(dp(1), border);
        return background;
    }

    public LinearLayout column() {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        return column;
    }

    public LinearLayout row() {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    public void add(LinearLayout parent, View view) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(12);
        parent.addView(view, params);
    }

    public void weighted(LinearLayout parent, View view) {
        parent.addView(view, new LinearLayout.LayoutParams(0, -2, 1));
    }

    public TextView text(String value, int size, boolean bold, int color) {
        TextView text = new TextView(context);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        if (bold) text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return text;
    }

    public TextView label(LinearLayout parent, String value, int size, boolean bold, int color) {
        TextView text = text(value, size, bold, color);
        add(parent, text);
        return text;
    }

    public void note(LinearLayout parent, String value) { label(parent, value, 13, false, MUTED); }

    public LinearLayout card(LinearLayout parent) {
        LinearLayout card = column();
        card.setBackground(shape(WHITE, 22, LINE));
        card.setPadding(dp(16), dp(16), dp(16), dp(4));
        add(parent, card);
        return card;
    }

    public Button action(String label, int background, int foreground, Runnable click) {
        Button button = new Button(context);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTextColor(foreground);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setMinHeight(dp(52));
        button.setStateListAnimator(null);
        button.setElevation(0);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x220e6b5e),
            shape(background, 14, background == WHITE ? LINE : 0), null));
        button.setOnClickListener(view -> click.run());
        return button;
    }

    public Button button(LinearLayout parent, String label, Runnable click) {
        Button button = action(label, GREEN, WHITE, click);
        add(parent, button);
        return button;
    }

    public void outline(LinearLayout parent, String label, Runnable click) {
        add(parent, action(label, WHITE, INK, click));
    }

    public void link(LinearLayout parent, String label, Runnable click) {
        TextView link = text(label, 13, true, GREEN);
        link.setPadding(0, dp(4), 0, dp(4));
        link.setFocusable(true);
        link.setOnClickListener(view -> click.run());
        add(parent, link);
    }

    public EditText field(LinearLayout parent, String label, String value, int type) {
        TextView caption = text(label, 13, true, INK);
        caption.setPadding(0, 0, 0, dp(5));
        parent.addView(caption);
        LinearLayout row = row();
        row.setBackground(shape(0xffeff3f1, 14, LINE));
        EditText input = new EditText(context);
        input.setId(View.generateViewId());
        input.setSingleLine(true);
        input.setInputType(type);
        input.setTypeface(Typeface.DEFAULT);
        input.setTextSize(15);
        input.setTextColor(INK);
        input.setHintTextColor(MUTED);
        input.setText(value);
        input.setHint(label);
        input.setPadding(dp(14), dp(12), dp(14), dp(12));
        input.setBackground(null);
        input.setMinHeight(dp(52));
        caption.setLabelFor(input.getId());
        weighted(row, input);
        if ((type & InputType.TYPE_MASK_VARIATION) == InputType.TYPE_TEXT_VARIATION_PASSWORD) {
            input.setSaveEnabled(false);
            TextView show = text("Mostrar", 13, false, GREEN);
            show.setGravity(Gravity.CENTER);
            show.setMinWidth(dp(72));
            show.setMinHeight(dp(48));
            show.setFocusable(true);
            show.setOnClickListener(view -> {
                boolean hidden = input.getTransformationMethod() instanceof android.text.method.PasswordTransformationMethod;
                input.setTransformationMethod(hidden ? null : android.text.method.PasswordTransformationMethod.getInstance());
                input.setSelection(input.length());
                show.setText(hidden ? "Ocultar" : "Mostrar");
            });
            row.addView(show);
        }
        add(parent, row);
        return input;
    }

    public void segments(LinearLayout parent, String[] labels, int initial,
                         java.util.function.IntConsumer selected) {
        LinearLayout strip = row();
        strip.setPadding(dp(4), dp(4), dp(4), dp(4));
        strip.setBackground(shape(0xffeff3f1, 14, 0));
        TextView[] items = new TextView[labels.length];
        for (int index = 0; index < labels.length; index++) {
            final int option = index;
            TextView item = text(labels[index], 12, true, MUTED);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(dp(8), 0, dp(4), 0);
            item.setMinHeight(dp(34));
            item.setFocusable(true);
            items[index] = item;
            weighted(strip, item);
            item.setOnClickListener(view -> {
                selectSegment(items, option);
                selected.accept(option);
            });
        }
        selectSegment(items, initial);
        add(parent, strip);
    }

    private void selectSegment(TextView[] items, int active) {
        for (int i = 0; i < items.length; i++) {
            items[i].setSelected(i == active);
            items[i].setTextColor(i == active ? GREEN : MUTED);
            items[i].setBackground(i == active ? shape(WHITE, 10, LINE) : null);
        }
    }

    public void info(LinearLayout parent, String label, String value) {
        LinearLayout line = row();
        weighted(line, text(label, 13, false, MUTED));
        weighted(line, text(value, 13, true, INK));
        add(parent, line);
    }

    public void menu(LinearLayout parent, int icon, String title, int color, Runnable click) {
        LinearLayout item = row();
        item.setMinimumHeight(dp(60));
        ImageView picture = image(icon, 40, 40);
        picture.setBackground(shape(SOFT, 12, 0));
        picture.setPadding(dp(10), dp(10), dp(10), dp(10));
        item.addView(picture, new LinearLayout.LayoutParams(dp(40), dp(40)));
        TextView label = text(title, 14, true, color);
        label.setPadding(dp(12), 0, dp(8), 0);
        weighted(item, label);
        item.addView(image(com.aeriq.app.R.drawable.ic_chevron, 18, 18),
            new LinearLayout.LayoutParams(dp(18), dp(18)));
        item.setFocusable(true);
        item.setOnClickListener(view -> click.run());
        parent.addView(item, new LinearLayout.LayoutParams(-1, -2));
        View divider = new View(context);
        divider.setBackgroundColor(LINE);
        parent.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
    }

    public ImageView image(int resource, int width, int height) {
        ImageView image = new ImageView(context);
        image.setImageResource(resource);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setLayoutParams(new ViewGroup.LayoutParams(dp(width), dp(height)));
        return image;
    }
}
