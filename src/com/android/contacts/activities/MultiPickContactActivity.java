/**
 * Copyright (C) 2013-2014, The Linux Foundation. All Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.contacts.activities;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.AsyncQueryHandler;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.RemoteException;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract;
import android.provider.ContactsContract.InternalContactCounts;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Groups;
import android.provider.ContactsContract.RawContacts;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.telephony.SubscriptionManager;
import android.text.Editable;
import android.text.format.DateUtils;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CursorAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.SectionIndexer;
import android.widget.TextView;
import android.widget.Toast;

import com.android.contacts.R;
import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.ContactPhotoManager.DefaultImageRequest;
import com.android.contacts.common.SimContactsConstants;
import com.android.contacts.common.SimContactsOperation;
import com.android.contacts.common.list.AccountFilterActivity;
import com.android.contacts.common.list.ContactListFilter;
import com.android.contacts.common.list.ContactListItemView;
import com.android.contacts.common.list.ContactsSectionIndexer;
import com.android.contacts.common.list.DefaultContactListAdapter;
import com.android.contacts.common.MoreContactUtils;
import com.android.contacts.common.model.account.SimAccountType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class MultiPickContactActivity extends ListActivity implements
        TextView.OnEditorActionListener, View.OnTouchListener,
        SearchView.OnQueryTextListener, SearchView.OnCloseListener,
        View.OnFocusChangeListener, DialogInterface.OnClickListener,
        DialogInterface.OnKeyListener {
    private final static String TAG = "MultiPickContactActivity";
    private final static boolean DEBUG = true;

    private static final String SORT_ORDER = " desc";

    static final String[] CONTACTS_SUMMARY_PROJECTION = new String[] {
            Contacts._ID, // 0
            Contacts.DISPLAY_NAME_PRIMARY, // 1
            Contacts.PHOTO_ID, // 2
            Contacts.LOOKUP_KEY, // 3
            RawContacts.ACCOUNT_TYPE, // 4
            RawContacts.ACCOUNT_NAME, // 5
            Contacts.NAME_RAW_CONTACT_ID, // 6
            Contacts.PHOTO_THUMBNAIL_URI // 7
    };

    static final String[] PHONES_PROJECTION = new String[] {
            Data.CONTACT_ID, // 0
            Data.DISPLAY_NAME, // 1
            Data.PHOTO_ID, // 2
            Data.LOOKUP_KEY, // 3
            Phone._ID, // 4
            Phone.TYPE, // 5
            Phone.LABEL, // 6
            Phone.NUMBER, // 7
    };

    static final String[] EMAILS_PROJECTION = new String[] {
            Data.CONTACT_ID, // 0
            Data.DISPLAY_NAME, // 1
            Data.PHOTO_ID, // 2
            Data.LOOKUP_KEY, // 3
            Email._ID, // 4
            Email.ADDRESS // 5
    };

    static final String[] CALL_LOG_PROJECTION = new String[] {
            Calls._ID,
            Calls.NUMBER,
            Calls.DATE,
            Calls.DURATION,
            Calls.TYPE,
            Calls.CACHED_NAME,
            Calls.CACHED_NUMBER_TYPE,
            Calls.CACHED_NUMBER_LABEL,
            Calls.PHONE_ACCOUNT_ID,
            Calls.GEOCODED_LOCATION,
    };

    static final String CONTACTS_SELECTION = Contacts.IN_VISIBLE_GROUP + "=1";

    static final String PHONES_SELECTION = RawContacts.ACCOUNT_TYPE + "<>?";

    static final String[] PHONES_SELECTION_ARGS = {
            SimContactsConstants.ACCOUNT_TYPE_SIM
    };

    public static final int CONTACT_COLUMN_ID = 0;
    public static final int CONTACT_COLUMN_DISPLAY_NAME = 1;
    public static final int CONTACT_COLUMN_PHOTO_ID = 2;
    public static final int CONTACT_COLUMN_LOOKUP_KEY = 3;
    // contacts query specific columns
    public static final int CONTACT_COLUMN_ACCOUNT_TYPE = 4;
    public static final int CONTACT_COLUMN_ACCOUNT_NAME = 5;
    public static final int CONTACT_COLUMN_RAW_CONTACT_ID = 6;
    public static final int CONTACT_COLUMN_PHOTO_URI = 7;
    // phone query specific columns
    public static final int PHONE_COLUMN_ID = 4;
    public static final int PHONE_COLUMN_TYPE = 5;
    public static final int PHONE_COLUMN_LABEL = 6;
    public static final int PHONE_COLUMN_NUMBER = 7;
    // email query specific columns
    public static final int EMAIL_COLUMN_ID = 4;
    public static final int EMAIL_COLUMN_ADDRESS = 5;

    public static final int CALLLOG_COLUMN_ID = 0;
    public static final int CALLLOG_COLUMN_NUMBER = 1;
    public static final int CALLLOG_COLUMN_DATE = 2;
    public static final int CALLLOG_COLUMN_DURATION = 3;
    public static final int CALLLOG_COLUMN_CALL_TYPE = 4;
    public static final int CALLLOG_COLUMN_CALLER_NAME = 5;
    public static final int CALLLOG_COLUMN_CALLER_NUMBERTYPE = 6;
    public static final int CALLLOG_COLUMN_CALLER_NUMBERLABEL = 7;
    public static final int CALLLOG_COLUMN_PHONE_ACCOUNT = 8;
    public static final int CALLLOG_COLUMN_CALLER_LOCATION = 9;

    private static final int QUERY_TOKEN = 42;
    private static final int MODE_MASK_SEARCH = 0x80000000;

    private static final int MODE_DEFAULT_CONTACT = 0;
    private static final int MODE_DEFAULT_PHONE = 1;
    private static final int MODE_DEFAULT_EMAIL = 1 << 1;
    private static final int MODE_DEFAULT_CALL = 1 << 1 << 1;
    private static final int MODE_DEFAULT_SIM = 1 << 1 << 1 << 1;
    private static final int MODE_SEARCH_CONTACT = MODE_DEFAULT_CONTACT | MODE_MASK_SEARCH;
    private static final int MODE_SEARCH_PHONE = MODE_DEFAULT_PHONE | MODE_MASK_SEARCH;
    private static final int MODE_SEARCH_EMAIL = MODE_DEFAULT_EMAIL | MODE_MASK_SEARCH;
    private static final int MODE_SEARCH_CALL = MODE_DEFAULT_CALL | MODE_MASK_SEARCH;
    private static final int MODE_SEARCH_SIM = MODE_DEFAULT_SIM | MODE_MASK_SEARCH;
    private static final int DIALOG_DEL_CALL = 1;

    public static final String ADD_GROUP_MEMBERS = "add_group_members";

    public static final String ACTION_MULTI_PICK = "com.android.contacts.action.MULTI_PICK";
    static final String ACTION_MULTI_PICK_EMAIL = "com.android.contacts.action.MULTI_PICK_EMAIL";
    static final String ACTION_MULTI_PICK_CALL = "com.android.contacts.action.MULTI_PICK_CALL";
    static final String ACTION_MULTI_PICK_SIM = "com.android.contacts.action.MULTI_PICK_SIM";

    public static final String EXTRA_IS_SELECT_ALL_DISALLOWED = "is_select_all_disallowed";
    public static final String EXTRA_SELECT_CALLLOG = "selectcalllog";
    public static final String EXTRA_NOT_SHOW_SIM_FLAG = "not_sim_show";
    public static final String EXTRA_GROUP_ID = "group_id";
    public static final String EXTRA_GROUP_ACTION = "group_action";

    public static final int GROUP_ACTION_ADD_MEMBER = 0;
    public static final int GROUP_ACTION_MOVE_MEMBER = 1;
    public static final int GROUP_ACTION_NONE = -1;

    private ContactItemListAdapter mAdapter;
    private QueryHandler mQueryHandler;
    private Bundle mChoiceSet;

    private ActionBar mActionBar;
    private SearchView mSearchView;
    private ViewGroup mSearchViewContainer;

    private int mMode;
    private boolean mSelectCallLog;
    private boolean mSearchUiVisible = false;

    private ArrayList<Long> mGroupIds = new ArrayList<Long>();

    private ProgressDialog mProgressDialog;
    private SimContactsOperation mSimContactsOperation;
    private AccountManager accountManager;
    private CharSequence[] mLabelArray;
    private AccountManager mAccountManager;

    private static final String[] SIM_COLUMN_NAMES = new String[] {
            "name",
            "number",
            "emails",
            "anrs",
            "_id"
    };

    private static final int SIM_COLUMN_DISPLAY_NAME = 0;
    private static final int SIM_COLUMN_NUMBER = 1;
    private static final int SIM_COLUMN_EMAILS = 2;
    private static final int SIM_COLUMN_ANRS = 3;
    private static final int SIM_COLUMN_ID = 4;

    private int MAX_CONTACTS_NUM_TO_SELECT_ONCE = 500;

    //registerReceiver to update content when airplane mode change.
    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
                updateContent();

                // If now is airplane mode, should cancel import sim contacts
                if (isPickSim() && MoreContactUtils.isAPMOnAndSIMPowerDown(context)) {
                    cancelSimContactsImporting();
                }
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        String action = intent.getAction();
        boolean isContact = intent.getBooleanExtra(SimContactsConstants.IS_CONTACT, false);

        if (Intent.ACTION_DELETE.equals(action)) {
            mMode = MODE_DEFAULT_CONTACT;
            setTitle(R.string.menu_deleteContact);
        } else if (SimContactsConstants.ACTION_MULTI_PICK.equals(action)) {
            mMode = isContact ? MODE_DEFAULT_CONTACT : MODE_DEFAULT_PHONE;
        } else if (SimContactsConstants.ACTION_MULTI_PICK_EMAIL.equals(action)) {
            mMode = MODE_DEFAULT_EMAIL;
        } else if (SimContactsConstants.ACTION_MULTI_PICK_CALL.equals(action)) {
            mMode = MODE_DEFAULT_CALL;
            setTitle(R.string.delete_call_title);
            if (intent.getBooleanExtra(EXTRA_SELECT_CALLLOG, false)) {
                mSelectCallLog = true;
                setTitle(R.string.select_call_title);
            }
        } else if (SimContactsConstants.ACTION_MULTI_PICK_SIM.equals(action)) {
            mMode = MODE_DEFAULT_SIM;
        }

        mChoiceSet = new Bundle();
        mAdapter = new ContactItemListAdapter(this);
        getListView().setAdapter(mAdapter);
        mQueryHandler = new QueryHandler(this);
        mSimContactsOperation = new SimContactsOperation(this);
        mAccountManager = AccountManager.get(this);

        mActionBar = getActionBar();
        mActionBar.setHomeButtonEnabled(true);
        mActionBar.setDisplayHomeAsUpEnabled(true);
        mActionBar.setDisplayShowTitleEnabled(true);
        inflateSearchView();

        startQuery();
        //register receiver.
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        registerReceiver(mBroadcastReceiver, filter);
    }

    private void inflateSearchView() {
        LayoutInflater inflater = LayoutInflater.from(mActionBar.getThemedContext());
        mSearchViewContainer = (ViewGroup) inflater.inflate(R.layout.custom_action_bar, null);
        mSearchView = (SearchView) mSearchViewContainer.findViewById(R.id.search_view);

        if (isPickCall() || isPickSim()) {
            mSearchView.setVisibility(View.GONE);
            return;
        }

        // In order to make the SearchView look like "shown via search menu", we need to
        // manually setup its state. See also DialtactsActivity.java and ActionBarAdapter.java.
        mSearchView.setIconifiedByDefault(true);
        mSearchView.setQueryHint(getString(R.string.hint_findContacts));
        mSearchView.setIconified(false);
        mSearchView.setFocusable(true);

        mSearchView.setOnQueryTextListener(this);
        mSearchView.setOnCloseListener(this);
        mSearchView.setOnQueryTextFocusChangeListener(this);

        mActionBar.setCustomView(mSearchViewContainer, new ActionBar.LayoutParams(
                ActionBar.LayoutParams.MATCH_PARENT, ActionBar.LayoutParams.WRAP_CONTENT));
        mActionBar.setDisplayShowCustomEnabled(true);

        configureSearchMode();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();

        inflater.inflate(R.menu.search_menu, menu);
        inflater.inflate(R.menu.multi_contact_picker_options, menu);

        final MenuItem selectAllItem = menu.findItem(R.id.select_all_check);
        selectAllItem.setVisible(!mSearchUiVisible);

        final MenuItem doneItem = menu.findItem(R.id.done);
        doneItem.setVisible(!mChoiceSet.isEmpty());

        final MenuItem searchItem = menu.findItem(R.id.menu_search);
        searchItem.setVisible(!mSearchUiVisible);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_search:
                mSearchUiVisible = !mSearchUiVisible;
                if (!mSearchUiVisible) {
                    exitSearchMode();
                } else {
                    configureSearchMode();
                }
                return true;
            case R.id.select_all_check:
                selectAll(!item.isChecked());
                return true;
            case R.id.done:
                if (isSearchMode()) {
                    exitSearchMode();
                }
                if (mMode == MODE_DEFAULT_CONTACT) {
                    if (SimContactsConstants.ACTION_MULTI_PICK.equals(getIntent().getAction())) {
                        if (mChoiceSet.size() > MAX_CONTACTS_NUM_TO_SELECT_ONCE) {
                            String text = getString(R.string.too_many_contacts_add_to_group,
                                    MAX_CONTACTS_NUM_TO_SELECT_ONCE);
                            Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
                        } else {
                            int memberAction = getIntent().getIntExtra(EXTRA_GROUP_ACTION,
                                    GROUP_ACTION_NONE);
                            switch (memberAction) {
                                case GROUP_ACTION_ADD_MEMBER:
                                    setResult(RESULT_OK, new Intent().putExtras(mChoiceSet));
                                    finish();
                                    break;
                                case GROUP_ACTION_MOVE_MEMBER:
                                    String account = getIntent().getStringExtra(
                                            SimContactsConstants.ACCOUNT_TYPE);
                                    long groupId = getIntent().getLongExtra(EXTRA_GROUP_ID, -1);
                                    showGroupSelectionList(account, groupId);
                                    break;
                                default:
                                    setResultAndFinish();
                                    break;
                            }
                        }
                    } else if (mChoiceSet.size() > 0) {
                        showDialog(R.id.dialog_delete_contact_confirmation);
                    }
                } else if (mMode == MODE_DEFAULT_PHONE) {
                    setResultAndFinish();
                } else if (mMode == MODE_DEFAULT_SIM) {
                    if (mChoiceSet.size() > 0) {
                        showDialog(R.id.dialog_import_sim_contact_confirmation);
                    }
                } else if (mMode == MODE_DEFAULT_EMAIL) {
                    setResultAndFinish();
                } else if (mMode == MODE_DEFAULT_CALL) {
                    if (mChoiceSet.size() > 0) {
                        if (mSelectCallLog) {
                            setResultAndFinish();
                        } else {
                            showDialog(DIALOG_DEL_CALL);
                        }
                    }
                }
                return true;
            case android.R.id.home:
                finish();
                return true;
        }
        return false;
    }

    @Override
    public void onBackPressed() {
        if (mSearchUiVisible) {
            exitSearchMode();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        updateState(query);
        return true;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        updateState(newText);
        return true;
    }

    @Override
    public boolean onClose() {
        if (!TextUtils.isEmpty(mSearchView.getQuery())) {
            mSearchView.setQuery(null, true);
        }
        return true;
    }

    @Override
    public void onFocusChange(View view, boolean hasFocus) {
        switch (view.getId()) {
            case R.id.search_view: {
                if (hasFocus) {
                    final InputMethodManager imm =
                            (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.showSoftInput(mSearchView.findFocus(), 0);
                }
            }
        }
    }

    private void updateState(String query) {
        if (!TextUtils.isEmpty(query)) {
            if (!isSearchMode()) {
                enterSearchMode();
            }
        } else if (isSearchMode()) {
            exitSearchMode();
        }
        doFilter(query);
    }

    private void setResultAndFinish() {
        Intent intent = new Intent();
        Bundle bundle = new Bundle();
        bundle.putBundle(SimContactsConstants.RESULT_KEY, mChoiceSet);
        intent.putExtras(bundle);
        setResult(RESULT_OK, intent);
        finish();
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        hideSoftKeyboard();

        ContactItemCache cache = (ContactItemCache) v.getTag();
        String key = String.valueOf(cache.id);

        if (!mChoiceSet.containsKey(key)) {
            String[] value = null;
            if (isPickContact()) {
                value = new String[] {
                    cache.lookupKey, String.valueOf(cache.id),
                    String.valueOf(cache.nameRawContactId),
                    cache.photoUri, cache.name
                };
            } else if (isPickPhone()) {
                value = new String[] {
                    cache.name, cache.number, cache.type,
                    cache.label, cache.contact_id
                };
            } else if (isPickEmail()) {
                value = new String[] {
                    cache.name, cache.email
                };
            } else if (isPickSim()) {
                value = new String[] {
                    cache.name, cache.number, cache.email, cache.anrs
                };
            } else if (isPickCall() && mSelectCallLog) {
                value = new String[] {
                    cache.name, cache.number
                };
            }
            mChoiceSet.putStringArray(key, value);
        } else {
            mChoiceSet.remove(key);
        }

        updateActionBar();
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK: {
                if (isSearchMode()) {
                    exitSearchMode();
                    return true;
                }
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private void updateActionBar() {
        String countTitle = null;
        if (!mChoiceSet.isEmpty()) {
            countTitle = getResources().getQuantityString(R.plurals.contacts_selected,
                    mChoiceSet.size(), mChoiceSet.size());
        }
        mActionBar.setSubtitle(countTitle);
        invalidateOptionsMenu();
    }

    private boolean isSearchMode() {
        return (mMode & MODE_MASK_SEARCH) == MODE_MASK_SEARCH;
    }

    private void enterSearchMode() {
        mMode |= MODE_MASK_SEARCH;
        configureSearchMode();
    }

    private void exitSearchMode() {
        mSearchUiVisible = false;
        mMode &= ~MODE_MASK_SEARCH;
        configureSearchMode();
    }

    private void configureSearchMode() {
        if (mSearchUiVisible) {
            mSearchViewContainer.setVisibility(View.VISIBLE);
            mSearchView.requestFocus();
        } else {
            mSearchViewContainer.setVisibility(View.GONE);
            mSearchView.setQuery(null, true);
        }
        mActionBar.setDisplayShowTitleEnabled(!mSearchUiVisible);
        updateActionBar();
    }

    @Override
    protected Dialog onCreateDialog(int id, Bundle bundle) {
        switch (id) {
            case R.id.dialog_delete_contact_confirmation:
                return new AlertDialog.Builder(this)
                        .setTitle(R.string.deleteConfirmation_title)
                        .setMessage(getResources().getQuantityString(
                                R.plurals.ContactMultiDeleteConfirmation,
                                mChoiceSet.size(), mChoiceSet.size()))
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok, this)
                        .create();
            case DIALOG_DEL_CALL:
                return new AlertDialog.Builder(this)
                        .setTitle(R.string.title_del_call)
                        .setMessage(R.string.delete_call_alert)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok, this)
                        .create();
            case R.id.dialog_import_sim_contact_confirmation:
                return new AlertDialog.Builder(this)
                        .setTitle(R.string.importConfirmation_title)
                        .setMessage(getResources().getQuantityString(
                                R.plurals.ContactMultiImportConfirmation,
                                mChoiceSet.size(), mChoiceSet.size()))
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok, this)
                        .create();
        }

        return super.onCreateDialog(id, bundle);
    }

    private class DeleteContactsThread extends Thread implements
            DialogInterface.OnCancelListener, DialogInterface.OnClickListener {
        boolean mCanceled = false;
        private String name = null;
        private String number = null;
        private final String[] PROJECTION = new String[] {
                Phone.CONTACT_ID,
                Phone.NUMBER,
                Phone.DISPLAY_NAME
        };
        private final int COLUMN_NUMBER = 1;
        private final int COLUMN_NAME = 2;

        private ArrayList<ContentProviderOperation> mOpsCalls = null;
        private ArrayList<ContentProviderOperation> mOpsContacts = null;

        public DeleteContactsThread() {
        }

        @Override
        public void run() {
            final Context context = MultiPickContactActivity.this;
            final ContentResolver resolver = getContentResolver();

            // The mChoiceSet object will change when activity restart, but
            // DeleteContactsThread running in background, so we need clone the
            // choiceSet to avoid ConcurrentModificationException.
            Bundle choiceSet = (Bundle) mChoiceSet.clone();
            Set<String> keySet = choiceSet.keySet();
            Iterator<String> it = keySet.iterator();

            ContentProviderOperation.Builder builder = null;
            ContentProviderOperation cpo = null;

            // Current contact count we can delete.
            int count = 0;

            // The contacts we batch delete once.
            final int BATCH_DELETE_CONTACT_NUMBER = 100;

            mOpsCalls = new ArrayList<ContentProviderOperation>();
            mOpsContacts = new ArrayList<ContentProviderOperation>();

            while (!mCanceled && it.hasNext()) {
                String id = it.next();
                Uri uri = null;
                if (isPickCall()) {
                    uri = Uri.withAppendedPath(Calls.CONTENT_URI, id);
                    builder = ContentProviderOperation.newDelete(uri);
                    cpo = builder.build();
                    mOpsCalls.add(cpo);
                } else {
                    uri = Uri.withAppendedPath(Contacts.CONTENT_URI, id);
                    long longId = Long.parseLong(id);
                    int subscription = mSimContactsOperation.getSimSubscription(longId);

                    if (subscription == SimContactsConstants.SUB_1
                            || subscription == SimContactsConstants.SUB_2) {
                        if (MoreContactUtils.isAPMOnAndSIMPowerDown(context)) {
                            break;
                        }
                        ContentValues values =
                                mSimContactsOperation.getSimAccountValues(longId);
                        log("values is : " + values + "; sub is " + subscription);
                        if (mSimContactsOperation.delete(values, subscription) == 0) {
                            mProgressDialog.incrementProgressBy(1);
                            continue;
                        }
                    }
                    builder = ContentProviderOperation.newDelete(uri);
                    cpo = builder.build();
                    mOpsContacts.add(cpo);
                }
                // If contacts more than 2000, delete all contacts
                // one by one will cause UI nonresponse.
                mProgressDialog.incrementProgressBy(1);
                // We batch delete contacts every 100.
                if (count % BATCH_DELETE_CONTACT_NUMBER == 0) {
                    batchDelete();
                }
                count++;
            }

            batchDelete();
            mOpsCalls = null;
            mOpsContacts = null;
            Log.d(TAG, "DeleteContactsThread run, progress:" + mProgressDialog.getProgress());
            mProgressDialog.dismiss();
            finish();
        }

        /**
         * Batch delete contacts more efficient than one by one.
         */
        private void batchDelete() {
            try {
                 getContentResolver().applyBatch(CallLog.AUTHORITY, mOpsCalls);
                 getContentResolver().applyBatch(ContactsContract.AUTHORITY, mOpsContacts);
                 mOpsCalls.clear();
                 mOpsContacts.clear();
             } catch (RemoteException e) {
                 e.printStackTrace();
             } catch (OperationApplicationException e) {
                 e.printStackTrace();
             }
        }

        @Override
        public void onCancel(DialogInterface dialog) {
            mCanceled = true;
            Log.d(TAG, "DeleteContactsThread onCancel, progress:" + mProgressDialog.getProgress());
            //  Give a toast show to tell user delete termination
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            if (which == DialogInterface.BUTTON_NEGATIVE) {
                mCanceled = true;
                mProgressDialog.dismiss();
            }
        }
    }

    @Override
    public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_SEARCH:
            case KeyEvent.KEYCODE_CALL:
                return true;
            default:
                return false;
        }
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        CharSequence title = null;
        CharSequence message = null;

        if (isPickCall()) {
            title = getString(R.string.delete_call_title);
            message = getString(R.string.delete_call_message);
        } else if (isPickSim()) {
            title = getString(R.string.import_sim_contacts_title);
            message = getString(R.string.import_sim_contacts_message);
        } else {
            title = getString(R.string.delete_contacts_title);
            message = getString(R.string.delete_contacts_message);
        }

        Thread thread = isPickSim()
                ? new ImportAllSimContactsThread() : new DeleteContactsThread();

        mProgressDialog = new ProgressDialog(MultiPickContactActivity.this);
        mProgressDialog.setTitle(title);
        mProgressDialog.setMessage(message);
        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mProgressDialog.setButton(DialogInterface.BUTTON_NEGATIVE,
                getString(android.R.string.cancel), (DialogInterface.OnClickListener) thread);
        mProgressDialog.setOnCancelListener((DialogInterface.OnCancelListener) thread);
        mProgressDialog.setOnKeyListener(this);
        mProgressDialog.setProgress(0);
        mProgressDialog.setMax(mChoiceSet.size());

        // set dialog can not be canceled by touching outside area of dialog
        mProgressDialog.setCanceledOnTouchOutside(false);
        mProgressDialog.show();

        thread.start();
    }

    @Override
    public void onDestroy() {
        mQueryHandler.removeCallbacksAndMessages(QUERY_TOKEN);
        if (mAdapter.getCursor() != null) {
            mAdapter.getCursor().close();
        }

        if (mProgressDialog != null) {
            mProgressDialog.cancel();
        }

        // unregister receiver.
        if (mBroadcastReceiver != null) {
            unregisterReceiver(mBroadcastReceiver);
        }

        super.onDestroy();
    }

    /**
     * Just get the uri we need to query contacts.
     *
     * @return uri with account info parameter if explicit request contacts fit
     *         current account, else just search contacts fit specified keyword.
     */
    private Uri getContactsFilterUri() {
        Uri filterUri = Contacts.CONTENT_FILTER_URI;

        // To confirm if the search rule must contain account limitation.
        Intent intent = getIntent();
        ContactListFilter filter = (ContactListFilter) intent.getParcelableExtra(
                AccountFilterActivity.KEY_EXTRA_CONTACT_LIST_FILTER);
        int operation = getIntent().getIntExtra(EXTRA_GROUP_ACTION, GROUP_ACTION_NONE);
        long groupId = getIntent().getLongExtra(EXTRA_GROUP_ID, -1);
        String accountName = getIntent().getStringExtra(SimContactsConstants.ACCOUNT_NAME);
        String accountType = getIntent().getStringExtra(SimContactsConstants.ACCOUNT_TYPE);
        switch (operation) {
            case GROUP_ACTION_ADD_MEMBER:
            case GROUP_ACTION_MOVE_MEMBER:
                Uri.Builder builder = Contacts.CONTENT_FILTER_URI.buildUpon();
                builder.appendQueryParameter(ADD_GROUP_MEMBERS,
                        operation == GROUP_ACTION_ADD_MEMBER ? "true" : "false");
                builder.appendQueryParameter(Groups._ID, String.valueOf(groupId));
                builder.appendQueryParameter(RawContacts.ACCOUNT_NAME, accountName);
                builder.appendQueryParameter(RawContacts.ACCOUNT_TYPE, accountType);
                return builder.build();
        }
        if (filter != null &&
                filter.filterType == ContactListFilter.FILTER_TYPE_ACCOUNT) {
            // Need consider account info limitation, construct the uri with
            // account info query parameter.
            Uri.Builder builder = filterUri.buildUpon();
            filter.addAccountQueryParameterToUrl(builder);
            return builder.build();
        }

        if (!isShowSIM()) {
            filterUri = filterUri.buildUpon()
                    .appendQueryParameter(RawContacts.ACCOUNT_TYPE, SimAccountType.ACCOUNT_TYPE)
                    .appendQueryParameter(SimContactsConstants.WITHOUT_SIM_FLAG, "true")
                    .build();
        }
        // No need to consider account info limitation, just return a uri
        // with "filter" path.
        return filterUri;
    }

    private Uri getUriToQuery() {
        Uri uri;
        switch (mMode) {
            case MODE_DEFAULT_CONTACT: {
                Intent intent = getIntent();
                int operation = intent.getIntExtra(EXTRA_GROUP_ACTION, GROUP_ACTION_NONE);
                long groupId = intent.getLongExtra(EXTRA_GROUP_ID, -1);
                String accountName = intent.getStringExtra(SimContactsConstants.ACCOUNT_NAME);
                String accountType = intent.getStringExtra(SimContactsConstants.ACCOUNT_TYPE);
                switch (operation) {
                    case GROUP_ACTION_ADD_MEMBER:
                    case GROUP_ACTION_MOVE_MEMBER:
                        Uri.Builder builder = Contacts.CONTENT_GROUP_URI.buildUpon();
                        builder.appendQueryParameter(ADD_GROUP_MEMBERS,
                                operation == GROUP_ACTION_ADD_MEMBER ? "true" : "false");
                        builder.appendQueryParameter(Groups._ID, String.valueOf(groupId));
                        builder.appendQueryParameter(RawContacts.ACCOUNT_NAME, accountName);
                        builder.appendQueryParameter(RawContacts.ACCOUNT_TYPE, accountType);
                        uri = builder.build();
                        break;
                    default:
                        uri = Contacts.CONTENT_URI;
                        break;
                }
                break;
            }
            case MODE_SEARCH_CONTACT:
                uri = Contacts.CONTENT_URI;
                break;
            case MODE_DEFAULT_EMAIL:
            case MODE_SEARCH_EMAIL:
                uri = Email.CONTENT_URI;
                break;
            case MODE_DEFAULT_PHONE:
            case MODE_SEARCH_PHONE:
                uri = Phone.CONTENT_URI;
                break;
            case MODE_DEFAULT_CALL:
            case MODE_SEARCH_CALL:
                uri = Calls.CONTENT_URI_WITH_VOICEMAIL;
                break;
            case MODE_DEFAULT_SIM:
            case MODE_SEARCH_SIM: {
                int subscription = getIntent().getIntExtra(SimContactsConstants.SUB, 0);
                uri = querySimContacts(subscription);
                break;
            }
            default:
                throw new IllegalArgumentException("getUriToQuery: Incorrect mode: " + mMode);
        }
        return uri.buildUpon()
                .appendQueryParameter(InternalContactCounts.EXTRA_ADDRESS_BOOK_INDEX, "true")
                .build();
    }

    private Uri getFilterUri() {
        switch (mMode) {
            case MODE_SEARCH_CONTACT:
                return getContactsFilterUri();
            case MODE_SEARCH_PHONE:
                return Phone.CONTENT_FILTER_URI;
            case MODE_SEARCH_EMAIL:
                return Email.CONTENT_FILTER_URI;
            default:
                log("getFilterUri: Incorrect mode: " + mMode);
        }
        return Contacts.CONTENT_FILTER_URI;
    }

    public String[] getProjectionForQuery() {
        switch (mMode) {
            case MODE_DEFAULT_CONTACT:
            case MODE_SEARCH_CONTACT:
                return CONTACTS_SUMMARY_PROJECTION;
            case MODE_DEFAULT_PHONE:
            case MODE_SEARCH_PHONE:
                return PHONES_PROJECTION;
            case MODE_DEFAULT_EMAIL:
            case MODE_SEARCH_EMAIL:
                return EMAILS_PROJECTION;
            case MODE_DEFAULT_CALL:
            case MODE_SEARCH_CALL:
                return CALL_LOG_PROJECTION;
            case MODE_DEFAULT_SIM:
            case MODE_SEARCH_SIM:
                return SIM_COLUMN_NAMES;
            default:
                log("getProjectionForQuery: Incorrect mode: " + mMode);
        }
        return CONTACTS_SUMMARY_PROJECTION;
    }

    private String getSortOrder(String[] projection) {
        switch (mMode) {
            case MODE_DEFAULT_CALL:
            case MODE_SEARCH_CALL:
                return CALL_LOG_PROJECTION[2] + SORT_ORDER;
        }
        return RawContacts.SORT_KEY_PRIMARY;
    }

    private String getSelectionForQuery() {
        switch (mMode) {
            case MODE_DEFAULT_EMAIL:
            case MODE_SEARCH_EMAIL:
            case MODE_DEFAULT_PHONE:
            case MODE_SEARCH_PHONE:
                if (isShowSIM()) {
                    return null;
                }
                return PHONES_SELECTION;
            case MODE_DEFAULT_CONTACT:
                return getSelectionForAccount();
            case MODE_DEFAULT_SIM:
            case MODE_SEARCH_SIM:
                return null;
            case MODE_DEFAULT_CALL:
                // Add a subscription judgement, if selection = -1 that means
                // need query both cards.
                String selection = null;
                int slotId = getIntent().getIntExtra(
                        SimContactsConstants.SUB, SimContactsConstants.SUB_INVALID);
                if (SimContactsConstants.SUB_INVALID != slotId) {
                    long[] subId = SubscriptionManager.getSubId(slotId);
                    if (subId != null && subId.length >= 1) {
                        selection = Calls.PHONE_ACCOUNT_ID + "=" + subId[0];
                    }
                }
                return selection;
            default:
                return null;
        }
    }

    private String getSelectionForAccount() {
        @SuppressWarnings("deprecation")
        ContactListFilter filter = (ContactListFilter) getIntent().getExtra(
                AccountFilterActivity.KEY_EXTRA_CONTACT_LIST_FILTER);
        if (filter == null) {
            return null;
        }
        switch (filter.filterType) {
            case ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS:
                return null;
            case ContactListFilter.FILTER_TYPE_CUSTOM:
                return CONTACTS_SELECTION;
            case ContactListFilter.FILTER_TYPE_ACCOUNT:
                return null;
        }
        return null;
    }

    private String[] getSelectionArgsForQuery() {
        switch (mMode) {
            case MODE_DEFAULT_EMAIL:
            case MODE_SEARCH_EMAIL:
            case MODE_DEFAULT_PHONE:
            case MODE_SEARCH_PHONE:
                if (isShowSIM()) {
                    return null;
                }
                return PHONES_SELECTION_ARGS;
            case MODE_DEFAULT_SIM:
            case MODE_SEARCH_SIM:
                return null;
            default:
                return null;
        }
    }

    private boolean isShowSIM() {
        // if airplane mode on, do not show SIM.
        return !getIntent().hasExtra(EXTRA_NOT_SHOW_SIM_FLAG)
                && !MoreContactUtils.isAPMOnAndSIMPowerDown(this);
    }

    public void startQuery() {
        Uri uri = getUriToQuery();
        ContactListFilter filter = (ContactListFilter) getIntent().getExtra(
                          AccountFilterActivity.KEY_EXTRA_CONTACT_LIST_FILTER);
        if (filter != null) {
            if (filter.filterType == ContactListFilter.FILTER_TYPE_ACCOUNT) {
                // We should exclude the invisiable contacts.
                uri = uri.buildUpon()
                        .appendQueryParameter(RawContacts.ACCOUNT_NAME, filter.accountName)
                        .appendQueryParameter(RawContacts.ACCOUNT_TYPE, filter.accountType)
                        .appendQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY,
                                String.valueOf(ContactsContract.Directory.DEFAULT))
                        .build();
            } else if (filter.filterType == ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS) {
                // Do not query sim contacts in airplane mode.
                if (!isShowSIM()) {
                    uri = uri.buildUpon()
                        .appendQueryParameter(RawContacts.ACCOUNT_TYPE,
                                SimAccountType.ACCOUNT_TYPE)
                        .appendQueryParameter(SimContactsConstants.WITHOUT_SIM_FLAG, "true")
                        .build();
                }
            }
        }
        String[] projection = getProjectionForQuery();
        mQueryHandler.startQuery(QUERY_TOKEN, null, uri, projection,
                getSelectionForQuery(), getSelectionArgsForQuery(), getSortOrder(projection));
    }

    public void doFilter(CharSequence s) {
        if (TextUtils.isEmpty(s)) {
            startQuery();
            return;
        }

        Uri uri = Uri.withAppendedPath(getFilterUri(), Uri.encode(s.toString()));
        String[] projection = getProjectionForQuery();
        mQueryHandler.startQuery(QUERY_TOKEN, null, uri, projection,
                getSelectionForQuery(), getSelectionArgsForQuery(), getSortOrder(projection));
    }

    public void updateContent() {
        if (isSearchMode()) {
            doFilter(mSearchView.getQuery().toString());
        } else {
            startQuery();
        }
    }

    private CharSequence getDisplayNumber(CharSequence number) {
        if (TextUtils.isEmpty(number)) {
            return "";
        }
        if (PhoneNumberUtils.isVoiceMailNumber(number.toString())) {
            return getString(R.string.voicemail);
        }
        return number;
    }

    private boolean isPickContact() {
        return mMode == MODE_DEFAULT_CONTACT || mMode == MODE_SEARCH_CONTACT;
    }

    private boolean isPickPhone() {
        return mMode == MODE_DEFAULT_PHONE || mMode == MODE_SEARCH_PHONE;
    }

    private boolean isPickSim() {
        return mMode == MODE_DEFAULT_SIM || mMode == MODE_SEARCH_SIM;
    }

    private boolean isPickEmail() {
        return mMode == MODE_DEFAULT_EMAIL || mMode == MODE_SEARCH_EMAIL;
    }

    private boolean isPickCall() {
        return mMode == MODE_DEFAULT_CALL || mMode == MODE_SEARCH_CALL;
    }

    private void selectAll(boolean isSelected) {
        // update mChoiceSet.
        // TODO: make it more efficient
        Cursor cursor = mAdapter.getCursor();
        if (cursor == null) {
            log("cursor is null.");
            return;
        }

        cursor.moveToPosition(-1);
        while (cursor.moveToNext()) {
            String id = null;
            String[] value = null;
            if (isPickContact()) {
                id = String.valueOf(cursor.getLong(CONTACT_COLUMN_ID));
                value = new String[] {
                    cursor.getString(CONTACT_COLUMN_LOOKUP_KEY), id,
                    cursor.getString(CONTACT_COLUMN_RAW_CONTACT_ID),
                    cursor.getString(CONTACT_COLUMN_PHOTO_URI),
                    cursor.getString(CONTACT_COLUMN_DISPLAY_NAME)
                };
            } else if (isPickPhone()) {
                id = String.valueOf(cursor.getLong(PHONE_COLUMN_ID));
                value = new String[] {
                    cursor.getString(CONTACT_COLUMN_DISPLAY_NAME),
                    cursor.getString(PHONE_COLUMN_NUMBER),
                    String.valueOf(cursor.getInt(PHONE_COLUMN_TYPE)),
                    cursor.getString(PHONE_COLUMN_LABEL),
                    String.valueOf(cursor.getLong(CONTACT_COLUMN_ID))
                };
            } else if (isPickEmail()) {
                id = String.valueOf(cursor.getLong(EMAIL_COLUMN_ID));
                value = new String[] {
                    cursor.getString(CONTACT_COLUMN_DISPLAY_NAME),
                    cursor.getString(EMAIL_COLUMN_ADDRESS),
                    id
                };
            } else if (isPickCall()) {
                id = String.valueOf(cursor.getLong(CALLLOG_COLUMN_ID));
                if (mSelectCallLog) {
                    value = new String[] {
                        cursor.getString(CALLLOG_COLUMN_NUMBER),
                        cursor.getString(CALLLOG_COLUMN_CALLER_NAME)
                    };
                } else {
                    value = new String[] {
                        id
                    };
                }
            } else if (isPickSim()) {
                id = String.valueOf(cursor.getLong(SIM_COLUMN_ID));
                value = new String[] {
                    cursor.getString(SIM_COLUMN_DISPLAY_NAME),
                    cursor.getString(SIM_COLUMN_NUMBER),
                    cursor.getString(SIM_COLUMN_EMAILS),
                    cursor.getString(SIM_COLUMN_ANRS)
                };
            }
            if (isSelected) {
                mChoiceSet.putStringArray(id, value);
            } else {
                mChoiceSet.remove(id);
            }
        }

        updateActionBar();
        mAdapter.notifyDataSetChanged();
    }

    private class QueryHandler extends AsyncQueryHandler {
        public QueryHandler(Context context) {
            super(context.getContentResolver());
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            mAdapter.changeCursor(cursor);
            if (cursor == null || cursor.getCount() == 0) {
                Toast.makeText(MultiPickContactActivity.this,
                        R.string.listFoundAllContactsZero, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private final class ContactItemCache {
        long id;
        String name;
        String number;
        String lookupKey;
        String type;
        String label;
        String contact_id;
        String email;
        String anrs;
        long nameRawContactId;
        String photoUri;
    }

    private final class ContactItemListAdapter extends CursorAdapter implements SectionIndexer {
        Context mContext;
        protected LayoutInflater mInflater;
        private ContactsSectionIndexer mIndexer;
        private ContactPhotoManager mContactPhotoManager;

        public ContactItemListAdapter(Context context) {
            super(context, null, false);

            mContext = context;
            mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            mContactPhotoManager = ContactPhotoManager.getInstance(mContext);
        }

        private void assignContactAndFillCache(ContactListItemView cliv, Cursor cursor,
                Account account, ContactItemCache cache) {
            String newLookupKey = cursor.getString(CONTACT_COLUMN_LOOKUP_KEY);
            boolean rebound = !TextUtils.equals(cache.lookupKey, newLookupKey);

            cache.lookupKey = newLookupKey;
            cache.name = cursor.getString(CONTACT_COLUMN_DISPLAY_NAME);

            long photoId = cursor.getLong(CONTACT_COLUMN_PHOTO_ID);
            mContactPhotoManager.loadThumbnail(cliv.getPhotoView(), photoId, account, false, true,
                    new DefaultImageRequest(cache.name, cache.lookupKey, true));

            CharSequence query = mSearchView != null ? mSearchView.getQuery() : null;
            cliv.setHighlightedPrefix(query != null ? query.toString().toUpperCase() : null);
            cliv.setDisplayName(cache.name);
            cliv.setChecked(mChoiceSet.containsKey(String.valueOf(cache.id)), !rebound);
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            ContactItemCache cache = (ContactItemCache) view.getTag();
            ContactListItemView cliv = (ContactListItemView) view.findViewById(R.id.contact);

            if (isPickContact()) {
                cache.id = cursor.getLong(CONTACT_COLUMN_ID);
                cache.nameRawContactId = cursor.getLong(CONTACT_COLUMN_RAW_CONTACT_ID);
                cache.photoUri = cursor.getString(CONTACT_COLUMN_PHOTO_URI);

                Account account = null;
                if (!cursor.isNull(CONTACT_COLUMN_ACCOUNT_TYPE)
                        && !cursor.isNull(CONTACT_COLUMN_ACCOUNT_NAME)) {
                    final String accountType = cursor.getString(CONTACT_COLUMN_ACCOUNT_TYPE);
                    final String accountName = cursor.getString(CONTACT_COLUMN_ACCOUNT_NAME);
                    account = new Account(accountName, accountType);
                }

                assignContactAndFillCache(cliv, cursor, account, cache);
                cliv.setPhoneNumber(null, null);
            } else if (isPickPhone()) {
                cache.id = cursor.getLong(PHONE_COLUMN_ID);
                cache.number = cursor.getString(PHONE_COLUMN_NUMBER);
                cache.label = cursor.getString(PHONE_COLUMN_LABEL);
                cache.type = String.valueOf(cursor.getInt(PHONE_COLUMN_TYPE));

                assignContactAndFillCache(cliv, cursor, null, cache);
                cliv.setPhoneNumber(cache.number, null);
            } else if (isPickSim()) {
                cache.id = cursor.getLong(SIM_COLUMN_ID);
                cache.name = cursor.getString(SIM_COLUMN_DISPLAY_NAME);
                cache.number = cursor.getString(SIM_COLUMN_NUMBER);
                cache.email = cursor.getString(SIM_COLUMN_EMAILS);
                cache.anrs = cursor.getString(SIM_COLUMN_ANRS);

                cliv.setDisplayName(cache.name);
                cliv.removePhotoView();
                if (!TextUtils.isEmpty(cache.number)) {
                    cliv.setPhoneNumber(cache.number, null);
                } else if (!TextUtils.isEmpty(cache.email)) {
                    String[] emailArray = (cache.email).split(",");
                    cliv.setPhoneNumber(emailArray[0], null);
                } else {
                    cliv.setPhoneNumber(null, null);
                }
            } else if (isPickEmail()) {
                cache.id = cursor.getLong(EMAIL_COLUMN_ID);
                cache.email = cursor.getString(EMAIL_COLUMN_ADDRESS);

                assignContactAndFillCache(cliv, cursor, null, cache);
                cliv.setPhoneNumber(cache.email, null);
            } else if (isPickCall()) {
                cache.id = cursor.getLong(CALLLOG_COLUMN_ID);
                cache.name = cursor.getString(CALLLOG_COLUMN_CALLER_NAME);
                cache.number = cursor.getString(CALLLOG_COLUMN_NUMBER);

                String callerName = cursor.getString(CALLLOG_COLUMN_CALLER_NAME);
                int callerNumberType = cursor.getInt(CALLLOG_COLUMN_CALLER_NUMBERTYPE);
                String callerNumberLabel = cursor.getString(CALLLOG_COLUMN_CALLER_NUMBERLABEL);
                String geocodedLocation = cursor.getString(CALLLOG_COLUMN_CALLER_LOCATION);
                String accountId = cursor.getString(CALLLOG_COLUMN_PHONE_ACCOUNT);
                long date = cursor.getLong(CALLLOG_COLUMN_DATE);
                long duration = cursor.getLong(CALLLOG_COLUMN_DURATION);
                int type = cursor.getInt(CALLLOG_COLUMN_CALL_TYPE);

                ImageView callType = (ImageView) view.findViewById(R.id.call_type_icon);
                TextView dateText = (TextView) view.findViewById(R.id.call_date);
                TextView durationText = (TextView) view.findViewById(R.id.duration);
                TextView subSlotText = (TextView) view.findViewById(R.id.subscription);
                TextView numberLabelText = (TextView) view.findViewById(R.id.label);
                TextView nameText = (TextView) view.findViewById(R.id.name);

                // only for monkey test, callType can not be null in normal behaviour
                if (callType == null) {
                    return;
                }

                callType.setVisibility(View.VISIBLE);
                // Set the icon
                switch (type) {
                    case Calls.INCOMING_TYPE:
                        callType.setImageResource(R.drawable.ic_call_incoming_holo_dark);
                        break;
                    case Calls.OUTGOING_TYPE:
                        callType.setImageResource(R.drawable.ic_call_outgoing_holo_dark);
                        break;
                    case Calls.MISSED_TYPE:
                        callType.setImageResource(R.drawable.ic_call_missed_holo_dark);
                        break;
                    default:
                        callType.setVisibility(View.INVISIBLE);
                        break;
                }

                // set the number
                if (!TextUtils.isEmpty(callerName)) {
                    nameText.setText(callerName);
                } else {
                    nameText.setText(getDisplayNumber(cache.number));
                }

                CharSequence numberLabel = null;
                if (callerNumberType != 0 && !PhoneNumberUtils.isUriNumber(cache.number)) {
                    numberLabel = Phone.getDisplayLabel(context, callerNumberType,
                            callerNumberLabel);
                } else {
                    numberLabel = geocodedLocation;
                }
                numberLabelText.setText(numberLabel);
                numberLabelText.setVisibility(TextUtils.isEmpty(numberLabel)
                        ? View.GONE : View.VISIBLE);

                // set date
                dateText.setText(DateUtils.getRelativeTimeSpanString(date,
                        System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
                        DateUtils.FORMAT_ABBREV_RELATIVE));

                // set duration
                durationText.setText(DateUtils.formatElapsedTime(duration));

                // set slot
                if (isMultiSimEnabled()) {
                    int slotId = SimContactsConstants.SUB_INVALID;
                    if (accountId != null) {
                        try {
                            slotId = SubscriptionManager.getSlotId(Long.valueOf(accountId));
                        } catch (NumberFormatException e) {
                            // ignore and keep the default 'invalid'
                        }
                    }
                    subSlotText.setText(MoreContactUtils.getMultiSimAliasesName(
                            MultiPickContactActivity.this, slotId));
                } else {
                    subSlotText.setVisibility(View.GONE);
                }

                CheckBox checkBox = (CheckBox) view.findViewById(R.id.pick_contact_check);
                checkBox.setChecked(mChoiceSet.containsKey(String.valueOf(cache.id)));
            }
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            int layoutResId = isPickCall() ? R.layout.pick_calls_item : R.layout.pick_contact_item;
            View v = mInflater.inflate(layoutResId, parent, false);
            ContactListItemView cliv = (ContactListItemView) v.findViewById(R.id.contact);
            if (cliv != null) {
                cliv.setUnknownNameText(getString(R.string.missing_name));
            }
            ContactItemCache cache = new ContactItemCache();
            v.setTag(cache);
            return v;
        }

        @Override
        protected void onContentChanged() {
            updateContent();
        }

        @Override
        public void changeCursor(Cursor cursor) {
            super.changeCursor(cursor);
            String[] sections = null;
            int[] counts = null;
            Bundle extras = cursor != null ? cursor.getExtras() : null;
            if (extras != null &&
                    extras.containsKey(InternalContactCounts.EXTRA_ADDRESS_BOOK_INDEX_TITLES)) {
                sections = extras.getStringArray(
                        InternalContactCounts.EXTRA_ADDRESS_BOOK_INDEX_TITLES);
                counts = extras.getIntArray(InternalContactCounts.EXTRA_ADDRESS_BOOK_INDEX_COUNTS);
            } else {
                sections = new String[0];
                counts = new int[0];
            }
            mIndexer = new ContactsSectionIndexer(sections, counts);
            updateActionBar();
        }

        @Override
        public Object[] getSections() {
            if (mIndexer != null) {
                return mIndexer.getSections();
            }
            return null;
        }

        @Override
        public int getPositionForSection(int section) {
            Cursor cursor = getCursor();
            if (cursor == null) {
                return 0;
            }
            if (mIndexer != null) {
                return mIndexer.getPositionForSection(section);
            }
            return 0;
        }

        @Override
        public int getSectionForPosition(int position) {
            if (mIndexer != null) {
                return mIndexer.getSectionForPosition(position);
            }
            return -1;
        }

        public int getSortIndex() {
            switch (mMode) {
                case MODE_DEFAULT_CONTACT:
                case MODE_SEARCH_CONTACT:
                case MODE_DEFAULT_PHONE:
                case MODE_SEARCH_PHONE:
                case MODE_DEFAULT_EMAIL:
                case MODE_SEARCH_EMAIL:
                    return CONTACT_COLUMN_DISPLAY_NAME;
                case MODE_DEFAULT_CALL:
                case MODE_SEARCH_CALL:
                    return CALLLOG_COLUMN_CALLER_NAME;
                case MODE_DEFAULT_SIM:
                case MODE_SEARCH_SIM:
                    return SIM_COLUMN_DISPLAY_NAME;
                default:
                    throw new IllegalArgumentException("Incorrect mode for multi pick");
            }
        }
    }

    /**
     * Dismisses the soft keyboard when the list takes focus.
     */
    public boolean onTouch(View view, MotionEvent event) {
        if (view == getListView()) {
            hideSoftKeyboard();
        }
        return false;
    }

    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (actionId == EditorInfo.IME_ACTION_DONE) {
            hideSoftKeyboard();
            return true;
        }
        return false;
    }

    private void hideSoftKeyboard() {
        // Hide soft keyboard, if visible
        InputMethodManager inputMethodManager =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(mSearchView.getWindowToken(), 0);
    }

    protected static void log(String msg) {
        if (DEBUG) Log.d(TAG, msg);
    }

    private Uri querySimContacts(int subscription) {
        Uri uri = null;
        if (subscription != SimContactsConstants.SUB_1
                && subscription != SimContactsConstants.SUB_2) {
            return uri;
        }
        long[] subId = SubscriptionManager.getSubId(subscription);
        if (subId != null && isMultiSimEnabled()) {
            uri = Uri.parse(SimContactsConstants.SIM_SUB_URI + subId[0]);
        }
        else {
            uri = Uri.parse(SimContactsConstants.SIM_URI);
        }

        return uri;
    }

    private boolean isMultiSimEnabled() {
        return TelephonyManager.getDefault().isMultiSimEnabled();
    }

    protected Account[] getSimAccounts() {
        return mAccountManager.getAccountsByType(SimContactsConstants.ACCOUNT_TYPE_SIM);
    }

    private class ImportAllSimContactsThread extends Thread
            implements DialogInterface.OnCancelListener, DialogInterface.OnClickListener {
        boolean mCanceled = false;
        // The total count how many to import.
        private int mTotalCount = 0;
        // The real count have imported.
        private int mActualCount = 0;

        private Account mAccount;

        public ImportAllSimContactsThread() {
        }

        @Override
        public void run() {
            final ContentValues emptyContentValues = new ContentValues();
            final ContentResolver resolver = getContentResolver();

            String type = getIntent().getStringExtra(SimContactsConstants.ACCOUNT_TYPE);
            String name = getIntent().getStringExtra(SimContactsConstants.ACCOUNT_NAME);
            mAccount = new Account(name != null ? name : SimContactsConstants.PHONE_NAME,
                    type != null ? type : SimContactsConstants.ACCOUNT_TYPE_PHONE);
            log("import sim contact to account: " + mAccount);
            mTotalCount = mChoiceSet.size();

            for (String key : mChoiceSet.keySet()) {
                if (mCanceled) {
                    break;
                }
                String[] values = mChoiceSet.getStringArray(key);
                actuallyImportOneSimContact(values, resolver, mAccount);
                mActualCount++;
                mProgressDialog.incrementProgressBy(1);
            }
            finish();
        }

        @Override
        public void onCancel(DialogInterface dialog) {
            final Context context = MultiPickContactActivity.this;
            mCanceled = true;
            // Give a toast show to tell user import termination.
            if (mActualCount < mTotalCount) {
                String text = getResources().getQuantityString(R.plurals.import_progress,
                        mActualCount, mActualCount);
                Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context, R.string.import_finish, Toast.LENGTH_SHORT).show();
            }
        }

        public void onClick(DialogInterface dialog, int which) {
            if (which == DialogInterface.BUTTON_NEGATIVE) {
                mCanceled = true;
                mProgressDialog.dismiss();
            }
        }
    }

    private static void actuallyImportOneSimContact(
            String[] values, final ContentResolver resolver, Account account) {

        final String name = values[SIM_COLUMN_DISPLAY_NAME];
        final String phoneNumber = values[SIM_COLUMN_NUMBER];
        final String emailAddresses = values[SIM_COLUMN_EMAILS];
        final String anrs = values[SIM_COLUMN_ANRS];
        final String[] emailAddressArray;
        final String[] anrArray;
        if (!TextUtils.isEmpty(emailAddresses)) {
            emailAddressArray = emailAddresses.split(",");
        } else {
            emailAddressArray = null;
        }
        if (!TextUtils.isEmpty(anrs)) {
            anrArray = anrs.split(",");
        } else {
            anrArray = null;
        }
        log(" actuallyImportOneSimContact: name= " + name +
                ", phoneNumber= " + phoneNumber + ", emails= " + emailAddresses
                + ", anrs= " + anrs + ", account is " + account);
        final ArrayList<ContentProviderOperation> operationList =
                new ArrayList<ContentProviderOperation>();
        ContentProviderOperation.Builder builder =
                ContentProviderOperation.newInsert(RawContacts.CONTENT_URI);
        builder.withValue(RawContacts.AGGREGATION_MODE, RawContacts.AGGREGATION_MODE_SUSPENDED);
        if (account != null) {
            builder.withValue(RawContacts.ACCOUNT_NAME, account.name);
            builder.withValue(RawContacts.ACCOUNT_TYPE, account.type);
        }
        operationList.add(builder.build());

        builder = ContentProviderOperation.newInsert(Data.CONTENT_URI);
        builder.withValueBackReference(StructuredName.RAW_CONTACT_ID, 0);
        builder.withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE);
        builder.withValue(StructuredName.DISPLAY_NAME, name);
        operationList.add(builder.build());

        builder = ContentProviderOperation.newInsert(Data.CONTENT_URI);
        builder.withValueBackReference(Phone.RAW_CONTACT_ID, 0);
        builder.withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
        builder.withValue(Phone.TYPE, Phone.TYPE_MOBILE);
        builder.withValue(Phone.NUMBER, phoneNumber);
        builder.withValue(Data.IS_PRIMARY, 1);
        operationList.add(builder.build());

        if (anrArray != null) {
            for (String anr : anrArray) {
                builder = ContentProviderOperation.newInsert(Data.CONTENT_URI);
                builder.withValueBackReference(Phone.RAW_CONTACT_ID, 0);
                builder.withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
                builder.withValue(Phone.TYPE, Phone.TYPE_HOME);
                builder.withValue(Phone.NUMBER, anr);
                operationList.add(builder.build());
            }
        }

        if (emailAddresses != null) {
            for (String emailAddress : emailAddressArray) {
                builder = ContentProviderOperation.newInsert(Data.CONTENT_URI);
                builder.withValueBackReference(Email.RAW_CONTACT_ID, 0);
                builder.withValue(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE);
                builder.withValue(Email.TYPE, Email.TYPE_MOBILE);
                builder.withValue(Email.ADDRESS, emailAddress);
                operationList.add(builder.build());
            }
        }

        try {
            resolver.applyBatch(ContactsContract.AUTHORITY, operationList);
        } catch (RemoteException e) {
            log(String.format("%s: %s", e.toString(), e.getMessage()));
        } catch (OperationApplicationException e) {
            log(String.format("%s: %s", e.toString(), e.getMessage()));
        }
    }

    /**
     * After turn on airplane mode, cancel import sim contacts operation.
     */
    private void cancelSimContactsImporting() {
        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            mProgressDialog.cancel();
        }
    }

    private void showGroupSelectionList(String accountType, long srcGroupId) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.label_groups));
        ContentResolver resolver = getContentResolver();
        String selection = Groups.ACCOUNT_TYPE + " =? AND " + Groups.DELETED + " != ?";
        ArrayList<String> items = new ArrayList<String>();

        mGroupIds.clear();
        items.clear();
        Cursor cursor = resolver.query(Groups.CONTENT_URI, new String[] {
                Groups._ID, Groups.TITLE
        },
        selection,
        new String[] {
                        accountType, "1"
        },
        null);
        if (cursor == null || cursor.getCount() == 0) {
            Toast.makeText(this, R.string.message_can_not_move_members,
                    Toast.LENGTH_LONG).show();
            return;
        } else {
            try {
                while (cursor.moveToNext()) {
                    if (!cursor.getString(0).equals(String.valueOf(srcGroupId))) {
                        mGroupIds.add(cursor.getLong(0));
                        items.add(cursor.getString(1));
                    }
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        if (mGroupIds.size() == 0) {
            Toast.makeText(this, R.string.message_can_not_move_members,
                    Toast.LENGTH_LONG).show();
            return;
        }
        String[] groupItem = new String[items.size()];
        for (int i = 0; i < items.size(); i++) {
            groupItem[i] = items.get(i);
        }
        builder.setItems(groupItem, new ChooseActionListener());
        builder.create().show();
    }

    private class ChooseActionListener implements DialogInterface.OnClickListener {
        public void onClick(DialogInterface dialog, int which) {
            new MoveGroupMemberTask(mChoiceSet,
                    getIntent().getLongExtra(EXTRA_GROUP_ID, -1),
                    mGroupIds.get(which)).execute();
        }
    }

    class MoveGroupMemberTask extends AsyncTask<Object, Object, Object> {

        private static final String GROUP_QUERY_GROUP_MEMBER_SELECTION =
                Data.MIMETYPE + "=? AND "
                        + GroupMembership.GROUP_ROW_ID + "=?";

        private static final String GROUP_QUERY_RAW_CONTACTS_SELECTION =
                RawContacts.CONTACT_ID + "=?";

        private static final String GROUP_DELETE_MEMBER_SELECTION = Data.CONTACT_ID
                + "=? AND "
                + Data.MIMETYPE
                + "=? AND "
                + GroupMembership.GROUP_ROW_ID
                + "=?";

        private static final int BUFFER_LENGTH = 499;

        private Bundle mChoiceSet;
        private long mDestGroupId;
        private long mSrcGroupId;
        private boolean mCanceled = false;

        private ArrayList<ContentProviderOperation> mAddOrMoveOperation;
        private ArrayList<ContentProviderOperation> mDeleteOperation;
        private ArrayList<String> mGroupMemberList = new ArrayList<String>();

        public MoveGroupMemberTask(Bundle choiceSet,
                long srcGroupId, long destGroupId) {
            mChoiceSet = choiceSet;
            mSrcGroupId = srcGroupId;
            mDestGroupId = destGroupId;
        }

        @Override
        protected void onPreExecute() {
            mProgressDialog = new ProgressDialog(MultiPickContactActivity.this,
                    com.android.internal.R.style.Theme_Holo_Light_Dialog_Alert);
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            mProgressDialog.setTitle(getProgressDialogTitle());
            mProgressDialog.setMessage(getProgressDialogMessage());
            mProgressDialog.setMax(mChoiceSet != null ? mChoiceSet.keySet().size() : 100);
            mProgressDialog.setProgress(0);
            mProgressDialog.setCanceledOnTouchOutside(false);
            mProgressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                public void onCancel(DialogInterface dialog) {
                    mCanceled = true;
                }
            });
            mProgressDialog.show();
        }

        @Override
        protected Bundle doInBackground(Object... params) {
            if (mChoiceSet == null || mSrcGroupId <= 0) {
                return null;
            }
            ContentResolver resolver = getContentResolver();
            Cursor memberCursor = null;

            memberCursor = resolver.query(Data.CONTENT_URI,
                    new String[] {
                        Data.CONTACT_ID
                    },
                    GROUP_QUERY_GROUP_MEMBER_SELECTION,
                    new String[] {
                            GroupMembership.CONTENT_ITEM_TYPE,
                            String.valueOf(mDestGroupId)
                    },
                    null);

            if (memberCursor != null && memberCursor.getCount() > 0) {
                try {
                    while (memberCursor.moveToNext()) {
                        // Mark those contacts that already exist in the dest
                        // group
                        mGroupMemberList.add(String.valueOf(memberCursor.getLong(0)));
                    }
                } finally {
                    if (memberCursor != null) {
                        memberCursor.close();
                    }
                }
            }

            Set<String> keySet = mChoiceSet.keySet();
            Iterator<String> it = keySet.iterator();

            ContentProviderOperation.Builder builder;

            mAddOrMoveOperation = new ArrayList<ContentProviderOperation>();
            mDeleteOperation = new ArrayList<ContentProviderOperation>();
            String id;
            int count = 0;
            int maxSize = mChoiceSet.keySet().size();
            while (!mCanceled && it.hasNext()) {
                id = it.next();
                ++count;

                if (mDestGroupId <= 0) {
                    // Invalid group id, cancel the task
                    return null;
                }
                if (mProgressDialog != null && mProgressDialog.isShowing()
                        && count < maxSize - (maxSize) / 100) {
                    mProgressDialog.incrementProgressBy(1);
                }
                if (mGroupMemberList.contains(id)) {
                    // If the contact already exists in the group, need to
                    // delete those
                    // contacts that in the previous group
                    builder = ContentProviderOperation.newDelete(Data.CONTENT_URI);
                    builder.withSelection(GROUP_DELETE_MEMBER_SELECTION,
                            new String[] {
                            id,
                            GroupMembership.CONTENT_ITEM_TYPE,
                            String.valueOf(mSrcGroupId)
                    });
                    mDeleteOperation.add(builder.build());
                    continue;
                }
                ContentValues values = new ContentValues();
                values.put(GroupMembership.GROUP_ROW_ID, mDestGroupId);
                builder = ContentProviderOperation.newUpdate(Data.CONTENT_URI);
                builder.withSelection(GROUP_DELETE_MEMBER_SELECTION,
                        new String[] {
                                id,
                                GroupMembership.CONTENT_ITEM_TYPE,
                                String.valueOf(mSrcGroupId)
                        });
                builder.withValues(values);
                mAddOrMoveOperation.add(builder.build());
            }

            if (mDeleteOperation.size() > 0) {
                if (mDeleteOperation.size() > BUFFER_LENGTH) {
                    addOrMoveApplyBatchByBuffer(mDeleteOperation, resolver);
                } else {
                    try {
                        resolver.applyBatch(ContactsContract.AUTHORITY, mDeleteOperation);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    } catch (OperationApplicationException e) {
                        e.printStackTrace();
                    }
                }
            }
            if (mAddOrMoveOperation.size() > BUFFER_LENGTH) {
                addOrMoveApplyBatchByBuffer(mAddOrMoveOperation, resolver);
            } else {
                try {
                    resolver.applyBatch(ContactsContract.AUTHORITY, mAddOrMoveOperation);
                } catch (RemoteException e) {
                    e.printStackTrace();
                } catch (OperationApplicationException e) {
                    e.printStackTrace();
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Object result) {
            if (mProgressDialog != null && mProgressDialog.isShowing()) {
                mProgressDialog.dismiss();
                finish();
            }
        }

        private void addOrMoveApplyBatchByBuffer(ArrayList<ContentProviderOperation> list,
                ContentResolver cr) {
            final ArrayList<ContentProviderOperation> temp
                = new ArrayList<ContentProviderOperation>(BUFFER_LENGTH);
            int bufferSize = list.size() / BUFFER_LENGTH;
            for (int index = 0; index <= bufferSize; index++) {
                temp.clear();
                if (index == bufferSize) {
                    for (int i = index * BUFFER_LENGTH; i < list.size(); i++) {
                        temp.add(list.get(i));
                    }
                } else {
                    for (int i = index * BUFFER_LENGTH;
                            i < index * BUFFER_LENGTH + BUFFER_LENGTH; i++) {
                        temp.add(list.get(i));
                    }
                }
                if (!temp.isEmpty()) {
                    try {
                        cr.applyBatch(ContactsContract.AUTHORITY, temp);
                    } catch (Exception e) {
                        Log.e(TAG, "apply batch by buffer error:" + e);
                    }
                }
            }
        }

        private String getProgressDialogTitle() {
            return getString(R.string.title_move_members);
        }

        private String getProgressDialogMessage() {
            return getString(R.string.message_move_members);
        }
    }
}
