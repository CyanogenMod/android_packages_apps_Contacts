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

import android.content.Intent;
import android.content.Context;
import android.net.Uri;
import android.provider.CallLog.Calls;
import android.util.Log;

import com.android.contacts.util.UriUtils;

/**
 * Class to store statistical details for a given contact/number.
 */
public class CallStatsDetails {

    private static final String TAG = "CallStatsDetails";
    private static final String NUMBER = "number";
    private static final String F_NUMBER = "f_number";
    private static final String C_ISO = "c_iso";
    private static final String GEOCODE = "geocode";
    private static final String NAME = "name";
    private static final String DATE = "date";
    private static final String NUM_TYPE = "num_type";
    private static final String NUM_LABEL = "num_label";
    private static final String C_URI = "c_uri";
    private static final String PHOTO_ID = "photo_id";
    private static final String PHOTO_URI = "photo_uri";
    private static final String IN_DUR = "in_dur";
    private static final String OUT_DUR = "out_dur";
    private static final String MISSED = "missed";

    public final CharSequence number;
    public final CharSequence formattedNumber;
    public final String countryIso;
    public final String geocode;
    public final long date;
    public final CharSequence name;
    public final int numberType;
    public final CharSequence numberLabel;
    public Uri contactUri;
    public Uri photoUri;
    public final long photoId;
    public long inDuration;
    public long outDuration;
    public int missedCount;

    /** Used when no contact has been found for this number */
    public CallStatsDetails(CharSequence number, CharSequence formattedNumber,
            String countryIso, String geocode, long date) {
        this(number, formattedNumber, countryIso, geocode, date, "", 0, "",
                null, null, 0, 0, 0, 0);
    }

    /**
     * Used when both contact information and initial call statistics are
     * available
     */
    public CallStatsDetails(CharSequence number, CharSequence formattedNumber,
            String countryIso, String geocode, long date, CharSequence name,
            int numberType, CharSequence numberLabel, Uri contactUri,
            Uri photoUri, long photoId, long inDuration, long outDuration,
            int missedCount) {
        this.number = number;
        this.formattedNumber = formattedNumber;
        this.countryIso = countryIso;
        this.geocode = geocode;
        this.date = date;
        this.name = name;
        this.numberType = numberType;
        this.numberLabel = numberLabel;
        this.contactUri = contactUri;
        this.photoUri = photoUri;
        this.photoId = photoId;
        this.inDuration = inDuration;
        this.outDuration = outDuration;
        this.missedCount = missedCount;
    }

    /** Used when we have a contact, but no statistics */
    public CallStatsDetails(CharSequence number, CharSequence formattedNumber,
            String countryIso, String geocode, long date, CharSequence name,
            int numberType, CharSequence numberLabel, Uri contactUri,
            Uri photoUri, long photoId) {
        this(number, formattedNumber, countryIso, geocode, date, name,
                numberType, numberLabel, contactUri, photoUri, photoId, 0, 0, 0);
    }

    public long getFullDuration() {
        return inDuration + outDuration;
    }

    public void addTimeOrMissed(int type, long time) {
        switch (type) {
            case Calls.INCOMING_TYPE:
                inDuration += time;
                break;
            case Calls.OUTGOING_TYPE:
                outDuration += time;
                break;
            case Calls.MISSED_TYPE:
                missedCount++;
                break;
        }
    }

    public String getInPercentage() {
        return String.valueOf(Math.round((float) inDuration * 100
                / (float) getFullDuration()))
                + "%";
    }

    public String getOutPercentage() {
        return String.valueOf(Math.round((float) outDuration * 100
                / (float) getFullDuration()))
                + "%";
    }

    /** Used to generate an intent to later recreate this item */
    public Intent getIntentWithExtras(Context context) {
        Intent intent = new Intent(context, CallStatsDetailActivity.class);
        intent.putExtra(NUMBER, number);
        intent.putExtra(F_NUMBER, formattedNumber);
        intent.putExtra(C_ISO, countryIso);
        intent.putExtra(GEOCODE, geocode);
        intent.putExtra(DATE, date);
        intent.putExtra(NAME, name);
        intent.putExtra(NUM_TYPE, numberType);
        intent.putExtra(NUM_LABEL, numberLabel);
        intent.putExtra(PHOTO_ID, photoId);
        intent.putExtra(IN_DUR, inDuration);
        intent.putExtra(OUT_DUR, outDuration);
        intent.putExtra(MISSED, missedCount);
        try {
            intent.putExtra(C_URI, contactUri.toString());
        } catch (NullPointerException ex) {
            Log.i(TAG, "Could not add contact uri");
        }
        try {
            intent.putExtra(PHOTO_URI, photoUri.toString());
        } catch (NullPointerException ex) {
            Log.i(TAG, "Could not add photo uri");
        }
        return intent;
    }

    /** Used to recreate an object from an intent */
    public static CallStatsDetails reCreateFromIntent(Intent intent) {
        final String number = intent.getStringExtra(NUMBER);
        final String f_number = intent.getStringExtra(F_NUMBER);
        final String c_iso = intent.getStringExtra(C_ISO);
        final String geocode = intent.getStringExtra(GEOCODE);
        final long date = intent.getLongExtra(DATE, 0);
        final String name = intent.getStringExtra(NAME);
        final int num_type = intent.getIntExtra(NUM_TYPE, 0);
        final String num_label = intent.getStringExtra(NUM_LABEL);
        final Uri contacturi = UriUtils.parseUriOrNull(intent.getStringExtra(C_URI));
        final Uri photouri = UriUtils.parseUriOrNull(intent.getStringExtra(PHOTO_URI));
        final long photoid = intent.getLongExtra(PHOTO_ID, 0);
        final long in_dur = intent.getLongExtra(IN_DUR, 0);
        final long out_dur = intent.getLongExtra(OUT_DUR, 0);
        final int missed = intent.getIntExtra(MISSED, 0);

        return new CallStatsDetails(number, f_number, c_iso, geocode, date,
                name, num_type, num_label, contacturi, photouri, photoid,
                in_dur, out_dur, missed);
    }

    public long getRequestedDuration(int type) {
        switch (type) {
            case Calls.INCOMING_TYPE:
                return inDuration;
            case Calls.OUTGOING_TYPE:
                return outDuration;
            case Calls.MISSED_TYPE:
                return (long) missedCount;
            default:
                return getFullDuration();
        }
    }

    public void mergeWith(CallStatsDetails other) {
        this.inDuration += other.inDuration;
        this.outDuration += other.outDuration;
        this.missedCount += other.missedCount;
    }
}
