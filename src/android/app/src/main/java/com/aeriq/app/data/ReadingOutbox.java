package com.aeriq.app.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

/** Cola privada persistente; cada lectura conserva UUID, autor y asignación originales. */
public final class ReadingOutbox extends SQLiteOpenHelper {
    public static final class Entry {
        public final String event, payload;
        Entry(String event, String payload) { this.event = event; this.payload = payload; }
    }
    public ReadingOutbox(Context context) { this(context, "aeriq-pending-readings.db"); }
    ReadingOutbox(Context context, String name) { super(context, name, null, 1); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE outbox (event TEXT PRIMARY KEY, owner INTEGER NOT NULL, payload TEXT NOT NULL, blocked INTEGER NOT NULL DEFAULT 0, error TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE INDEX owner_pending ON outbox(owner,blocked)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new IllegalStateException("Migración de cola no implementada; no se borran lecturas");
    }
    public void add(long owner, String event, String payload) {
        ContentValues row = new ContentValues();
        row.put("owner", owner); row.put("event", event); row.put("payload", payload);
        getWritableDatabase().insertOrThrow("outbox", null, row);
    }
    public List<Entry> next(long owner) {
        List<Entry> rows = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT event,payload FROM outbox WHERE owner=? AND blocked=0 ORDER BY rowid LIMIT 20", new String[]{Long.toString(owner)})) {
            while (c.moveToNext()) rows.add(new Entry(c.getString(0), c.getString(1)));
        }
        return rows;
    }
    public void acknowledge(long owner, String event) {
        getWritableDatabase().delete("outbox", "owner=? AND event=?", new String[]{Long.toString(owner),event});
    }
    public void block(long owner, String event, String error) {
        ContentValues row = new ContentValues(); row.put("blocked", 1); row.put("error", error);
        getWritableDatabase().update("outbox", row, "owner=? AND event=?", new String[]{Long.toString(owner),event});
    }
    public void retry(long owner) {
        ContentValues row = new ContentValues(); row.put("blocked", 0); row.put("error", "");
        getWritableDatabase().update("outbox", row, "owner=?", new String[]{Long.toString(owner)});
    }
    public int count(long owner, boolean blocked) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM outbox WHERE owner=? AND blocked=?", new String[]{Long.toString(owner),blocked ? "1" : "0"})) {
            c.moveToFirst(); return c.getInt(0);
        }
    }
}
