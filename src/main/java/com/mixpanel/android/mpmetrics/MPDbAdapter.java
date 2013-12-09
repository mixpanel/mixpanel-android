package com.mixpanel.android.mpmetrics;

import java.io.File;

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
 */
class MPDbAdapter {
    private static final String LOGTAG = "MixpanelAPI";

    public enum Table {
        EVENTS ("events"),
        PEOPLE ("people");

        Table(String name) {
            mTableName = name;
        }

        public String getName() {
            return mTableName;
        }

        private final String mTableName;
    }

    private static final String DATABASE_NAME = "mixpanel";
    private static final int DATABASE_VERSION = 4;

    public static final String KEY_DATA = "data";
    public static final String KEY_CREATED_AT = "created_at";

    private static final String CREATE_EVENTS_TABLE =
       "CREATE TABLE " + Table.EVENTS.getName() + " (_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
        KEY_DATA + " STRING NOT NULL, " +
        KEY_CREATED_AT + " INTEGER NOT NULL);";
    private static final String CREATE_PEOPLE_TABLE =
       "CREATE TABLE " + Table.PEOPLE.getName() + " (_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
        KEY_DATA + " STRING NOT NULL, " +
        KEY_CREATED_AT + " INTEGER NOT NULL);";
    private static final String EVENTS_TIME_INDEX =
        "CREATE INDEX IF NOT EXISTS time_idx ON " + Table.EVENTS.getName() +
        " (" + KEY_CREATED_AT + ");";
    private static final String PEOPLE_TIME_INDEX =
        "CREATE INDEX IF NOT EXISTS time_idx ON " + Table.PEOPLE.getName() +
        " (" + KEY_CREATED_AT + ");";

    private final MPDatabaseHelper mDb;

    private static class MPDatabaseHelper extends SQLiteOpenHelper {
        MPDatabaseHelper(Context context, String dbName) {
            super(context, dbName, null, DATABASE_VERSION);
            mDatabaseFile = context.getDatabasePath(dbName);
        }

        /**
         * Completely deletes the DB file from the file system.
         */
        public void deleteDatabase() {
            close();
            mDatabaseFile.delete();
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

            db.execSQL("DROP TABLE IF EXISTS " + Table.EVENTS.getName());
            db.execSQL("DROP TABLE IF EXISTS " + Table.PEOPLE.getName());
            db.execSQL(CREATE_EVENTS_TABLE);
            db.execSQL(CREATE_PEOPLE_TABLE);
            db.execSQL(EVENTS_TIME_INDEX);
            db.execSQL(PEOPLE_TIME_INDEX);
        }

        private final File mDatabaseFile;
    }

    public MPDbAdapter(Context context) {
        this(context, DATABASE_NAME);
    }

    public MPDbAdapter(Context context, String dbName) {
        if (MPConfig.DEBUG) Log.d(LOGTAG, "Mixpanel Database (" + dbName + ") adapter constructed in context " + context);

        mDb = new MPDatabaseHelper(context, dbName);
    }

    /**
     * Adds a JSON string representing an event with properties or a person record
     * to the SQLiteDatabase.
     * @param j the JSON to record
     * @param table the table to insert into, either "events" or "people"
     * @return the number of rows in the table, or -1 on failure
     */
    public int addJSON(JSONObject j, Table table) {
        final String tableName = table.getName();
        if (MPConfig.DEBUG) Log.d(LOGTAG, "addJSON " + tableName);

        Cursor c = null;
        int count = -1;

        try {
            final SQLiteDatabase db = mDb.getWritableDatabase();

            final ContentValues cv = new ContentValues();
            cv.put(KEY_DATA, j.toString());
            cv.put(KEY_CREATED_AT, System.currentTimeMillis());
            db.insert(tableName, null, cv);

            c = db.rawQuery("SELECT COUNT(*) FROM " + tableName, null);
            c.moveToFirst();
            count = c.getInt(0);
        } catch (final SQLiteException e) {
            Log.e(LOGTAG, "addJSON " + tableName + " FAILED. Deleting DB.", e);

            // We assume that in general, the results of a SQL exception are
            // unrecoverable, and could be associated with an oversized or
            // otherwise unusable DB. Better to bomb it and get back on track
            // than to leave it junked up (and maybe filling up the disk.)
            if (c != null) {
                c.close();
                c = null;
            }
            mDb.deleteDatabase();
        } finally {
            if (c != null) {
                c.close();
            }
            mDb.close();
        }
        return count;
    }

    /**
     * Removes events with an _id <= last_id from table
     * @param last_id the last id to delete
     * @param table the table to remove events from, either "events" or "people"
     */
    public void cleanupEvents(String last_id, Table table) {
        final String tableName = table.getName();
        if (MPConfig.DEBUG) Log.d(LOGTAG, "cleanupEvents _id " + last_id + " from table " + tableName);

        try {
            final SQLiteDatabase db = mDb.getWritableDatabase();
            db.delete(tableName, "_id <= " + last_id, null);
        } catch (final SQLiteException e) {
            Log.e(LOGTAG, "cleanupEvents " + tableName + " by id FAILED. Deleting DB.", e);

            // We assume that in general, the results of a SQL exception are
            // unrecoverable, and could be associated with an oversized or
            // otherwise unusable DB. Better to bomb it and get back on track
            // than to leave it junked up (and maybe filling up the disk.)
            mDb.deleteDatabase();
        } finally {
            mDb.close();
        }
    }

    /**
     * Removes events before time.
     * @param time the unix epoch in milliseconds to remove events before
     * @param table the table to remove events from, either "events" or "people"
     */
    public void cleanupEvents(long time, Table table) {
        final String tableName = table.getName();
        if (MPConfig.DEBUG) Log.d(LOGTAG, "cleanupEvents time " + time + " from table " + tableName);

        try {
            final SQLiteDatabase db = mDb.getWritableDatabase();
            db.delete(tableName, KEY_CREATED_AT + " <= " + time, null);
        } catch (final SQLiteException e) {
            Log.e(LOGTAG, "cleanupEvents " + tableName + " by time FAILED. Deleting DB.", e);

            // We assume that in general, the results of a SQL exception are
            // unrecoverable, and could be associated with an oversized or
            // otherwise unusable DB. Better to bomb it and get back on track
            // than to leave it junked up (and maybe filling up the disk.)
            mDb.deleteDatabase();
        } finally {
            mDb.close();
        }
    }

    public void deleteDB() {
        mDb.deleteDatabase();
    }


    /**
     * Returns the data string to send to Mixpanel and the maximum ID of the row that
     * we're sending, so we know what rows to delete when a track request was successful.
     *
     * @param table the table to read the JSON from, either "events" or "people"
     * @return String array containing the maximum ID and the data string
     * representing the events, or null if none could be successfully retrieved.
     */
    public String[] generateDataString(Table table) {
        Cursor c = null;
        String data = null;
        String last_id = null;
        final String tableName = table.getName();

        try {
            final SQLiteDatabase db = mDb.getReadableDatabase();
            c = db.rawQuery("SELECT * FROM " + tableName  +
                    " ORDER BY " + KEY_CREATED_AT + " ASC LIMIT 50", null);
            final JSONArray arr = new JSONArray();

            while (c.moveToNext()) {
                if (c.isLast()) {
                    last_id = c.getString(c.getColumnIndex("_id"));
                }
                try {
                    final JSONObject j = new JSONObject(c.getString(c.getColumnIndex(KEY_DATA)));
                    arr.put(j);
                } catch (final JSONException e) {
                    // Ignore this object
                }
            }

            if (arr.length() > 0) {
                data = arr.toString();
            }
        } catch (final SQLiteException e) {
            Log.e(LOGTAG, "generateDataString " + tableName, e);

            // We'll dump the DB on write failures, but with reads we can
            // let things ride in hopes the issue clears up.
            // (A bit more likely, since we're opening the DB for read and not write.)
            // A corrupted or disk-full DB will be cleaned up on the next write or clear call.
            last_id = null;
            data = null;
        } finally {
            mDb.close();
            if (c != null) {
                c.close();
            }
        }

        if (last_id != null && data != null) {
            final String[] ret = {last_id, data};
            return ret;
        }
        return null;
    }
}
