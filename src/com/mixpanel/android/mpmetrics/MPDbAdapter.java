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
	private static final int DATABASE_VERSION = 2;
	
	public static final String KEY_DATA = "data";
	public static final String KEY_CREATED_AT = "created_at";
	 
	private static final String DATABASE_CREATE = 
       "CREATE TABLE " + DATABASE_TABLE + " (_id INTEGER PRIMARY KEY AUTOINCREMENT, " + 
		KEY_DATA + " STRING NOT NULL," + 
		KEY_CREATED_AT + " INTEGER NOT NULL);";
	private static final String DATABASE_INDEX =
		"CREATE INDEX IF NOT EXISTS time_idx ON " + DATABASE_TABLE + 
		" (" + KEY_CREATED_AT + ");";
	
	private MPDatabaseHelper mDb;
	 
	private static class MPDatabaseHelper extends SQLiteOpenHelper {
		MPDatabaseHelper(Context context) {
			super(context, DATABASE_NAME, null, DATABASE_VERSION);
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			db.execSQL(DATABASE_CREATE);
			db.execSQL(DATABASE_INDEX);
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			// If onUpgrade is run, the app must have been reinstalled, so its safe to
			// delete all old events.
		    if (Global.DEBUG) Log.w(LOGTAG, "Upgrading database from version " + oldVersion + " to " + newVersion);

		    db.execSQL("DROP TABLE " + DATABASE_TABLE);
		    db.execSQL(DATABASE_CREATE);
		    db.execSQL(DATABASE_INDEX);
		}
	}
	
	public MPDbAdapter(Context context) {
		mDb = new MPDatabaseHelper(context);
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
			    db.insert(DATABASE_TABLE, null, cv);
			    
			    c = db.rawQuery("SELECT * FROM " + DATABASE_TABLE, null);
			    count = c.getCount();
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
	 * Removes events before time.
	 * @param time the unix epoch in milliseconds to remove events before
	 */
	public void cleanupEvents(long time) {
		synchronized (this) {
			if (Global.DEBUG) { Log.d(LOGTAG, "cleanupEvent"); }
			
			try {
				SQLiteDatabase db = mDb.getWritableDatabase();
			    db.delete(DATABASE_TABLE, KEY_CREATED_AT + " <= " + time, null);
			} catch (SQLiteException e) {
				// If there's an exception, oh well, let the events persist
				Log.e(LOGTAG, "cleanupEvents", e);
			} finally {
			    mDb.close();
			}
		}
	}
	
	/**
	 * Returns "<timestamp>:<data>" where timestamp is the unix epoch in milliseconds
	 * of when the most recent event was submitted and <data> is the base 64 encoded 
	 * string for events and properties stored in the database. If we couldn't
	 * successfully retrieve any objects, return null.
	 * 
	 * We need the timestamp to delete when a track request was successful. We
	 * add it to the string to because we have the data here, and so we don't have to
	 * call getReadableDatabase() multiple times.
	 * 
	 * @return the data string representing the events, or null if none could be 
	 * successfully retrieved.
	 */
	public String generateDataString() {
		synchronized (this) {
			Cursor c = null;
			String data = null;
			String timestamp = null;
			
			try {
				SQLiteDatabase db = mDb.getReadableDatabase();
				c = db.rawQuery("SELECT * FROM " + DATABASE_TABLE + 
		    		            " ORDER BY " + KEY_CREATED_AT + " ASC", null);
				JSONArray arr = new JSONArray();
				
				while (c.moveToNext()) {
					if (c.isLast()) {
						timestamp = c.getString(c.getColumnIndex(KEY_CREATED_AT));
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
			
			if (timestamp != null && data != null) {
				return timestamp + ":" + data;
			}
			return null;
		}
	}
}
