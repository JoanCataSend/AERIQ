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
    public static final int BANNER_BG = 0xffe8f3ef;
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
        card.setPadding(dp(16), dp(16), dp(16), dp(12));
        add(parent, card);
        return card;
    }

    public LinearLayout banner(LinearLayout parent, int bg) {
        LinearLayout card = column();
        card.setBackground(shape(bg, 18, 0));
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        add(parent, card);
        return card;
    }

    public Button action(String label, int background, int foreground, Runnable click) {
        Button button = new Button(context);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setTextColor(foreground);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setMinimumHeight(dp(52));
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

    public Button buttonWithIcon(LinearLayout parent, String label, int iconRes, int background, int foreground, Runnable click) {
        Button button = new Button(context);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setTextColor(foreground);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setMinimumHeight(dp(52));
        button.setStateListAnimator(null);
        button.setElevation(0);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x220e6b5e),
            shape(background, 14, background == WHITE ? LINE : 0), null));
        if (iconRes != 0) {
            android.graphics.drawable.Drawable icon = context.getDrawable(iconRes);
            if (icon != null) {
                icon = icon.mutate();
                icon.setBounds(0, 0, dp(22), dp(22));
                if (foreground == WHITE) {
                    icon.setTint(WHITE);
                }
                button.setCompoundDrawables(icon, null, null, null);
                button.setCompoundDrawablePadding(dp(10));
            }
        }
        button.setOnClickListener(view -> click.run());
        add(parent, button);
        return button;
    }

    public LinearLayout socialButton(String label, int iconRes, Runnable click) {
        LinearLayout button = row();
        button.setGravity(Gravity.CENTER);
        button.setMinimumHeight(dp(48));
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x15000000),
            shape(WHITE, 14, LINE), null));
        button.setClickable(true);
        button.setFocusable(true);
        if (iconRes != 0) {
            ImageView icon = new ImageView(context);
            icon.setImageResource(iconRes);
            LinearLayout.LayoutParams icParams = new LinearLayout.LayoutParams(dp(20), dp(20));
            icParams.rightMargin = dp(8);
            button.addView(icon, icParams);
        }
        TextView text = text(label, 14, true, INK);
        button.addView(text);
        button.setOnClickListener(view -> click.run());
        return button;
    }

    public void dividerWithText(LinearLayout parent, String text) {
        LinearLayout row = row();
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(14);
        params.bottomMargin = dp(14);
        row.setLayoutParams(params);

        View leftLine = new View(context);
        leftLine.setBackgroundColor(LINE);
        row.addView(leftLine, new LinearLayout.LayoutParams(0, dp(1), 1));

        TextView label = text(text, 12, false, MUTED);
        label.setPadding(dp(12), 0, dp(12), 0);
        label.setGravity(Gravity.CENTER);
        row.addView(label);

        View rightLine = new View(context);
        rightLine.setBackgroundColor(LINE);
        row.addView(rightLine, new LinearLayout.LayoutParams(0, dp(1), 1));

        parent.addView(row);
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
        input.setMinimumHeight(dp(52));
        caption.setLabelFor(input.getId());
        weighted(row, input);
        if ((type & InputType.TYPE_MASK_VARIATION) == InputType.TYPE_TEXT_VARIATION_PASSWORD) {
            input.setSaveEnabled(false);
            TextView toggle = text("Mostrar", 13, true, GREEN);
            toggle.setPadding(dp(8), dp(12), dp(16), dp(12));
            toggle.setFocusable(true);
            toggle.setClickable(true);
            toggle.setOnClickListener(view -> {
                boolean hidden = input.getTransformationMethod() instanceof android.text.method.PasswordTransformationMethod;
                input.setTransformationMethod(hidden ? null : android.text.method.PasswordTransformationMethod.getInstance());
                input.setSelection(input.length());
                toggle.setText(hidden ? "Ocultar" : "Mostrar");
            });
            row.addView(toggle);
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
            item.setMinimumHeight(dp(34));
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

    /** Interruptor redondeado verde como en el diseño (52 × 30 dp). */
    public Switch toggle(boolean checked) {
        Switch toggle = new Switch(context);
        int[][] states = {{android.R.attr.state_checked}, {}};
        GradientDrawable track = new GradientDrawable();
        track.setCornerRadius(dp(15));
        track.setSize(dp(52), dp(30));
        track.setColor(new ColorStateList(states, new int[]{GREEN, 0xffcfd8d4}));
        GradientDrawable thumb = new GradientDrawable();
        thumb.setShape(GradientDrawable.OVAL);
        thumb.setSize(dp(30), dp(30));
        thumb.setColor(WHITE);
        thumb.setStroke(dp(4), 0x00000000);
        toggle.setTrackDrawable(track);
        toggle.setThumbDrawable(thumb);
        toggle.setShowText(false);
        toggle.setChecked(checked);
        return toggle;
    }

    /** Imagen recortada con esquinas redondeadas sobre fondo gris claro. */
    public ImageView photo(int resource, int radius) {
        ImageView image = new ImageView(context);
        image.setImageResource(resource);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackground(shape(0xffeef1f0, radius, 0));
        image.setClipToOutline(true);
        return image;
    }

    public ImageView image(int resource, int width, int height) {
        ImageView image = new ImageView(context);
        image.setImageResource(resource);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setLayoutParams(new ViewGroup.LayoutParams(dp(width), dp(height)));
        return image;
    }
}
