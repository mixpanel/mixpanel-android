package com.mixpanel.android.mpmetrics;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

/**
 * SQLite database adapter for MixpanelAPI.
 *
 * <p>Not thread-safe. Instances of this class should only be used
 * by a single thread.
 *
 * @author anlu(Anlu Wang)
 *
 */
class MPDbAdapter {
    private static final String LOGTAG = "MixpanelAPI";

    private static final String DATABASE_NAME = "mixpanel";
    public static final String EVENTS_TABLE = "events";
    public static final String PEOPLE_TABLE = "people";
    private static final int DATABASE_VERSION = 4;

    public static final String KEY_DATA = "data";
    public static final String KEY_CREATED_AT = "created_at";

    private static final String CREATE_EVENTS_TABLE =
       "CREATE TABLE " + EVENTS_TABLE + " (_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
        KEY_DATA + " STRING NOT NULL, " +
        KEY_CREATED_AT + " INTEGER NOT NULL);";
    private static final String CREATE_PEOPLE_TABLE =
       "CREATE TABLE " + PEOPLE_TABLE + " (_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
        KEY_DATA + " STRING NOT NULL, " +
        KEY_CREATED_AT + " INTEGER NOT NULL);";
    private static final String EVENTS_TIME_INDEX =
        "CREATE INDEX IF NOT EXISTS time_idx ON " + EVENTS_TABLE +
        " (" + KEY_CREATED_AT + ");";
    private static final String PEOPLE_TIME_INDEX =
        "CREATE INDEX IF NOT EXISTS time_idx ON " + PEOPLE_TABLE +
        " (" + KEY_CREATED_AT + ");";

    private final MPDatabaseHelper mDb;

    private static class MPDatabaseHelper extends SQLiteOpenHelper {
        MPDatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            if (MPConfig.DEBUG) Log.d(LOGTAG, "Creating a new Mixpanel events DB");

            db.execSQL(CREATE_EVENTS_TABLE);
            db.execSQL(CREATE_PEOPLE_TABLE);
            db.execSQL(EVENTS_TIME_INDEX);
            db.execSQL(PEOPLE_TIME_INDEX);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (MPConfig.DEBUG) Log.d(LOGTAG, "Upgrading app, replacing Mixpanel events DB");

            db.execSQL("DROP TABLE IF EXISTS " + EVENTS_TABLE);
            db.execSQL("DROP TABLE IF EXISTS " + PEOPLE_TABLE);
            db.execSQL(CREATE_EVENTS_TABLE);
            db.execSQL(CREATE_PEOPLE_TABLE);
            db.execSQL(EVENTS_TIME_INDEX);
            db.execSQL(PEOPLE_TIME_INDEX);
        }
    }

    public MPDbAdapter(Context context) {
        if (MPConfig.DEBUG) Log.d(LOGTAG, "Mixpanel Database adapter constructed in context " + context);

        mDb = new MPDatabaseHelper(context);
    }

    /**
     * Adds a JSON string representing an event with properties or a person record
     * to the SQLiteDatabase.
     * @param j the JSON to record
     * @param table the table to insert into, either "events" or "people"
     * @return the number of rows in the table, or -1 on failure
     */
    public int addJSON(JSONObject j, String table) {
        if (MPConfig.DEBUG) { Log.d(LOGTAG, "addJSON " + table); }

        Cursor c = null;
        int count = -1;

        try {
            SQLiteDatabase db = mDb.getWritableDatabase();

            ContentValues cv = new ContentValues();
            cv.put(KEY_DATA, j.toString());
            cv.put(KEY_CREATED_AT, System.currentTimeMillis());
            db.insert(table, null, cv);

            c = db.rawQuery("SELECT COUNT(*) FROM " + table, null);
            c.moveToFirst();
            count = c.getInt(0);
        } catch (SQLiteException e) {
            Log.e(LOGTAG, "addJSON " + table, e);
        } finally {
            mDb.close();
            if (c != null) {
                c.close();
            }
        }
        return count;
    }

    /**
     * Removes events with an _id <= last_id from table
     * @param last_id the last id to delete
     * @param table the table to remove events from, either "events" or "people"
     */
    public void cleanupEvents(String last_id, String table) {
        if (MPConfig.DEBUG) { Log.d(LOGTAG, "cleanupEvents _id " + last_id + " from table " + table); }

        try {
            SQLiteDatabase db = mDb.getWritableDatabase();
            db.delete(table, "_id <= " + last_id, null);
        } catch (SQLiteException e) {
            // If there's an exception, oh well, let the events persist
            Log.e(LOGTAG, "cleanupEvents " + table, e);
        } finally {
            mDb.close();
        }
    }

    /**
     * Removes events before time.
     * @param time the unix epoch in milliseconds to remove events before
     * @param table the table to remove events from, either "events" or "people"
     */
    public void cleanupEvents(long time, String table) {
        if (MPConfig.DEBUG) { Log.d(LOGTAG, "cleanupEvents time " + time + " from table " + table); }

        try {
            SQLiteDatabase db = mDb.getWritableDatabase();
            db.delete(table, KEY_CREATED_AT + " <= " + time, null);
        } catch (SQLiteException e) {
            // If there's an exception, oh well, let the events persist
            Log.e(LOGTAG, "cleanupEvents " + table, e);
        } finally {
            mDb.close();
        }
    }

    /**
     * Drops *all* queued events from our table.
     * @param table
     */
    public void deleteAllEvents(String table) {
        try {
            SQLiteDatabase db = mDb.getWritableDatabase();
            db.delete(table, null, null);
        } catch (SQLiteException e) {
            // If there's an exception, oh well, let the events persist
            Log.e(LOGTAG, "deleteAllEvents " + table, e);
        } finally {
            mDb.close();
        }
    }



    /**
     * Returns the data string to send to Mixpanel and the maximum ID of the row that
     * we're sending, so we know what rows to delete when a track request was successful.
     *
     * @param table the table to read the JSON from, either "events" or "people"
     * @return String array containing the maximum ID and the data string
     * representing the events, or null if none could be successfully retrieved.
     */
    public String[] generateDataString(String table) {
        Cursor c = null;
        String data = null;
        String last_id = null;

        try {
            SQLiteDatabase db = mDb.getReadableDatabase();
            c = db.rawQuery("SELECT * FROM " + table  +
                    " ORDER BY " + KEY_CREATED_AT + " ASC LIMIT 50", null);
            JSONArray arr = new JSONArray();

            while (c.moveToNext()) {
                if (c.isLast()) {
                    last_id = c.getString(c.getColumnIndex("_id"));
                }
                try {
                    JSONObject j = new JSONObject(c.getString(c.getColumnIndex(KEY_DATA)));
                    arr.put(j);
                } catch (JSONException e) {
                    // Ignore this object
                }
            }

            if (arr.length() > 0) {
                data = arr.toString();
            }
        } catch (SQLiteException e) {
            Log.e(LOGTAG, "generateDataString " + table, e);
        } finally {
            mDb.close();
            if (c != null) {
                c.close();
            }
        }

        if (last_id != null && data != null) {
            String[] ret = {last_id, data};
            return ret;
        }
        return null;
    }
}
