package com.mixpanel.android.dbadapter;

import java.text.SimpleDateFormat;
import java.util.Date;

import com.mixpanel.android.mpmetrics.Global;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class MPDbAdapter {
    
    private static final String LOGTAG = "MPDbAdapter";
    
    public static final String DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
    
    public static final String KEY_DATA = "data";
	public static final String KEY_CREATED_AT = "created_at";
	
	public static final String KEY_ROWID = "_id";

	private DatabaseHelper mDbHelper;
	private SQLiteDatabase mDb;
	private final Context mContext;
	private static final String DATABASE_NAME = "mixpanel";
    private static final String DATABASE_TABLE = "events";
    private static final int DATABASE_VERSION = 1;

	/**
	 * Database creation sql statement
	 */
	private static final String DATABASE_CREATE = "create table " + DATABASE_TABLE + " (_id integer primary key autoincrement, " + 
													KEY_DATA + " string not null," + 
													KEY_CREATED_AT + " string not null);";
	
	

	private static class DatabaseHelper extends SQLiteOpenHelper {

		DatabaseHelper(Context context) {
			super(context, DATABASE_NAME, null, DATABASE_VERSION);
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			db.execSQL(DATABASE_CREATE);
			
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		    if (Global.DEBUG) Log.w(LOGTAG, "Upgrading database from version " + oldVersion + " to " + newVersion);
			
		    switch (newVersion) {
    		    default: 
    		        break;
		    }
		}
	}

	/**
	 * Constructor - takes the context to allow the database to be
	 * opened/created
	 * 
	 * @param context the Context within which to work
	 */
	public MPDbAdapter(Context context) {
	    mContext = context;
	}

	/**
	 * Open the MPMetrics database. If it cannot be opened, try to create a new
	 * instance of the database. If it cannot be created, throw an exception to
	 * signal the failure
	 * 
	 * @return this (self reference, allowing this to be chained in an
	 *         initialization call)
	 * @throws SQLException if the database could be neither opened or created
	 */
	public MPDbAdapter open() throws SQLException {
		mDbHelper = new DatabaseHelper(mContext);
		try {
			mDb = mDbHelper.getWritableDatabase();
		} catch (Exception e) {
		    if (Global.DEBUG) Log.d(LOGTAG, "Exception with opening database, delete and retry");
		    if (mDb != null) {
		    	mDb.close();
		    	mDb = null;
		    }		
		    try {
		    	mContext.deleteDatabase(DATABASE_NAME);
		    } catch (Exception de) {}
		    
			try {
				if (mDbHelper == null) {
					mDbHelper = new DatabaseHelper(mContext);
				}
				mDb = mDbHelper.getWritableDatabase();
				
			} catch (Exception ex) {
			    if (Global.DEBUG) Log.d(LOGTAG, "Retry failed. Giving up.");			    
			}
		}
		return this;
	}

	public void close() {
		if (mDb != null) {
			mDb.close();
		}

		if (mDbHelper != null) {
			mDbHelper.close();
		}		
	}

	/**
     * Delete events before the provided date 
     * 
     * @param date date before which events should be deleted
     */
	public void cleanupEvents(Date date) {
		if (mDb == null) {
			this.open();
			if (mDb == null) return;
		}
	    SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_TIME_FORMAT);
	    String strDate = dateFormat.format(date);
	    
	    mDb.delete(DATABASE_TABLE, KEY_CREATED_AT + " < \"" + strDate + "\"", null);
	}
	
	/**
	 * Create a new set of events 
	 * 
	 * @param data the base64 encoded data of the events
	 * @return rowId or -1 if failed
	 */
	public long createEvents(String data) {
		if (mDb == null) {
			this.open();
			if (mDb == null) return -1;
		}		
	    SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_TIME_FORMAT);
	    Date date = new Date();
	    
		ContentValues initialValues = new ContentValues();
		initialValues.put(KEY_DATA, data);
		initialValues.put(KEY_CREATED_AT, dateFormat.format(date));
		
		return mDb.insert(DATABASE_TABLE, null, initialValues);
	}

	/**
	 * Delete the set of events with the given rowId
	 * 
	 * @param rowId id of set of events to delete
	 * @return true if deleted, false otherwise
	 */
	public boolean deleteEvents(long rowId) {
		if (mDb == null) {
			this.open();
			if (mDb == null) return false;
		}		
		return mDb.delete(DATABASE_TABLE, KEY_ROWID + "=" + rowId, null) > 0;
	}
	
	/**
	 * Delete all events
	 * 
	 * @return true if deleted, false otherwise
	 */
	public boolean deleteEvents() {
		if (mDb == null) {
			this.open();
			if (mDb == null) return false;
		}		
		
		return mDb.delete(DATABASE_TABLE, null, null) > 0;
	}

	/**
	 * Update the set of events using the details provided. The events to be updated is
	 * specified using the rowId, and it is altered to use the data values passed in
	 * 
	 * @param rowId id of set of events to update
	 * @param data the base64 encoded data of the events
	 * @return true if the set of events was successfully updated, false otherwise
	 */
	public boolean updateEvents(long rowId, String data) {
		if (mDb == null) {
			this.open();
			if (mDb == null) return false;
		}		
		
		ContentValues args = new ContentValues();
		
		args.put(KEY_DATA, data);
		return mDb.update(DATABASE_TABLE, args, KEY_ROWID + "=" + rowId, null) > 0;
	}
	
	/**
	 * Return a Cursor positioned at the set of events that matches the given rowId
	 * 
	 * @param rowId id of set of events to retrieve
	 * @return String the base64 encoded data of the events, null of the rowId doesn't exist
	 * @throws SQLException if set of events could not be found/retrieved
	 */
	public String fetchEventsData(long rowId) throws SQLException {
		if (mDb == null) {
			this.open();
			if (mDb == null) return null;
		}		
		
		Cursor mCursor = mDb.query(true, DATABASE_TABLE, new String[] { KEY_DATA }, KEY_ROWID + "=" + rowId,
				null, null, null, null, null);
		if (mCursor != null) {
			mCursor.moveToFirst();
			int dataColumnIndex = mCursor.getColumnIndexOrThrow(KEY_DATA);
			String data = mCursor.getString(dataColumnIndex);
			mCursor.close();
			return data;
		}
		return null;
	}
	

	/**
	 * Return a Cursor for all the events in the database. Cursors will be returned in descending id order
	 * 
	 * @return Cursor positioned at the beginning of all set of events
	 * @throws SQLException if set of events could not be found/retrieved
	 */
	public Cursor fetchEvents() throws SQLException {
		if (mDb == null) {
			this.open();
			if (mDb == null) return null;
		}		

		Cursor mCursor = mDb.query(true, DATABASE_TABLE, new String[] {
		        KEY_ROWID, KEY_DATA, KEY_CREATED_AT }, null, null, null, null, KEY_ROWID + " DESC", null);
		if (mCursor != null) {
			mCursor.moveToFirst();
		}
		return mCursor;
	}
	
}
