package com.aeriq.app.data;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Avisos entre personas (HU36), tal como los devuelve mobile-avisos.php (la misma lógica que el panel web,
 * server/Avisos.php). Quién puede escribir a quién lo decide el servidor: aquí solo se muestran los contactos que manda.
 */
public final class Notices {
    private Notices() {}

    /** Un aviso en la bandeja. {@code fresh}: tiene mensajes de la otra persona que aún no has abierto. */
    public static final class Summary {
        public final long id;
        public final String subject, state, stateName, updatedAt, withName, withRole, last;
        public final boolean fresh, closed;

        Summary(JSONObject data) throws JSONException {
            id = data.getLong("id");
            subject = data.optString("asunto");
            state = data.optString("estado");
            stateName = data.optString("estado_nombre", state);
            updatedAt = data.optString("actualizado_en");
            withName = data.optString("con_nombre");
            withRole = data.optString("con_rol");
            last = data.isNull("ultimo") ? "" : data.optString("ultimo");
            fresh = data.optBoolean("nuevo");
            closed = "cerrado".equals(state);
        }
    }

    /** Persona a la que la cuenta puede escribir. */
    public static final class Contact {
        public final long id;
        public final String name, role, campaign;

        Contact(JSONObject data) throws JSONException {
            id = data.getLong("id");
            name = data.optString("nombre");
            role = data.optString("rol");
            campaign = data.isNull("campana") ? "" : data.optString("campana");
        }

        public String label() { return name + " · " + role + (campaign.isEmpty() ? "" : " · " + campaign); }
    }

    public static final class Inbox {
        public final List<Summary> notices;
        public final List<Contact> contacts;
        public final int unread;

        Inbox(JSONObject data) throws JSONException {
            List<Summary> rows = new ArrayList<>();
            JSONArray list = data.getJSONArray("notices");
            for (int i = 0; i < list.length(); i++) rows.add(new Summary(list.getJSONObject(i)));
            notices = Collections.unmodifiableList(rows);
            List<Contact> people = new ArrayList<>();
            list = data.getJSONArray("contacts");
            for (int i = 0; i < list.length(); i++) people.add(new Contact(list.getJSONObject(i)));
            contacts = Collections.unmodifiableList(people);
            unread = data.optInt("unread");
        }

        private Inbox() {
            notices = Collections.emptyList();
            contacts = Collections.emptyList();
            unread = 0;
        }

        public static Inbox empty() { return new Inbox(); }

        public int open() {
            int count = 0;
            for (Summary notice : notices) if (!notice.closed) count++;
            return count;
        }
    }

    public static final class Message {
        public final String author, text, createdAt;
        public final boolean mine;

        Message(JSONObject data) throws JSONException {
            author = data.optString("autor");
            text = data.optString("texto");
            createdAt = data.optString("creado_en");
            mine = data.optBoolean("mio");
        }
    }

    /** Un aviso abierto, con todos sus mensajes. Abrirlo lo marca como visto en el servidor. */
    public static final class Conversation {
        public final long id;
        public final String subject, state, stateName, createdAt, withName, withRole;
        public final boolean closed;
        public final List<Message> messages;

        Conversation(JSONObject data) throws JSONException {
            JSONObject notice = data.getJSONObject("notice");
            id = notice.getLong("id");
            subject = notice.optString("asunto");
            state = notice.optString("estado");
            stateName = notice.optString("estado_nombre", state);
            closed = notice.optBoolean("cerrado");
            createdAt = notice.optString("creado_en");
            withName = notice.optString("con_nombre");
            withRole = notice.optString("con_rol");
            List<Message> rows = new ArrayList<>();
            JSONArray list = data.getJSONArray("messages");
            for (int i = 0; i < list.length(); i++) rows.add(new Message(list.getJSONObject(i)));
            messages = Collections.unmodifiableList(rows);
        }
    }

    static Inbox inbox(JSONObject data) throws JSONException { return new Inbox(data); }
    static Conversation conversation(JSONObject data) throws JSONException { return new Conversation(data); }
}
