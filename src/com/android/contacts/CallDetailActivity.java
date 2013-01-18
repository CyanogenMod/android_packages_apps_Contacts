/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.contacts;

import android.app.ActionBar;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.provider.VoicemailContract.Voicemails;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.contacts.BackScrollManager.ScrollableHeader;
import com.android.contacts.calllog.CallDetailHistoryAdapter;
import com.android.contacts.calllog.CallTypeHelper;
import com.android.contacts.calllog.ContactInfo;
import com.android.contacts.calllog.ContactInfoHelper;
import com.android.contacts.calllog.PhoneNumberHelper;
import com.android.contacts.util.AsyncTaskExecutor;
import com.android.contacts.util.AsyncTaskExecutors;
import com.android.contacts.voicemail.VoicemailPlaybackFragment;
import com.android.contacts.voicemail.VoicemailStatusHelper;
import com.android.contacts.voicemail.VoicemailStatusHelper.StatusMessage;
import com.android.contacts.voicemail.VoicemailStatusHelperImpl;

import java.util.List;

/**
 * Displays the details of a specific call log entry.
 * <p>
 * This activity can be either started with the URI of a single call log entry, or with the
 * {@link #EXTRA_CALL_LOG_IDS} extra to specify a group of call log entries.
 */
public class CallDetailActivity extends Activity implements ProximitySensorAware {
    private static final String TAG = "CallDetail";

    /** The time to wait before enabling the blank the screen due to the proximity sensor. */
    private static final long PROXIMITY_BLANK_DELAY_MILLIS = 100;
    /** The time to wait before disabling the blank the screen due to the proximity sensor. */
    private static final long PROXIMITY_UNBLANK_DELAY_MILLIS = 500;

    /** The enumeration of {@link AsyncTask} objects used in this class. */
    public enum Tasks {
        MARK_VOICEMAIL_READ,
        DELETE_VOICEMAIL_AND_FINISH,
        REMOVE_FROM_CALL_LOG_AND_FINISH,
        UPDATE_PHONE_CALL_DETAILS,
    }

    /** A long array extra containing ids of call log entries to display. */
    public static final String EXTRA_CALL_LOG_IDS = "EXTRA_CALL_LOG_IDS";
    /** If we are started with a voicemail, we'll find the uri to play with this extra. */
    public static final String EXTRA_VOICEMAIL_URI = "EXTRA_VOICEMAIL_URI";
    /** If we should immediately start playback of the voicemail, this extra will be set to true. */
    public static final String EXTRA_VOICEMAIL_START_PLAYBACK = "EXTRA_VOICEMAIL_START_PLAYBACK";
    /** If the activity was triggered from a notification. */
    public static final String EXTRA_FROM_NOTIFICATION = "EXTRA_FROM_NOTIFICATION";

    private CallDetailHeader mCallDetailHeader;
    private CallTypeHelper mCallTypeHelper;
    private PhoneNumberHelper mPhoneNumberHelper;
    private PhoneCallDetailsHelper mPhoneCallDetailsHelper;
    private TextView mHeaderTextView;
    private AsyncTaskExecutor mAsyncTaskExecutor;
    private ContactInfoHelper mContactInfoHelper;

    private String mNumber = null;
    private String mDefaultCountryIso;

    /* package */ LayoutInflater mInflater;
    /* package */ Resources mResources;
    /** Helper to make async queries to content resolver. */
    private CallDetailActivityQueryHandler mAsyncQueryHandler;
    /** Helper to get voicemail status messages. */
    private VoicemailStatusHelper mVoicemailStatusHelper;
    // Views related to voicemail status message.
    private View mStatusMessageView;
    private TextView mStatusMessageText;
    private TextView mStatusMessageAction;

    /** Whether we should show "edit number before call" in the options menu. */
    private boolean mHasEditNumberBeforeCallOption;
    /** Whether we should show "trash" in the options menu. */
    private boolean mHasTrashOption;
    /** Whether we should show "remove from call log" in the options menu. */
    private boolean mHasRemoveFromCallLogOption;

    private ProximitySensorManager mProximitySensorManager;
    private final ProximitySensorListener mProximitySensorListener = new ProximitySensorListener();

    /** Listener to changes in the proximity sensor state. */
    private class ProximitySensorListener implements ProximitySensorManager.Listener {
        /** Used to show a blank view and hide the action bar. */
        private final Runnable mBlankRunnable = new Runnable() {
            @Override
            public void run() {
                View blankView = findViewById(R.id.blank);
                blankView.setVisibility(View.VISIBLE);
                getActionBar().hide();
            }
        };
        /** Used to remove the blank view and show the action bar. */
        private final Runnable mUnblankRunnable = new Runnable() {
            @Override
            public void run() {
                View blankView = findViewById(R.id.blank);
                blankView.setVisibility(View.GONE);
                getActionBar().show();
            }
        };

        @Override
        public synchronized void onNear() {
            clearPendingRequests();
            postDelayed(mBlankRunnable, PROXIMITY_BLANK_DELAY_MILLIS);
        }

        @Override
        public synchronized void onFar() {
            clearPendingRequests();
            postDelayed(mUnblankRunnable, PROXIMITY_UNBLANK_DELAY_MILLIS);
        }

        /** Removed any delayed requests that may be pending. */
        public synchronized void clearPendingRequests() {
            View blankView = findViewById(R.id.blank);
            blankView.removeCallbacks(mBlankRunnable);
            blankView.removeCallbacks(mUnblankRunnable);
        }

        /** Post a {@link Runnable} with a delay on the main thread. */
        private synchronized void postDelayed(Runnable runnable, long delayMillis) {
            // Post these instead of executing immediately so that:
            // - They are guaranteed to be executed on the main thread.
            // - If the sensor values changes rapidly for some time, the UI will not be
            //   updated immediately.
            View blankView = findViewById(R.id.blank);
            blankView.postDelayed(runnable, delayMillis);
        }
    }

    static final String[] CALL_LOG_PROJECTION = new String[] {
        CallLog.Calls.DATE,
        CallLog.Calls.DURATION,
        CallLog.Calls.NUMBER,
        CallLog.Calls.TYPE,
        CallLog.Calls.COUNTRY_ISO,
        CallLog.Calls.GEOCODED_LOCATION,
    };

    static final int DATE_COLUMN_INDEX = 0;
    static final int DURATION_COLUMN_INDEX = 1;
    static final int NUMBER_COLUMN_INDEX = 2;
    static final int CALL_TYPE_COLUMN_INDEX = 3;
    static final int COUNTRY_ISO_COLUMN_INDEX = 4;
    static final int GEOCODED_LOCATION_COLUMN_INDEX = 5;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        setContentView(R.layout.call_detail);

        mAsyncTaskExecutor = AsyncTaskExecutors.createThreadPoolExecutor();
        mInflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        mResources = getResources();

        mCallTypeHelper = new CallTypeHelper(getResources());
        mPhoneNumberHelper = new PhoneNumberHelper(mResources);
        mCallDetailHeader = new CallDetailHeader(this, mPhoneNumberHelper);
        mPhoneCallDetailsHelper = new PhoneCallDetailsHelper(mResources, mCallTypeHelper,
                mPhoneNumberHelper);
        mVoicemailStatusHelper = new VoicemailStatusHelperImpl();
        mAsyncQueryHandler = new CallDetailActivityQueryHandler(this);
        mHeaderTextView = (TextView) findViewById(R.id.header_text);
        mStatusMessageView = findViewById(R.id.voicemail_status);
        mStatusMessageText = (TextView) findViewById(R.id.voicemail_status_message);
        mStatusMessageAction = (TextView) findViewById(R.id.voicemail_status_action);
        mDefaultCountryIso = ContactsUtils.getCurrentCountryIso(this);
        mProximitySensorManager = new ProximitySensorManager(this, mProximitySensorListener);
        mContactInfoHelper = new ContactInfoHelper(this, ContactsUtils.getCurrentCountryIso(this));
        configureActionBar();
        optionallyHandleVoicemail();
        if (getIntent().getBooleanExtra(EXTRA_FROM_NOTIFICATION, false)) {
            closeSystemDialogs();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateData(getCallLogEntryUris());
    }

    /**
     * Handle voicemail playback or hide voicemail ui.
     * <p>
     * If the Intent used to start this Activity contains the suitable extras, then start voicemail
     * playback.  If it doesn't, then hide the voicemail ui.
     */
    private void optionallyHandleVoicemail() {
        View voicemailContainer = findViewById(R.id.voicemail_container);
        if (hasVoicemail()) {
            // Has voicemail: add the voicemail fragment.  Add suitable arguments to set the uri
            // to play and optionally start the playback.
            // Do a query to fetch the voicemail status messages.
            VoicemailPlaybackFragment playbackFragment = new VoicemailPlaybackFragment();
            Bundle fragmentArguments = new Bundle();
            fragmentArguments.putParcelable(EXTRA_VOICEMAIL_URI, getVoicemailUri());
            if (getIntent().getBooleanExtra(EXTRA_VOICEMAIL_START_PLAYBACK, false)) {
                fragmentArguments.putBoolean(EXTRA_VOICEMAIL_START_PLAYBACK, true);
            }
            playbackFragment.setArguments(fragmentArguments);
            voicemailContainer.setVisibility(View.VISIBLE);
            getFragmentManager().beginTransaction()
                    .add(R.id.voicemail_container, playbackFragment).commitAllowingStateLoss();
            mAsyncQueryHandler.startVoicemailStatusQuery(getVoicemailUri());
            markVoicemailAsRead(getVoicemailUri());
        } else {
            // No voicemail uri: hide the status view.
            mStatusMessageView.setVisibility(View.GONE);
            voicemailContainer.setVisibility(View.GONE);
        }
    }

    private boolean hasVoicemail() {
        return getVoicemailUri() != null;
    }

    private Uri getVoicemailUri() {
        return getIntent().getParcelableExtra(EXTRA_VOICEMAIL_URI);
    }

    private void markVoicemailAsRead(final Uri voicemailUri) {
        mAsyncTaskExecutor.submit(Tasks.MARK_VOICEMAIL_READ, new AsyncTask<Void, Void, Void>() {
            @Override
            public Void doInBackground(Void... params) {
                ContentValues values = new ContentValues();
                values.put(Voicemails.IS_READ, true);
                getContentResolver().update(voicemailUri, values,
                        Voicemails.IS_READ + " = 0", null);
                return null;
            }
        });
    }

    /**
     * Returns the list of URIs to show.
     * <p>
     * There are two ways the URIs can be provided to the activity: as the data on the intent, or as
     * a list of ids in the call log added as an extra on the URI.
     * <p>
     * If both are available, the data on the intent takes precedence.
     */
    private Uri[] getCallLogEntryUris() {
        Uri uri = getIntent().getData();
        if (uri != null) {
            // If there is a data on the intent, it takes precedence over the extra.
            return new Uri[]{ uri };
        }
        long[] ids = getIntent().getLongArrayExtra(EXTRA_CALL_LOG_IDS);
        Uri[] uris = new Uri[ids.length];
        for (int index = 0; index < ids.length; ++index) {
            uris[index] = ContentUris.withAppendedId(Calls.CONTENT_URI_WITH_VOICEMAIL, ids[index]);
        }
        return uris;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (mCallDetailHeader.handleKeyDown(keyCode, event)) {
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    /**
     * Update user interface with details of given call.
     *
     * @param callUris URIs into {@link CallLog.Calls} of the calls to be displayed
     */
    private void updateData(final Uri... callUris) {
        class UpdateContactDetailsTask extends AsyncTask<Void, Void, PhoneCallDetails[]> {
            @Override
            public PhoneCallDetails[] doInBackground(Void... params) {
                // TODO: All phone calls correspond to the same person, so we can make a single
                // lookup.
                final int numCalls = callUris.length;
                PhoneCallDetails[] details = new PhoneCallDetails[numCalls];
                try {
                    for (int index = 0; index < numCalls; ++index) {
                        details[index] = getPhoneCallDetailsForUri(callUris[index]);
                    }
                    return details;
                } catch (IllegalArgumentException e) {
                    // Something went wrong reading in our primary data.
                    Log.w(TAG, "invalid URI starting call details", e);
                    return null;
                }
            }

            @Override
            public void onPostExecute(PhoneCallDetails[] details) {
                if (details == null) {
                    // Somewhere went wrong: we're going to bail out and show error to users.
                    Toast.makeText(CallDetailActivity.this, R.string.toast_call_detail_error,
                            Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }

                // We know that all calls are from the same number and the same contact, so pick the
                // first.
                PhoneCallDetails firstDetails = details[0];
                mNumber = firstDetails.number.toString();

                // Set the details header, based on the first phone call.
                mPhoneCallDetailsHelper.setCallDetailsHeader(mHeaderTextView, firstDetails);
                mCallDetailHeader.updateViews(mNumber, firstDetails);

                mHasEditNumberBeforeCallOption = mCallDetailHeader.canEditNumberBeforeCall();
                mHasTrashOption = hasVoicemail();
                mHasRemoveFromCallLogOption = !hasVoicemail();
                invalidateOptionsMenu();

                ListView historyList = (ListView) findViewById(R.id.history);
                historyList.setAdapter(
                        new CallDetailHistoryAdapter(CallDetailActivity.this, mInflater,
                                mCallTypeHelper, details, hasVoicemail(),
                                mCallDetailHeader.canPlaceCallsTo(),
                                findViewById(R.id.controls)));
                BackScrollManager.bind(
                        new ScrollableHeader() {
                            private View mControls = findViewById(R.id.controls);
                            private View mPhoto = findViewById(R.id.contact_background_sizer);
                            private View mHeader = findViewById(R.id.photo_text_bar);
                            private View mSeparator = findViewById(R.id.blue_separator);

                            @Override
                            public void setOffset(int offset) {
                                mControls.setY(-offset);
                            }

                            @Override
                            public int getMaximumScrollableHeaderOffset() {
                                // We can scroll the photo out, but we should keep the header if
                                // present.
                                if (mHeader.getVisibility() == View.VISIBLE) {
                                    return mPhoto.getHeight() - mHeader.getHeight();
                                } else {
                                    // If the header is not present, we should also scroll out the
                                    // separator line.
                                    return mPhoto.getHeight() + mSeparator.getHeight();
                                }
                            }
                        },
                        historyList);
                mCallDetailHeader.loadContactPhotos(firstDetails.photoUri);
                findViewById(R.id.call_detail).setVisibility(View.VISIBLE);
            }
        }
        mAsyncTaskExecutor.submit(Tasks.UPDATE_PHONE_CALL_DETAILS, new UpdateContactDetailsTask());
    }

    /** Return the phone call details for a given call log URI. */
    private PhoneCallDetails getPhoneCallDetailsForUri(Uri callUri) {
        ContentResolver resolver = getContentResolver();
        Cursor callCursor = resolver.query(callUri, CALL_LOG_PROJECTION, null, null, null);
        try {
            if (callCursor == null || !callCursor.moveToFirst()) {
                throw new IllegalArgumentException("Cannot find content: " + callUri);
            }

            // Read call log specifics.
            String number = callCursor.getString(NUMBER_COLUMN_INDEX);
            long date = callCursor.getLong(DATE_COLUMN_INDEX);
            long duration = callCursor.getLong(DURATION_COLUMN_INDEX);
            int callType = callCursor.getInt(CALL_TYPE_COLUMN_INDEX);
            String countryIso = callCursor.getString(COUNTRY_ISO_COLUMN_INDEX);
            final String geocode = callCursor.getString(GEOCODED_LOCATION_COLUMN_INDEX);

            if (TextUtils.isEmpty(countryIso)) {
                countryIso = mDefaultCountryIso;
            }

            // Formatted phone number.
            final CharSequence formattedNumber;
            // Read contact specifics.
            final CharSequence nameText;
            final int numberType;
            final CharSequence numberLabel;
            final Uri photoUri;
            final Uri lookupUri;
            // If this is not a regular number, there is no point in looking it up in the contacts.
            ContactInfo info =
                    mPhoneNumberHelper.canPlaceCallsTo(number)
                    && !mPhoneNumberHelper.isVoicemailNumber(number)
                            ? mContactInfoHelper.lookupNumber(number, countryIso)
                            : null;
            if (info == null) {
                formattedNumber = mPhoneNumberHelper.getDisplayNumber(number, null);
                nameText = "";
                numberType = 0;
                numberLabel = "";
                photoUri = null;
                lookupUri = null;
            } else {
                formattedNumber = info.formattedNumber;
                nameText = info.name;
                numberType = info.type;
                numberLabel = info.label;
                photoUri = info.photoUri;
                lookupUri = info.lookupUri;
            }
            return new PhoneCallDetails(number, formattedNumber, countryIso, geocode,
                    new int[]{ callType }, date, duration,
                    nameText, numberType, numberLabel, lookupUri, photoUri);
        } finally {
            if (callCursor != null) {
                callCursor.close();
            }
        }
    }

    protected void updateVoicemailStatusMessage(Cursor statusCursor) {
        if (statusCursor == null) {
            mStatusMessageView.setVisibility(View.GONE);
            return;
        }
        final StatusMessage message = getStatusMessage(statusCursor);
        if (message == null || !message.showInCallDetails()) {
            mStatusMessageView.setVisibility(View.GONE);
            return;
        }

        mStatusMessageView.setVisibility(View.VISIBLE);
        mStatusMessageText.setText(message.callDetailsMessageId);
        if (message.actionMessageId != -1) {
            mStatusMessageAction.setText(message.actionMessageId);
        }
        if (message.actionUri != null) {
            mStatusMessageAction.setClickable(true);
            mStatusMessageAction.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(Intent.ACTION_VIEW, message.actionUri));
                }
            });
        } else {
            mStatusMessageAction.setClickable(false);
        }
    }

    private StatusMessage getStatusMessage(Cursor statusCursor) {
        List<StatusMessage> messages = mVoicemailStatusHelper.getStatusMessages(statusCursor);
        if (messages.size() == 0) {
            return null;
        }
        // There can only be a single status message per source package, so num of messages can
        // at most be 1.
        if (messages.size() > 1) {
            Log.w(TAG, String.format("Expected 1, found (%d) num of status messages." +
                    " Will use the first one.", messages.size()));
        }
        return messages.get(0);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.call_details_options, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // This action deletes all elements in the group from the call log.
        // We don't have this action for voicemails, because you can just use the trash button.
        menu.findItem(R.id.menu_remove_from_call_log).setVisible(mHasRemoveFromCallLogOption);
        menu.findItem(R.id.menu_edit_number_before_call).setVisible(mHasEditNumberBeforeCallOption);
        menu.findItem(R.id.menu_trash).setVisible(mHasTrashOption);
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

    public void onMenuRemoveFromCallLog(MenuItem menuItem) {
        final StringBuilder callIds = new StringBuilder();
        for (Uri callUri : getCallLogEntryUris()) {
            if (callIds.length() != 0) {
                callIds.append(",");
            }
            callIds.append(ContentUris.parseId(callUri));
        }
        mAsyncTaskExecutor.submit(Tasks.REMOVE_FROM_CALL_LOG_AND_FINISH,
                new AsyncTask<Void, Void, Void>() {
                    @Override
                    public Void doInBackground(Void... params) {
                        getContentResolver().delete(Calls.CONTENT_URI_WITH_VOICEMAIL,
                                Calls._ID + " IN (" + callIds + ")", null);
                        return null;
                    }

                    @Override
                    public void onPostExecute(Void result) {
                        finish();
                    }
                });
    }

    public void onMenuEditNumberBeforeCall(MenuItem menuItem) {
        startActivity(new Intent(Intent.ACTION_DIAL, ContactsUtils.getCallUri(mNumber)));
    }

    public void onMenuTrashVoicemail(MenuItem menuItem) {
        final Uri voicemailUri = getVoicemailUri();
        mAsyncTaskExecutor.submit(Tasks.DELETE_VOICEMAIL_AND_FINISH,
                new AsyncTask<Void, Void, Void>() {
                    @Override
                    public Void doInBackground(Void... params) {
                        getContentResolver().delete(voicemailUri, null, null);
                        return null;
                    }
                    @Override
                    public void onPostExecute(Void result) {
                        finish();
                    }
                });
    }

    private void configureActionBar() {
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_HOME);
        }
    }

    /** Invoked when the user presses the home button in the action bar. */
    private void onHomeSelected() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Calls.CONTENT_URI);
        // This will open the call log even if the detail view has been opened directly.
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onPause() {
        // Immediately stop the proximity sensor.
        disableProximitySensor(false);
        mProximitySensorListener.clearPendingRequests();
        super.onPause();
    }

    @Override
    public void enableProximitySensor() {
        mProximitySensorManager.enable();
    }

    @Override
    public void disableProximitySensor(boolean waitForFarState) {
        mProximitySensorManager.disable(waitForFarState);
    }

    private void closeSystemDialogs() {
        sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
    }
}
