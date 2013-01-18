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
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract;
import android.telephony.PhoneNumberUtils;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.format.DateUtils;
import android.text.TextUtils;
import android.util.Log;
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
import android.widget.Toast;

import com.android.common.io.MoreCloseables;
import com.android.contacts.ContactsUtils;
import com.android.contacts.R;
import com.android.contacts.calllog.ContactInfoHelper;
import com.android.contacts.util.Constants;
import com.android.internal.telephony.CallerInfo;
import com.android.internal.telephony.ITelephony;

import com.google.common.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;

public class CallStatsActivity extends ListActivity implements
        CallStatsQueryHandler.Listener, CallStatsAdapter.CallFetcher,
        ActionBar.OnNavigationListener {
    private static final String TAG = "CallStatsActivity";

    private static final String CONTACTS_PACKAGE = "com.android.contacts";
    private static final String PHONE_CLASS_NAME =
            "com.android.contacts.activities.DialtactsActivity";

    private ArrayList<CallStatsDetails> mList = new ArrayList<CallStatsDetails>();
    private static final int NAV_ALL = 0;
    private static final int NAV_INCOMING = 1;
    private static final int NAV_OUTGOING = 2;
    private static final int NAV_MISSED = 3;
    private String[] mNavItems;
    private long mFilterFrom = -1;
    private long mFilterTo = -1;

    private static final String STATE_SELECTED_NAVIGATION_ITEM = "selected_navigation_item";

    private static final int EMPTY_LOADER_ID = 0;

    private CallStatsAdapter mAdapter;
    private CallStatsNavAdapter mNavAdapter;
    private CallStatsQueryHandler mCallStatsQueryHandler;
    private boolean mScrollToTop;

    private View mStatusMessageView;
    private TextView mStatusMessageText;
    private TextView mStatusMessageAction;
    private TextView mSumHeaderView;
    private TextView mDateFilterView;

    private boolean mCallStatsFetched;

    private final Handler mHandler = new Handler();

    private TelephonyManager mTelephonyManager;

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

        protected void resetPickers() {
            mDialog.resetPickers();
        }

        public void onDateSet(long from, long to) {
            if (to < from) {
                Toast toast = Toast.makeText(mContext,
                        getActivity().getResources().getString(R.string.call_stats_filter_error),
                        Toast.LENGTH_SHORT);
                toast.show();
            } else {
                mFilterFrom = from;
                mFilterTo = to;
                fetchCalls();
            }
        }
    }

    public class CallStatsNavAdapter extends ArrayAdapter<String> {

        public CallStatsNavAdapter(Context context, int textResourceId,
                Object[] objects) {
            super(context, textResourceId, mNavItems);
        }

        @Override
        public View getDropDownView(int position, View convertView,
                ViewGroup parent) {
            return getCustomView(position, convertView, parent);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            return getCustomView(position, convertView, parent);
        }

        public View getCustomView(int position, View convertView,
                ViewGroup parent) {
            LayoutInflater inflater = getLayoutInflater();
            View item = inflater.inflate(R.layout.call_stats_nav_item, parent,
                    false);
            TextView label = (TextView) item
                    .findViewById(R.id.call_stats_nav_text);
            label.setText(mNavItems[position]);
            ImageView icon = (ImageView) item
                    .findViewById(R.id.call_stats_nav_icon);
            switch (position) {
                case NAV_ALL:
                    icon.setImageResource(R.drawable.ic_call_inout_holo_dark);
                    break;
                case NAV_INCOMING:
                    icon.setImageResource(R.drawable.ic_call_incoming_holo_dark);
                    break;
                case NAV_OUTGOING:
                    icon.setImageResource(R.drawable.ic_call_outgoing_holo_dark);
                    break;
                case NAV_MISSED:
                    icon.setImageResource(R.drawable.ic_call_missed_holo_dark);
                    break;
            }
            return item;
        }
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        mCallStatsQueryHandler = new CallStatsQueryHandler(
                getContentResolver(), this);
        getContentResolver().registerContentObserver(CallLog.CONTENT_URI, true,
                mCallLogObserver);
        getContentResolver().registerContentObserver(
                ContactsContract.Contacts.CONTENT_URI, true, mContactsObserver);

        Resources res = getResources();
        mFilterFragment = new DoubleDatePickerFragment(this);
        mNavItems = res.getStringArray(R.array.call_stats_nav_items);
        configureActionBar();
        String currentCountryIso = ContactsUtils.getCurrentCountryIso(this);
        mAdapter = new CallStatsAdapter(this, this, new ContactInfoHelper(this,
                currentCountryIso), mList);
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
        mNavAdapter = new CallStatsNavAdapter(actionBar.getThemedContext(),
                android.R.layout.simple_list_item_1, mNavItems);

        // Set up the dropdown list navigation in the action bar.
        actionBar.setListNavigationCallbacks(mNavAdapter, this);
        actionBar.setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP
                | ActionBar.DISPLAY_SHOW_HOME | ActionBar.DISPLAY_SHOW_TITLE);
    }

    /** Invoked when the user presses the home button in the action bar. */
    private void onHomeSelected() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.setClassName(CONTACTS_PACKAGE, PHONE_CLASS_NAME);
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
        outState.putInt(STATE_SELECTED_NAVIGATION_ITEM, getActionBar()
                .getSelectedNavigationIndex());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.call_stats_options, menu);
        return super.onCreateOptionsMenu(menu);
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
        mAdapter.processCursor(cursor, mCallTypeFilter, mFilterFrom, mFilterTo);
        if (mScrollToTop) {
            final ListView listView = getListView();
            // The smooth-scroll animation happens over a fixed time period.
            // As a result, if it scrolls through a large portion of the list,
            // each frame will jump so far from the previous one that the user
            // will not experience the illusion of downward motion. Instead,
            // if we're not already near the top of the list, we instantly jump
            // near the top, and animate from there.
            if (listView.getFirstVisiblePosition() > 5) {
                listView.setSelection(5);
            }
            // Workaround for framework issue: the smooth-scroll doesn't
            // occur if setSelection() is called immediately before.
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (isFinishing()) {
                        return;
                    }
                    listView.smoothScrollToPosition(0);
                }
            });

            mScrollToTop = false;
        }
        cursor.close();
        mCallStatsFetched = true;
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

    @Override
    public void fetchCalls(long from, long to) {
        mCallStatsQueryHandler.fetchCalls(from, to);
    }

    public void startCallsQuery() {
        mCallStatsQueryHandler.fetchCalls(mFilterFrom, mFilterTo);
    }

    private void updateHeader() {
        mSumHeaderView.setText(getResources().getString(R.string.call_stats_header_total)
                + " " + mAdapter.getFullDurationString());
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

    CallStatsAdapter getAdapter() {
        return mAdapter;
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
