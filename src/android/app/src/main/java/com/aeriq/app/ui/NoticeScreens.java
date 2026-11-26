package com.aeriq.app.ui;

import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import com.aeriq.app.MainActivity;
import com.aeriq.app.MainActivity.Screen;
import com.aeriq.app.data.Notices;

import static com.aeriq.app.ui.Views.*;

/**
 * Avisos entre personas (HU36), como en la sección «Avisos» del panel web: bandeja, conversación y nuevo aviso.
 * A quién se puede escribir lo decide el servidor: el admin principal con admins y operadores; el admin de campaña
 * con sus operadores y el admin principal; el operador con su admin y el admin principal.
 */
public final class NoticeScreens {
    public static final int MAX_TEXT = 4000, MAX_SUBJECT = 160;
    private static final int OPEN_BG = 0xfffff1dc, OPEN_TEXT = 0xff8a5300;
    private static final int ANSWERED_BG = 0xffe3eefc, ANSWERED_TEXT = 0xff1f4f8a;
    private static final int CLOSED_BG = 0xffeceff0;
    private final MainActivity activity;
    private final Views ui;
    private boolean onlyOpen = true;

    public NoticeScreens(MainActivity activity, Views ui) {
        this.activity = activity;
        this.ui = ui;
    }

    private LinearLayout panel(LinearLayout body, int padding) {
        LinearLayout panel = ui.column();
        panel.setBackground(ui.shape(WHITE, 24, LINE));
        panel.setPadding(ui.dp(padding), ui.dp(padding), ui.dp(padding), ui.dp(padding));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = ui.dp(14);
        body.addView(panel, params);
        return panel;
    }

    private TextView pill(String state, String label) {
        int bg = "abierto".equals(state) ? OPEN_BG : "respondido".equals(state) ? ANSWERED_BG : CLOSED_BG;
        int fg = "abierto".equals(state) ? OPEN_TEXT : "respondido".equals(state) ? ANSWERED_TEXT : MUTED;
        TextView pill = ui.text(label, 12, true, fg);
        pill.setPadding(ui.dp(10), ui.dp(4), ui.dp(10), ui.dp(4));
        pill.setBackground(ui.shape(bg, 14, 0));
        return pill;
    }

    private TextView status(LinearLayout parent, String status) {
        TextView view = ui.text(status, 13, true, status.startsWith("No se pudo") ? RED : GREEN);
        view.setPadding(0, ui.dp(10), 0, 0);
        parent.addView(view, new LinearLayout.LayoutParams(-1, -2));
        return view;
    }

    /** Campo de texto de varias líneas con límite de caracteres. */
    private EditText area(LinearLayout parent, String label, String hint, int lines) {
        EditText input = ui.field(parent, label, "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
            | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setSingleLine(false);
        input.setMinLines(lines);
        input.setMaxLines(10);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setHint(hint);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MAX_TEXT)});
        return input;
    }

    // ================================================================= Bandeja

    public void inbox(LinearLayout body, Notices.Inbox inbox, String status) {
        LinearLayout intro = panel(body, 18);
        intro.addView(ui.text("Avisos de tu equipo", 19, true, INK));
        TextView how = ui.text("Escribe a tu admin o a tu equipo y sigue la conversación hasta cerrarla. "
            + "Los avisos con un punto tienen mensajes nuevos.", 14, false, MUTED);
        how.setPadding(0, ui.dp(8), 0, 0);
        how.setLineSpacing(ui.dp(2), 1);
        intro.addView(how);
        if (!status.isEmpty()) status(intro, status);

        Button write = ui.button(body, "Nuevo aviso", () -> activity.go(Screen.NOTICE_NEW));
        write.setEnabled(!inbox.contacts.isEmpty());
        write.setAlpha(inbox.contacts.isEmpty() ? .5f : 1f);
        write.setContentDescription(inbox.contacts.isEmpty() ? "Nuevo aviso. No hay nadie a quien puedas escribir todavía"
            : "Escribir un aviso nuevo");

        // Filtro: abiertos (por defecto) o todos.
        LinearLayout filter = ui.row();
        filter.setPadding(ui.dp(4), ui.dp(4), ui.dp(4), ui.dp(4));
        filter.setBackground(ui.shape(0xffe9eeec, 14, 0));
        String[] labels = {"Abiertos · " + inbox.open(), "Todos · " + inbox.notices.size()};
        for (int i = 0; i < 2; i++) {
            boolean active = (i == 0) == onlyOpen;
            TextView tab = ui.text(labels[i], 14, true, active ? INK : MUTED);
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(0, ui.dp(10), 0, ui.dp(10));
            if (active) tab.setBackground(ui.shape(WHITE, 12, 0));
            tab.setSelected(active);
            boolean open = i == 0;
            tab.setOnClickListener(view -> { onlyOpen = open; activity.renderCurrent(); });
            filter.addView(tab, new LinearLayout.LayoutParams(0, -2, 1));
        }
        LinearLayout.LayoutParams filterParams = new LinearLayout.LayoutParams(-1, -2);
        filterParams.bottomMargin = ui.dp(14);
        body.addView(filter, filterParams);

        LinearLayout list = panel(body, 6);
        int shown = 0;
        for (Notices.Summary notice : inbox.notices) {
            if (onlyOpen && notice.closed) continue;
            if (shown++ > 0) {
                View line = new View(activity);
                line.setBackgroundColor(LINE);
                LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(-1, ui.dp(1));
                lineParams.setMargins(ui.dp(12), 0, ui.dp(12), 0);
                list.addView(line, lineParams);
            }
            list.addView(row(notice), new LinearLayout.LayoutParams(-1, -2));
        }
        if (shown == 0) {
            TextView none = ui.text(inbox.notices.isEmpty() ? "Todavía no tienes avisos." : "No tienes avisos abiertos.",
                14, false, MUTED);
            none.setPadding(ui.dp(12), ui.dp(14), ui.dp(12), ui.dp(14));
            list.addView(none);
        }
    }

    private View row(Notices.Summary notice) {
        LinearLayout row = ui.row();
        row.setGravity(Gravity.TOP);
        row.setPadding(ui.dp(12), ui.dp(14), ui.dp(12), ui.dp(14));
        View dot = new View(activity);
        dot.setBackground(ui.shape(notice.fresh ? GREEN : 0, 5, 0));
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(ui.dp(10), ui.dp(10));
        dotParams.setMargins(0, ui.dp(6), ui.dp(10), 0);
        row.addView(dot, dotParams);
        LinearLayout texts = ui.column();
        LinearLayout top = ui.row();
        top.addView(ui.text(notice.withName, 15, true, INK), new LinearLayout.LayoutParams(0, -2, 1));
        top.addView(ui.text(Quality.when(Quality.utc(notice.updatedAt)), 12, false, MUTED));
        texts.addView(top, new LinearLayout.LayoutParams(-1, -2));
        TextView subject = ui.text(notice.subject, 15, notice.fresh, INK);
        subject.setPadding(0, ui.dp(2), 0, 0);
        texts.addView(subject);
        TextView preview = ui.text(notice.withRole + (notice.last.isEmpty() ? "" : " · " + notice.last), 13, false, MUTED);
        preview.setMaxLines(2);
        preview.setEllipsize(android.text.TextUtils.TruncateAt.END);
        preview.setPadding(0, ui.dp(2), 0, ui.dp(6));
        texts.addView(preview);
        texts.addView(pill(notice.state, notice.stateName), new LinearLayout.LayoutParams(-2, -2));
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        row.setContentDescription((notice.fresh ? "Nuevo. " : "") + notice.subject + ", con " + notice.withName
            + ", " + notice.stateName);
        row.setOnClickListener(view -> activity.openNotice(notice.id));
        return row;
    }

    // ================================================================= Conversación

    public void conversation(LinearLayout body, Notices.Conversation notice, String status) {
        if (notice == null) {
            LinearLayout wait = panel(body, 18);
            wait.addView(ui.text(status.isEmpty() ? "Abriendo el aviso…" : status, 15, false,
                status.startsWith("No se pudo") ? RED : MUTED));
            return;
        }
        LinearLayout head = panel(body, 18);
        head.addView(ui.text(notice.subject, 19, true, INK));
        TextView who = ui.text("Con " + notice.withName + " · " + notice.withRole + " · desde "
            + Quality.when(Quality.utc(notice.createdAt)), 13, false, MUTED);
        who.setPadding(0, ui.dp(4), 0, ui.dp(8));
        head.addView(who);
        head.addView(pill(notice.state, notice.stateName), new LinearLayout.LayoutParams(-2, -2));
        if (!status.isEmpty()) status(head, status);

        // Mensajes: los tuyos a la derecha en verde claro; los de la otra persona a la izquierda en blanco.
        for (Notices.Message message : notice.messages) {
            LinearLayout bubble = ui.column();
            bubble.setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12));
            bubble.setBackground(ui.shape(message.mine ? SOFT : WHITE, 18, message.mine ? 0 : LINE));
            bubble.addView(ui.text(message.mine ? "Tú" : message.author, 13, true, message.mine ? GREEN : INK));
            TextView text = ui.text(message.text, 15, false, INK);
            text.setPadding(0, ui.dp(4), 0, ui.dp(4));
            text.setLineSpacing(ui.dp(2), 1);
            text.setTextIsSelectable(true);
            bubble.addView(text);
            bubble.addView(ui.text(Quality.when(Quality.utc(message.createdAt)), 11, false, MUTED));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.bottomMargin = ui.dp(10);
            if (message.mine) params.leftMargin = ui.dp(40); else params.rightMargin = ui.dp(40);
            body.addView(bubble, params);
        }

        LinearLayout reply = panel(body, 18);
        EditText text = area(reply, "Responder", notice.closed ? "Escribe para volver a abrir el aviso…"
            : "Escribe tu respuesta…", 3);
        ui.button(reply, "Enviar respuesta", () -> {
            String value = text.getText().toString().trim();
            if (value.isEmpty()) {
                activity.message("Escribe el mensaje antes de enviarlo.", MainActivity.TONE_WARN);
                return;
            }
            activity.replyNotice(notice.id, value);
        });
        if (!notice.closed) ui.outline(reply, "Cerrar aviso", () -> activity.closeNotice(notice.id));
    }

    // ================================================================= Nuevo aviso

    public void compose(LinearLayout body, Notices.Inbox inbox) {
        LinearLayout form = panel(body, 18);
        form.addView(ui.text("¿A quién escribes?", 17, true, INK));
        TextView help = ui.text("Solo aparecen las personas a las que puedes escribir.", 13, false, MUTED);
        help.setPadding(0, ui.dp(4), 0, ui.dp(10));
        form.addView(help);
        long[] chosen = {0};
        RadioGroup people = new RadioGroup(activity);
        for (Notices.Contact contact : inbox.contacts) {
            RadioButton option = new RadioButton(activity);
            option.setId(View.generateViewId());
            option.setText(contact.label());
            option.setTextSize(15);
            option.setTextColor(INK);
            option.setButtonTintList(android.content.res.ColorStateList.valueOf(GREEN));
            option.setPadding(ui.dp(6), ui.dp(10), 0, ui.dp(10));
            option.setOnCheckedChangeListener((button, checked) -> { if (checked) chosen[0] = contact.id; });
            people.addView(option, new RadioGroup.LayoutParams(-1, -2));
        }
        if (inbox.contacts.size() == 1) ((RadioButton) people.getChildAt(0)).setChecked(true);
        LinearLayout.LayoutParams peopleParams = new LinearLayout.LayoutParams(-1, -2);
        peopleParams.bottomMargin = ui.dp(12);
        form.addView(people, peopleParams);
        if (inbox.contacts.isEmpty()) form.addView(ui.text("No hay nadie a quien puedas escribir todavía.", 14, false, MUTED));

        EditText subject = ui.field(form, "Asunto", "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        subject.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MAX_SUBJECT)});
        EditText message = area(form, "Mensaje", "Escribe el aviso…", 5);
        ui.button(form, "Enviar aviso", () -> {
            String title = subject.getText().toString().trim(), text = message.getText().toString().trim();
            if (chosen[0] == 0) { activity.message("Elige a quién escribir.", MainActivity.TONE_WARN); return; }
            if (title.isEmpty()) { activity.message("Escribe el asunto.", MainActivity.TONE_WARN); return; }
            if (text.isEmpty()) { activity.message("Escribe el mensaje antes de enviarlo.", MainActivity.TONE_WARN); return; }
            activity.createNotice(chosen[0], title, text);
        });
    }
}
