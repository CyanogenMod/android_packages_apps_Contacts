/*
 * Copyright (C) 2016 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.contacts.incall;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.google.common.base.Preconditions;

import java.util.LinkedList;
import java.util.List;

public class InCallMetricsDbHelper extends SQLiteOpenHelper {
    private static final String TAG = InCallMetricsDbHelper.class.getSimpleName();
    private static final boolean DEBUG = false;

    private static InCallMetricsDbHelper mInstance = null;

    private static final int DATABASE_VERSION = 1;
    private static final String DATABASE_NAME = "contacts_incall_metrics.db";

    private final Context mContext;

    // db query select statements
    private static final String SELECT_NUDGE_ID_AND_ACCEPT_TIME =
            InAppColumns.NUDGE_ID + "==? AND " + InAppColumns.EVENT_ACCEPTANCE_TIME + " ==? ";
    private static final String SELECT_PROVIDER_AND_EVENT =
            UserActionsColumns.PROVIDER_NAME + "==? AND " + UserActionsColumns.EVENT_NAME + " ==?";

    public interface Tables {
        // stores events in the INAPP_ACTIONS category
        static final String INAPP_TABLE = "inapp_metrics";
        // store events in the USER_ACTIONS category
        static final String USER_ACTIONS_TABLE = "user_actions_metrics";
    }

    public interface InAppColumns {
        static final String _ID = "id";
        static final String CATEGORY = "category";
        static final String EVENT_NAME = "event_name";
        static final String COUNT = "count";
        static final String NUDGE_ID = "nudge_id";
        static final String EVENT_ACCEPTANCE = "event_acceptance";
        static final String EVENT_ACCEPTANCE_TIME = "event_acceptance_time";
        static final String PROVIDER_NAME = "provider_name";
    }

    public interface UserActionsColumns {
        static final String _ID = "id";
        static final String CATEGORY = "category";
        static final String EVENT_NAME = "event_name";
        static final String COUNT = "count";
        static final String PROVIDER_NAME = "provider_name";
        static final String RAW_ID = "raw_id";
    }

    private static final String[] INAPP_PROJECTION = new String[] {
            InAppColumns._ID,
            InAppColumns.CATEGORY,
            InAppColumns.EVENT_NAME,
            InAppColumns.COUNT,
            InAppColumns.NUDGE_ID,
            InAppColumns.EVENT_ACCEPTANCE,
            InAppColumns.EVENT_ACCEPTANCE_TIME,
            InAppColumns.PROVIDER_NAME
    };

    private static final String[] USER_ACTIONS_PROJECTION = new String[] {
            UserActionsColumns._ID,
            UserActionsColumns.CATEGORY,
            UserActionsColumns.EVENT_NAME,
            UserActionsColumns.COUNT,
            UserActionsColumns.PROVIDER_NAME,
            UserActionsColumns.RAW_ID
    };

    /**
     * If there's an existing entry that matches the NUDGE_ID and the EVENT_ACCEPTANCE_TIME
     * is not set, increment the count stored "colName" column. Otherwise, create a new entry.
     *
     * @param  provider  component name of the InCall provider
     * @param  event     metric event
     * @param  cat       metric category
     * @param  nudgeId   metric nudge ID
     * @param  colName   database column name to increment
     */
    public void incrementInAppParam(String provider, String event, String cat, String nudgeId,
            String colName) {
        // find entries that match nudge_id, and timestamp 0 to increment count
        if (nudgeId == null) {
            nudgeId = InCallMetricsHelper.NUDGE_ID_INVALID;
        }
        ContentValues entry = new ContentValues();
        entry.put(InAppColumns.EVENT_NAME, event);
        entry.put(InAppColumns.CATEGORY, cat);
        entry.put(InAppColumns.NUDGE_ID, nudgeId);
        entry.put(InAppColumns.PROVIDER_NAME, provider);
        String[] selectionArgs = new String[] {nudgeId, String.valueOf(0)};
        SQLiteDatabase db = getWritableDatabase();
        Cursor queryCursor = db.query(
                Tables.INAPP_TABLE,
                INAPP_PROJECTION,
                SELECT_NUDGE_ID_AND_ACCEPT_TIME,
                selectionArgs,
                null,
                null,
                null);
        if (queryCursor != null && queryCursor.moveToFirst()) {
            // increment existing entry
            entry.put(colName, queryCursor.getInt(queryCursor.getColumnIndex(colName)) + 1);
            db.update(Tables.INAPP_TABLE, entry, SELECT_NUDGE_ID_AND_ACCEPT_TIME, selectionArgs);
        } else {
            // no entry where acceptance time is not set
            entry.put(colName, 1);
            db.insert(Tables.INAPP_TABLE, null, entry);
        }
        queryCursor.close();
    }

    /**
     * Tables.INAPP_TABLE stores in-app nudge related interaction events. When a user
     * accepts (accept set to 1)/dismisses (accept set to 0) the in-app nudge, the timestamp
     * is stored in EVENT_ACCEPTANCE_TIME. Once that timestamp is captured, any subsequent in-app
     * nudge interaction events should be recorded in a new entry.
     * If there's an existing entry that matches the NUDGE_ID and the EVENT_ACCEPTANCE_TIME
     * is not set, set the EVENT_ACCEPTANCE and EVENT_ACCEPTANCE_TIME. Otherwise, create a new
     * entry.
     *
     * @param  provider  component name of the InCall provider
     * @param  event     metric event
     * @param  cat       metric category
     * @param  nudgeId   metric nudge ID
     */
    public void setInAppAcceptance(String provider, String event, String cat, Integer accept,
            String nudgeId) {
        // find entries that match nudge_id, and timestamp 0
        // if all matche entries have timestamp set, create a new entry
        if (nudgeId == null) {
            nudgeId = InCallMetricsHelper.NUDGE_ID_INVALID;
        }
        String[] selectionArgs = new String[] {nudgeId, String.valueOf(0)};
        SQLiteDatabase db = getWritableDatabase();
        Cursor queryCursor = db.query(
                Tables.INAPP_TABLE,
                INAPP_PROJECTION,
                SELECT_NUDGE_ID_AND_ACCEPT_TIME,
                selectionArgs,
                null,
                null,
                null);
        ContentValues entry = new ContentValues();
        entry.put(InAppColumns.EVENT_NAME, event);
        entry.put(InAppColumns.CATEGORY, cat);
        entry.put(InAppColumns.NUDGE_ID, nudgeId);
        entry.put(InAppColumns.PROVIDER_NAME, provider);
        entry.put(InAppColumns.EVENT_ACCEPTANCE, accept);
        entry.put(InAppColumns.EVENT_ACCEPTANCE_TIME, System.currentTimeMillis());
        if (queryCursor != null && queryCursor.moveToFirst()) {
            db.update(Tables.INAPP_TABLE, entry, SELECT_NUDGE_ID_AND_ACCEPT_TIME, selectionArgs);
        } else {
            db.insert(Tables.INAPP_TABLE, null, entry);
        }
        queryCursor.close();
    }

    /**
     * Tables.USER_ACTIONS_TABLE stores auto/manual merge counts and invite counts.
     * If there's an existing entry that matches the PROVIDER_NAME and the EVENT_NAME,
     * increment the count stored in the "colName" column. Otherwise, create a new entry.
     *
     * @param  provider  component name of the InCall provider
     * @param  event     metric event
     * @param  cat       metric category
     * @param  colName   database column name to increment
     */
    public void incrementUserActionsParam(String provider, String rawIds, String event, String
            cat, String colName) {
        // query for event type, update, if not found insert
        ContentValues entry = new ContentValues();
        entry.put(UserActionsColumns.EVENT_NAME, event);
        entry.put(UserActionsColumns.CATEGORY, cat);
        entry.put(UserActionsColumns.PROVIDER_NAME, provider);
        entry.put(UserActionsColumns.RAW_ID, rawIds);

        String[] selectionArgs = new String[] {provider, event};
        SQLiteDatabase db = getWritableDatabase();
        Cursor queryCursor = db.query(
                Tables.USER_ACTIONS_TABLE,
                USER_ACTIONS_PROJECTION,
                SELECT_PROVIDER_AND_EVENT,
                selectionArgs,
                null,
                null,
                null);
        if (queryCursor != null && queryCursor.moveToFirst()) {
            // increment existing entry, only increment if it's manual merge
            if (event.equals(InCallMetricsHelper.Events.CONTACTS_MANUAL_MERGED)) {
                entry.put(colName,
                        queryCursor.getInt(queryCursor.getColumnIndex(colName)) + 1);
                db.update(Tables.USER_ACTIONS_TABLE, entry, SELECT_PROVIDER_AND_EVENT,
                        selectionArgs);
            }
        } else {
            // no existing entry, create one
            entry.put(colName, 1);
            db.insert(Tables.USER_ACTIONS_TABLE, null, entry);
        }
        queryCursor.close();
    }

    /**
     * Return all entries in the table storing events from the corresponding category
     *
     * @param  cat     metric category
     * @param  clear   if the database table should be cleared afterwards
     * @return List of ContentValues in the respective table
     */
    public List<ContentValues> getAllEntries(InCallMetricsHelper.Categories cat, boolean clear) {
        List<ContentValues> list = new LinkedList<ContentValues>();
        String table = "";
        switch (cat) {
            case USER_ACTIONS:
                table = Tables.USER_ACTIONS_TABLE;
                break;
            case INAPP_NUDGES:
                table = Tables.INAPP_TABLE;
                break;
            default:
                return list;
        }
        SQLiteDatabase db = getWritableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + table, null);
        if (cursor != null && cursor.moveToFirst()) {
            do {
                ContentValues entry = new ContentValues();
                DatabaseUtils.cursorRowToContentValues(cursor, entry);
                list.add(entry);
            } while (cursor.moveToNext());
        }
        if (!DEBUG && clear) {
            db.delete(table, null, null);
        }
        cursor.close();
        return list;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        setupTables(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion == 0) {
            Log.e(TAG, "Malformed database version..recreating database");
        }

        if (oldVersion < newVersion) {
            setupTables(db);
        }
    }

    public static synchronized InCallMetricsDbHelper getInstance(Context context) {
        if (DEBUG) {
            Log.v(TAG, "Getting Instance");
        }
        if (mInstance == null) {
            // Use application context instead of activity context because this is a singleton,
            // and we don't want to leak the activity if the activity is not running but the
            // database helper is still doing work.
            mInstance = new InCallMetricsDbHelper(context.getApplicationContext(), DATABASE_NAME);
        }
        return mInstance;
    }

    protected InCallMetricsDbHelper(Context context, String databaseName) {
        this(context, databaseName, DATABASE_VERSION);
    }

    protected InCallMetricsDbHelper(Context context, String databaseName, int dbVersion) {
        super(context, databaseName, null, dbVersion);
        mContext = context;
    }

    private void setupTables(SQLiteDatabase db) {
        dropTables(db);
        db.execSQL("CREATE TABLE " + Tables.INAPP_TABLE + " (" +
                InAppColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                InAppColumns.CATEGORY + " TEXT, " +
                InAppColumns.EVENT_NAME + " TEXT, " +
                InAppColumns.COUNT + " INTEGER DEFAULT 0, " +
                InAppColumns.NUDGE_ID + " TEXT, " +
                InAppColumns.EVENT_ACCEPTANCE + " INTEGER DEFAULT -1, " +
                InAppColumns.EVENT_ACCEPTANCE_TIME + " INTEGER DEFAULT 0, " +
                InAppColumns.PROVIDER_NAME + " TEXT" +
                ");");
        db.execSQL("CREATE TABLE " + Tables.USER_ACTIONS_TABLE + " (" +
                UserActionsColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                UserActionsColumns.CATEGORY + " TEXT, " +
                UserActionsColumns.EVENT_NAME + " TEXT, " +
                UserActionsColumns.COUNT + " INTEGER DEFAULT 0, " +
                UserActionsColumns.PROVIDER_NAME + " TEXT, " +
                UserActionsColumns.RAW_ID + " INTEGER DEFAULT 0" +
                ");");
    }

    public void dropTables(SQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS " + Tables.INAPP_TABLE);
        db.execSQL("DROP TABLE IF EXISTS " + Tables.USER_ACTIONS_TABLE);
    }
}