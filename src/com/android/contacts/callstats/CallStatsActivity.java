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

import android.app.ActionBar;
import android.app.ListActivity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract;
import android.telephony.PhoneNumberUtils;
import android.text.format.DateUtils;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.android.contacts.ContactsUtils;
import com.android.contacts.R;
import com.android.contacts.activities.DialtactsActivity;
import com.android.contacts.calllog.ContactInfoHelper;
import com.android.contacts.util.Constants;
import com.android.internal.telephony.CallerInfo;

import java.util.ArrayList;
import java.util.List;

public class CallStatsActivity extends ListActivity implements
        CallStatsQueryHandler.Listener, ActionBar.OnNavigationListener {
    private static final String TAG = "CallStatsActivity";

    private ArrayList<CallStatsDetails> mList = new ArrayList<CallStatsDetails>();
    private static final int[] CALL_DIRECTION_RESOURCES = new int[] {
        R.drawable.ic_call_inout_holo_dark,
        R.drawable.ic_call_incoming_holo_dark,
        R.drawable.ic_call_outgoing_holo_dark,
        R.drawable.ic_call_missed_holo_dark
    };

    private String[] mNavItems;
    private long mFilterFrom = -1;
    private long mFilterTo = -1;
    private boolean mSortByDuration = true;

    private static final String STATE_SELECTED_NAVIGATION_ITEM = "selected_navigation_item";

    private CallStatsAdapter mAdapter;
    private CallStatsQueryHandler mCallStatsQueryHandler;

    private TextView mSumHeaderView;
    private TextView mDateFilterView;

    private final Handler mHandler = new Handler();

    DoubleDatePickerFragment mFilterFragment;
    private final ContentObserver mCallLogObserver = new CustomContentObserver();
    private final ContentObserver mContactsObserver = new CustomContentObserver();
    private boolean mRefreshDataRequired = true;

    private int mCallTypeFilter = CallStatsQueryHandler.CALL_TYPE_ALL;

    private class CustomContentObserver extends ContentObserver {
        public CustomContentObserver() {
            super(mHandler);
        }

        @Override
        public void onChange(boolean selfChange) {
            mRefreshDataRequired = true;
        }
    }

    public class DoubleDatePickerFragment extends DialogFragment
            implements DoubleDatePickerDialog.OnDateSetListener {

        DoubleDatePickerDialog mDialog;
        Context mContext;

        public DoubleDatePickerFragment(Context context) {
            mContext = context;
            mDialog = new DoubleDatePickerDialog(context, this);
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return mDialog;
        }

        @Override
        public void onStart() {
            if (mFilterFrom != -1) {
                mDialog.setValues(mFilterFrom, mFilterTo);
            } else {
                mDialog.resetPickers();
            }
            super.onStart();
        }

        public void onDateSet(long from, long to) {
            mFilterFrom = from;
            mFilterTo = to;
            invalidateOptionsMenu();
            fetchCalls();
        }
    }

    public class CallStatsNavAdapter extends ArrayAdapter<String> {

        public CallStatsNavAdapter(Context context, int textResourceId, Object[] objects) {
            super(context, textResourceId, mNavItems);
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            return getCustomView(position, convertView, parent);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            return getCustomView(position, convertView, parent);
        }

        public View getCustomView(int position, View convertView, ViewGroup parent) {
            LayoutInflater inflater = getLayoutInflater();
            View item = inflater.inflate(R.layout.call_stats_nav_item, parent, false);

            TextView label = (TextView) item.findViewById(R.id.call_stats_nav_text);
            label.setText(mNavItems[position]);

            ImageView icon = (ImageView) item.findViewById(R.id.call_stats_nav_icon);
            icon.setImageResource(CALL_DIRECTION_RESOURCES[position]);

            return item;
        }
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        final ContentResolver cr = getContentResolver();
        mCallStatsQueryHandler = new CallStatsQueryHandler(cr, this);
        cr.registerContentObserver(CallLog.CONTENT_URI, true, mCallLogObserver);
        cr.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, mContactsObserver);

        Resources res = getResources();
        mFilterFragment = new DoubleDatePickerFragment(this);
        mNavItems = res.getStringArray(R.array.call_stats_nav_items);
        configureActionBar();

        String currentCountryIso = ContactsUtils.getCurrentCountryIso(this);
        mAdapter = new CallStatsAdapter(this,
                new ContactInfoHelper(this, currentCountryIso), mList);
        setListAdapter(mAdapter);

        getListView().setItemsCanFocus(true);
        setContentView(R.layout.call_stats_activity);

        mSumHeaderView = (TextView) findViewById(R.id.sum_header);
        mDateFilterView = (TextView) findViewById(R.id.date_filter);
    }

    private void configureActionBar() {
        final ActionBar actionBar = getActionBar();
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);

        CallStatsNavAdapter navAdapter = new CallStatsNavAdapter(
                actionBar.getThemedContext(), android.R.layout.simple_list_item_1, mNavItems);

        // Set up the dropdown list navigation in the action bar.
        actionBar.setListNavigationCallbacks(navAdapter, this);
        actionBar.setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP
                | ActionBar.DISPLAY_SHOW_HOME | ActionBar.DISPLAY_SHOW_TITLE);
    }

    /** Invoked when the user presses the home button in the action bar. */
    private void onHomeSelected() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.setClass(this, DialtactsActivity.class);
        startActivity(intent);
        finish();
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        // Restore the previously serialized current dropdown position.
        if (savedInstanceState.containsKey(STATE_SELECTED_NAVIGATION_ITEM)) {
            getActionBar().setSelectedNavigationItem(
                    savedInstanceState.getInt(STATE_SELECTED_NAVIGATION_ITEM));
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        // Serialize the current dropdown position.
        outState.putInt(STATE_SELECTED_NAVIGATION_ITEM,
                getActionBar().getSelectedNavigationIndex());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.call_stats_options, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        final MenuItem resetItem = menu.findItem(R.id.reset_date_filter);
        final MenuItem sortDurationItem = menu.findItem(R.id.sort_by_duration);
        final MenuItem sortCountItem = menu.findItem(R.id.sort_by_count);

        resetItem.setVisible(mFilterFrom != -1);
        sortDurationItem.setVisible(!mSortByDuration);
        sortCountItem.setVisible(mSortByDuration);

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home: {
                onHomeSelected();
                break;
            }
            case R.id.date_filter: {
                mFilterFragment.show(getFragmentManager(), "filter");
                break;
            }
            case R.id.reset_date_filter: {
                mFilterFrom = -1;
                mFilterTo = -1;
                fetchCalls();
                invalidateOptionsMenu();
                break;
            }
            case R.id.sort_by_duration: {
                mSortByDuration = true;
                mAdapter.sort(true);
                invalidateOptionsMenu();
                break;
            }
            case R.id.sort_by_count: {
                mSortByDuration = false;
                mAdapter.sort(false);
                invalidateOptionsMenu();
                break;
            }
        }
        return true;
    }

    @Override
    public boolean onNavigationItemSelected(int position, long id) {
        mCallTypeFilter = position;
        mCallStatsQueryHandler.fetchCalls(mFilterFrom, mFilterTo);
        return true;
    }

    /**
     * Called by the CallStatsQueryHandler when the list of calls has been
     * fetched or updated.
     */
    @Override
    public void onCallsFetched(Cursor cursor) {
        if (isFinishing()) {
            return;
        }
        mAdapter.processCursor(cursor, mCallTypeFilter, mFilterFrom, mFilterTo, mSortByDuration);
        cursor.close();
        updateHeader();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshData();
    }

    @Override
    public void onPause() {
        super.onPause();
        // Kill the requests thread
        mAdapter.stopRequestProcessing();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mAdapter.stopRequestProcessing();
        getContentResolver().unregisterContentObserver(mCallLogObserver);
        getContentResolver().unregisterContentObserver(mContactsObserver);
    }

    private void fetchCalls() {
        mCallStatsQueryHandler.fetchCalls(mFilterFrom, mFilterTo);
    }

    public void startCallsQuery() {
        mCallStatsQueryHandler.fetchCalls(mFilterFrom, mFilterTo);
    }

    private void updateHeader() {
        final String callCount = mAdapter.getTotalCallCountString();
        final String duration = mAdapter.getFullDurationString(false);

        if (duration != null) {
            mSumHeaderView.setText(getString(R.string.call_stats_header_total, callCount, duration));
        } else {
            mSumHeaderView.setText(getString(R.string.call_stats_header_total_callsonly, callCount));
        }

        if (mFilterFrom == -1) {
            mDateFilterView.setVisibility(View.GONE);
        } else {
            mDateFilterView.setText(DateUtils.formatDateRange(this, mFilterFrom, mFilterTo, 0));
            mDateFilterView.setVisibility(View.VISIBLE);
        }
    }

    public void callSelectedEntry() {
        int position = getListView().getSelectedItemPosition();
        if (position < 0) {
            // In touch mode you may often not have something selected, so
            // just call the first entry to make sure that [send] [send] calls
            // the
            // most recent entry.
            position = 0;
        }
        final CallStatsDetails item = mAdapter.getItem(position);
        String number = (String) item.number;
        if (TextUtils.isEmpty(number)
                || number.equals(CallerInfo.UNKNOWN_NUMBER)
                || number.equals(CallerInfo.PRIVATE_NUMBER)
                || number.equals(CallerInfo.PAYPHONE_NUMBER)) {
            // This number can't be called, do nothing
            return;
        }
        Intent intent;
        // If "number" is really a SIP address, construct a sip: URI.
        if (PhoneNumberUtils.isUriNumber(number)) {
            intent = ContactsUtils.getCallIntent(Uri.fromParts(
                    Constants.SCHEME_SIP, number, null));
        } else {
            if (!number.startsWith("+")) {
                // If the caller-id matches a contact with a better qualified
                // number, use it
                String countryIso = item.countryIso;
                number = mAdapter.getBetterNumberFromContacts(number,
                        countryIso);
            }
            intent = ContactsUtils.getCallIntent(Uri.fromParts(
                    Constants.SCHEME_TEL, number, null));
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        startActivity(intent);
    }

    /** Requests updates to the data to be shown. */
    private void refreshData() {
        // Prevent unnecessary refresh.
        if (mRefreshDataRequired) {
            // Mark all entries in the contact info cache as out of date, so
            // they will be looked up again once being shown.
            mAdapter.invalidateCache();
            startCallsQuery();
            mRefreshDataRequired = false;
        }
    }
}
