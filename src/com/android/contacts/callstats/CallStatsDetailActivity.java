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
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.provider.Contacts.Intents.Insert;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.os.AsyncTask;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.format.DateUtils;
import android.text.TextUtils;
import android.util.Log;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.contacts.ContactPhotoManager;

import com.android.contacts.calllog.ContactInfo;
import com.android.contacts.calllog.ContactInfoHelper;
import com.android.contacts.calllog.PhoneNumberHelper;
import com.android.contacts.format.FormatUtils;
import com.android.contacts.util.ClipboardUtils;
import com.android.contacts.util.Constants;
import com.android.contacts.ContactsUtils;

import com.android.contacts.R;

import java.util.List;
import java.util.ArrayList;

/**
 * Activity to display detailed information about a callstat item
 */
public class CallStatsDetailActivity extends Activity {
    private static final String TAG = "CallStatsDetailActivity";

    private PhoneNumberHelper mPhoneNumberHelper;
    private CallStatsDetailHelper mCallStatsDetailHelper;
    private TextView mHeaderTextView;
    private View mHeaderOverlayView;
    private ImageView mMainActionView;
    private ImageButton mMainActionPushLayerView;
    private ImageView mContactBackgroundView;
    private ContactInfoHelper mContactInfoHelper;
    private TextView mDateFilterView;

    private static final String CONTACTS_PACKAGE = "com.android.contacts";
    private static final String CALL_STATS_CLASS_NAME =
            "com.android.contacts.callstats.CallStatsActivity";

    private String mNumber = null;

    LayoutInflater mInflater;
    Resources mResources;

    private ContactPhotoManager mContactPhotoManager;

    private boolean mHasEditNumberBeforeCallOption;

    private ActionMode mPhoneNumberActionMode;

    private CallStatsDetails mData;

    private CharSequence mPhoneNumberLabelToCopy;
    private CharSequence mPhoneNumberToCopy;

    private final View.OnClickListener mPrimaryActionListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            if (finishPhoneNumerSelectedActionModeIfShown()) {
                return;
            }
            startActivity(((ViewEntry) view.getTag()).primaryIntent);
        }
    };

    private final View.OnClickListener mSecondaryActionListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            if (finishPhoneNumerSelectedActionModeIfShown()) {
                return;
            }
            startActivity(((ViewEntry) view.getTag()).secondaryIntent);
        }
    };

    private final View.OnLongClickListener mPrimaryLongClickListener =
            new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View v) {
            if (finishPhoneNumerSelectedActionModeIfShown()) {
                return true;
            }
            startPhoneNumberSelectedActionMode(v);
            return true;
        }
    };

    private class UpdateContactTask extends
            AsyncTask<String, Void, ContactInfo> {
        protected ContactInfo doInBackground(String... strings) {
            ContactInfo info = mContactInfoHelper.lookupNumber(strings[0],
                    strings[1]);
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

        mInflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        mResources = getResources();

        mPhoneNumberHelper = new PhoneNumberHelper(mResources);
        mCallStatsDetailHelper = new CallStatsDetailHelper(mResources,
                mPhoneNumberHelper);
        mHeaderTextView = (TextView) findViewById(R.id.header_text);
        mHeaderOverlayView = findViewById(R.id.photo_text_bar);
        mMainActionView = (ImageView) findViewById(R.id.main_action);
        mMainActionPushLayerView = (ImageButton) findViewById(R.id.main_action_push_layer);
        mContactBackgroundView = (ImageView) findViewById(R.id.contact_background);
        mContactPhotoManager = ContactPhotoManager.getInstance(this);
        mContactInfoHelper = new ContactInfoHelper(this,
                ContactsUtils.getCurrentCountryIso(this));
        configureActionBar();
        Intent launchIntent = getIntent();
        mData = CallStatsDetails.reCreateFromIntent(launchIntent);
        mDateFilterView = (TextView) findViewById(R.id.date_filter);
        long filterFrom = launchIntent.getLongExtra("from", -1);
        if (filterFrom == -1) {
            mDateFilterView.setVisibility(View.GONE);
        } else {
            long filterTo = launchIntent.getLongExtra("to", -1);
            mDateFilterView.setText(DateUtils.formatDateRange(this, filterFrom, filterTo,
                    DateUtils.FORMAT_ABBREV_ALL));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        new UpdateContactTask()
                .execute((String) mData.number, mData.countryIso);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_CALL: {
                // Make sure phone isn't already busy before starting direct
                // call
                TelephonyManager tm = (TelephonyManager)
                        getSystemService(Context.TELEPHONY_SERVICE);
                if (tm.getCallState() == TelephonyManager.CALL_STATE_IDLE) {
                    startActivity(ContactsUtils.getCallIntent(Uri.fromParts(
                            Constants.SCHEME_TEL, mNumber, null)));
                    return true;
                }
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    private void updateData() {

        mNumber = mData.number.toString();
        final Uri contactUri = mData.contactUri;
        final Uri photoUri = mData.photoUri;

        // Set the details header, based on the first phone call.
        mCallStatsDetailHelper.setCallStatsDetailHeader(mHeaderTextView, mData);

        // Cache the details about the phone number.
        final boolean canPlaceCallsTo = mPhoneNumberHelper
                .canPlaceCallsTo(mNumber);
        final boolean isVoicemailNumber = mPhoneNumberHelper
                .isVoicemailNumber(mNumber);
        final boolean isSipNumber = mPhoneNumberHelper.isSipNumber(mNumber);

        // Let user view contact details if they exist, otherwise add option to
        // create new
        // contact from this number.
        final Intent mainActionIntent;
        final int mainActionIcon;
        final String mainActionDescription;

        final CharSequence nameOrNumber;
        if (!TextUtils.isEmpty(mData.name)) {
            nameOrNumber = mData.name;
        } else {
            nameOrNumber = mData.number;
        }

        if (contactUri != null) {
            mainActionIntent = new Intent(Intent.ACTION_VIEW, contactUri);
            // This will launch People's detail contact screen, so we probably
            // want to
            // treat it as a separate People task.
            mainActionIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            mainActionIcon = R.drawable.ic_contacts_holo_dark;
            mainActionDescription = getString(
                    R.string.description_view_contact, nameOrNumber);
        } else if (isVoicemailNumber) {
            mainActionIntent = null;
            mainActionIcon = 0;
            mainActionDescription = null;
        } else if (isSipNumber) {
            mainActionIntent = null;
            mainActionIcon = 0;
            mainActionDescription = null;
        } else if (canPlaceCallsTo) {
            mainActionIntent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
            mainActionIntent.setType(Contacts.CONTENT_ITEM_TYPE);
            mainActionIntent.putExtra(Insert.PHONE, mNumber);
            mainActionIcon = R.drawable.ic_add_contact_holo_dark;
            mainActionDescription = getString(R.string.description_add_contact);
        } else {
            // If we cannot call the number, when we probably cannot add it as a
            // contact either.
            // This is usually the case of private, unknown, or payphone
            // numbers.
            mainActionIntent = null;
            mainActionIcon = 0;
            mainActionDescription = null;
        }

        if (mainActionIntent == null) {
            mMainActionView.setVisibility(View.INVISIBLE);
            mMainActionPushLayerView.setVisibility(View.GONE);
            mHeaderTextView.setVisibility(View.INVISIBLE);
            mHeaderOverlayView.setVisibility(View.INVISIBLE);
        } else {
            mMainActionView.setVisibility(View.VISIBLE);
            mMainActionView.setImageResource(mainActionIcon);
            mMainActionPushLayerView.setVisibility(View.VISIBLE);
            mMainActionPushLayerView
                    .setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            startActivity(mainActionIntent);
                        }
                    });
            mMainActionPushLayerView
                    .setContentDescription(mainActionDescription);
            mHeaderTextView.setVisibility(View.VISIBLE);
            mHeaderOverlayView.setVisibility(View.VISIBLE);
        }

        // This action allows to call the number that places the call.
        if (canPlaceCallsTo) {
            final CharSequence displayNumber = mPhoneNumberHelper
                    .getDisplayNumber(mData.number, mData.formattedNumber);

            ViewEntry entry = new ViewEntry(getString(R.string.menu_callNumber,
                    FormatUtils.forceLeftToRight(displayNumber)),
                    ContactsUtils.getCallIntent(mNumber), getString(
                            R.string.description_call, nameOrNumber));

            // Only show a label if the number is shown and it is not a SIP
            // address.
            if (!TextUtils.isEmpty(mData.name)
                    && !TextUtils.isEmpty(mData.number)
                    && !PhoneNumberUtils.isUriNumber(mData.number.toString())) {
                entry.label = Phone.getTypeLabel(mResources, mData.numberType,
                        mData.numberLabel);
            }

            // The secondary action allows to send an SMS to the number that
            // placed the call.
            if (mPhoneNumberHelper.canSendSmsTo(mNumber)) {
                entry.setSecondaryAction(
                        R.drawable.ic_text_holo_dark,
                        new Intent(Intent.ACTION_SENDTO, Uri.fromParts("sms",
                                mNumber, null)),
                        getString(R.string.description_send_text_message,
                                nameOrNumber));
            }

            configureCallButton(entry);
            mPhoneNumberToCopy = displayNumber;
            mPhoneNumberLabelToCopy = entry.label;
        } else {
            disableCallButton();
            mPhoneNumberToCopy = null;
            mPhoneNumberLabelToCopy = null;
        }

        mHasEditNumberBeforeCallOption = canPlaceCallsTo && !isSipNumber
                && !isVoicemailNumber;
        invalidateOptionsMenu();
        loadContactPhotos(photoUri);

        TextView mTotalText = (TextView) findViewById(R.id.total);
        TextView mInText = (TextView) findViewById(R.id.in_line_one);
        TextView mInText2 = (TextView) findViewById(R.id.in_line_two);
        TextView mOutText = (TextView) findViewById(R.id.out_line_one);
        TextView mOutText2 = (TextView) findViewById(R.id.out_line_two);
        TextView mMissedText = (TextView) findViewById(R.id.missed_line_one);
        PieChartView mPieChart = (PieChartView) findViewById(R.id.pie_chart);

        mPieChart.setOriginAngle(240);
        mPieChart.removeAllSlices();

        String preTotal = mResources
                .getString(R.string.call_stats_header_total);
        mTotalText.setText(preTotal + " "
                + CallStatsDetailHelper.getDurationString(this, 0,
                        mData.getFullDuration()));

        if (mData.inDuration != 0) {
            String preIn = mResources
                    .getString(R.string.call_stats_nav_incoming);
            mInText.setText(preIn + ": " + mData.getInPercentage());
            mInText2.setText(CallStatsDetailHelper.getDurationString(this, 1,
                    mData.inDuration));
            mPieChart.addSlice(mData.inDuration, Color.parseColor("#33b5e5"));
        } else {
            ((LinearLayout) findViewById(R.id.in_container))
                    .setVisibility(View.GONE);
        }

        if (mData.outDuration != 0) {
            String preOut = mResources
                    .getString(R.string.call_stats_nav_outgoing);
            mOutText.setText(preOut + ": " + mData.getOutPercentage());
            mOutText2.setText(CallStatsDetailHelper.getDurationString(this, 2,
                    mData.outDuration));
            mPieChart.addSlice(mData.outDuration, Color.parseColor("#99cc00"));
        } else {
            ((LinearLayout) findViewById(R.id.out_container))
                    .setVisibility(View.GONE);
        }

        if (mData.missedCount != 0) {
            String preMissed = mResources
                    .getString(R.string.call_stats_nav_missed);
            mMissedText.setText(preMissed
                    + ": "
                    + CallStatsDetailHelper.getDurationString(this, 3,
                            mData.missedCount));
        } else {
            ((LinearLayout) findViewById(R.id.missed_container))
                    .setVisibility(View.GONE);
        }

        mPieChart.generatePath();
        findViewById(R.id.call_stats_detail).setVisibility(View.VISIBLE);
    }

    /** Load the contact photos and places them in the corresponding views. */
    private void loadContactPhotos(Uri photoUri) {
        mContactPhotoManager.loadPhoto(mContactBackgroundView, photoUri,
                mContactBackgroundView.getWidth(), true);
    }

    static final class ViewEntry {
        public final String text;
        public final Intent primaryIntent;
        public final String primaryDescription;

        public CharSequence label = null;

        public int secondaryIcon = 0;
        public Intent secondaryIntent = null;
        public String secondaryDescription = null;

        public ViewEntry(String text, Intent intent, String description) {
            this.text = text;
            primaryIntent = intent;
            primaryDescription = description;
        }

        public void setSecondaryAction(int icon, Intent intent,
                String description) {
            secondaryIcon = icon;
            secondaryIntent = intent;
            secondaryDescription = description;
        }
    }

    private void disableCallButton() {
        findViewById(R.id.call_and_sms).setVisibility(View.GONE);
    }

    private void configureCallButton(ViewEntry entry) {
        View convertView = findViewById(R.id.call_and_sms);
        convertView.setVisibility(View.VISIBLE);

        ImageView icon = (ImageView) convertView
                .findViewById(R.id.call_and_sms_icon);
        View divider = convertView.findViewById(R.id.call_and_sms_divider);
        TextView text = (TextView) convertView
                .findViewById(R.id.call_and_sms_text);

        View mainAction = convertView
                .findViewById(R.id.call_and_sms_main_action);
        mainAction.setOnClickListener(mPrimaryActionListener);
        mainAction.setTag(entry);
        mainAction.setContentDescription(entry.primaryDescription);
        mainAction.setOnLongClickListener(mPrimaryLongClickListener);

        if (entry.secondaryIntent != null) {
            icon.setOnClickListener(mSecondaryActionListener);
            icon.setImageResource(entry.secondaryIcon);
            icon.setVisibility(View.VISIBLE);
            icon.setTag(entry);
            icon.setContentDescription(entry.secondaryDescription);
            divider.setVisibility(View.VISIBLE);
        } else {
            icon.setVisibility(View.GONE);
            divider.setVisibility(View.GONE);
        }
        text.setText(entry.text);

        TextView label = (TextView) convertView
                .findViewById(R.id.call_and_sms_label);
        if (TextUtils.isEmpty(entry.label)) {
            label.setVisibility(View.GONE);
        } else {
            label.setText(entry.label);
            label.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.call_stats_details_options, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.menu_edit_number_before_call).setVisible(
                mHasEditNumberBeforeCallOption);
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
        startActivity(new Intent(Intent.ACTION_DIAL,
                ContactsUtils.getCallUri(mNumber)));
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
        intent.setClassName(CONTACTS_PACKAGE, CALL_STATS_CLASS_NAME);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }

    private boolean finishPhoneNumerSelectedActionModeIfShown() {
        if (mPhoneNumberActionMode == null)
            return false;
        mPhoneNumberActionMode.finish();
        return true;
    }

    private void startPhoneNumberSelectedActionMode(View targetView) {
        mPhoneNumberActionMode = startActionMode(
                new PhoneNumberActionModeCallback(targetView));
    }

    private class PhoneNumberActionModeCallback implements ActionMode.Callback {
        private final View mTargetView;
        private final Drawable mOriginalViewBackground;

        public PhoneNumberActionModeCallback(View targetView) {
            mTargetView = targetView;

            // Highlight the phone number view. Remember the old background, and
            // put a new one.
            mOriginalViewBackground = mTargetView.getBackground();
            mTargetView.setBackgroundColor(getResources().getColor(
                    R.color.item_selected));
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            if (TextUtils.isEmpty(mPhoneNumberToCopy))
                return false;

            getMenuInflater().inflate(R.menu.call_details_cab, menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return true;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            switch (item.getItemId()) {
                case R.id.copy_phone_number:
                    ClipboardUtils.copyText(CallStatsDetailActivity.this,
                            mPhoneNumberLabelToCopy, mPhoneNumberToCopy, true);
                    mode.finish(); // Close the CAB
                    return true;
            }
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            mPhoneNumberActionMode = null;

            // Restore the view background.
            mTargetView.setBackground(mOriginalViewBackground);
        }
    }
}
