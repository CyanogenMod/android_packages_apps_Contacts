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

    public CallStatsDetailHelper(Resources resources,
            PhoneNumberHelper phoneNumberHelper) {
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
                numberText = mResources
                        .getString(R.string.call_log_empty_gecode);
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
            case 0:
                inPercent = ratio
                        * ((float) details.inDuration / (float) details
                                .getFullDuration());
                outPercent = ratio
                        * ((float) details.outDuration / (float) details
                                .getFullDuration());
                views.barView.redIsTheNewBlue(false);
                break;
            case 1:
                inPercent = ratio;
                views.barView.redIsTheNewBlue(false);
                break;
            case 2:
                outPercent = ratio;
                views.barView.redIsTheNewBlue(false);
                break;
            case 3:
                // small cheat here
                inPercent = ratio;
                views.barView.redIsTheNewBlue(true);
                break;
        }

        views.barView.setRatios(inPercent, outPercent, 1.0f - inPercent
                - outPercent);
        views.nameView.setText(nameText);
        views.numberView.setText(numberText);
        views.labelView.setText(labelText);
        views.labelView.setVisibility(TextUtils.isEmpty(labelText) ? View.GONE
                : View.VISIBLE);
        if (type < 3) {
            views.percentView.setText(String.format("%.1f%%", percent));
        } else {
            views.percentView.setText(mResources.getQuantityString(
                    R.plurals.call, details.missedCount, details.missedCount));
        }
    }

    public void setCallStatsDetailHeader(TextView nameView,
            CallStatsDetails details) {
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

    public static String getDurationString(Context c, int type, long duration) {
        long elapsed = duration;
        if (type == 3) {
            return c.getResources().getQuantityString(R.plurals.call,
                    (int) elapsed, (int) elapsed);
        }

        long hours = 0;
        long minutes = 0;
        long seconds = 0;

        if (elapsed >= 3600) {
            hours = elapsed / 3600;
            elapsed -= hours * 3600;
        }

        if (elapsed >= 60) {
            minutes = elapsed / 60;
            elapsed -= minutes * 60;
        }
        seconds = elapsed;

        return c.getResources().getString(R.string.callDetailsDurationFormat,
                hours, minutes, seconds);
    }
}
