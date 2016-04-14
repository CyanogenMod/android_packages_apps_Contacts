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

package com.android.contacts.androidtest;

import com.android.contacts.incall.InCallMetricsDbHelper;
import com.android.contacts.incall.InCallMetricsHelper;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.util.LinkedList;
import java.util.List;

public class InCallMetricsTestDbUtils {
    private static final String TAG = InCallMetricsTestDbUtils.class.getSimpleName();

    private static final String[] INAPP_PROJECTION = new String[] {
            InCallMetricsDbHelper.InAppColumns._ID,
            InCallMetricsDbHelper.InAppColumns.CATEGORY,
            InCallMetricsDbHelper.InAppColumns.EVENT_NAME,
            InCallMetricsDbHelper.InAppColumns.COUNT,
            InCallMetricsDbHelper.InAppColumns.NUDGE_ID,
            InCallMetricsDbHelper.InAppColumns.EVENT_ACCEPTANCE,
            InCallMetricsDbHelper.InAppColumns.EVENT_ACCEPTANCE_TIME,
            InCallMetricsDbHelper.InAppColumns.PROVIDER_NAME
    };
    private static final String[] USER_ACTIONS_PROJECTION = new String[] {
            InCallMetricsDbHelper.UserActionsColumns._ID,
            InCallMetricsDbHelper.UserActionsColumns.CATEGORY,
            InCallMetricsDbHelper.UserActionsColumns.EVENT_NAME,
            InCallMetricsDbHelper.UserActionsColumns.COUNT,
            InCallMetricsDbHelper.UserActionsColumns.PROVIDER_NAME,
            InCallMetricsDbHelper.UserActionsColumns.RAW_ID
    };
    private static final String SELECT_EVENT =
            InCallMetricsDbHelper.UserActionsColumns.EVENT_NAME + " ==?";

    public static boolean clearAllEntries(Context context) {
        List<ContentValues> list = new LinkedList<ContentValues>();
        SQLiteDatabase db = InCallMetricsDbHelper.getInstance(context).getWritableDatabase();
        if (db == null) {
            Log.d(TAG, "No valid db");
            return false;
        }
        db.delete(InCallMetricsDbHelper.Tables.USER_ACTIONS_TABLE, null, null);
        db.delete(InCallMetricsDbHelper.Tables.INAPP_TABLE, null, null);
        return true;
    }

    public static ContentValues getEntry(Context context, String table,
            InCallMetricsHelper.Events event) {
        InCallMetricsDbHelper dbHelper = InCallMetricsDbHelper.getInstance(context);
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        String[] projection = new String[] {};
        if (table.equals(InCallMetricsDbHelper.Tables.USER_ACTIONS_TABLE)) {
            projection = USER_ACTIONS_PROJECTION;
        } else if (table.equals(InCallMetricsDbHelper.Tables.INAPP_TABLE)) {
            projection = INAPP_PROJECTION;
        }
        Cursor cursor = db.query(
                table,
                projection,
                SELECT_EVENT,
                new String[]{event.value()},
                null,
                null,
                null);
        ContentValues cv = getContentValues(cursor);
        if (cursor != null) {
            cursor.close();
        }
        return cv;
    }

    private static ContentValues getContentValues(Cursor cursor) {
        ContentValues map = new ContentValues();;
        if (cursor != null && cursor.moveToFirst()) {
            if(cursor.moveToFirst()) {
                DatabaseUtils.cursorRowToContentValues(cursor, map);
                Log.d(TAG, "getContentValues:" + map);
            }
        }
        return map;
    }
}
