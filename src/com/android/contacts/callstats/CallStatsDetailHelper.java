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

import android.content.res.Resources;
import android.content.Context;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import com.android.contacts.R;
import com.android.contacts.calllog.PhoneNumberHelper;

/**
 * Class used to populate a detailed view for a callstats item
 */
public class CallStatsDetailHelper {

    private final Resources mResources;
    private final PhoneNumberHelper mPhoneNumberHelper;

    public CallStatsDetailHelper(Resources resources, PhoneNumberHelper phoneNumberHelper) {
        mResources = resources;
        mPhoneNumberHelper = phoneNumberHelper;
    }

    public void setCallStatsDetails(CallStatsDetailViews views,
            CallStatsDetails details, int type, boolean byDuration,
            float percent, float ratio) {

        CharSequence numberFormattedLabel = null;
        // Only show a label if the number is shown and it is not a SIP address.
        if (!TextUtils.isEmpty(details.number)
                && !PhoneNumberUtils.isUriNumber(details.number.toString())) {
            numberFormattedLabel = Phone.getTypeLabel(mResources,
                    details.numberType, details.numberLabel);
        }

        final CharSequence nameText;
        final CharSequence numberText;
        final CharSequence labelText;
        final CharSequence displayNumber = mPhoneNumberHelper.getDisplayNumber(
                details.number, details.formattedNumber);

        if (TextUtils.isEmpty(details.name)) {
            nameText = displayNumber;
            if (TextUtils.isEmpty(details.geocode)
                    || mPhoneNumberHelper.isVoicemailNumber(details.number)) {
                numberText = mResources.getString(R.string.call_log_empty_gecode);
            } else {
                numberText = details.geocode;
            }
            labelText = null;
        } else {
            nameText = details.name;
            numberText = displayNumber;
            labelText = numberFormattedLabel;
        }

        float in = 0, out = 0, missed = 0;

        if (type == CallStatsQueryHandler.CALL_TYPE_ALL) {
            in = byDuration
                    ? details.getDurationPercentage(Calls.INCOMING_TYPE) * ratio / 100F
                    : details.getCountPercentage(Calls.INCOMING_TYPE) * ratio / 100F;
            out = byDuration
                    ? details.getDurationPercentage(Calls.OUTGOING_TYPE) * ratio / 100F
                    : details.getCountPercentage(Calls.OUTGOING_TYPE) * ratio / 100F;
        } else if (type == Calls.INCOMING_TYPE) {
            in = ratio;
        } else if (type == Calls.OUTGOING_TYPE) {
            out = ratio;
        }

        if (!byDuration) {
            missed = details.getCountPercentage(Calls.MISSED_TYPE) * ratio / 100F;
        } else if (type == Calls.MISSED_TYPE) {
            missed = ratio;
        }

	android.util.Log.d("foo", "for " + nameText + " in " + in + " out " + out + " missed " + missed);
        views.barView.setRatios(in, out, missed);
        views.nameView.setText(nameText);
        views.numberView.setText(numberText);
        views.labelView.setText(labelText);
        views.labelView.setVisibility(
                TextUtils.isEmpty(labelText) ? View.GONE : View.VISIBLE);

        if (byDuration && type == Calls.MISSED_TYPE) {
            views.percentView.setText(getCallCountString(mResources, details.missedCount));
        } else {
            views.percentView.setText(String.format("%.1f%%", percent));
        }
    }

    public void setCallStatsDetailHeader(TextView nameView, CallStatsDetails details) {
        final CharSequence nameText;
        final CharSequence displayNumber = mPhoneNumberHelper.getDisplayNumber(
                details.number,
                mResources.getString(R.string.recentCalls_addToContact));
        if (TextUtils.isEmpty(details.name)) {
            nameText = displayNumber;
        } else {
            nameText = details.name;
        }

        nameView.setText(nameText);
    }

    public static String getCallCountString(Resources res, long count) {
        return res.getQuantityString(R.plurals.call, (int) count, (int) count);
    }

    public static String getDurationString(Resources res, long duration, boolean includeSeconds) {
        int hours, minutes, seconds;

        hours = (int) (duration / 3600);
        duration -= (long) hours * 3600;
        minutes = (int) (duration / 60);
        duration -= (long) minutes * 60;
        seconds = (int) duration;

        if (!includeSeconds) {
            if (seconds >= 30) {
                minutes++;
            }
            if (minutes >= 60) {
                hours++;
            }
        }

        boolean dispHours = hours > 0;
        boolean dispMinutes = minutes > 0 || !includeSeconds;
        boolean dispSeconds = includeSeconds && (seconds > 0 || (hours == 0 && minutes == 0));

        final String hourString = dispHours ?
            res.getQuantityString(R.plurals.hour, hours, hours) : null;
        final String minuteString = dispMinutes ?
            res.getQuantityString(R.plurals.minute, minutes, minutes) : null;
        final String secondString = dispSeconds ?
            res.getQuantityString(R.plurals.second, seconds, seconds) : null;

        int index = ((dispHours ? 4 : 0) | (dispMinutes ? 2 : 0) | (dispSeconds ? 1 : 0)) - 1;
        String[] formats = res.getStringArray(R.array.call_stats_duration);
        return String.format(formats[index], hourString, minuteString, secondString);
    }
}
