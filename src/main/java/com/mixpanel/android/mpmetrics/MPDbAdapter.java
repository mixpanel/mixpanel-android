package com.mixpanel.android.mpmetrics;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;

import com.mixpanel.android.util.MPLog;

/**
 * SQLite database adapter for MixpanelAPI.
 *
 * <p>Not thread-safe. Instances of this class should only be used
 * by a single thread.
 *
 */
/* package */ class MPDbAdapter {
    private static final String LOGTAG = "MixpanelAPI.Database";
    private static final Map<Context, MPDbAdapter> sInstances = new HashMap<>();

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

    public static final String KEY_DATA = "data";
    public static final String KEY_CREATED_AT = "created_at";
    public static final String KEY_AUTOMATIC_DATA = "automatic_data";
    public static final String KEY_TOKEN = "token";

    public static final int DB_UPDATE_ERROR = -1;
    public static final int DB_OUT_OF_MEMORY_ERROR = -2;
    public static final int DB_UNDEFINED_CODE = -3;

    private static final String DATABASE_NAME = "mixpanel";
    private static final int DATABASE_VERSION = 5;

    private static final String CREATE_EVENTS_TABLE =
       "CREATE TABLE " + Table.EVENTS.getName() + " (_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
        KEY_DATA + " STRING NOT NULL, " +
        KEY_CREATED_AT + " INTEGER NOT NULL, " +
        KEY_AUTOMATIC_DATA + " INTEGER DEFAULT 0, " +
        KEY_TOKEN + " STRING NOT NULL DEFAULT '')";
    private static final String CREATE_PEOPLE_TABLE =
       "CREATE TABLE " + Table.PEOPLE.getName() + " (_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
        KEY_DATA + " STRING NOT NULL, " +
        KEY_CREATED_AT + " INTEGER NOT NULL, " +
        KEY_AUTOMATIC_DATA + " INTEGER DEFAULT 0, " +
        KEY_TOKEN + " STRING NOT NULL DEFAULT '')";
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
            mConfig = MPConfig.getInstance(context);
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
            MPLog.v(LOGTAG, "Creating a new Mixpanel events DB");

            db.execSQL(CREATE_EVENTS_TABLE);
            db.execSQL(CREATE_PEOPLE_TABLE);
            db.execSQL(EVENTS_TIME_INDEX);
            db.execSQL(PEOPLE_TIME_INDEX);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            MPLog.v(LOGTAG, "Upgrading app, replacing Mixpanel events DB");

            if (newVersion == 5) {
                migrateTableFrom4To5(db);
            } else {
                db.execSQL("DROP TABLE IF EXISTS " + Table.EVENTS.getName());
                db.execSQL("DROP TABLE IF EXISTS " + Table.PEOPLE.getName());
                db.execSQL(CREATE_EVENTS_TABLE);
                db.execSQL(CREATE_PEOPLE_TABLE);
                db.execSQL(EVENTS_TIME_INDEX);
                db.execSQL(PEOPLE_TIME_INDEX);
            }
        }

        public boolean belowMemThreshold() {
            if (mDatabaseFile.exists()) {
                return Math.max(mDatabaseFile.getUsableSpace(), mConfig.getMinimumDatabaseLimit()) >= mDatabaseFile.length();
            }
            return true;
        }

        private void migrateTableFrom4To5(SQLiteDatabase db) {
            db.execSQL("ALTER TABLE " + Table.EVENTS.getName() + " ADD COLUMN " + KEY_AUTOMATIC_DATA + " INTEGER DEFAULT 0");
            db.execSQL("ALTER TABLE " + Table.PEOPLE.getName() + " ADD COLUMN " + KEY_AUTOMATIC_DATA + " INTEGER DEFAULT 0");
            db.execSQL("ALTER TABLE " + Table.EVENTS.getName() + " ADD COLUMN " + KEY_TOKEN + " STRING NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE " + Table.PEOPLE.getName() + " ADD COLUMN " + KEY_TOKEN + " STRING NOT NULL DEFAULT ''");

            Cursor eventsCursor = db.rawQuery("SELECT * FROM " + Table.EVENTS.getName(), null);
            while (eventsCursor.moveToNext()) {
                int rowId = 0;
                try {
                    final JSONObject j = new JSONObject(eventsCursor.getString(eventsCursor.getColumnIndex(KEY_DATA)));
                    String token = j.getJSONObject("properties").getString("token");
                    rowId = eventsCursor.getInt(eventsCursor.getColumnIndex("_id"));
                    db.execSQL("UPDATE " + Table.EVENTS.getName() + " SET " + KEY_TOKEN + " = '" + token + "' WHERE _id = " + rowId);
                } catch (final JSONException e) {
                    db.delete(Table.EVENTS.getName(), "_id = " + rowId, null);
                }
            }

            Cursor peopleCursor = db.rawQuery("SELECT * FROM " + Table.PEOPLE.getName(), null);
            while (peopleCursor.moveToNext()) {
                int rowId = 0;
                try {
                    final JSONObject j = new JSONObject(peopleCursor.getString(peopleCursor.getColumnIndex(KEY_DATA)));
                    String token = j.getString("$token");
                    rowId = peopleCursor.getInt(peopleCursor.getColumnIndex("_id"));
                    db.execSQL("UPDATE " + Table.PEOPLE.getName() + " SET " + KEY_TOKEN + " = '" + token + "' WHERE _id = " + rowId);
                } catch (final JSONException e) {
                    db.delete(Table.PEOPLE.getName(), "_id = " + rowId, null);
                }
            }
        }

        private final File mDatabaseFile;
        private final MPConfig mConfig;
    }

    public MPDbAdapter(Context context) {
        this(context, DATABASE_NAME);
    }

    public MPDbAdapter(Context context, String dbName) {
        mDb = new MPDatabaseHelper(context, dbName);
    }

    public static MPDbAdapter getInstance(Context context) {
        synchronized (sInstances) {
            final Context appContext = context.getApplicationContext();
            MPDbAdapter ret;
            if (! sInstances.containsKey(appContext)) {
                ret = new MPDbAdapter(appContext);
                sInstances.put(appContext, ret);
            } else {
                ret = sInstances.get(appContext);
            }
            return ret;
        }
    }

    /**
     * Adds a JSON string representing an event with properties or a person record
     * to the SQLiteDatabase.
     * @param j the JSON to record
     * @param token token of the project
     * @param table the table to insert into, either "events" or "people"
     * @param isAutomaticRecord mark the record as an automatic event or not
     * @return the number of rows in the table, or DB_OUT_OF_MEMORY_ERROR/DB_UPDATE_ERROR
     * on failure
     */
    public int addJSON(JSONObject j, String token, Table table, boolean isAutomaticRecord) {
        // we are aware of the race condition here, but what can we do..?
        if (!this.belowMemThreshold()) {
            MPLog.e(LOGTAG, "There is not enough space left on the device to store Mixpanel data, so data was discarded");
            return DB_OUT_OF_MEMORY_ERROR;
        }

        final String tableName = table.getName();

        Cursor c = null;
        int count = DB_UPDATE_ERROR;

        try {
            final SQLiteDatabase db = mDb.getWritableDatabase();

            final ContentValues cv = new ContentValues();
            cv.put(KEY_DATA, j.toString());
            cv.put(KEY_CREATED_AT, System.currentTimeMillis());
            cv.put(KEY_AUTOMATIC_DATA, isAutomaticRecord);
            cv.put(KEY_TOKEN, token);
            db.insert(tableName, null, cv);

            c = db.rawQuery("SELECT COUNT(*) FROM " + tableName + " WHERE token='" + token + "'", null);
            c.moveToFirst();
            count = c.getInt(0);
        } catch (final SQLiteException e) {
            MPLog.e(LOGTAG, "Could not add Mixpanel data to table " + tableName + ". Re-initializing database.", e);

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
     * @param includeAutomaticEvents whether or not automatic events should be included in the cleanup
     */
    public void cleanupEvents(String last_id, Table table, String token, boolean includeAutomaticEvents) {
        final String tableName = table.getName();

        try {
            final SQLiteDatabase db = mDb.getWritableDatabase();
            StringBuffer deleteQuery = new StringBuffer("_id <= " + last_id + " AND " + KEY_TOKEN + " = '" + token + "'");

            if (!includeAutomaticEvents) {
                deleteQuery.append(" AND " + KEY_AUTOMATIC_DATA + "=0");
            }
            db.delete(tableName, deleteQuery.toString(), null);
        } catch (final SQLiteException e) {
            MPLog.e(LOGTAG, "Could not clean sent Mixpanel records from " + tableName + ". Re-initializing database.", e);

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

        try {
            final SQLiteDatabase db = mDb.getWritableDatabase();
            db.delete(tableName, KEY_CREATED_AT + " <= " + time, null);
        } catch (final SQLiteException e) {
            MPLog.e(LOGTAG, "Could not clean timed-out Mixpanel records from " + tableName + ". Re-initializing database.", e);

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
     * Removes all events given a project token.
     * @param table the table to remove events from, either "events" or "people"
     * @param token token of the project to remove events from
     */
    public void cleanupAllEvents(Table table, String token) {
        final String tableName = table.getName();

        try {
            final SQLiteDatabase db = mDb.getWritableDatabase();
            db.delete(tableName, KEY_TOKEN + " = '" + token + "'", null);
        } catch (final SQLiteException e) {
            MPLog.e(LOGTAG, "Could not clean timed-out Mixpanel records from " + tableName + ". Re-initializing database.", e);

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
     * Removes automatic events.
     * @param token token of the project you want to remove automatic events from
     */
    public synchronized void cleanupAutomaticEvents(String token) {
        cleanupAutomaticEvents(Table.EVENTS, token);
        cleanupAutomaticEvents(Table.PEOPLE, token);
    }

    private void cleanupAutomaticEvents(Table table, String token) {
        final String tableName = table.getName();

        try {
            final SQLiteDatabase db = mDb.getWritableDatabase();
            db.delete(tableName, KEY_AUTOMATIC_DATA + " = 1 AND " + KEY_TOKEN + " = '" + token + "'", null);
        } catch (final SQLiteException e) {
            MPLog.e(LOGTAG, "Could not clean automatic Mixpanel records from " + tableName + ". Re-initializing database.", e);

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
     * @param token the token of the project you want to retrieve the records for
     * @param includeAutomaticEvents whether or not it should include pre-track records
     * @return String array containing the maximum ID, the data string
     * representing the events (or null if none could be successfully retrieved) and the total
     * current number of events in the queue.
     */
    public String[] generateDataString(Table table, String token, boolean includeAutomaticEvents) {
        Cursor c = null;
        Cursor queueCountCursor = null;
        String data = null;
        String last_id = null;
        String queueCount = null;
        final String tableName = table.getName();
        final SQLiteDatabase db = mDb.getReadableDatabase();

        try {
            StringBuffer rawDataQuery = new StringBuffer("SELECT * FROM " + tableName + " WHERE " + KEY_TOKEN + " = '" + token + "' ");
            StringBuffer queueCountQuery = new StringBuffer("SELECT COUNT(*) FROM " + tableName + " WHERE " + KEY_TOKEN + " = '" + token + "' ");
            if (!includeAutomaticEvents) {
                rawDataQuery.append("AND " + KEY_AUTOMATIC_DATA + " = 0 ");
                queueCountQuery.append(" AND " + KEY_AUTOMATIC_DATA + " = 0");
            }

            rawDataQuery.append("ORDER BY " + KEY_CREATED_AT + " ASC LIMIT 50");
            c = db.rawQuery(rawDataQuery.toString(), null);

            queueCountCursor = db.rawQuery(queueCountQuery.toString(), null);
            queueCountCursor.moveToFirst();
            queueCount = String.valueOf(queueCountCursor.getInt(0));

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
            MPLog.e(LOGTAG, "Could not pull records for Mixpanel out of database " + tableName + ". Waiting to send.", e);

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
            if (queueCountCursor != null) {
                queueCountCursor.close();
            }
        }

        if (last_id != null && data != null) {
            final String[] ret = {last_id, data, queueCount};
            return ret;
        }
        return null;
    }

    public File getDatabaseFile() {
        return mDb.mDatabaseFile;
    }

    /* For testing use only, do not call from in production code */
    protected boolean belowMemThreshold() {
        return mDb.belowMemThreshold();
    }
}
