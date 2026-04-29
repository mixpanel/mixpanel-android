package com.mixpanel.android.mpmetrics;

import java.io.File;
import java.io.FilenameFilter;
import java.util.HashMap;
import java.util.Map;

import androidx.annotation.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
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
    private static final Map<String, MPDbAdapter> sInstances = new HashMap<>();

    public enum Table {
        EVENTS ("events"),
        PEOPLE ("people"),
        ANONYMOUS_PEOPLE ("anonymous_people"),
        GROUPS ("groups");

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

    public static final int ID_COLUMN_INDEX = 0;
    public static final int DATA_COLUMN_INDEX = 1;
    public static final int CREATED_AT_COLUMN_INDEX = 2;
    public static final int AUTOMATIC_DATA_COLUMN_INDEX = 3;
    public static final int TOKEN_COLUMN_INDEX = 4;

    public static final int DB_UPDATE_ERROR = -1;
    public static final int DB_OUT_OF_MEMORY_ERROR = -2;
    public static final int DB_UNDEFINED_CODE = -3;

    private static final String DATABASE_NAME = "mixpanel";
    private static final int MIN_DB_VERSION = 4;

    // If you increment DATABASE_VERSION, don't forget to define migration
    private static final int DATABASE_VERSION = 7; // current database version
    private static final int MAX_DB_VERSION = 7; // Max database version onUpdate can migrate to.


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
    private static final String CREATE_GROUPS_TABLE =
            "CREATE TABLE " + Table.GROUPS.getName() + " (_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    KEY_DATA + " STRING NOT NULL, " +
                    KEY_CREATED_AT + " INTEGER NOT NULL, " +
                    KEY_AUTOMATIC_DATA + " INTEGER DEFAULT 0, " +
                    KEY_TOKEN + " STRING NOT NULL DEFAULT '')";
    private static final String CREATE_ANONYMOUS_PEOPLE_TABLE =
            "CREATE TABLE " + Table.ANONYMOUS_PEOPLE.getName() + " (_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
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
    private static final String GROUPS_TIME_INDEX =
            "CREATE INDEX IF NOT EXISTS time_idx ON " + Table.GROUPS.getName() +
                    " (" + KEY_CREATED_AT + ");";
    private static final String ANONYMOUS_PEOPLE_TIME_INDEX =
            "CREATE INDEX IF NOT EXISTS time_idx ON " + Table.ANONYMOUS_PEOPLE.getName() +
                    " (" + KEY_CREATED_AT + ");";

    private final MPDatabaseHelper mDb;

    private static class MPDatabaseHelper extends SQLiteOpenHelper {
        MPDatabaseHelper(Context context, String dbName, MPConfig config) {
            super(context, dbName, null, DATABASE_VERSION);
            mDatabaseFile = context.getDatabasePath(dbName);
            mIsNewDatabase = !mDatabaseFile.exists();
            mConfig = config;
            mContext = context;
        }

        /**
         * Returns true if this is a newly created database (the database file did not exist
         * before this helper was initialized).
         */
        public boolean isNewDatabase() {
            return mIsNewDatabase;
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
            db.execSQL(CREATE_GROUPS_TABLE);
            db.execSQL(CREATE_ANONYMOUS_PEOPLE_TABLE);
            db.execSQL(EVENTS_TIME_INDEX);
            db.execSQL(PEOPLE_TIME_INDEX);
            db.execSQL(GROUPS_TIME_INDEX);
            db.execSQL(ANONYMOUS_PEOPLE_TIME_INDEX);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            MPLog.v(LOGTAG, "Upgrading app, replacing Mixpanel events DB");

            if (oldVersion >= MIN_DB_VERSION && newVersion <= MAX_DB_VERSION) {
                if (oldVersion == 4) {
                    migrateTableFrom4To5(db);
                    migrateTableFrom5To6(db);
                    migrateTableFrom6To7(db);
                }

                if (oldVersion == 5) {
                    migrateTableFrom5To6(db);
                    migrateTableFrom6To7(db);
                }

                if (oldVersion == 6) {
                    migrateTableFrom6To7(db);
                }
            } else {
                db.execSQL("DROP TABLE IF EXISTS " + Table.EVENTS.getName());
                db.execSQL("DROP TABLE IF EXISTS " + Table.PEOPLE.getName());
                db.execSQL("DROP TABLE IF EXISTS " + Table.GROUPS.getName());
                db.execSQL("DROP TABLE IF EXISTS " + Table.ANONYMOUS_PEOPLE.getName());
                db.execSQL(CREATE_EVENTS_TABLE);
                db.execSQL(CREATE_PEOPLE_TABLE);
                db.execSQL(CREATE_GROUPS_TABLE);
                db.execSQL(CREATE_ANONYMOUS_PEOPLE_TABLE);
                db.execSQL(EVENTS_TIME_INDEX);
                db.execSQL(PEOPLE_TIME_INDEX);
                db.execSQL(GROUPS_TIME_INDEX);
                db.execSQL(ANONYMOUS_PEOPLE_TIME_INDEX);
            }
        }

        public boolean aboveMemThreshold() {
            if (mDatabaseFile.exists()) {
                return mDatabaseFile.length() > Math.max(mDatabaseFile.getUsableSpace(), mConfig.getMinimumDatabaseLimit()) ||
                        mDatabaseFile.length() > mConfig.getMaximumDatabaseLimit();
            }
            return false;
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
                    final int dataColumnIndex = eventsCursor.getColumnIndex(KEY_DATA) >= 0 ? eventsCursor.getColumnIndex(KEY_DATA) : DATA_COLUMN_INDEX;
                    final JSONObject j = new JSONObject(eventsCursor.getString(dataColumnIndex));
                    String token = j.getJSONObject("properties").getString("token");
                    final int idColumnIndex = eventsCursor.getColumnIndex("_id") >= 0 ? eventsCursor.getColumnIndex("_id") : ID_COLUMN_INDEX;
                    rowId = eventsCursor.getInt(idColumnIndex);
                    db.execSQL("UPDATE " + Table.EVENTS.getName() + " SET " + KEY_TOKEN + " = '" + token + "' WHERE _id = " + rowId);
                } catch (final JSONException e) {
                    db.delete(Table.EVENTS.getName(), "_id = " + rowId, null);
                }
            }

            Cursor peopleCursor = db.rawQuery("SELECT * FROM " + Table.PEOPLE.getName(), null);
            while (peopleCursor.moveToNext()) {
                int rowId = 0;
                try {
                    final int dataColumnIndex = peopleCursor.getColumnIndex(KEY_DATA) >= 0 ? peopleCursor.getColumnIndex(KEY_DATA) : DATA_COLUMN_INDEX;
                    final JSONObject j = new JSONObject(peopleCursor.getString(dataColumnIndex));
                    String token = j.getString("$token");
                    final int idColumnIndex = peopleCursor.getColumnIndex("_id") >= 0 ? peopleCursor.getColumnIndex("_id") : ID_COLUMN_INDEX;
                    rowId = peopleCursor.getInt(idColumnIndex);
                    db.execSQL("UPDATE " + Table.PEOPLE.getName() + " SET " + KEY_TOKEN + " = '" + token + "' WHERE _id = " + rowId);
                } catch (final JSONException e) {
                    db.delete(Table.PEOPLE.getName(), "_id = " + rowId, null);
                }
            }
        }

        private void migrateTableFrom5To6(SQLiteDatabase db) {
            db.execSQL(CREATE_GROUPS_TABLE);
            db.execSQL(GROUPS_TIME_INDEX);
        }

        private void migrateTableFrom6To7(SQLiteDatabase db) {
            db.execSQL(CREATE_ANONYMOUS_PEOPLE_TABLE);
            db.execSQL(ANONYMOUS_PEOPLE_TIME_INDEX);

            File prefsDir = new File(mContext.getApplicationInfo().dataDir, "shared_prefs");

            if (prefsDir.exists() && prefsDir.isDirectory()) {
                String[] storedPrefsFiles = prefsDir.list(new FilenameFilter() {
                    @Override
                    public boolean accept(File dir, String name) {
                        return name.startsWith("com.mixpanel.android.mpmetrics.MixpanelAPI_");
                    }
                });

                for (String storedPrefFile : storedPrefsFiles) {
                    String storedPrefName = storedPrefFile.split("\\.xml")[0];
                    SharedPreferences s = mContext.getSharedPreferences(storedPrefName, Context.MODE_PRIVATE);
                    final String waitingPeopleUpdates = s.getString("waiting_array", null);
                    if (waitingPeopleUpdates != null) {
                        try {
                            JSONArray waitingObjects = new JSONArray(waitingPeopleUpdates);
                            db.beginTransaction();
                            try {
                                for (int i = 0; i < waitingObjects.length(); i++) {
                                    try {
                                        final JSONObject j = waitingObjects.getJSONObject(i);
                                        String token = j.getString("$token");

                                        final ContentValues cv = new ContentValues();
                                        cv.put(KEY_DATA, j.toString());
                                        cv.put(KEY_CREATED_AT, System.currentTimeMillis());
                                        cv.put(KEY_AUTOMATIC_DATA, false);
                                        cv.put(KEY_TOKEN, token);
                                        db.insert(Table.ANONYMOUS_PEOPLE.getName(), null, cv);
                                    } catch (JSONException e) {
                                        // ignore record
                                    }
                                }
                                db.setTransactionSuccessful();
                            } finally {
                                db.endTransaction();
                            }
                        } catch (JSONException e) {
                            // waiting array is corrupted. dismiss.
                        }

                        SharedPreferences.Editor e = s.edit();
                        e.remove("waiting_array");
                        e.apply();
                    }
                }
            }
        }

        private final File mDatabaseFile;
        private final boolean mIsNewDatabase;
        private final MPConfig mConfig;
        private final Context mContext;
    }

    public MPDbAdapter(Context context, MPConfig config) {
        this(context, getDbName(config.getInstanceName()), config);
    }

    private static String getDbName(String instanceName) {
        return (instanceName == null || instanceName.trim().isEmpty()) ? DATABASE_NAME : (DATABASE_NAME + "_" + instanceName);
    }

    public MPDbAdapter(Context context, String dbName, MPConfig config) {
        mDb = new MPDatabaseHelper(context, dbName, config);
    }

    public static MPDbAdapter getInstance(Context context, MPConfig config) {
        synchronized (sInstances) {
            final Context appContext = context.getApplicationContext();
            MPDbAdapter ret;
            String instanceName = config.getInstanceName();
            if (!sInstances.containsKey(instanceName)) {
                ret = new MPDbAdapter(appContext, config);
                sInstances.put(instanceName, ret);
            } else {
                ret = sInstances.get(instanceName);
            }
            return ret;
        }
    }

    /**
     * Adds a JSON string representing an event with properties or a person record
     * to the SQLiteDatabase.
     * @param j the JSON to record
     * @param token token of the project
     * @param table the table to insert into, one of "events", "people", "groups" or "anonymous_people"
     * @return the number of rows in the table, or DB_OUT_OF_MEMORY_ERROR/DB_UPDATE_ERROR
     * on failure
     */
    public int addJSON(JSONObject j, String token, Table table) {
        // we are aware of the race condition here, but what can we do..?
        if (this.aboveMemThreshold()) {
            MPLog.e(LOGTAG, "There is not enough space left on the device or " +
                    "the data was over the maximum size limit so it was discarded");
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
            cv.put(KEY_TOKEN, token);
            db.insert(tableName, null, cv);

            c = db.rawQuery("SELECT COUNT(*) FROM " + tableName + " WHERE token='" + token + "'", null);
            c.moveToFirst();
            count = c.getInt(0);
        } catch (final SQLiteException e) {
            MPLog.e(LOGTAG, "Could not add Mixpanel data to table");

            // We assume that in general, the results of a SQL exception are
            // unrecoverable, and could be associated with an oversized or
            // otherwise unusable DB. Better to bomb it and get back on track
            // than to leave it junked up (and maybe filling up the disk.)
            if (c != null) {
                c.close();
                c = null;
            }
            mDb.deleteDatabase();
        } catch (final OutOfMemoryError e) {
            MPLog.e(LOGTAG, "Out of memory when adding Mixpanel data to table");
        } finally {
            if (c != null) {
                c.close();
            }
            mDb.close();
        }
        return count;
    }

    /**
     * Copies anonymous people updates to people db after a user has been identified
     * @param token project token
     * @param distinctId people profile distinct id
     * @return the number of rows copied (anonymous updates), or DB_OUT_OF_MEMORY_ERROR/DB_UPDATE_ERROR
     * on failure
     */
    /* package */ int pushAnonymousUpdatesToPeopleDb(String token, String distinctId) {
        if (this.aboveMemThreshold()) {
            MPLog.e(LOGTAG, "There is not enough space left on the device or " +
                    "the data was over the maximum size limit so it was discarded");
            return DB_OUT_OF_MEMORY_ERROR;
        }
        Cursor selectCursor = null;
        int count = DB_UPDATE_ERROR;

        try {
            final SQLiteDatabase db = mDb.getWritableDatabase();
            StringBuffer allAnonymousQuery = new StringBuffer("SELECT * FROM " + Table.ANONYMOUS_PEOPLE.getName() + " WHERE " + KEY_TOKEN + " = '" + token + "'");

            selectCursor = db.rawQuery(allAnonymousQuery.toString(), null);
            db.beginTransaction();
            try {
                while (selectCursor.moveToNext()) {
                    try {
                        ContentValues values = new ContentValues();
                        final int createdAtColumnIndex = selectCursor.getColumnIndex(KEY_CREATED_AT) >= 0 ? selectCursor.getColumnIndex(KEY_CREATED_AT) : CREATED_AT_COLUMN_INDEX;
                        values.put(KEY_CREATED_AT, selectCursor.getLong(createdAtColumnIndex));
                        final int automaticDataColumnIndex = selectCursor.getColumnIndex(KEY_AUTOMATIC_DATA) >= 0 ? selectCursor.getColumnIndex(KEY_AUTOMATIC_DATA) : AUTOMATIC_DATA_COLUMN_INDEX;
                        values.put(KEY_AUTOMATIC_DATA, selectCursor.getInt(automaticDataColumnIndex));
                        final int tokenColumnIndex = selectCursor.getColumnIndex(KEY_TOKEN) >= 0 ? selectCursor.getColumnIndex(KEY_TOKEN) : TOKEN_COLUMN_INDEX;
                        values.put(KEY_TOKEN, selectCursor.getString(tokenColumnIndex));
                        final int dataColumnIndex = selectCursor.getColumnIndex(KEY_DATA) >= 0 ? selectCursor.getColumnIndex(KEY_DATA) : DATA_COLUMN_INDEX;
                        JSONObject updatedData = new JSONObject(selectCursor.getString(dataColumnIndex));
                        updatedData.put("$distinct_id", distinctId);
                        values.put(KEY_DATA, updatedData.toString());
                        db.insert(Table.PEOPLE.getName(), null, values);
                        final int idColumnIndex = selectCursor.getColumnIndex("_id") >= 0 ? selectCursor.getColumnIndex("_id") : ID_COLUMN_INDEX;
                        int rowId = selectCursor.getInt(idColumnIndex);
                        db.delete(Table.ANONYMOUS_PEOPLE.getName(), "_id = " + rowId, null);
                        count++;
                    } catch (final JSONException e) {
                        // Ignore this object
                    }
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } catch (final SQLiteException e) {
            MPLog.e(LOGTAG, "Could not push anonymous updates records from " + Table.ANONYMOUS_PEOPLE.getName() + ". Re-initializing database.", e);

            if (selectCursor != null) {
                selectCursor.close();
                selectCursor = null;
            }
            // We assume that in general, the results of a SQL exception are
            // unrecoverable, and could be associated with an oversized or
            // otherwise unusable DB. Better to bomb it and get back on track
            // than to leave it junked up (and maybe filling up the disk.)
            mDb.deleteDatabase();
        } finally {
            if (selectCursor != null) {
                selectCursor.close();
            }
            mDb.close();
        }

        return count;
    }

    /**
     * Copies anonymous people updates to people db after a user has been identified
     * @param properties Map of properties that will be added to existing events.
     * @param token project token
     * @return the number of rows updated , or DB_OUT_OF_MEMORY_ERROR/DB_UPDATE_ERROR
     * on failure
     */
    /* package */ int rewriteEventDataWithProperties(Map<String, String> properties, String token) {
        if (this.aboveMemThreshold()) {
            MPLog.e(LOGTAG, "There is not enough space left on the device or " +
                    "the data was over the maximum size limit so it was discarded");
            return DB_OUT_OF_MEMORY_ERROR;
        }
        Cursor selectCursor = null;
        int count = 0;

        try {
            final SQLiteDatabase db = mDb.getWritableDatabase();
            StringBuffer allAnonymousQuery = new StringBuffer("SELECT * FROM " + Table.EVENTS.getName() + " WHERE " + KEY_TOKEN + " = '" + token + "'");

            selectCursor = db.rawQuery(allAnonymousQuery.toString(), null);
            db.beginTransaction();
            try {
                while (selectCursor.moveToNext()) {
                    try {
                        ContentValues values = new ContentValues();
                        final int dataColumnIndex = selectCursor.getColumnIndex(KEY_DATA) >= 0 ? selectCursor.getColumnIndex(KEY_DATA) : DATA_COLUMN_INDEX;
                        JSONObject updatedData = new JSONObject(selectCursor.getString(dataColumnIndex));
                        JSONObject existingProps = updatedData.getJSONObject("properties");
                        for (final Map.Entry<String, String> entry : properties.entrySet()) {
                            final String key = entry.getKey();
                            final String value = entry.getValue();
                            existingProps.put(key, value);
                        }
                        updatedData.put("properties", existingProps);
                        values.put(KEY_DATA, updatedData.toString());
                        final int idColumnIndex = selectCursor.getColumnIndex("_id") >= 0 ? selectCursor.getColumnIndex("_id") : ID_COLUMN_INDEX;
                        int rowId = selectCursor.getInt(idColumnIndex);
                        db.update(Table.EVENTS.getName(), values, "_id = " + rowId, null);
                        count++;
                    } catch (final JSONException e) {
                        // Ignore this object
                    }
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } catch (final SQLiteException e) {
            MPLog.e(LOGTAG, "Could not re-write events history. Re-initializing database.", e);

            if (selectCursor != null) {
                selectCursor.close();
                selectCursor = null;
            }
            // We assume that in general, the results of a SQL exception are
            // unrecoverable, and could be associated with an oversized or
            // otherwise unusable DB. Better to bomb it and get back on track
            // than to leave it junked up (and maybe filling up the disk.)
            mDb.deleteDatabase();
        } finally {
            if (selectCursor != null) {
                selectCursor.close();
            }
            mDb.close();
        }

        return count;
    }

    /**
     * Removes events with an _id <= last_id from table
     * @param last_id the last id to delete
     * @param table the table to remove events from, one of "events", "people", "groups" or "anonymous_people"
     */
    public void cleanupEvents(String last_id, Table table, String token) {
        final String tableName = table.getName();

        try {
            final SQLiteDatabase db = mDb.getWritableDatabase();
            StringBuffer deleteQuery = new StringBuffer("_id <= " + last_id + " AND " + KEY_TOKEN + " = '" + token + "'");

            db.delete(tableName, deleteQuery.toString(), null);
        } catch (final SQLiteException e) {
            MPLog.e(LOGTAG, "Could not clean sent Mixpanel records from " + tableName + ". Re-initializing database.", e);

            // We assume that in general, the results of a SQL exception are
            // unrecoverable, and could be associated with an oversized or
            // otherwise unusable DB. Better to bomb it and get back on track
            // than to leave it junked up (and maybe filling up the disk.)
            mDb.deleteDatabase();
        } catch (final Exception e) {
            MPLog.e(LOGTAG, "Unknown exception. Could not clean sent Mixpanel records from " + tableName + ".Re-initializing database.", e);
            mDb.deleteDatabase();
        } finally {
            mDb.close();
        }
    }

    /**
     * Removes events before time.
     * @param time the unix epoch in milliseconds to remove events before
     * @param table the table to remove events from, one of "events", "people", "groups" or "anonymous_people"
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
     * @param table the table to remove events from, one of "events", "people", "groups" or "anonymous_people"
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

    public void deleteDB() {
        mDb.deleteDatabase();
    }

    /**
     * Returns the data string to send to Mixpanel and the maximum ID of the row that
     * we're sending, so we know what rows to delete when a track request was successful.
     *
     * @param table the table to read the JSON from, one of "events", "people", or "groups"
     * @param token the token of the project you want to retrieve the records for
     * @return String array containing the maximum ID, the data string
     * representing the events (or null if none could be successfully retrieved) and the total
     * current number of events in the queue.
     */
    public String[] generateDataString(Table table, String token) {
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


            rawDataQuery.append("ORDER BY " + KEY_CREATED_AT + " ASC LIMIT " + Integer.toString(mDb.mConfig.getFlushBatchSize()));
            c = db.rawQuery(rawDataQuery.toString(), null);

            queueCountCursor = db.rawQuery(queueCountQuery.toString(), null);
            queueCountCursor.moveToFirst();
            queueCount = String.valueOf(queueCountCursor.getInt(0));

            final JSONArray arr = new JSONArray();

            while (c.moveToNext()) {
                if (c.isLast()) {
                    final int idColumnIndex = c.getColumnIndex("_id") >= 0 ? c.getColumnIndex("_id") : ID_COLUMN_INDEX;
                    last_id = c.getString(idColumnIndex);
                }
                try {
                    final int dataColumnIndex = c.getColumnIndex(KEY_DATA) >= 0 ? c.getColumnIndex(KEY_DATA) : DATA_COLUMN_INDEX;
                    final JSONObject j = new JSONObject(c.getString(dataColumnIndex));
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

    /**
     * Returns true if this is a newly created database (the database file did not exist
     * before this adapter was initialized). Used to detect first app launch.
     */
    public boolean isNewDatabase() {
        return mDb.isNewDatabase();
    }

    /* For testing use only, do not call from in production code */
    protected boolean aboveMemThreshold() {
        return mDb.aboveMemThreshold();
    }
}
