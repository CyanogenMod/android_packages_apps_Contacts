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
import android.app.Activity;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.provider.CallLog.Calls;
import android.os.AsyncTask;
import android.text.format.DateUtils;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.android.contacts.CallDetailHeader;
import com.android.contacts.ContactsUtils;
import com.android.contacts.R;
import com.android.contacts.calllog.ContactInfo;
import com.android.contacts.calllog.ContactInfoHelper;
import com.android.contacts.calllog.PhoneNumberHelper;

/**
 * Activity to display detailed information about a callstat item
 */
public class CallStatsDetailActivity extends Activity {
    private static final String TAG = "CallStatsDetailActivity";

    private CallStatsDetailHelper mCallStatsDetailHelper;
    private ContactInfoHelper mContactInfoHelper;
    private CallDetailHeader mCallDetailHeader;
    private Resources mResources;

    private TextView mHeaderTextView;
    private TextView mTotalText;
    private TextView mInText;
    private TextView mInText2;
    private TextView mOutText;
    private TextView mOutText2;
    private TextView mMissedText;
    private PieChartView mPieChart;


    private CallStatsDetails mData;
    private String mNumber = null;

    private class UpdateContactTask extends AsyncTask<String, Void, ContactInfo> {
        protected ContactInfo doInBackground(String... strings) {
            ContactInfo info = mContactInfoHelper.lookupNumber(strings[0], strings[1]);
            return info;
        }

        protected void onPostExecute(ContactInfo info) {
            mData.contactUri = info.lookupUri;
            mData.photoUri = info.photoUri;
            updateData();
        }
    }

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        setContentView(R.layout.call_stats_detail);

        mResources = getResources();

        PhoneNumberHelper phoneNumberHelper = new PhoneNumberHelper(mResources);
        mCallDetailHeader = new CallDetailHeader(this, phoneNumberHelper);
        mCallStatsDetailHelper = new CallStatsDetailHelper(mResources, phoneNumberHelper);
        mContactInfoHelper = new ContactInfoHelper(this, ContactsUtils.getCurrentCountryIso(this));

        mHeaderTextView = (TextView) findViewById(R.id.header_text);
        mTotalText = (TextView) findViewById(R.id.total);
        mInText = (TextView) findViewById(R.id.in_line_one);
        mInText2 = (TextView) findViewById(R.id.in_line_two);
        mOutText = (TextView) findViewById(R.id.out_line_one);
        mOutText2 = (TextView) findViewById(R.id.out_line_two);
        mMissedText = (TextView) findViewById(R.id.missed_line_one);
        mPieChart = (PieChartView) findViewById(R.id.pie_chart);

        configureActionBar();
        Intent launchIntent = getIntent();
        mData = CallStatsDetails.reCreateFromIntent(launchIntent);

        TextView dateFilterView = (TextView) findViewById(R.id.date_filter);
        long filterFrom = launchIntent.getLongExtra("from", -1);
        if (filterFrom == -1) {
            dateFilterView.setVisibility(View.GONE);
        } else {
            long filterTo = launchIntent.getLongExtra("to", -1);
            dateFilterView.setText(DateUtils.formatDateRange(
                    this, filterFrom, filterTo, DateUtils.FORMAT_ABBREV_ALL));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        new UpdateContactTask().execute((String) mData.number, mData.countryIso);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (mCallDetailHeader.handleKeyDown(keyCode, event)) {
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    private void updateData() {
        mNumber = mData.number.toString();

        // Set the details header, based on the first phone call.
        mCallStatsDetailHelper.setCallStatsDetailHeader(mHeaderTextView, mData);
        mCallDetailHeader.updateViews(mNumber, mData);

        invalidateOptionsMenu();
        mCallDetailHeader.loadContactPhotos(mData.photoUri);

        mPieChart.setOriginAngle(240);
        mPieChart.removeAllSlices();

        boolean byDuration = getIntent().getBooleanExtra("by_duration", true);

        mTotalText.setText(getString(R.string.call_stats_header_total_details,
                CallStatsDetailHelper.getDurationString(mResources, mData.getFullDuration(), true)));

        if (mData.inDuration != 0) {
            int percent = byDuration
                    ? mData.getDurationPercentage(Calls.INCOMING_TYPE)
                    : mData.getCountPercentage(Calls.INCOMING_TYPE);

            mInText.setText(getString(R.string.call_stats_incoming, percent,
                    CallStatsDetailHelper.getCallCountString(mResources, mData.incomingCount)));
            mInText2.setText(CallStatsDetailHelper.getDurationString(
                    mResources, mData.inDuration, true));
            mPieChart.addSlice(byDuration ? mData.inDuration : mData.incomingCount,
                    mResources.getColor(R.color.call_stats_incoming));
        } else {
            findViewById(R.id.in_container).setVisibility(View.GONE);
        }

        if (mData.outDuration != 0) {
            int percent = byDuration
                    ? mData.getDurationPercentage(Calls.OUTGOING_TYPE)
                    : mData.getCountPercentage(Calls.OUTGOING_TYPE);

            mOutText.setText(getString(R.string.call_stats_outgoing, percent,
                    CallStatsDetailHelper.getCallCountString(mResources, mData.outgoingCount)));
            mOutText2.setText(CallStatsDetailHelper.getDurationString(
                    mResources, mData.outDuration, true));
            mPieChart.addSlice(byDuration ? mData.outDuration : mData.outgoingCount,
                    mResources.getColor(R.color.call_stats_outgoing));
        } else {
            findViewById(R.id.out_container).setVisibility(View.GONE);
        }

        if (mData.missedCount != 0) {
            final String missedCount =
                    CallStatsDetailHelper.getCallCountString(mResources, mData.missedCount);

            if (byDuration) {
                mMissedText.setText(getString(R.string.call_stats_missed_countonly, missedCount));
            } else {
                mMissedText.setText(getString(R.string.call_stats_missed,
                        mData.getCountPercentage(Calls.MISSED_TYPE), missedCount));
                mPieChart.addSlice(mData.missedCount, mResources.getColor(R.color.call_stats_missed));
            }
        } else {
            findViewById(R.id.missed_container).setVisibility(View.GONE);
        }

        mPieChart.generatePath();
        findViewById(R.id.call_stats_detail).setVisibility(View.VISIBLE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.call_stats_details_options, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.menu_edit_number_before_call).setVisible(
                mCallDetailHeader.canEditNumberBeforeCall());
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home: {
                onHomeSelected();
                return true;
            }
            // All the options menu items are handled by onMenu... methods.
            default:
                throw new IllegalArgumentException();
        }
    }

    public void onMenuEditNumberBeforeCall(MenuItem menuItem) {
        startActivity(new Intent(Intent.ACTION_DIAL, ContactsUtils.getCallUri(mNumber)));
    }

    private void configureActionBar() {
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP
                    | ActionBar.DISPLAY_SHOW_HOME);
        }
    }

    private void onHomeSelected() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setClass(this, CallStatsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }
}
