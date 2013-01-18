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

import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CallLog.Calls;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import com.android.contacts.ContactPhotoManager;
import com.android.contacts.ContactsUtils;
import com.android.contacts.R;
import com.android.contacts.calllog.CallLogAdapterHelper;
import com.android.contacts.calllog.CallLogAdapterHelper.NumberWithCountryIso;
import com.android.contacts.calllog.ContactInfo;
import com.android.contacts.calllog.ContactInfoHelper;
import com.android.contacts.calllog.PhoneNumberHelper;
import com.android.contacts.util.UriUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * Adapter class to hold and handle call stat entries
 */
class CallStatsAdapter extends ArrayAdapter<CallStatsDetails>
        implements CallLogAdapterHelper.Callback {

    private final View.OnClickListener mPrimaryActionListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            IntentProvider intentProvider = (IntentProvider) view.getTag();
            if (intentProvider != null) {
                mContext.startActivity(intentProvider.getIntent(mContext));
            }
        }
    };

    private static final String TAG = "CallStatsAdapter";
    private final Context mContext;
    private final CallLogAdapterHelper mAdapterHelper;
    private final CallStatsDetailHelper mCallStatsDetailHelper;
    private ArrayList<CallStatsDetails> mList;

    private long mFullDuration = 0;
    private long mFullInDuration = 0;
    private long mFullOutDuration = 0;
    private long mTotalIncomingCount = 0;
    private long mTotalOutgoingCount = 0;
    private long mTotalMissedCount = 0;

    /**
     * Separate list to hold [this]/[sum] percent values for the respective
     * items
     */
    private ArrayList<Float> mPercentageMap = new ArrayList<Float>();
    /**
     * Separate list to hold [this]/[highest] ratio values for the respective
     * items
     */
    private ArrayList<Float> mRatioMap = new ArrayList<Float>();
    private int mType = CallStatsQueryHandler.CALL_TYPE_ALL;
    private long mFilterFrom;
    private long mFilterTo;
    private boolean mSortByDuration;

    private final ContactPhotoManager mContactPhotoManager;

    private final Comparator<CallStatsDetails> mDurationComparator = new Comparator<CallStatsDetails>() {
        @Override
        public int compare(CallStatsDetails o1, CallStatsDetails o2) {
            Long duration1 = o1.getRequestedDuration(mType);
            Long duration2 = o2.getRequestedDuration(mType);
            // sort descending
            return duration2.compareTo(duration1);
        }
    };
    private final Comparator<CallStatsDetails> mCountComparator = new Comparator<CallStatsDetails>() {
        @Override
        public int compare(CallStatsDetails o1, CallStatsDetails o2) {
            Integer count1 = o1.getRequestedCount(mType);
            Integer count2 = o2.getRequestedCount(mType);
            // sort descending
            return count2.compareTo(count1);
        }
    };
     CallStatsAdapter(Context context,
            ContactInfoHelper contactInfoHelper,
            ArrayList<CallStatsDetails> list) {
        super(context, R.layout.call_stats_list_item, R.id.number, list);

        mList = list;
        mContext = context;

        Resources resources = mContext.getResources();
        PhoneNumberHelper phoneNumberHelper = new PhoneNumberHelper(resources);

        mAdapterHelper = new CallLogAdapterHelper(mContext, this,
                contactInfoHelper, phoneNumberHelper);
        mContactPhotoManager = ContactPhotoManager.getInstance(mContext);
        mCallStatsDetailHelper = new CallStatsDetailHelper(resources, phoneNumberHelper);
    }

    private void resetData() {
        mFullDuration = 0;
        mFullInDuration = 0;
        mFullOutDuration = 0;
        mTotalIncomingCount = 0;
        mTotalOutgoingCount = 0;
        mTotalMissedCount = 0;
        mList.clear();
        mPercentageMap.clear();
        mRatioMap.clear();
    }

    public void processCursor(Cursor c, int qType, long from, long to, boolean sortByDuration) {
        final int count = c.getCount();
        mFilterFrom = from;
        mFilterTo = to;
        mType = qType;

        if (count == 0) {
            return;
        }

        resetData();
        c.moveToFirst();
        String firstNumber = c.getString(CallStatsQuery.NUMBER);
        do {
            final String number = c.getString(CallStatsQuery.NUMBER);
            final long duration = c.getLong(CallStatsQuery.DURATION);
            final int callType = c.getInt(CallStatsQuery.CALL_TYPE);

            if (!ContactsUtils.phoneNumbersEqual(firstNumber, number) || mList.isEmpty()) {
                final long date = c.getLong(CallStatsQuery.DATE);
                final String countryIso = c.getString(CallStatsQuery.COUNTRY_ISO);
                final ContactInfo cachedContactInfo = getContactInfoFromCallStats(c);
                final ContactInfo info = mAdapterHelper.lookupContact(
                        number, countryIso, cachedContactInfo);

                final Uri lookupUri = info.lookupUri;
                final String name = info.name;
                final int ntype = info.type;
                final String label = info.label;
                final long photoId = info.photoId;
                final Uri photoUri = info.photoUri;
                CharSequence formattedNumber = info.formattedNumber;
                final String geocode = c.getString(CallStatsQuery.GEOCODED_LOCATION);
                final CallStatsDetails details;

                if (TextUtils.isEmpty(name)) {
                    details = new CallStatsDetails(number, formattedNumber,
                            countryIso, geocode, date);
                } else {
                    details = new CallStatsDetails(number, formattedNumber,
                            countryIso, geocode, date, name, ntype, label,
                            lookupUri, photoUri, photoId);
                }

                details.addTimeOrMissed(callType, duration);
                mList.add(details);
                firstNumber = number;
            } else {
                mList.get(mList.size() - 1).addTimeOrMissed(callType, duration);
            }
            switch (callType) {
                case Calls.INCOMING_TYPE:
                    mTotalIncomingCount++;
                    mFullInDuration += duration;
                    break;
                case Calls.OUTGOING_TYPE:
                    mTotalOutgoingCount++;
                    mFullOutDuration += duration;
                    break;
                case Calls.MISSED_TYPE:
                    mTotalMissedCount++;
                    break;
            }
            mFullDuration += duration;
        } while (c.moveToNext());

        mergeByNumberAndRemoveZeros();
        sort(sortByDuration);
    }

    public void sort(boolean sortByDuration) {
        if (mList.isEmpty()) {
            return;
        }

        mRatioMap.clear();
        mPercentageMap.clear();
        mSortByDuration = sortByDuration;
        Collections.sort(mList, sortByDuration ? mDurationComparator : mCountComparator);

        float totalValue = (float) (sortByDuration ? getTotalDuration() : getTotalCount());
        float firstValue = Float.NaN;

        for (CallStatsDetails item : mList) {
            float value = sortByDuration
                    ? (float) item.getRequestedDuration(mType)
                    : (float) item.getRequestedCount(mType);
            if (Float.isNaN(firstValue)) firstValue = value;

            float ratio = value / firstValue;
            mRatioMap.add(ratio);
            mPercentageMap.add(value * 100F / totalValue);
        }

        notifyDataSetChanged();
    }

    private void mergeByNumberAndRemoveZeros() {
        CallStatsDetails outerItem;
        // temporarily store items marked for removal
        ArrayList<CallStatsDetails> toRemove = new ArrayList<CallStatsDetails>();

        // numbers in non-international format will be the first
        for (int i = 0; i < mList.size(); i++) {
            outerItem = mList.get(i);
            if (outerItem.getRequestedDuration(mType) == 0) {
                toRemove.add(outerItem); // nothing to merge, remove zero item
                continue;
            }
            if (outerItem.number.toString().startsWith("+")) {
                continue; // we don't check numbers starting with +, only removing from this point
            }
            String currentFormattedNumber = outerItem.number.toString();
            for (int j = mList.size() - 1; j > i; j--) {
                final CallStatsDetails innerItem = mList.get(j);
                final String innerNumber = innerItem.number.toString();

                if (ContactsUtils.phoneNumbersEqual(currentFormattedNumber, innerNumber)
                        || !innerNumber.startsWith("+")) {
                    innerItem.mergeWith(outerItem);
                    toRemove.add(outerItem);
                    break; // we don't have multiple items with the same number, stop
                }
            }
        }
        for (CallStatsDetails bye : toRemove) {
            mList.remove(bye);
        }
    }

    private long getTotalDuration() {
        switch (mType) {
            case CallStatsQueryHandler.CALL_TYPE_ALL:
                return mFullDuration;
            case Calls.INCOMING_TYPE:
                return mFullInDuration;
            case Calls.OUTGOING_TYPE:
                return mFullOutDuration;
            case Calls.MISSED_TYPE:
                return mTotalMissedCount;
        }
        return 0;
    }

    private long getTotalCount() {
        switch (mType) {
            case CallStatsQueryHandler.CALL_TYPE_ALL:
                return mTotalIncomingCount + mTotalOutgoingCount + mTotalMissedCount;
            case Calls.INCOMING_TYPE:
                return mTotalIncomingCount;
            case Calls.OUTGOING_TYPE:
                return mTotalOutgoingCount;
            case Calls.MISSED_TYPE:
                return mTotalMissedCount;
        }
        return 0;
    }

    public void stopRequestProcessing() {
        mAdapterHelper.stopRequestProcessing();
    }

    public String getBetterNumberFromContacts(String number, String countryIso) {
        return mAdapterHelper.getBetterNumberFromContacts(number, countryIso);
    }

    public void invalidateCache() {
        mAdapterHelper.invalidateCache();
    }

    public String getTotalCallCountString() {
        long callCount = 0;

        switch (mType) {
            case CallStatsQueryHandler.CALL_TYPE_ALL:
                callCount = mTotalIncomingCount + mTotalOutgoingCount + mTotalMissedCount;
                break;
            case Calls.INCOMING_TYPE:
                callCount = mTotalIncomingCount;
                break;
            case Calls.OUTGOING_TYPE:
                callCount = mTotalOutgoingCount;
                break;
            case Calls.MISSED_TYPE:
                callCount = mTotalMissedCount;
                break;
        }

        return CallStatsDetailHelper.getCallCountString(mContext.getResources(), callCount);
    }

    public String getFullDurationString(boolean withSeconds) {
        long duration;

        switch (mType) {
            case CallStatsQueryHandler.CALL_TYPE_ALL:
                duration = mFullDuration;
                break;
            case Calls.INCOMING_TYPE:
                duration = mFullInDuration;
                break;
            case Calls.OUTGOING_TYPE:
                duration = mFullOutDuration;
                break;
            default:
                return null;
        }

        return CallStatsDetailHelper.getDurationString(
                mContext.getResources(), duration, withSeconds);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater)
                mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View v = convertView;
        if (v == null) {
            v = inflater.inflate(R.layout.call_stats_list_item, parent, false);
        }
        findAndCacheViews(v);
        bindView(position, v);

        return v;
    }

    private void bindView(int position, View v) {
        final CallStatsListItemViews views = (CallStatsListItemViews) v.getTag();
        CallStatsDetails details = mList.get(position);
        final float percent = mPercentageMap.get(position);
        final float ratio = mRatioMap.get(position);

        views.primaryActionView.setVisibility(View.VISIBLE);
        views.primaryActionView.setTag(IntentProvider.getCallStatsDetailIntentProvider(
                details, mFilterFrom, mFilterTo, mSortByDuration));

        mCallStatsDetailHelper.setCallStatsDetails(views.callStatsDetailViews,
                details, mType, mSortByDuration, percent, ratio);
        setPhoto(views, details.photoId, details.contactUri);

        // Listen for the first draw
        mAdapterHelper.registerOnPreDrawListener(v);
    }

    private void findAndCacheViews(View view) {
        CallStatsListItemViews views = CallStatsListItemViews.fromView(view);
        views.primaryActionView.setOnClickListener(mPrimaryActionListener);
        view.setTag(views);
    }

    private void setPhoto(CallStatsListItemViews views, long photoId, Uri contactUri) {
        views.quickContactView.assignContactUri(contactUri);
        mContactPhotoManager.loadThumbnail(views.quickContactView, photoId, true);
    }

    private ContactInfo getContactInfoFromCallStats(Cursor c) {
        ContactInfo info = new ContactInfo();
        info.lookupUri = UriUtils.parseUriOrNull(c.getString(CallStatsQuery.CACHED_LOOKUP_URI));
        info.name = c.getString(CallStatsQuery.CACHED_NAME);
        info.type = c.getInt(CallStatsQuery.CACHED_NUMBER_TYPE);
        info.label = c.getString(CallStatsQuery.CACHED_NUMBER_LABEL);
        String matchedNumber = c.getString(CallStatsQuery.CACHED_MATCHED_NUMBER);
        info.number = matchedNumber == null ? c.getString(CallStatsQuery.NUMBER) : matchedNumber;
        info.normalizedNumber = c.getString(CallStatsQuery.CACHED_NORMALIZED_NUMBER);
        info.photoId = c.getLong(CallStatsQuery.CACHED_PHOTO_ID);
        info.photoUri = null; // We do not cache the photo URI.
        info.formattedNumber = c.getString(CallStatsQuery.CACHED_FORMATTED_NUMBER);
        return info;
    }

    @Override
    public void dataSetChanged() {
        notifyDataSetChanged();
    }

    @Override
    public void updateContactInfo(String number, String countryIso,
            ContactInfo updatedInfo, ContactInfo callLogInfo) {
    }
}
