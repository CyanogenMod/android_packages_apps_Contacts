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

import android.content.ContentValues;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract.PhoneLookup;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewTreeObserver;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import com.android.contacts.ContactPhotoManager;
import com.android.contacts.R;
import com.android.contacts.calllog.PhoneNumberHelper;
import com.android.contacts.calllog.ContactInfo;
import com.android.contacts.calllog.PhoneQuery;
import com.android.contacts.calllog.ContactInfoHelper;
import com.android.contacts.util.ExpirableCache;
import com.android.contacts.util.UriUtils;

import com.google.common.base.Objects;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * Adapter class to hold and handle call stat entries
 */
class CallStatsAdapter extends ArrayAdapter<CallStatsDetails> implements
        ViewTreeObserver.OnPreDrawListener {
    public interface CallFetcher {
        public void fetchCalls(long from, long to);
    }

    private final View.OnClickListener mPrimaryActionListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            IntentProvider intentProvider = (IntentProvider) view.getTag();
            if (intentProvider != null) {
                mContext.startActivity(intentProvider.getIntent(mContext));
            }
        }
    };

    private static final class NumberWithCountryIso {
        public final String number;
        public final String countryIso;

        public NumberWithCountryIso(String number, String countryIso) {
            this.number = number;
            this.countryIso = countryIso;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null)
                return false;
            if (!(o instanceof NumberWithCountryIso))
                return false;
            NumberWithCountryIso other = (NumberWithCountryIso) o;
            return TextUtils.equals(number, other.number)
                    && TextUtils.equals(countryIso, other.countryIso);
        }

        @Override
        public int hashCode() {
            return (number == null ? 0 : number.hashCode())
                    ^ (countryIso == null ? 0 : countryIso.hashCode());
        }
    }

    private static final class ContactInfoRequest {
        public final String number;
        public final String countryIso;
        public final ContactInfo callStatsInfo;

        public ContactInfoRequest(String number, String countryIso,
                ContactInfo callStatsInfo) {
            this.number = number;
            this.countryIso = countryIso;
            this.callStatsInfo = callStatsInfo;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (!(obj instanceof ContactInfoRequest))
                return false;

            ContactInfoRequest other = (ContactInfoRequest) obj;

            if (!TextUtils.equals(number, other.number))
                return false;
            if (!TextUtils.equals(countryIso, other.countryIso))
                return false;
            if (!Objects.equal(callStatsInfo, other.callStatsInfo))
                return false;

            return true;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result
                    + ((callStatsInfo == null) ? 0 : callStatsInfo.hashCode());
            result = prime * result
                    + ((countryIso == null) ? 0 : countryIso.hashCode());
            result = prime * result
                    + ((number == null) ? 0 : number.hashCode());
            return result;
        }
    }

    private class QueryThread extends Thread {
        private volatile boolean mDone = false;

        public QueryThread() {
            super("CallStatsAdapter.QueryThread");
        }

        public void stopProcessing() {
            mDone = true;
        }

        @Override
        public void run() {
            boolean needRedraw = false;
            while (true) {
                // Check if thread is finished, and if so return immediately.
                if (mDone)
                    return;

                // Obtain next request, if any is available.
                // Keep synchronized section small.
                ContactInfoRequest req = null;
                synchronized (mRequests) {
                    if (!mRequests.isEmpty()) {
                        req = mRequests.removeFirst();
                    }
                }

                if (req != null) {
                    // Process the request. If the lookup succeeds, schedule a
                    // redraw.
                    needRedraw |= queryContactInfo(req.number, req.countryIso,
                            req.callStatsInfo);
                } else {
                    // Throttle redraw rate by only sending them when there are
                    // more requests.
                    if (needRedraw) {
                        needRedraw = false;
                        mHandler.sendEmptyMessage(REDRAW);
                    }

                    // Wait until another request is available, or until this
                    // thread is no longer needed (as indicated by being
                    // interrupted).
                    try {
                        synchronized (mRequests) {
                            mRequests.wait(1000);
                        }
                    } catch (InterruptedException ie) {
                        // Ignore, and attempt to continue processing requests.
                    }
                }
            }
        }
    }

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case REDRAW:
                    notifyDataSetChanged();
                    break;
                case START_THREAD:
                    startRequestProcessing();
                    break;
            }
        }
    };

    private static final String TAG = "CallStatsAdapter";
    private final Context mContext;
    private final ContactInfoHelper mContactInfoHelper;
    private final CallStatsDetailHelper mCallStatsDetailHelper;
    private final CallFetcher mCallFetcher;
    private ViewTreeObserver mViewTreeObserver = null;
    private ArrayList<CallStatsDetails> mList;
    private long mFullDuration = 0;
    private long mFullInDuration = 0;
    private long mFullOutDuration = 0;
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
    private int mType = 0;
    private long mFilterFrom;
    private long mFilterTo;

    private final ContactPhotoManager mContactPhotoManager;
    private PhoneNumberHelper mPhoneNumberHelper;

    private volatile boolean mRequestProcessingDisabled = false;
    private QueryThread mCallerIdThread;
    private static boolean sShouldProcess = false;

    private final LinkedList<ContactInfoRequest> mRequests;
    private ExpirableCache<NumberWithCountryIso, ContactInfo> mContactInfoCache;

    private static final int START_PROCESSING_REQUESTS_DELAY_MILLIS = 1000;
    private static final int CONTACT_INFO_CACHE_SIZE = 100;

    private static final int REDRAW = 1;
    private static final int START_THREAD = 2;

    CallStatsAdapter(Context context, CallFetcher callFetcher,
            ContactInfoHelper contactInfoHelper,
            ArrayList<CallStatsDetails> list) {
        super(context, R.layout.call_stats_list_item, R.id.number, list);

        mList = list;
        mContext = context;
        mCallFetcher = callFetcher;
        mContactInfoHelper = contactInfoHelper;

        mContactInfoCache = ExpirableCache.create(CONTACT_INFO_CACHE_SIZE);
        mRequests = new LinkedList<ContactInfoRequest>();

        Resources resources = mContext.getResources();

        mContactPhotoManager = ContactPhotoManager.getInstance(mContext);
        mPhoneNumberHelper = new PhoneNumberHelper(resources);
        mCallStatsDetailHelper = new CallStatsDetailHelper(
                resources, mPhoneNumberHelper);
    }

    private void resetData() {
        mFullDuration = 0;
        mFullInDuration = 0;
        mFullOutDuration = 0;
        mTotalMissedCount = 0;
        mList.clear();
        mPercentageMap.clear();
        mRatioMap.clear();
    }

    public void processCursor(Cursor c, int qType, long from, long to) {
        final int count = c.getCount();
        mFilterFrom = from;
        mFilterTo = to;
        if (count == 0) {
            return;
        }
        mType = qType;
        resetData();
        c.moveToFirst();
        String firstNumber = c.getString(CallStatsQuery.NUMBER);
        do {
            final String number = c.getString(CallStatsQuery.NUMBER);
            final long duration = c.getLong(CallStatsQuery.DURATION);
            final int callType = c.getInt(CallStatsQuery.CALL_TYPE);

            if (!equalNumbers(firstNumber, number) || mList.isEmpty()) {
                final long date = c.getLong(CallStatsQuery.DATE);
                final String countryIso = c
                        .getString(CallStatsQuery.COUNTRY_ISO);
                final ContactInfo cachedContactInfo = getContactInfoFromCallStats(c);

                // Lookup contacts with this number
                NumberWithCountryIso numberCountryIso = new NumberWithCountryIso(
                        number, countryIso);
                ExpirableCache.CachedValue<ContactInfo> cachedInfo = mContactInfoCache
                        .getCachedValue(numberCountryIso);
                ContactInfo info = cachedInfo == null ? null : cachedInfo
                        .getValue();
                if (!mPhoneNumberHelper.canPlaceCallsTo(number)
                        || mPhoneNumberHelper.isVoicemailNumber(number)) {
                    // If this is a number that cannot be dialed, there is no
                    // point in looking up a contact for it.
                    info = ContactInfo.EMPTY;
                } else if (cachedInfo == null) {
                    mContactInfoCache.put(numberCountryIso, ContactInfo.EMPTY);
                    info = cachedContactInfo;
                    // The db request should happen on a non-UI thread.
                    // Request the contact details immediately since they are
                    // currently missing.
                    enqueueRequest(number, countryIso, cachedContactInfo, true);
                    // We will format the phone number when we make the
                    // background request.
                } else {
                    if (cachedInfo.isExpired()) {
                        // The contact info is no longer up to date, we should
                        // request it. However, we
                        // do not need to request them immediately.
                        enqueueRequest(number, countryIso, cachedContactInfo,
                                false);
                    } else if (!callStatsInfoMatches(cachedContactInfo, info)) {
                        enqueueRequest(number, countryIso, cachedContactInfo,
                                false);
                    }

                    if (info == ContactInfo.EMPTY) {
                        // Use the cached contact info
                        info = cachedContactInfo;
                    }
                }

                final Uri lookupUri = info.lookupUri;
                final String name = info.name;
                final int ntype = info.type;
                final String label = info.label;
                final long photoId = info.photoId;
                final Uri photoUri = info.photoUri;
                CharSequence formattedNumber = info.formattedNumber;
                final String geocode = c
                        .getString(CallStatsQuery.GEOCODED_LOCATION);
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
                    mFullInDuration += duration;
                    break;
                case Calls.OUTGOING_TYPE:
                    mFullOutDuration += duration;
                    break;
                case Calls.MISSED_TYPE:
                    mTotalMissedCount++;
                    break;
            }
            mFullDuration += duration;
        } while (c.moveToNext());

        mergeByNumberAndRemoveZeros();

        Collections.sort(mList, new Comparator<CallStatsDetails>() {
            public int compare(CallStatsDetails o1, CallStatsDetails o2) {
                return (o1.getRequestedDuration(mType) > o2.getRequestedDuration(mType)
                        ? -1 : (o1.getRequestedDuration(mType) == o2.getRequestedDuration(mType)
                                ? 0 : 1));
            }
        });
        mapPercentagesAndRatios();
        notifyDataSetChanged();
    }

    private void mergeByNumberAndRemoveZeros() {
        CallStatsDetails innerItem;
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
            if (((String) outerItem.number).startsWith("+")) {
                continue; // we don't check numbers starting with +, only removing from this point
            }
            String currentFormattedNumber = (String) outerItem.number;
            for (int j = mList.size() - 1; j > i; j--) {
                innerItem = mList.get(j);
                if (equalNumbers(currentFormattedNumber, (String) innerItem.number)
                        || !((String) innerItem.number).startsWith("+")) {
                    mList.get(j).mergeWith(mList.get(i));
                    toRemove.add(outerItem);
                    break; // we don't have multiple items with the same number, stop
                }
            }
        }
        for (CallStatsDetails bye : toRemove) {
            mList.remove(bye);
        }
    }

    private void mapPercentagesAndRatios() {
        long fullDuration = 0;
        switch (mType) {
            case 0:
                fullDuration = mFullDuration;
                break;
            case 1:
                fullDuration = mFullInDuration;
                break;
            case 2:
                fullDuration = mFullOutDuration;
                break;
            case 3:
                fullDuration = mTotalMissedCount;
        }
        for (CallStatsDetails item : mList) {
            float duration = (float) item.getRequestedDuration(mType);
            float ratio = duration
                    / (float) mList.get(0).getRequestedDuration(mType);
            mRatioMap.add(ratio);
            mPercentageMap.add((duration / (float) fullDuration) * 100);
        }
    }

    public String getFullDurationString() {
        switch (mType) {
            case 0:
                return CallStatsDetailHelper.getDurationString(mContext, mType,
                        mFullDuration);
            case 1:
                return CallStatsDetailHelper.getDurationString(mContext, mType,
                        mFullInDuration);
            case 2:
                return CallStatsDetailHelper.getDurationString(mContext, mType,
                        mFullOutDuration);
            case 3:
                return CallStatsDetailHelper.getDurationString(mContext, mType,
                        mTotalMissedCount);
        }
        return null; // should not happen
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) mContext
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View v = convertView;
        if (v == null) {
            v = inflater.inflate(R.layout.call_stats_list_item, parent, false);
        }
        findAndCacheViews(v);
        bindView(position, v);

        return v;
    }

    private void bindView(int position, View v) {
        final CallStatsListItemViews views = (CallStatsListItemViews) v
                .getTag();

        CallStatsDetails details = mList.get(position);
        views.primaryActionView.setVisibility(View.VISIBLE);
        final float percent = mPercentageMap.get(position);
        final float ratio = mRatioMap.get(position);

        views.primaryActionView.setTag(IntentProvider
                .getCallStatsDetailIntentProvider(details, mFilterFrom, mFilterTo));

        mCallStatsDetailHelper.setCallStatsDetails(views.callStatsDetailViews,
                details, mType, percent, ratio);
        setPhoto(views, details.photoId, details.contactUri);

        // Listen for the first draw
        if (mViewTreeObserver == null) {
            mViewTreeObserver = v.getViewTreeObserver();
            mViewTreeObserver.addOnPreDrawListener(this);
        }
    }

    private void findAndCacheViews(View view) {
        CallStatsListItemViews views = CallStatsListItemViews.fromView(view);
        views.primaryActionView.setOnClickListener(mPrimaryActionListener);
        view.setTag(views);
    }

    @Override
    public boolean onPreDraw() {
        // We only wanted to listen for the first draw (and this is it).
        unregisterPreDrawListener();

        // Only schedule a thread-creation message if the thread hasn't been
        // created yet. This is purely an optimization, to queue fewer messages.
        if (mCallerIdThread == null && sShouldProcess) {
            mHandler.sendEmptyMessageDelayed(START_THREAD,
                    START_PROCESSING_REQUESTS_DELAY_MILLIS);
        }

        return true;
    }

    private void unregisterPreDrawListener() {
        if (mViewTreeObserver != null && mViewTreeObserver.isAlive()) {
            mViewTreeObserver.removeOnPreDrawListener(this);
        }
        mViewTreeObserver = null;
    }

    void enqueueRequest(String number, String countryIso,
            ContactInfo callStatsInfo, boolean immediate) {
        ContactInfoRequest request = new ContactInfoRequest(number, countryIso,
                callStatsInfo);
        synchronized (mRequests) {
            if (!mRequests.contains(request)) {
                mRequests.add(request);
                mRequests.notifyAll();
            }
        }
        sShouldProcess = true;
        if (immediate) {
            startRequestProcessing();
        }
    }

    private void setPhoto(CallStatsListItemViews views, long photoId,
            Uri contactUri) {
        views.quickContactView.assignContactUri(contactUri);
        mContactPhotoManager.loadThumbnail(views.quickContactView, photoId,
                true);
    }

    private boolean isLastOfSection(Cursor c) {
        if (c.isLast())
            return true;
        if (!c.moveToNext())
            return true;
        c.moveToPrevious();
        return false;
    }

    private boolean queryContactInfo(String number, String countryIso,
            ContactInfo callStatsInfo) {
        final ContactInfo info = mContactInfoHelper.lookupNumber(number,
                countryIso);

        if (info == null) {
            // The lookup failed, just return without requesting to update the
            // view.
            return false;
        }

        // Check the existing entry in the cache: only if it has changed we
        // should update the
        // view.
        NumberWithCountryIso numberCountryIso = new NumberWithCountryIso(
                number, countryIso);
        ContactInfo existingInfo = mContactInfoCache
                .getPossiblyExpired(numberCountryIso);
        boolean updated = (existingInfo != ContactInfo.EMPTY)
                && !info.equals(existingInfo);

        // Store the data in the cache so that the UI thread can use to display
        // it. Store it
        // even if it has not changed so that it is marked as not expired.
        mContactInfoCache.put(numberCountryIso, info);
        return updated;
    }

    private boolean callStatsInfoMatches(ContactInfo callStatsInfo,
            ContactInfo info) {
        return TextUtils.equals(callStatsInfo.name, info.name)
                && callStatsInfo.type == info.type
                && TextUtils.equals(callStatsInfo.label, info.label);
    }

    /**
     * Starts a background thread to process contact-lookup requests, unless one
     * has already been started.
     */
    private synchronized void startRequestProcessing() {
        if (mRequestProcessingDisabled)
            return;

        if (mCallerIdThread != null)
            return;

        mCallerIdThread = new QueryThread();
        mCallerIdThread.setPriority(Thread.MIN_PRIORITY);
        mCallerIdThread.start();
        sShouldProcess = true;
    }

    /**
     * Stops the background thread that processes updates and cancels any
     * pending requests to start it.
     */
    public synchronized void stopRequestProcessing() {
        // Remove any pending requests to start the processing thread.
        mHandler.removeMessages(START_THREAD);
        if (mCallerIdThread != null) {
            // Stop the thread; we are finished with it.
            mCallerIdThread.stopProcessing();
            mCallerIdThread.interrupt();
            mCallerIdThread = null;
            sShouldProcess = false;
        }
    }

    private ContactInfo getContactInfoFromCallStats(Cursor c) {
        ContactInfo info = new ContactInfo();
        info.lookupUri = UriUtils.parseUriOrNull(c
                .getString(CallStatsQuery.CACHED_LOOKUP_URI));
        info.name = c.getString(CallStatsQuery.CACHED_NAME);
        info.type = c.getInt(CallStatsQuery.CACHED_NUMBER_TYPE);
        info.label = c.getString(CallStatsQuery.CACHED_NUMBER_LABEL);
        String matchedNumber = c
                .getString(CallStatsQuery.CACHED_MATCHED_NUMBER);
        info.number = matchedNumber == null ? c
                .getString(CallStatsQuery.NUMBER) : matchedNumber;
        info.normalizedNumber = c
                .getString(CallStatsQuery.CACHED_NORMALIZED_NUMBER);
        info.photoId = c.getLong(CallStatsQuery.CACHED_PHOTO_ID);
        info.photoUri = null; // We do not cache the photo URI.
        info.formattedNumber = c
                .getString(CallStatsQuery.CACHED_FORMATTED_NUMBER);
        return info;
    }

    public String getBetterNumberFromContacts(String number, String countryIso) {
        String matchingNumber = null;
        // Look in the cache first. If it's not found then query the Phones db
        NumberWithCountryIso numberCountryIso = new NumberWithCountryIso(
                number, countryIso);
        ContactInfo ci = mContactInfoCache.getPossiblyExpired(numberCountryIso);
        if (ci != null && ci != ContactInfo.EMPTY) {
            matchingNumber = ci.number;
        } else {
            try {
                Cursor phonesCursor = mContext.getContentResolver().query(
                        Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI,
                                number), PhoneQuery._PROJECTION, null, null,
                        null);
                if (phonesCursor != null) {
                    if (phonesCursor.moveToFirst()) {
                        matchingNumber = phonesCursor
                                .getString(PhoneQuery.MATCHED_NUMBER);
                    }
                    phonesCursor.close();
                }
            } catch (Exception e) {
                // Use the number from the call log
            }
        }
        if (!TextUtils.isEmpty(matchingNumber)
                && (matchingNumber.startsWith("+") || matchingNumber.length() > number
                        .length())) {
            number = matchingNumber;
        }
        return number;
    }

    public void invalidateCache() {
        mContactInfoCache.expireAll();

        // Restart the request-processing thread after the next draw.
        stopRequestProcessing();
        unregisterPreDrawListener();
    }

    boolean equalNumbers(String number1, String number2) {
        if (PhoneNumberUtils.isUriNumber(number1)
                || PhoneNumberUtils.isUriNumber(number2)) {
            return compareSipAddresses(number1, number2);
        } else {
            return PhoneNumberUtils.compare(number1, number2);
        }
    }

    boolean compareSipAddresses(String number1, String number2) {
        if (number1 == null || number2 == null) {
            return number1 == number2;
        }

        int index1 = number1.indexOf('@');
        final String userinfo1;
        final String rest1;
        if (index1 != -1) {
            userinfo1 = number1.substring(0, index1);
            rest1 = number1.substring(index1);
        } else {
            userinfo1 = number1;
            rest1 = "";
        }

        int index2 = number2.indexOf('@');
        final String userinfo2;
        final String rest2;
        if (index2 != -1) {
            userinfo2 = number2.substring(0, index2);
            rest2 = number2.substring(index2);
        } else {
            userinfo2 = number2;
            rest2 = "";
        }

        return userinfo1.equals(userinfo2) && rest1.equalsIgnoreCase(rest2);
    }
}
