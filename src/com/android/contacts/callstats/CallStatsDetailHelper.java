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
            CallStatsDetails details, int type, float percent, float ratio) {

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

        float inPercent = 0;
        float outPercent = 0;
        switch (type) {
            case CallStatsQueryHandler.CALL_TYPE_ALL:
                inPercent = ratio
                        * ((float) details.inDuration / (float) details.getFullDuration());
                outPercent = ratio
                        * ((float) details.outDuration / (float) details.getFullDuration());
                views.barView.redIsTheNewBlue(false);
                break;
            case Calls.INCOMING_TYPE:
                inPercent = ratio;
                views.barView.redIsTheNewBlue(false);
                break;
            case Calls.OUTGOING_TYPE:
                outPercent = ratio;
                views.barView.redIsTheNewBlue(false);
                break;
            case Calls.MISSED_TYPE:
                // small cheat here
                inPercent = ratio;
                views.barView.redIsTheNewBlue(true);
                break;
        }

        views.barView.setRatios(inPercent, outPercent, 1.0f - inPercent - outPercent);
        views.nameView.setText(nameText);
        views.numberView.setText(numberText);
        views.labelView.setText(labelText);
        views.labelView.setVisibility(
                TextUtils.isEmpty(labelText) ? View.GONE : View.VISIBLE);

        if (type == Calls.MISSED_TYPE) {
            views.percentView.setText(mResources.getQuantityString(
                    R.plurals.call, details.missedCount, details.missedCount));
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

    public static String getCallCountString(Context c, long count) {
        return c.getResources().getQuantityString(R.plurals.call,
                (int) count, (int) count);
    }

    public static String getDurationString(Context c, long duration, boolean includeSeconds) {
        long hours, minutes, seconds;

        hours = duration / 3600;
        duration -= hours * 3600;
        minutes = duration / 60;
        duration -= minutes * 60;
        seconds = duration;

        if (!includeSeconds) {
            if (seconds >= 30) {
                minutes++;
            }
            if (minutes >= 60) {
                hours++;
            }
        }

        if (!includeSeconds && hours > 0) {
            return c.getString(R.string.callDetailsDurationFormatWithoutSeconds, hours, minutes);
        } else if (!includeSeconds) {
            return c.getString(R.string.callDetailsDurationFormatMinuteOnly, minutes);
        } else if (hours > 0) {
            return c.getString(R.string.callDetailsDurationFormatWithHour,
                    hours, minutes, seconds);
        }
        return c.getString(R.string.callDetailsDurationFormat, minutes, seconds);
    }
}
