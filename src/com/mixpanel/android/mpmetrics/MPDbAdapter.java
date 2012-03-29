package com.mixpanel.android.mpmetrics;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.mixpanel.android.util.Base64Coder;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

/**
 * SQLite database adapter for MPMetrics. This class is used from both the UI and
 * HTTP request threads, but maintains a single database connection. This is because
 * when performing concurrent writes from multiple database connections, some will
 * silently fail (save for a small message in logcat). Synchronize on each method,
 * so we don't close the connection when another thread is using it.
 *
 * @author anlu(Anlu Wang)
 *
 */
public class MPDbAdapter {
	private static final String LOGTAG = "MPDbAdapter";

	private static final String DATABASE_NAME = "mixpanel";
	private static final String DATABASE_TABLE = "events";
	private static final int DATABASE_VERSION = 3;

	public static final String KEY_DATA = "data";
	public static final String KEY_CREATED_AT = "created_at";
	public static final String KEY_TOKEN = "token";

	private static final String DATABASE_CREATE =
       "CREATE TABLE " + DATABASE_TABLE + " (_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
		KEY_DATA + " STRING NOT NULL," +
		KEY_CREATED_AT + " INTEGER NOT NULL," +
		KEY_TOKEN + " STRING NOT NULL);";
	private static final String TIME_INDEX =
		"CREATE INDEX IF NOT EXISTS time_idx ON " + DATABASE_TABLE +
		" (" + KEY_CREATED_AT + ");";
	private static final String TOKEN_INDEX =
		"CREATE INDEX IF NOT EXISTS token_idx ON " + DATABASE_TABLE +
		" (" + KEY_TOKEN + ");";

	private MPDatabaseHelper mDb;
	
	private String mToken;

	private static class MPDatabaseHelper extends SQLiteOpenHelper {
		MPDatabaseHelper(Context context) {
			super(context, DATABASE_NAME, null, DATABASE_VERSION);
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			db.execSQL(DATABASE_CREATE);
			db.execSQL(TIME_INDEX);
			db.execSQL(TOKEN_INDEX);
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			// If onUpgrade is run, the app must have been reinstalled, so its safe to
			// delete all old events.
		    if (Global.DEBUG) Log.w(LOGTAG, "Upgrading database from version " + oldVersion + " to " + newVersion);

		    db.execSQL("DROP TABLE " + DATABASE_TABLE);
		    db.execSQL(DATABASE_CREATE);
			db.execSQL(TIME_INDEX);
			db.execSQL(TOKEN_INDEX);
		}
	}

	public MPDbAdapter(Context context, String token) {
		mDb = new MPDatabaseHelper(context);
		mToken = token;
	}

	/**
	 * Adds an event to the SQLiteDatabase.
	 * @param j the event and properties to track
	 * @return the number of events in the table, or -1 on failure
	 */
	public int addEvent(JSONObject j) {
		synchronized (this) {
			if (Global.DEBUG) { Log.d(LOGTAG, "addEvent"); }

			Cursor c = null;
			int count = -1;

			try {
				SQLiteDatabase db = mDb.getWritableDatabase();

				ContentValues cv = new ContentValues();
				cv.put(KEY_DATA, j.toString());
				cv.put(KEY_CREATED_AT, System.currentTimeMillis());
				cv.put(KEY_TOKEN, mToken);
			    db.insert(DATABASE_TABLE, null, cv);

			    c = db.rawQuery("SELECT COUNT(*) FROM " + DATABASE_TABLE + " WHERE token = '" + mToken + "'", null);
			    c.moveToFirst();
			    count = c.getInt(0);
			} catch (SQLiteException e) {
				Log.e(LOGTAG, "addEvent", e);
			} finally {
			    mDb.close();
			    if (c != null) {
			    	c.close();
			    }
			}
			return count;
		}
	}

	/**
	 * Removes events with an _id <= last_id
	 * @param last_id the last id to delete
	 */
	public void cleanupEvents(String last_id) {
		synchronized (this) {
			if (Global.DEBUG) { Log.d(LOGTAG, "cleanupEvents _id " + last_id); }

			try {
				SQLiteDatabase db = mDb.getWritableDatabase();
			    db.delete(DATABASE_TABLE, "_id <= " + last_id + " AND token = '" + mToken + "'", null);
			} catch (SQLiteException e) {
				// If there's an exception, oh well, let the events persist
				Log.e(LOGTAG, "cleanupEvents", e);
			} finally {
			    mDb.close();
			}
		}
	}
	
	/**
	 * Removes events before time.
	 * @param time the unix epoch in milliseconds to remove events before
	 */
	public void cleanupEvents(long time) {
		synchronized (this) {
			if (Global.DEBUG) { Log.d(LOGTAG, "cleanupEvents time " + time); }

			try {
				SQLiteDatabase db = mDb.getWritableDatabase();
			    db.delete(DATABASE_TABLE, KEY_CREATED_AT + " <= " + time + " AND token = '" + mToken + "'", null);
			} catch (SQLiteException e) {
				// If there's an exception, oh well, let the events persist
				Log.e(LOGTAG, "cleanupEvents", e);
			} finally {
			    mDb.close();
			}
		}
	}

	/**
	 * Returns the data string to send to Mixpanel and the maximum ID of the row that
	 * we're sending, so we know what rows to delete when a track request was successful.
	 *
	 * @return String array containing the maximum ID and the data string 
	 * representing the events, or null if none could be successfully retrieved.
	 */
	public String[] generateDataString() {
		synchronized (this) {
			Cursor c = null;
			String data = null;
			String last_id = null;

			try {
				SQLiteDatabase db = mDb.getReadableDatabase();
				c = db.rawQuery("SELECT * FROM " + DATABASE_TABLE +
						        " WHERE token = '" + mToken + "'" +
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
					data = Base64Coder.encodeString(arr.toString());
				}
			} catch (SQLiteException e) {
				Log.e(LOGTAG, "generateDataString", e);
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
}
