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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.Contacts.Intents.Insert;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.contacts.calllog.PhoneNumberHelper;
import com.android.contacts.format.FormatUtils;
import com.android.contacts.util.ClipboardUtils;
import com.android.contacts.util.Constants;

public class CallDetailHeader {
    private Activity mActivity;
    private Resources mResources;
    private PhoneNumberHelper mPhoneNumberHelper;
    private ContactPhotoManager mContactPhotoManager;

    private String mNumber;

    private TextView mHeaderTextView;
    private View mHeaderOverlayView;
    private ImageView mMainActionView;
    private ImageButton mMainActionPushLayerView;
    private ImageView mContactBackgroundView;

    private ActionMode mPhoneNumberActionMode;
    private boolean mHasEditNumberBeforeCallOption;
    private boolean mCanPlaceCallsTo;

    private CharSequence mPhoneNumberLabelToCopy;
    private CharSequence mPhoneNumberToCopy;

    public interface Data {
        CharSequence getName();
        CharSequence getNumber();
        int getNumberType();
        CharSequence getNumberLabel();
        CharSequence getFormattedNumber();
        Uri getContactUri();
    }

    private final View.OnClickListener mPrimaryActionListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            if (finishPhoneNumerSelectedActionModeIfShown()) {
                return;
            }
            mActivity.startActivity(((ViewEntry) view.getTag()).primaryIntent);
        }
    };

    private final View.OnClickListener mSecondaryActionListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            if (finishPhoneNumerSelectedActionModeIfShown()) {
                return;
            }
            mActivity.startActivity(((ViewEntry) view.getTag()).secondaryIntent);
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

    public CallDetailHeader(Activity activity, PhoneNumberHelper phoneNumberHelper) {
        mActivity = activity;
        mResources = activity.getResources();
        mPhoneNumberHelper = phoneNumberHelper;
        mContactPhotoManager = ContactPhotoManager.getInstance(activity);

        mHeaderTextView = (TextView) activity.findViewById(R.id.header_text);
        mHeaderOverlayView = activity.findViewById(R.id.photo_text_bar);
        mMainActionView = (ImageView) activity.findViewById(R.id.main_action);
        mMainActionPushLayerView = (ImageButton) activity.findViewById(R.id.main_action_push_layer);
        mContactBackgroundView = (ImageView) activity.findViewById(R.id.contact_background);
    }

    /**
     * If the phone number is selected, unselect it and return {@code true}.
     * Otherwise, just {@code false}.
     */
    private boolean finishPhoneNumerSelectedActionModeIfShown() {
        if (mPhoneNumberActionMode == null) return false;
        mPhoneNumberActionMode.finish();
        return true;
    }

    private void startPhoneNumberSelectedActionMode(View targetView) {
        mPhoneNumberActionMode =
                mActivity.startActionMode(new PhoneNumberActionModeCallback(targetView));
    }

    private class PhoneNumberActionModeCallback implements ActionMode.Callback {
        private final View mTargetView;
        private final Drawable mOriginalViewBackground;

        public PhoneNumberActionModeCallback(View targetView) {
            mTargetView = targetView;

            // Highlight the phone number view.  Remember the old background, and put a new one.
            mOriginalViewBackground = mTargetView.getBackground();
            mTargetView.setBackgroundColor(mResources.getColor(R.color.item_selected));
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            if (TextUtils.isEmpty(mPhoneNumberToCopy)) return false;

            mActivity.getMenuInflater().inflate(R.menu.call_details_cab, menu);
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
                    ClipboardUtils.copyText(mActivity, mPhoneNumberLabelToCopy,
                            mPhoneNumberToCopy, true);
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

    public void updateViews(String number, Data data) {
        // Cache the details about the phone number.
        final boolean isVoicemailNumber = mPhoneNumberHelper.isVoicemailNumber(number);
        final boolean isSipNumber = mPhoneNumberHelper.isSipNumber(number);

        mNumber = number;
        mCanPlaceCallsTo = mPhoneNumberHelper.canPlaceCallsTo(number);

        // Let user view contact details if they exist, otherwise add option to create new
        // contact from this number.
        final Intent mainActionIntent;
        final int mainActionIcon;
        final String mainActionDescription;

        final CharSequence dataName = data.getName();
        final CharSequence dataNumber = data.getNumber();
        final Uri contactUri = data.getContactUri();

        final CharSequence nameOrNumber;
        if (!TextUtils.isEmpty(dataName)) {
            nameOrNumber = dataName;
        } else {
            nameOrNumber = dataNumber;
        }

        if (contactUri != null) {
            mainActionIntent = new Intent(Intent.ACTION_VIEW, contactUri);
            // This will launch People's detail contact screen, so we probably want to
            // treat it as a separate People task.
            mainActionIntent.setFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            mainActionIcon = R.drawable.ic_contacts_holo_dark;
            mainActionDescription =
                mResources.getString(R.string.description_view_contact, nameOrNumber);
        } else if (isVoicemailNumber) {
            mainActionIntent = null;
            mainActionIcon = 0;
            mainActionDescription = null;
        } else if (isSipNumber) {
            // TODO: This item is currently disabled for SIP addresses, because
            // the Insert.PHONE extra only works correctly for PSTN numbers.
            //
            // To fix this for SIP addresses, we need to:
            // - define ContactsContract.Intents.Insert.SIP_ADDRESS, and use it here if
            //   the current number is a SIP address
            // - update the contacts UI code to handle Insert.SIP_ADDRESS by
            //   updating the SipAddress field
            // and then we can remove the "!isSipNumber" check above.
            mainActionIntent = null;
            mainActionIcon = 0;
            mainActionDescription = null;
        } else if (mCanPlaceCallsTo) {
            mainActionIntent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
            mainActionIntent.setType(Contacts.CONTENT_ITEM_TYPE);
            mainActionIntent.putExtra(Insert.PHONE, number);
            mainActionIcon = R.drawable.ic_add_contact_holo_dark;
            mainActionDescription = mResources.getString(R.string.description_add_contact);
        } else {
            // If we cannot call the number, when we probably cannot add it as a contact either.
            // This is usually the case of private, unknown, or payphone numbers.
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
            mMainActionPushLayerView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mActivity.startActivity(mainActionIntent);
                }
            });
            mMainActionPushLayerView.setContentDescription(mainActionDescription);
            mHeaderTextView.setVisibility(View.VISIBLE);
            mHeaderOverlayView.setVisibility(View.VISIBLE);
        }

        // This action allows to call the number that places the call.
        if (mCanPlaceCallsTo) {
            final CharSequence displayNumber =
                mPhoneNumberHelper.getDisplayNumber(
                        dataNumber, data.getFormattedNumber());

            ViewEntry entry = new ViewEntry(
                    mResources.getString(R.string.menu_callNumber,
                        FormatUtils.forceLeftToRight(displayNumber)),
                    ContactsUtils.getCallIntent(number),
                    mResources.getString(R.string.description_call, nameOrNumber));

            // Only show a label if the number is shown and it is not a SIP address.
            if (!TextUtils.isEmpty(dataName)
                    && !TextUtils.isEmpty(dataNumber)
                    && !PhoneNumberUtils.isUriNumber(dataNumber.toString())) {
                entry.label = Phone.getTypeLabel(mResources, data.getNumberType(),
                        data.getNumberLabel());
                    }

            // The secondary action allows to send an SMS to the number that placed the
            // call.
            if (mPhoneNumberHelper.canSendSmsTo(number)) {
                entry.setSecondaryAction(
                        R.drawable.ic_text_holo_dark,
                        new Intent(Intent.ACTION_SENDTO,
                            Uri.fromParts("sms", number, null)),
                        mResources.getString(R.string.description_send_text_message, nameOrNumber));
            }

            configureCallButton(entry);
            mPhoneNumberToCopy = displayNumber;
            mPhoneNumberLabelToCopy = entry.label;
        } else {
            disableCallButton();
            mPhoneNumberToCopy = null;
            mPhoneNumberLabelToCopy = null;
        }

        mHasEditNumberBeforeCallOption =
            mCanPlaceCallsTo && !isSipNumber && !isVoicemailNumber;
    }

    /** Load the contact photos and places them in the corresponding views. */
    public void loadContactPhotos(Uri photoUri) {
        mContactPhotoManager.loadPhoto(mContactBackgroundView, photoUri,
                mContactBackgroundView.getWidth(), true);
    }

    public boolean canEditNumberBeforeCall() {
        return mHasEditNumberBeforeCallOption;
    }

    public boolean canPlaceCallsTo() {
        return mCanPlaceCallsTo;
    }

    static final class ViewEntry {
        public final String text;
        public final Intent primaryIntent;
        /** The description for accessibility of the primary action. */
        public final String primaryDescription;

        public CharSequence label = null;
        /** Icon for the secondary action. */
        public int secondaryIcon = 0;
        /** Intent for the secondary action. If not null, an icon must be defined. */
        public Intent secondaryIntent = null;
        /** The description for accessibility of the secondary action. */
        public String secondaryDescription = null;

        public ViewEntry(String text, Intent intent, String description) {
            this.text = text;
            primaryIntent = intent;
            primaryDescription = description;
        }

        public void setSecondaryAction(int icon, Intent intent, String description) {
            secondaryIcon = icon;
            secondaryIntent = intent;
            secondaryDescription = description;
        }
    }

    /** Disables the call button area, e.g., for private numbers. */
    private void disableCallButton() {
        mActivity.findViewById(R.id.call_and_sms).setVisibility(View.GONE);
    }

    /** Configures the call button area using the given entry. */
    private void configureCallButton(ViewEntry entry) {
        View convertView = mActivity.findViewById(R.id.call_and_sms);
        convertView.setVisibility(View.VISIBLE);

        ImageView icon = (ImageView) convertView.findViewById(R.id.call_and_sms_icon);
        View divider = convertView.findViewById(R.id.call_and_sms_divider);
        TextView text = (TextView) convertView.findViewById(R.id.call_and_sms_text);

        View mainAction = convertView.findViewById(R.id.call_and_sms_main_action);
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

        TextView label = (TextView) convertView.findViewById(R.id.call_and_sms_label);
        if (TextUtils.isEmpty(entry.label)) {
            label.setVisibility(View.GONE);
        } else {
            label.setText(entry.label);
            label.setVisibility(View.VISIBLE);
        }
    }

    public boolean handleKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_CALL: {
                // Make sure phone isn't already busy before starting direct call
                TelephonyManager tm = (TelephonyManager)
                        mActivity.getSystemService(Context.TELEPHONY_SERVICE);
                if (tm.getCallState() == TelephonyManager.CALL_STATE_IDLE) {
                    mActivity.startActivity(ContactsUtils.getCallIntent(
                            Uri.fromParts(Constants.SCHEME_TEL, mNumber, null)));
                    return true;
                }
            }
        }

        return false;
    }
}
