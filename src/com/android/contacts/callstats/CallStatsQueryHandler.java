/*
 * Copyright (C) 2011 The Android Open Source Project
 * Copyright (C) 2013 Android Open Kang Project
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

package com.android.contacts.callstats;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.util.Log;

import com.android.common.io.MoreCloseables;
import com.google.common.collect.Lists;

import java.lang.ref.WeakReference;
import java.util.List;

/**
 * Class to handle call-log queries, optionally with a date-range filter
 */
public class CallStatsQueryHandler {

    private static final String[] EMPTY_STRING_ARRAY = new String[0];

    private static final int NUM_LOGS_TO_DISPLAY = 1000;

    public static final int CALL_TYPE_ALL = 0;

    private static final String TAG = "CallStatsQueryHandler";

    private final WeakReference<Listener> mListener;
    private ContentResolver mContentResolver;

    private Cursor mCursor;

    public CallStatsQueryHandler(ContentResolver cr, Listener listener) {
        mContentResolver = cr;
        mListener = new WeakReference<Listener>(listener);
    }

    public void fetchCalls(long from, long to) {
        String selection = new String("");
        List<String> selectionArgs = Lists.newArrayList();
        if (from != -1) {
            selection = String.format("(%s > ?)", Calls.DATE);
            selectionArgs.add(String.valueOf(from));
        }
        if (to != -1) {
            selection = String.format("(%s) AND (%s < ?)", selection, Calls.DATE);
            selectionArgs.add(String.valueOf(to));
        }
        Uri uri = Calls.CONTENT_URI;
        Cursor c = mContentResolver.query(uri, CallStatsQuery._PROJECTION,
                selection, selectionArgs.toArray(EMPTY_STRING_ARRAY),
                Calls.NUMBER + " DESC");
        updateAdapterData(c);
    }

    private void updateAdapterData(Cursor c) {
        final Listener listener = mListener.get();
        if (listener != null) {
            listener.onCallsFetched(c);
        }
    }

    public interface Listener {
        void onCallsFetched(Cursor c);
    }
}
