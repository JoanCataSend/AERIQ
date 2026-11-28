package com.aeriq.app.data;
/*
 * Fichero: ReadingOutbox.java
 * Autor: Codigo Norte
 * Descripción: Archivo fuente de src/android/app/src/main/java/com/aeriq/app/data.
 * Fecha: 2026-09-25
 * Copyright (c) 2026 Codigo Norte
 */
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

/**
 * Diseño lógico: event: Text, payload: Text --> Entry() --> Text
 * Descripción: Describe la operación realizada y su resultado.
 */
        Entry(String event, String payload) { this.event = event; this.payload = payload; }
    }

    /**
     * Diseño lógico: context: Text --> ReadingOutbox() --> Text
     * Descripción: Procesa reading outbox y devuelve el resultado correspondiente.
     */
    public ReadingOutbox(Context context) { this(context, "aeriq-pending-readings.db"); }

  /**
   * Diseño lógico: context: Text, name: Text --> ReadingOutbox() --> Text
   * Descripción: Procesa reading outbox y devuelve el resultado correspondiente.
   */
    ReadingOutbox(Context context, String name) { super(context, name, null, 1); }
    /**
     * Diseño lógico: (SQLiteDatabase db) --> onCreate()
     * Descripción: Describe la operación realizada y su resultado.
     */
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE outbox (event TEXT PRIMARY KEY, owner INTEGER NOT NULL, payload TEXT NOT NULL, blocked INTEGER NOT NULL DEFAULT 0, error TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE INDEX owner_pending ON outbox(owner,blocked)");
    }
    /**
     * Diseño lógico: (SQLiteDatabase db, int oldVersion, int newVersion) --> onUpgrade()
     * Descripción: Describe la operación realizada y su resultado.
     */
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new IllegalStateException("Migración de cola no implementada; no se borran lecturas");
    }
    /**
     * Diseño lógico: owner: Z, event: Text, payload: Text --> add()
     * Descripción: Procesa add y devuelve el resultado correspondiente..
     */
    public void add(long owner, String event, String payload) {
        ContentValues row = new ContentValues();
        row.put("owner", owner); row.put("event", event); row.put("payload", payload);
        getWritableDatabase().insertOrThrow("outbox", null, row);
    }
    /**
     * Diseño lógico: owner: Z --> next() --> [ Text ]
     * Descripción: Procesa next y devuelve el resultado correspondiente.
     */
    public List<Entry> next(long owner) {
        List<Entry> rows = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT event,payload FROM outbox WHERE owner=? AND blocked=0 ORDER BY rowid LIMIT 20", new String[]{Long.toString(owner)})) {
            while (c.moveToNext()) rows.add(new Entry(c.getString(0), c.getString(1)));
        }
        return rows;
    }
    /**
     * Diseño lógico: owner: Z, event: Text --> acknowledge()
     * Descripción: Procesa acknowledge y devuelve el resultado correspondiente..
     */
    public void acknowledge(long owner, String event) {
        getWritableDatabase().delete("outbox", "owner=? AND event=?", new String[]{Long.toString(owner),event});
    }
    /**
     * Diseño lógico: owner: Z, event: Text, error: Text --> block()
     * Descripción: Procesa block y devuelve el resultado correspondiente..
     */
    public void block(long owner, String event, String error) {
        ContentValues row = new ContentValues(); row.put("blocked", 1); row.put("error", error);
        getWritableDatabase().update("outbox", row, "owner=? AND event=?", new String[]{Long.toString(owner),event});
    }
    /**
     * Diseño lógico: owner: Z --> retry()
     * Descripción: Procesa retry y devuelve el resultado correspondiente..
     */
    public void retry(long owner) {
        ContentValues row = new ContentValues(); row.put("blocked", 0); row.put("error", "");
        getWritableDatabase().update("outbox", row, "owner=?", new String[]{Long.toString(owner)});
    }
    /**
     * Diseño lógico: owner: Z, blocked: B --> count() --> Z
     * Descripción: Procesa count y devuelve el resultado correspondiente.
     */
    public int count(long owner, boolean blocked) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM outbox WHERE owner=? AND blocked=?", new String[]{Long.toString(owner),blocked ? "1" : "0"})) {
            c.moveToFirst(); return c.getInt(0);
        }
    }
}
