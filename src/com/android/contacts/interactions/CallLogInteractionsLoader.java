/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.contacts.interactions;

import android.content.AsyncTaskLoader;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.Uri;
import android.provider.CallLog.Calls;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;

import com.android.contacts.incall.InCallPluginHelper;
import com.android.phone.common.incall.utils.CallMethodFilters;
import com.google.common.annotations.VisibleForTesting;

import com.android.contacts.common.util.PermissionsUtil;
import com.android.phone.common.incall.CallMethodInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

public class CallLogInteractionsLoader extends AsyncTaskLoader<List<ContactInteraction>> {
    private final Context mContext;
    private final String[] mPhoneNumbers;
    private final int mMaxToRetrieve;
    private List<ContactInteraction> mData;
    private HashMap<ComponentName, List<String>> mPluginAccountsMap;

    public CallLogInteractionsLoader(Context context, String[] phoneNumbers, HashMap<ComponentName,
                List<String>> pluginAccountsMap, int maxToRetrieve) {
        super(context);
        mContext = context;
        mPhoneNumbers = phoneNumbers;
        mPluginAccountsMap = pluginAccountsMap;
        mMaxToRetrieve = maxToRetrieve;
    }

    @Override
    public List<ContactInteraction> loadInBackground() {
        if (!PermissionsUtil.hasPhonePermissions(getContext()) || !getContext().getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
                || (((mPhoneNumbers == null || mPhoneNumbers.length <= 0 || mMaxToRetrieve <= 0))
                && (mPluginAccountsMap == null || mPluginAccountsMap.size() == 0))) {
            return Collections.emptyList();
        }

        final List<ContactInteraction> interactions = new ArrayList<>();
        if (mPhoneNumbers != null) {
            for (String number : mPhoneNumbers) {
                interactions.addAll(getCallLogInteractions(number, null));
            }
        }
        // add plugin entries
        if (InCallPluginHelper.infoReady()) {
            HashMap<ComponentName, CallMethodInfo> inCallPlugins = CallMethodFilters
                    .getAllEnabledCallMethods((getContext()));
            if (inCallPlugins != null) {
                for (ComponentName cn : inCallPlugins.keySet()) {
                    List<String> accountList = mPluginAccountsMap.get(cn);
                    CallMethodInfo cmi = inCallPlugins.get(cn);
                    if (cmi == null) continue;
                    if (accountList == null || cmi == null) continue;
                    for (int i = 0; i < accountList.size(); i++) {
                        interactions.addAll(getCallLogInteractions(accountList.get(i), cmi));
                    }
                }
            }
        }
        // Sort the call log interactions by date for duplicate removal
        Collections.sort(interactions, new Comparator<ContactInteraction>() {
            @Override
            public int compare(ContactInteraction i1, ContactInteraction i2) {
                if (i2.getInteractionDate() - i1.getInteractionDate() > 0) {
                    return 1;
                } else if (i2.getInteractionDate() == i1.getInteractionDate()) {
                    return 0;
                } else {
                    return -1;
                }
            }
        });
        // Duplicates only occur because of fuzzy matching. No need to dedupe a single number.
        if (interactions.size() == 1) {
            return interactions;
        }
        return pruneDuplicateCallLogInteractions(interactions, mMaxToRetrieve);
    }

    /**
     * Two different phone numbers can match the same call log entry (since phone number
     * matching is inexact). Therefore, we need to remove duplicates. In a reasonable call log,
     * every entry should have a distinct date. Therefore, we can assume duplicate entries are
     * adjacent entries.
     * @param interactions The interaction list potentially containing duplicates
     * @return The list with duplicates removed
     */
    @VisibleForTesting
    static List<ContactInteraction> pruneDuplicateCallLogInteractions(
            List<ContactInteraction> interactions, int maxToRetrieve) {
        final List<ContactInteraction> subsetInteractions = new ArrayList<>();
        for (int i = 0; i < interactions.size(); i++) {
            if (i >= 1 && interactions.get(i).getInteractionDate() ==
                    interactions.get(i-1).getInteractionDate()) {
                continue;
            }
            subsetInteractions.add(interactions.get(i));
            if (subsetInteractions.size() >= maxToRetrieve) {
                break;
            }
        }
        return subsetInteractions;
    }

    private List<ContactInteraction> getCallLogInteractions(String phoneNumber, CallMethodInfo
            cmi) {
        // TODO: the phone number added to the ContactInteractions result should retain their
        // original formatting since TalkBack is not reading the normalized number correctly
        String pluginComponent = cmi == null ? "" : cmi.mComponent.flattenToString();
        final String normalizedNumber = TextUtils.isEmpty(pluginComponent) ?
                PhoneNumberUtils.normalizeNumber(phoneNumber) : phoneNumber;
        // If the number contains only symbols, we can skip it
        if (TextUtils.isEmpty(normalizedNumber)) {
            return Collections.emptyList();
        }
        final Uri uri = Uri.withAppendedPath(Calls.CONTENT_FILTER_URI,
                Uri.encode(normalizedNumber));
        // Append the LIMIT clause onto the ORDER BY clause. This won't cause crashes as long
        // as we don't also set the {@link android.provider.CallLog.Calls.LIMIT_PARAM_KEY} that
        // becomes available in KK.
        final String orderByAndLimit = Calls.DATE + " DESC LIMIT " + mMaxToRetrieve;
        final Cursor cursor = getContext().getContentResolver().query(uri, null, null, null,
                orderByAndLimit);
        try {
            if (cursor == null || cursor.getCount() < 1) {
                return Collections.emptyList();
            }
            cursor.moveToPosition(-1);
            List<ContactInteraction> interactions = new ArrayList<>();
            while (cursor.moveToNext()) {
                final ContentValues values = new ContentValues();
                DatabaseUtils.cursorRowToContentValues(cursor, values);
                CallLogInteraction interaction = new CallLogInteraction(values);
                // loadInBackground calls this function twice
                // First pass: argument phoneNumber: PSTN number, pluginComponent: null
                // (if the PSTN number was dialed through a plugin, the queried cursor entry should
                // contain the plugin component in the "plugin_package_name" column)
                // Second pass: argument phoneNumber: plugin user handle, pluginComponent: valid
                if ((TextUtils.isEmpty(pluginComponent) &&
                        !TextUtils.isEmpty(interaction.getPluginPkgName())) ||
                        (!TextUtils.isEmpty(pluginComponent) &&
                                TextUtils.equals(interaction.getPluginPkgName(), pluginComponent)))
                {
                    // PSTN dialed through a plugin
                    if (cmi == null) {
                        cmi = InCallPluginHelper.INCALL.get(mContext).getModIfExists(ComponentName
                                .unflattenFromString(interaction.getPluginPkgName()));
                    }
                    // No matching plugin, skip
                    if (cmi == null) continue;
                    interaction.setPluginInfo(mContext, cmi.mBrandIconId, cmi.mName);
                }
                interactions.add(interaction);
            }
            return interactions;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    @Override
    protected void onStartLoading() {
        super.onStartLoading();

        if (mData != null) {
            deliverResult(mData);
        }

        if (takeContentChanged() || mData == null) {
            forceLoad();
        }
    }

    @Override
    protected void onStopLoading() {
        // Attempt to cancel the current load task if possible.
        cancelLoad();
    }

    @Override
    public void deliverResult(List<ContactInteraction> data) {
        mData = data;
        if (isStarted()) {
            super.deliverResult(data);
        }
    }

    @Override
    protected void onReset() {
        super.onReset();

        // Ensure the loader is stopped
        onStopLoading();
        if (mData != null) {
            mData.clear();
        }
    }
}