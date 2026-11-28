package com.aeriq.app.data;
/*
 * Fichero: Notices.java
 * Autor: Codigo Norte
 * Descripción: Archivo fuente de src/android/app/src/main/java/com/aeriq/app/data.
 * Fecha: 2026-09-25
 * Copyright (c) 2026 Codigo Norte
 */
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

    /**
     * Diseño lógico: Notices() --> Text
     * Descripción: Describe la operación realizada y su resultado.
     */
    private Notices() {}

    /**
     * Diseño lógico: data: Text --> Summary() --> Text
     * Descripción: Procesa summary y devuelve el resultado correspondiente.
     */
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

    /**
     * Diseño lógico: data: Text --> Contact() --> Text
     * Descripción: Procesa contact y devuelve el resultado correspondiente.
     */
        Contact(JSONObject data) throws JSONException {
            id = data.getLong("id");
            name = data.optString("nombre");
            role = data.optString("rol");
            campaign = data.isNull("campana") ? "" : data.optString("campana");
        }

        /**
         * Diseño lógico: label() --> Text
         * Descripción: Procesa label y devuelve el resultado correspondiente.
         */
        public String label() { return name + " · " + role + (campaign.isEmpty() ? "" : " · " + campaign); }
    }

    public static final class Inbox {
        public final List<Summary> notices;
        public final List<Contact> contacts;
        public final int unread;

      /**
       * Diseño lógico: data: Text --> Inbox() --> Text
       * Descripción: Procesa inbox y devuelve el resultado correspondiente.
       */
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

        /**
         * Diseño lógico: Inbox() --> Text
         * Descripción: Procesa inbox y devuelve el resultado correspondiente.
         */
        private Inbox() {
            notices = Collections.emptyList();
            contacts = Collections.emptyList();
            unread = 0;
        }

        /**
         * Diseño lógico: empty() --> Text
         * Descripción: Procesa empty y devuelve el resultado correspondiente.
         */
        public static Inbox empty() { return new Inbox(); }

        /**
         * Diseño lógico: open() --> Z
         * Descripción: Procesa open y devuelve el resultado correspondiente.
         */
        public int open() {
            int count = 0;
            for (Summary notice : notices) if (!notice.closed) count++;
            return count;
        }
    }

    public static final class Message {
        public final String author, text, createdAt;
        public final boolean mine;

      /**
       * Diseño lógico: data: Text --> Message() --> Text
       * Descripción: Procesa message y devuelve el resultado correspondiente.
       */
        Message(JSONObject data) throws JSONException {
            author = data.optString("autor");
            text = data.optString("texto");
            createdAt = data.optString("creado_en");
            mine = data.optBoolean("mio");
        }
    }

    /**
     * Diseño lógico: data: Text --> Conversation() --> Text
     * Descripción: Procesa conversation y devuelve el resultado correspondiente.
     */
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

    /**
     * Diseño lógico: data: Text --> inbox() --> Text
     * Descripción: Procesa inbox y devuelve el resultado correspondiente.
     */
    static Inbox inbox(JSONObject data) throws JSONException { return new Inbox(data); }

    /**
     * Diseño lógico: data: Text --> conversation() --> Text
     * Descripción: Procesa conversation y devuelve el resultado correspondiente.
     */
    static Conversation conversation(JSONObject data) throws JSONException { return new Conversation(data); }
}
