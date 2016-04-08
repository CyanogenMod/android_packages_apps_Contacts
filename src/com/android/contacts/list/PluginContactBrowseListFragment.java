/*
 * Copyright (C) 2010 The Android Open Source Project
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
package com.android.contacts.list;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Loader;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.*;
import android.provider.ContactsContract;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.android.common.widget.CompositeCursorAdapter;
import com.android.contacts.common.list.AutoScrollListView;
import com.android.contacts.common.list.ContactEntryListFragment;
import com.android.contacts.common.list.DirectoryPartition;
import com.android.contacts.common.util.ContactLoaderUtils;
import com.android.contacts.incall.InCallMetricsHelper;
import com.android.contacts.incall.InCallPluginInfo;
import com.android.contacts.R;
import com.android.contacts.common.list.ContactListAdapter;
import com.android.contacts.common.list.ContactListFilter;
import com.android.contacts.common.list.ContactListItemView;
import com.android.contacts.common.list.DefaultContactListAdapter;
import com.android.contacts.common.list.ProfileAndContactsLoader;
import com.android.contacts.incall.InCallPluginUtils;
import com.android.phone.common.incall.utils.CallMethodUtils;

import java.util.List;

public class PluginContactBrowseListFragment extends ContactEntryListFragment<ContactListAdapter>
        implements View.OnClickListener {
    private static final String TAG = PluginContactBrowseListFragment.class.getSimpleName();
    private boolean DEBUG = false;
    private InCallPluginInfo mInCallPluginInfo;
    private View mLayout;
    private View mListView;
    private View mLoginView;
    private Button mLoginBtn;
    private ImageView mLoginIconView;
    private TextView mLoginMsg;
    private TextView mEmptyView;
    private static final String KEY_SELECTED_URI = "selectedUri";
    private static final String KEY_SELECTION_VERIFIED = "selectionVerified";
    private static final String KEY_FILTER = "filter";
    private static final String KEY_LAST_SELECTED_POSITION = "lastSelected";
    private static final String KEY_SHARED_PREFS_FILE_NAME = "prefsFileName";

    private static final String PERSISTENT_SELECTION_PREFIX = "defaultContactBrowserSelection";
    private static final String PREFS_FILE_PREFIX = "plugin.";
    private SharedPreferences mPrefs;
    private String mPrefsFileName;

    private boolean mStartedLoading;
    private boolean mSelectionToScreenRequested;
    private boolean mSmoothScrollRequested;
    private boolean mSelectionPersistenceRequested;
    private Uri mSelectedContactUri;
    private long mSelectedContactDirectoryId;
    private String mSelectedContactLookupKey;
    private long mSelectedContactId;
    private boolean mSelectionVerified;
    private int mLastSelectedPosition = -1;
    private boolean mRefreshingContactUri;
    private ContactListFilter mFilter;
    private boolean mInitialized = false;
    private boolean mAuthenticated = false;
    private String mAccountType = "";
    private String mAccountHandle = "";
    private String mPersistentSelectionPrefix = PERSISTENT_SELECTION_PREFIX;

    protected OnContactBrowserActionListener mListener;
    private ContactLookupTask mContactLookupTask;

    private final class ContactLookupTask extends AsyncTask<Void, Void, Uri> {

        private final Uri mUri;
        private boolean mIsCancelled;

        public ContactLookupTask(Uri uri) {
            mUri = uri;
        }

        @Override
        protected Uri doInBackground(Void... args) {
            Cursor cursor = null;
            try {
                final ContentResolver resolver = getContext().getContentResolver();
                final Uri uriCurrentFormat = ContactLoaderUtils.ensureIsContactUri(resolver, mUri);
                cursor = resolver.query(uriCurrentFormat,
                        new String[] { ContactsContract.Contacts._ID,
                                ContactsContract.Contacts.LOOKUP_KEY }, null, null, null);

                if (cursor != null && cursor.moveToFirst()) {
                    final long contactId = cursor.getLong(0);
                    final String lookupKey = cursor.getString(1);
                    if (contactId != 0 && !TextUtils.isEmpty(lookupKey)) {
                        return ContactsContract.Contacts.getLookupUri(contactId, lookupKey);
                    }
                }

                Log.e(TAG, "Error: No contact ID or lookup key for contact " + mUri);
                return null;
            } catch (Exception e) {
                Log.e(TAG, "Error loading the contact: " + mUri, e);
                return null;
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }

        public void cancel() {
            super.cancel(true);
            // Use a flag to keep track of whether the {@link AsyncTask} was cancelled or not in
            // order to ensure onPostExecute() is not executed after the cancel request. The flag is
            // necessary because {@link AsyncTask} still calls onPostExecute() if the cancel request
            // came after the worker thread was finished.
            mIsCancelled = true;
        }

        @Override
        protected void onPostExecute(Uri uri) {
            // Make sure the {@link Fragment} is at least still attached to the {@link Activity}
            // before continuing. Null URIs should still be allowed so that the list can be
            // refreshed and a default contact can be selected (i.e. the case of deleted
            // contacts).
            if (mIsCancelled || !isAdded()) {
                return;
            }
            onContactUriQueryFinished(uri);
        }
    }

    public PluginContactBrowseListFragment() {
        if (DEBUG) Log.d(TAG, "Constructor");
        setPhotoLoaderEnabled(true);
        setQuickContactEnabled(false);
        setSectionHeaderDisplayEnabled(true);
        setVisibleScrollbarEnabled(true);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (DEBUG) Log.d(TAG, "onAttach");
    }

    // This method is called in parent class of onCreateView
    @Override
    protected View inflateView(LayoutInflater inflater, ViewGroup container) {
        mLayout = inflater.inflate(R.layout.plugin_contact_list_content, null);
        return mLayout;
    }

    @Override
    protected void onCreateView(LayoutInflater inflater, ViewGroup container) {
        if (DEBUG) Log.d(TAG, "onCreateView");
        super.onCreateView(inflater, container);
        mLoginView = mLayout.findViewById(R.id.plugin_login_layout);
        mListView = mLayout.findViewById(android.R.id.list);
        mLoginBtn = (Button) mLayout.findViewById(R.id.plugin_login_button);
        mLoginBtn.setOnClickListener(this);
        mLoginIconView = (ImageView) mLayout.findViewById(R.id.plugin_login_icon);
        mLoginMsg = (TextView) mLayout.findViewById(R.id.plugin_login_msg);
        mEmptyView = (TextView) mLayout.findViewById(android.R.id.empty);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        if (DEBUG) Log.d(TAG, "onActivityCreated");
        super.onActivityCreated(savedInstanceState);
        mPrefs = getActivity().getSharedPreferences(mPrefsFileName, Context.MODE_PRIVATE);
        updatePluginView(); // this is execute again reflect change in plugin info
        if (savedInstanceState != null) {
            restoreFilter();
        }
        restoreSelectedUri(false);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        final SharedPreferences oldFragPrefs = getActivity().getSharedPreferences(mPrefsFileName,
                Context.MODE_PRIVATE);
        oldFragPrefs.edit().clear().commit();
    }

    public void setFilter(ContactListFilter filter) {
        setFilter(filter, true);
    }

    public void setFilter(ContactListFilter filter, boolean restoreSelectedUri) {
        if (mFilter == null && filter == null) {
            return;
        }

        if (mFilter != null && mFilter.equals(filter)) {
            return;
        }

        if (DEBUG) Log.v(TAG, "New filter: " + filter);

        mFilter = filter;
        mLastSelectedPosition = -1;
        saveFilter();
        if (restoreSelectedUri) {
            mSelectedContactUri = null;
            restoreSelectedUri(true);
        }
        reloadData();
    }

    public ContactListFilter getFilter() {
        return mFilter;
    }

    @Override
    public void restoreSavedState(Bundle savedState) {
        super.restoreSavedState(savedState);
        if (savedState == null) {
            if (DEBUG) Log.d(TAG, "restoreSavedState null");
            return;
        }

        mFilter = savedState.getParcelable(KEY_FILTER);
        mSelectedContactUri = savedState.getParcelable(KEY_SELECTED_URI);
        mSelectionVerified = savedState.getBoolean(KEY_SELECTION_VERIFIED);
        mLastSelectedPosition = savedState.getInt(KEY_LAST_SELECTED_POSITION);
        mPrefsFileName = savedState.getString(KEY_SHARED_PREFS_FILE_NAME, "");
        if (DEBUG) Log.d(TAG, "restoreSavedState mPrefsFileName :" + mPrefsFileName);
        if (!mPrefsFileName.equals("")) {
            mPrefs = getActivity().getSharedPreferences(mPrefsFileName, Context.MODE_PRIVATE);
        }
        parseSelectedContactUri();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(KEY_FILTER, mFilter);
        outState.putParcelable(KEY_SELECTED_URI, mSelectedContactUri);
        outState.putBoolean(KEY_SELECTION_VERIFIED, mSelectionVerified);
        outState.putInt(KEY_LAST_SELECTED_POSITION, mLastSelectedPosition);
        outState.putString(KEY_SHARED_PREFS_FILE_NAME, mPrefsFileName);
    }

    protected void refreshSelectedContactUri() {
        if (mContactLookupTask != null) {
            mContactLookupTask.cancel();
        }

        if (!isSelectionVisible()) {
            return;
        }

        mRefreshingContactUri = true;

        if (mSelectedContactUri == null) {
            onContactUriQueryFinished(null);
            return;
        }

        if (mSelectedContactDirectoryId != ContactsContract.Directory.DEFAULT
                && mSelectedContactDirectoryId != ContactsContract.Directory.LOCAL_INVISIBLE) {
            onContactUriQueryFinished(mSelectedContactUri);
        } else {
            mContactLookupTask = new ContactLookupTask(mSelectedContactUri);
            mContactLookupTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[])null);
        }
    }

    protected void onContactUriQueryFinished(Uri uri) {
        mRefreshingContactUri = false;
        mSelectedContactUri = uri;
        parseSelectedContactUri();
        checkSelection();
    }

    public InCallPluginInfo getPluginInfo() {
        return mInCallPluginInfo;
    }

    public Uri getSelectedContactUri() {
        return mSelectedContactUri;
    }

    /**
     * Sets the new selection for the list.
     */
    public void setSelectedContactUri(Uri uri) {
        setSelectedContactUri(uri, true, false /* no smooth scroll */, true, false);
    }

    /**
     * Sets the new contact selection.
     *
     * @param uri the new selection
     * @param required if true, we need to check if the selection is present in
     *            the list and if not notify the listener so that it can load a
     *            different list
     * @param smoothScroll if true, the UI will roll smoothly to the new
     *            selection
     * @param persistent if true, the selection will be stored in shared
     *            preferences.
     * @param willReloadData if true, the selection will be remembered but not
     *            actually shown, because we are expecting that the data will be
     *            reloaded momentarily
     */
    private void setSelectedContactUri(Uri uri, boolean required, boolean smoothScroll,
                                       boolean persistent, boolean willReloadData) {
        mSmoothScrollRequested = smoothScroll;
        mSelectionToScreenRequested = true;

        if ((mSelectedContactUri == null && uri != null)
                || (mSelectedContactUri != null && !mSelectedContactUri.equals(uri))) {
            mSelectionVerified = false;
            mSelectionPersistenceRequested = persistent;
            mSelectedContactUri = uri;
            parseSelectedContactUri();

            if (!willReloadData) {
                // Configure the adapter to show the selection based on the
                // lookup key extracted from the URI
                ContactListAdapter adapter = getAdapter();
                if (adapter != null) {
                    adapter.setSelectedContact(mSelectedContactDirectoryId,
                            mSelectedContactLookupKey, mSelectedContactId);
                    getListView().invalidateViews();
                }
            }

            // Also, launch a loader to pick up a new lookup URI in case it has changed
            refreshSelectedContactUri();
        }
    }

    private void parseSelectedContactUri() {
        if (mSelectedContactUri != null) {
            String directoryParam =
                    mSelectedContactUri.getQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY);
            mSelectedContactDirectoryId = TextUtils.isEmpty(directoryParam) ?
                    ContactsContract.Directory.DEFAULT : Long.parseLong(directoryParam);
            if (mSelectedContactUri.toString().startsWith(
                    ContactsContract.Contacts.CONTENT_LOOKUP_URI.toString())) {
                List<String> pathSegments = mSelectedContactUri.getPathSegments();
                mSelectedContactLookupKey = Uri.encode(pathSegments.get(2));
                if (pathSegments.size() == 4) {
                    mSelectedContactId = ContentUris.parseId(mSelectedContactUri);
                }
            } else if (mSelectedContactUri.toString().startsWith(
                    ContactsContract.Contacts.CONTENT_URI.toString()) &&
                    mSelectedContactUri.getPathSegments().size() >= 2) {
                mSelectedContactLookupKey = null;
                mSelectedContactId = ContentUris.parseId(mSelectedContactUri);
            } else {
                Log.e(TAG, "Unsupported contact URI: " + mSelectedContactUri);
                mSelectedContactLookupKey = null;
                mSelectedContactId = 0;
            }

        } else {
            mSelectedContactDirectoryId = ContactsContract.Directory.DEFAULT;
            mSelectedContactLookupKey = null;
            mSelectedContactId = 0;
        }
    }

    @Override
    protected void configureAdapter() {
        super.configureAdapter();

        ContactListAdapter adapter = getAdapter();
        if (adapter == null) {
            return;
        }

        if (mFilter != null) {
            adapter.setFilter(mFilter);
        }

        adapter.setIncludeProfile(false); // TODO: do not need profile
    }

    @Override
    public CursorLoader createCursorLoader(Context context) {
        return new ProfileAndContactsLoader(context);
    }
    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        super.onLoadFinished(loader, data);
        mSelectionVerified = false;

        // Refresh the currently selected lookup in case it changed while we were sleeping
        refreshSelectedContactUri();
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        super.onLoaderReset(loader);
    }

    private void checkSelection() {
        if (mSelectionVerified) {
            return;
        }

        if (mRefreshingContactUri) {
            return;
        }

        if (isLoadingDirectoryList()) {
            return;
        }

        ContactListAdapter adapter = getAdapter();
        if (adapter == null) {
            return;
        }

        boolean directoryLoading = true;
        int count = adapter.getPartitionCount();
        for (int i = 0; i < count; i++) {
            CompositeCursorAdapter.Partition partition = adapter.getPartition(i);
            if (partition instanceof DirectoryPartition) {
                DirectoryPartition directory = (DirectoryPartition) partition;
                if (directory.getDirectoryId() == mSelectedContactDirectoryId) {
                    directoryLoading = directory.isLoading();
                    break;
                }
            }
        }

        if (directoryLoading) {
            return;
        }

        adapter.setSelectedContact(
                mSelectedContactDirectoryId, mSelectedContactLookupKey, mSelectedContactId);

        final int selectedPosition = adapter.getSelectedContactPosition();
        if (selectedPosition != -1) {
            mLastSelectedPosition = selectedPosition;
        } else {
            saveSelectedUri(null);
            selectDefaultContact();
        }

        mSelectionVerified = true;

        if (mSelectionPersistenceRequested) {
            saveSelectedUri(mSelectedContactUri);
            mSelectionPersistenceRequested = false;
        }

        if (mSelectionToScreenRequested) {
            requestSelectionToScreen(selectedPosition);
        }

        getListView().invalidateViews();

        if (mListener != null) {
            mListener.onSelectionChange();
        }
    }

    protected void selectDefaultContact() {
        Uri contactUri = null;
        ContactListAdapter adapter = getAdapter();
        if (mLastSelectedPosition != -1) {
            int count = adapter.getCount();
            int pos = mLastSelectedPosition;
            if (pos >= count && count > 0) {
                pos = count - 1;
            }
            contactUri = adapter.getContactUri(pos);
        }

        if (contactUri == null) {
            contactUri = adapter.getFirstContactUri();
        }

        setSelectedContactUri(contactUri, false, mSmoothScrollRequested, false, false);
    }

    protected void requestSelectionToScreen(int selectedPosition) {
        if (selectedPosition != -1) {
            AutoScrollListView listView = (AutoScrollListView)getListView();
            listView.requestPositionToScreen(
                    selectedPosition + listView.getHeaderViewsCount(), mSmoothScrollRequested);
            mSelectionToScreenRequested = false;
        }
    }

    @Override
    public boolean isLoading() {
        return mRefreshingContactUri || super.isLoading();
    }

    @Override
    protected void startLoading() {
        mStartedLoading = true;
        mSelectionVerified = false;
        super.startLoading();
    }

    public void reloadDataAndSetSelectedUri(Uri uri) {
        setSelectedContactUri(uri, true, true, true, true);
        reloadData();
    }

    @Override
    public void reloadData() {
        if (mStartedLoading) {
            mSelectionVerified = false;
            mLastSelectedPosition = -1;
            super.reloadData();
        }
    }

    public void setOnContactListActionListener(OnContactBrowserActionListener listener) {
        mListener = listener;
    }

    public void viewContact(Uri contactUri) {
        setSelectedContactUri(contactUri, false, false, true, false);
        if (mListener != null) mListener.onViewContactAction(contactUri);
    }

    public void deleteContact(Uri contactUri) {
        if (mListener != null) mListener.onDeleteContactAction(contactUri);
    }

    private void notifyInvalidSelection() {
        if (mListener != null) mListener.onInvalidSelection();
    }

    @Override
    protected void finish() {
        super.finish();
        if (mListener != null) mListener.onFinishAction();
    }

    private void saveSelectedUri(Uri contactUri) {
        if (isSearchMode()) {
            return;
        }

        ContactListFilter.storeToPreferences(mPrefs, mFilter);

        SharedPreferences.Editor editor = mPrefs.edit();
        if (contactUri == null) {
            editor.remove(getPersistentSelectionKey());
        } else {
            editor.putString(getPersistentSelectionKey(), contactUri.toString());
        }
        editor.apply();
    }

    private void restoreSelectedUri(boolean willReloadData) {
        String selectedUri = mPrefs.getString(getPersistentSelectionKey(), null);
        if (selectedUri == null) {
            setSelectedContactUri(null, false, false, false, willReloadData);
        } else {
            setSelectedContactUri(Uri.parse(selectedUri), false, false, false, willReloadData);
        }
    }

    private void saveFilter() {
        ContactListFilter.storeToPreferences(mPrefs, mFilter);
    }

    private void restoreFilter() {
        if (mPrefs != null) {
            mFilter = ContactListFilter.restoreDefaultPreferences(mPrefs);
        }
    }

    private String getPersistentSelectionKey() {
        if (mFilter == null) {
            return mPersistentSelectionPrefix;
        } else {
            return mPersistentSelectionPrefix + "-" + mFilter.getId();
        }
    }

    public boolean isOptionsMenuChanged() {
        // This fragment does not have an option menu of its own
        return false;
    }

    @Override
    protected void onItemClick(int position, long id) {
        final Uri uri = getAdapter().getContactUri(position);
        if (uri == null) {
            return;
        }
        viewContact(uri);
    }

    @Override
    protected ContactListAdapter createListAdapter() {
        DefaultContactListAdapter adapter = new DefaultContactListAdapter(getContext());
        adapter.setSectionHeaderDisplayEnabled(isSectionHeaderDisplayEnabled());
        adapter.setDisplayPhotos(true);
        adapter.setPhotoPosition(
                ContactListItemView.getDefaultPhotoPosition(/* opposite = */ false));

        return adapter;
    }

    @Override
    public void onClick(View view) {
        if (mInCallPluginInfo != null) {
            try {
                if (view == mLoginBtn) {
                    if (mInCallPluginInfo.mCallMethodInfo.mLoginIntent != null) {
                        mInCallPluginInfo.mCallMethodInfo.mLoginIntent.send();
                        InCallMetricsHelper.setValue(
                                getActivity(),
                                mInCallPluginInfo.mCallMethodInfo.mComponent,
                                InCallMetricsHelper.Categories.INAPP_NUDGES,
                                InCallMetricsHelper.Events.INAPP_NUDGE_CONTACTS_TAB_LOGIN,
                                InCallMetricsHelper.Parameters.EVENT_ACCEPTANCE,
                                InCallMetricsHelper.EVENT_ACCEPT,
                                InCallMetricsHelper.generateNudgeId(mInCallPluginInfo
                                        .mCallMethodInfo.mLoginSubtitle));
                    }
                } else if (view == mEmptyView) {
                    if (mInCallPluginInfo.mCallMethodInfo.mDefaultDirectorySearchIntent != null) {
                        mInCallPluginInfo.mCallMethodInfo.mDefaultDirectorySearchIntent.send();
                    }
                }
            } catch (PendingIntent.CanceledException e) {
                Log.e(TAG, "PendingIntent exception", e);
            }
        }
    }

    public synchronized void updateInCallPluginInfo(InCallPluginInfo pluginInfo) {
        mInCallPluginInfo = pluginInfo;
        updatePluginView();
    }

    public synchronized void updatePluginView() {
        if (mInCallPluginInfo != null) {
            if (DEBUG) Log.d(TAG, "updatePluginView: " + mInCallPluginInfo.mCallMethodInfo.mName);
            if (mListView != null && mLoginView != null) {
                // if the UI auth state is initialized the first time or the auth state has changed
                if (!mInitialized || mInCallPluginInfo.mCallMethodInfo.mIsAuthenticated !=
                        mAuthenticated) {
                    mInitialized = true;
                    mAuthenticated = mInCallPluginInfo.mCallMethodInfo.mIsAuthenticated;
                    if (mAuthenticated || CallMethodUtils.isSoftLoggedOut(getContext(),
                            mInCallPluginInfo.mCallMethodInfo)) {
                        // Show list view
                        mLoginView.setVisibility(View.GONE);
                        mListView.setVisibility(View.VISIBLE);
                        if (mEmptyView != null) {
                            mEmptyView.setVisibility(View.VISIBLE);
                            ((ListView) mListView).setEmptyView(mEmptyView);
                            initEmptyView();
                        }
                    } else {
                        // Show login view
                        ((ListView) mListView).setEmptyView(null);
                        mListView.setVisibility(View.GONE);
                        if (mEmptyView != null) {
                            mEmptyView.setVisibility(View.GONE);
                        }
                        mLoginView.setVisibility(View.VISIBLE);
                        if (mLoginIconView != null) {
                            if (mInCallPluginInfo.mCallMethodInfo.mLoginIconId == 0) {
                                // plugin does not provide a valid icon
                                mLoginIconView.setVisibility(View.GONE);
                            } else {
                                if (mInCallPluginInfo.mCallMethodInfo.mLoginIcon != null) {
                                    mLoginIconView.setImageDrawable(
                                            mInCallPluginInfo.mCallMethodInfo.mLoginIcon);
                                } else {
                                    // The fragment has been restored so only the icon id is
                                    // a valid value, need to manually load
                                    if (mInCallPluginInfo.mCallMethodInfo.mLoginIconId != 0) {
                                        new GetDrawableAsyncTask().execute();
                                    }
                                }
                            }
                        }
                        if (mLoginMsg != null) {
                            if (!TextUtils
                                    .isEmpty(mInCallPluginInfo.mCallMethodInfo.mLoginSubtitle)) {
                                mLoginMsg.setText(mInCallPluginInfo.mCallMethodInfo.mLoginSubtitle);
                            } else {
                                mLoginMsg
                                        .setText(getResources().getString(R.string.plugin_login_msg,
                                                mInCallPluginInfo.mCallMethodInfo.mName));
                            }
                        }
                    }
                }
            }
            mPrefsFileName = PREFS_FILE_PREFIX +
                    mInCallPluginInfo.mCallMethodInfo.mComponent.getClassName();
            if (mPrefs != null) {
                // Account filter should be updated
                if (mInCallPluginInfo.mCallMethodInfo.mIsAuthenticated && (!TextUtils.equals
                        (mAccountType, mInCallPluginInfo.mCallMethodInfo.mAccountType) ||
                        TextUtils.equals(mAccountHandle,
                        mInCallPluginInfo.mCallMethodInfo.mAccountHandle)) &&
                        !TextUtils.isEmpty(mInCallPluginInfo.mCallMethodInfo.mAccountType) &&
                        !TextUtils.isEmpty(mInCallPluginInfo.mCallMethodInfo.mAccountHandle)) {
                    mAccountType = mInCallPluginInfo.mCallMethodInfo.mAccountType;
                    mAccountHandle = mInCallPluginInfo.mCallMethodInfo.mAccountHandle;
                    setFilter(ContactListFilter.createAccountFilter(
                            mInCallPluginInfo.mCallMethodInfo.mAccountType,
                            mInCallPluginInfo.mCallMethodInfo.mAccountHandle,
                            null,
                            null));
                }
            }
        }
    }

    private class GetDrawableAsyncTask extends AsyncTask<Void, Void, Drawable> {
        @Override
        protected Drawable doInBackground(Void... params) {
            Drawable loginIcon = null;
            if (mInCallPluginInfo.mCallMethodInfo.mLoginIconId != 0) {
                loginIcon = InCallPluginUtils.getDrawable
                        (PluginContactBrowseListFragment.this.getActivity(),
                                mInCallPluginInfo.mCallMethodInfo.mLoginIconId,
                                mInCallPluginInfo.mCallMethodInfo.mComponent);
            }
            return loginIcon;
        }

        @Override
        protected void onPostExecute(Drawable icon) {
            if (icon != null) {
                mLoginIconView.setImageDrawable(icon);
            }
        }
    }

    private void initEmptyView() {
        if (mEmptyView != null) {
            Resources rc = getResources();
            mEmptyView.setText(rc.getString(R.string.plugin_empty_list_text,
                            TextUtils.isEmpty(mInCallPluginInfo.mCallMethodInfo.mName) ? "" :
                                    mInCallPluginInfo.mCallMethodInfo.mName),
                    TextView.BufferType.SPANNABLE);
            Spannable actionText = new SpannableString(
                    rc.getString(R.string.plugin_empty_list_action_text));
            actionText.setSpan(new ForegroundColorSpan(rc.getColor(R.color.primary_color)), 0,
                    actionText.length(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
            actionText.setSpan(new StyleSpan(Typeface.BOLD), 0, actionText.length(), Spannable
                    .SPAN_INCLUSIVE_EXCLUSIVE);
            mEmptyView.append(actionText);
            mEmptyView.setOnClickListener(this);
        }
    }
}