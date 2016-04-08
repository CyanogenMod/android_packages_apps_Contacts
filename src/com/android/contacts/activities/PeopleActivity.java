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

package com.android.contacts.activities;

import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.UserManager;
import android.preference.PreferenceActivity;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.ProviderStatus;
import android.provider.Settings;
import android.preference.PreferenceManager;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.Toast;
import android.widget.Toolbar;

import com.android.contacts.ContactsActivity;
import com.android.contacts.incall.InCallMetricsHelper;
import com.android.phone.common.incall.InCallPluginHelper;
import com.android.contacts.incall.InCallPluginInfo;
import com.android.contacts.R;
import com.android.contacts.activities.ActionBarAdapter.TabState;
import com.android.contacts.common.ContactsUtils;
import com.android.contacts.common.activity.RequestPermissionsActivity;
import com.android.contacts.common.dialog.ClearFrequentsDialog;
import com.android.contacts.common.util.ImplicitIntentsUtil;
import com.android.contacts.common.widget.FloatingActionButtonController;
import com.android.contacts.editor.EditorIntents;
import com.android.contacts.common.editor.SelectAccountDialogFragment;
import com.android.contacts.group.GroupBrowseListFragment;
import com.android.contacts.group.GroupBrowseListFragment.OnGroupBrowserActionListener;
import com.android.contacts.group.GroupDetailFragment;
import com.android.contacts.interactions.ContactDeletionInteraction;
import com.android.contacts.common.interactions.ImportExportDialogFragment;
import com.android.contacts.common.interactions.ImportExportDialogFragment.ExportToSimThread;
import com.android.contacts.common.list.AccountFilterActivity;
import com.android.contacts.common.list.ContactEntryListFragment;
import com.android.contacts.common.list.ContactListFilter;
import com.android.contacts.common.list.ContactListFilterController;
import com.android.contacts.common.list.ContactTileAdapter.DisplayType;
import com.android.contacts.interactions.ContactMultiDeletionInteraction;
import com.android.contacts.interactions.ContactMultiDeletionInteraction.MultiContactDeleteListener;
import com.android.contacts.interactions.JoinContactsDialogFragment;
import com.android.contacts.interactions.JoinContactsDialogFragment.JoinContactsListener;
import com.android.contacts.list.MultiSelectContactsListFragment;
import com.android.contacts.list.MultiSelectContactsListFragment.OnCheckBoxListActionListener;
import com.android.contacts.list.ContactTileListFragment;
import com.android.contacts.list.ContactsIntentResolver;
import com.android.contacts.list.ContactsRequest;
import com.android.contacts.list.ContactsUnavailableFragment;
import com.android.contacts.common.list.DirectoryListLoader;
import com.android.contacts.common.preference.DisplayOptionsPreferenceFragment;
import com.android.contacts.list.OnContactBrowserActionListener;
import com.android.contacts.list.OnContactsUnavailableActionListener;
import com.android.contacts.list.ProviderStatusWatcher;
import com.android.contacts.list.ProviderStatusWatcher.ProviderStatusListener;
import com.android.contacts.common.list.ViewPagerTabs;
import com.android.contacts.list.PluginContactBrowseListFragment;
import com.android.contacts.preference.ContactsPreferenceActivity;
import com.android.contacts.common.SimContactsConstants;
import com.android.contacts.common.util.AccountFilterUtil;
import com.android.contacts.common.util.ViewUtil;
import com.android.contacts.quickcontact.QuickContactActivity;
import com.android.contacts.util.AccountPromptUtils;
import com.android.contacts.common.util.Constants;
import com.android.contacts.common.vcard.ExportVCardActivity;
import com.android.contacts.common.vcard.VCardCommonArguments;
import com.android.contacts.util.DialogManager;
import com.android.contactsbind.HelpUtils;
import com.android.phone.common.incall.CallMethodInfo;
import com.android.phone.common.incall.utils.CallMethodFilters;
import com.android.phone.common.incall.utils.CallMethodUtils;

import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Displays a list to browse contacts.
 */
public class PeopleActivity extends ContactsActivity implements
        View.OnCreateContextMenuListener,
        View.OnClickListener,
        ActionBarAdapter.Listener,
        DialogManager.DialogShowingViewActivity,
        ContactListFilterController.ContactListFilterListener,
        ProviderStatusListener,
        MultiContactDeleteListener,
        JoinContactsListener {

    private static final String TAG = "PeopleActivity";
    private static final boolean DEBUG = false;

    public static String EDITABLE_KEY = "search_contacts";
    private static final String ENABLE_DEBUG_OPTIONS_HIDDEN_CODE = "debug debug!";
    private static final int INCALL_PLUGIN_LOADER_ID = 0;

    // These values needs to start at 2. See {@link ContactEntryListFragment}.
    private static final int SUBACTIVITY_ACCOUNT_FILTER = 2;
    private static final int SUBACTIVITY_NEW_GROUP = 4;
    private static final int SUBACTIVITY_EDIT_GROUP = 5;
    private final DialogManager mDialogManager = new DialogManager(this);

    private ContactsIntentResolver mIntentResolver;
    private ContactsRequest mRequest;

    private ActionBarAdapter mActionBarAdapter;
    private FloatingActionButtonController mFloatingActionButtonController;
    private GroupDetailFragment mGroupDetailFragment;
    private final GroupDetailFragmentListener mGroupDetailFragmentListener =
            new GroupDetailFragmentListener();
    private boolean wasLastFabAnimationScaleIn = false;

    private ContactTileListFragment.Listener mFavoritesFragmentListener =
            new StrequentContactListFragmentListener();

    private ContactListFilterController mContactListFilterController;

    private boolean mAccountUnavailable;
    private ProviderStatusWatcher mProviderStatusWatcher;
    private Integer mProviderStatus;

    private boolean mOptionsMenuContactsAvailable;

    /**
     * Showing a list of Contacts. Also used for showing search results in search mode.
     */
    private MultiSelectContactsListFragment mAllFragment;
    private ContactTileListFragment mFavoritesFragment;
    private GroupBrowseListFragment mGroupsFragment;
    private ContactsUnavailableFragment mAllUnavailableFragment;
    private ContactsUnavailableFragment mFavoritesUnavailableFragment;
    private ContactsUnavailableFragment mGroupsUnavailableFragment;
    private List<InCallPluginInfo> mPluginTabInfo = new ArrayList<InCallPluginInfo>();
    private int mPluginLength;
    private int mTabStateGroup = TabState.GROUPS;

    /** ViewPager for swipe */
    private ViewPager mTabPager;
    private ViewPagerTabs mViewPagerTabs;
    private TabPagerAdapter mTabPagerAdapter;
    private List<TabEntry> mTabTitles;
    private static final String CALL_METHOD_HELPER_SUBSCRIBER_ID = "PeopleActivity";
    private final TabPagerListener mTabPagerListener = new TabPagerListener();
    private int mPageStateCount; // total number of pages
    private boolean mEnableDebugMenuOptions;

    /* Floating action button */
    private View mFloatingActionButtonContainer;
    private ImageButton mFloatingActionButton;

    private ExportToSimThread mExportThread = null;
    /**
     * True if this activity instance is a re-created one.  i.e. set true after orientation change.
     * This is set in {@link #onCreate} for later use in {@link #onStart}.
     */
    private boolean mIsRecreatedInstance;

    /**
     * If {@link #configureFragments(boolean)} is already called.  Used to avoid calling it twice
     * in {@link #onStart}.
     * (This initialization only needs to be done once in onStart() when the Activity was just
     * created from scratch -- i.e. onCreate() was just called)
     */
    private boolean mFragmentInitialized;

    /**
     * This is to disable {@link #onOptionsItemSelected} when we trying to stop the activity.
     */
    private boolean mDisableOptionItemSelected;

    /** Sequential ID assigned to each instance; used for logging */
    private final int mInstanceId;
    private static final AtomicInteger sNextInstanceId = new AtomicInteger();
    // TODO: we need to refactor the export code in future release.
    // QRD enhancement: contacts list for multi contact pick
    private ArrayList<String[]> mContactList;

    private BroadcastReceiver mExportToSimCompleteListener = null;

    final String FAVORITE_TAG = "tab-pager-favorite";
    final String ALL_TAG = "tab-pager-all";
    final String GROUPS_TAG = "tab-pager-groups";
    final String FAVORITE_UNAVAILABLE_TAG = "tab-pager-favorite-unav";
    final String ALL_UNAVAILABLE_TAG = "tab-pager-all-unav";
    final String GROUPS_UNAVAILABLE_TAG = "tab-pager-groups-unav";
    private static final String KEY_PLUGIN_INFO_LIST = "pluginInfoList";
    SharedPreferences mPrefs;

    public PeopleActivity() {
        mInstanceId = sNextInstanceId.getAndIncrement();
        mIntentResolver = new ContactsIntentResolver(this);
        mProviderStatusWatcher = ProviderStatusWatcher.getInstance(this);
    }

    @Override
    public String toString() {
        // Shown on logcat
        return String.format("%s@%d", getClass().getSimpleName(), mInstanceId);
    }

    public boolean areContactsAvailable() {
        return (mProviderStatus != null)
                && mProviderStatus.equals(ProviderStatus.STATUS_NORMAL);
    }

    private boolean areContactWritableAccountsAvailable() {
        return ContactsUtils.areContactWritableAccountsAvailable(this);
    }

    private boolean areGroupWritableAccountsAvailable() {
        return ContactsUtils.areGroupWritableAccountsAvailable(this);
    }

    @Override
    protected void onCreate(Bundle savedState) {
        if (Log.isLoggable(Constants.PERFORMANCE_TAG, Log.DEBUG)) {
            Log.d(Constants.PERFORMANCE_TAG, "PeopleActivity.onCreate start");
        }
        super.onCreate(savedState);
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (RequestPermissionsActivity.startPermissionActivity(this)) {
            return;
        }

        if (!processIntent(false)) {
            finish();
            return;
        }
        mContactListFilterController = ContactListFilterController.getInstance(this);
        mContactListFilterController.checkFilterValidity(false);
        mContactListFilterController.addListener(this);

        mProviderStatusWatcher.addListener(this);

        mIsRecreatedInstance = (savedState != null);
        createViewsAndFragments(savedState);

        if (Log.isLoggable(Constants.PERFORMANCE_TAG, Log.DEBUG)) {
            Log.d(Constants.PERFORMANCE_TAG, "PeopleActivity.onCreate finish");
        }
        getWindow().setBackgroundDrawable(null);
        registerReceivers();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        setIntent(intent);
        if (!processIntent(true)) {
            finish();
            return;
        }
        mActionBarAdapter.initialize(null, mRequest, mPageStateCount);

        mContactListFilterController.checkFilterValidity(false);

        // Re-configure fragments.
        configureFragments(true /* from request */);
        initializeFabVisibility();
        invalidateOptionsMenuIfNeeded();
    }

    private void registerReceivers() {
        mExportToSimCompleteListener = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action.equals(SimContactsConstants.INTENT_EXPORT_COMPLETE)) {
                    ImportExportDialogFragment.destroyExportToSimThread();
                    mExportThread = null;
                }
            }
        };

        IntentFilter exportCompleteFilter = new IntentFilter(
                SimContactsConstants.INTENT_EXPORT_COMPLETE);
        registerReceiver(mExportToSimCompleteListener, exportCompleteFilter);
    }

    /**
     * Resolve the intent and initialize {@link #mRequest}, and launch another activity if redirect
     * is needed.
     *
     * @param forNewIntent set true if it's called from {@link #onNewIntent(Intent)}.
     * @return {@code true} if {@link PeopleActivity} should continue running.  {@code false}
     *         if it shouldn't, in which case the caller should finish() itself and shouldn't do
     *         farther initialization.
     */
    private boolean processIntent(boolean forNewIntent) {
        // Extract relevant information from the intent
        mRequest = mIntentResolver.resolveIntent(getIntent());
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, this + " processIntent: forNewIntent=" + forNewIntent
                    + " intent=" + getIntent() + " request=" + mRequest);
        }
        if (!mRequest.isValid()) {
            setResult(RESULT_CANCELED);
            return false;
        }

        if (mRequest.getActionCode() == ContactsRequest.ACTION_VIEW_CONTACT) {
            final Intent intent = ImplicitIntentsUtil.composeQuickContactIntent(
                    mRequest.getContactUri(), QuickContactActivity.MODE_FULLY_EXPANDED);
            ImplicitIntentsUtil.startActivityInApp(this, intent);
            return false;
        }
        return true;
    }

    private void createViewsAndFragments(Bundle savedState) {
        // Disable the ActionBar so that we can use a Toolbar. This needs to be called before
        // setContentView().

        getWindow().requestFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.people_activity);

        // Configure action button, need to initialize early before ViewPager sets the
        // visibility depending on the fragment type
        mFloatingActionButtonContainer = findViewById(R.id.floating_action_button_container);
        mFloatingActionButton = (ImageButton) findViewById(R.id.floating_action_button);
        mFloatingActionButton.setOnClickListener(this);
        mFloatingActionButtonController = new FloatingActionButtonController(this,
                mFloatingActionButtonContainer, mFloatingActionButton);

        final FragmentManager fragmentManager = getFragmentManager();

        // Hide all tabs (the current tab will later be reshown once a tab is selected)
        final FragmentTransaction transaction = fragmentManager.beginTransaction();

        mTabTitles = new LinkedList<TabEntry>();
        mTabTitles.add(TabState.FAVORITES, new TabEntry(FAVORITE_TAG, getString(R.string
                .favorites_tab_label)));
        mTabTitles.add(TabState.ALL, new TabEntry(ALL_TAG, getString(R.string
                .all_contacts_tab_label)));
        mTabTitles.add(TabState.GROUPS,new TabEntry(GROUPS_TAG, getString(R.string
                .contacts_groups_label)));

        if (savedState != null) {
            // Reconstruct the plugin info list
            List<InCallPluginInfo> restoreList = savedState.getParcelableArrayList
                    (KEY_PLUGIN_INFO_LIST);
            if (restoreList != null) {
                mPluginTabInfo = restoreList;
            }
            mPluginLength = mPluginTabInfo.size();
            for (int i = 0; i < mPluginLength; i++) {
                InCallPluginInfo pluginInfo = mPluginTabInfo.get(i);
                mCallMethodMap.put(pluginInfo.mCallMethodInfo.mComponent, pluginInfo
                        .mCallMethodInfo);
                mTabTitles.add(TabState.GROUPS + i, new TabEntry(pluginInfo.mTabTag,
                        pluginInfo.mCallMethodInfo.mName));
                pluginInfo.mFragment = (PluginContactBrowseListFragment) fragmentManager
                        .findFragmentByTag(pluginInfo.mTabTag);
                pluginInfo.mFragment.setOnContactListActionListener
                        (new PluginContactBrowserActionListener());
                pluginInfo.mFragment.updateInCallPluginInfo(pluginInfo);
            }
        } else {
            // Init plugin info list
            mPluginTabInfo.clear();
            mPluginLength = 0;
        }
        mPageStateCount = TabState.COUNT + mPluginLength;
        mTabStateGroup = TabState.GROUPS + mPluginLength;

        mTabPager = getView(R.id.tab_pager);
        mTabPagerAdapter = new TabPagerAdapter();
        mTabPager.setAdapter(mTabPagerAdapter);
        mTabPager.setOnPageChangeListener(mTabPagerListener);

        // Configure toolbar and toolbar tabs. If in landscape mode, we  configure tabs differntly.
        final Toolbar toolbar = getView(R.id.toolbar);
        setActionBar(toolbar);
        final ViewPagerTabs portraitViewPagerTabs
                = (ViewPagerTabs) findViewById(R.id.lists_pager_header);
        ViewPagerTabs landscapeViewPagerTabs = null;
        if (portraitViewPagerTabs ==  null) {
            landscapeViewPagerTabs = (ViewPagerTabs) getLayoutInflater().inflate(
                    R.layout.people_activity_tabs_lands, toolbar, /* attachToRoot = */ false);
            mViewPagerTabs = landscapeViewPagerTabs;
        } else {
            mViewPagerTabs = portraitViewPagerTabs;
        }
        mViewPagerTabs.setViewPager(mTabPager);

        final String FAVORITE_TAG = "tab-pager-favorite";
        final String ALL_TAG = "tab-pager-all";
        final String GROUPS_TAG = "tab-pager-groups";

        // Create the fragments and add as children of the view pager.
        // The pager adapter will only change the visibility; it'll never create/destroy
        // fragments.
        // However, if it's after screen rotation, the fragments have been re-created by
        // the fragment manager, so first see if there're already the target fragments
        // existing.
        mFavoritesFragment = (ContactTileListFragment)
                fragmentManager.findFragmentByTag(FAVORITE_TAG);
        mAllFragment = (MultiSelectContactsListFragment)
                fragmentManager.findFragmentByTag(ALL_TAG);
        mGroupsFragment = (GroupBrowseListFragment)
                fragmentManager.findFragmentByTag(GROUPS_TAG);
        mFavoritesUnavailableFragment = (ContactsUnavailableFragment) fragmentManager
                .findFragmentByTag(FAVORITE_UNAVAILABLE_TAG);
        mAllUnavailableFragment = (ContactsUnavailableFragment) fragmentManager
                .findFragmentByTag(ALL_UNAVAILABLE_TAG);
        mGroupsUnavailableFragment = (ContactsUnavailableFragment) fragmentManager
                .findFragmentByTag(GROUPS_UNAVAILABLE_TAG);


        if (mFavoritesFragment == null) {
            mFavoritesFragment = new ContactTileListFragment();
            mAllFragment = new MultiSelectContactsListFragment();
            mGroupsFragment = new GroupBrowseListFragment();
            mFavoritesUnavailableFragment = new ContactsUnavailableFragment();
            mAllUnavailableFragment = new ContactsUnavailableFragment();
            mGroupsUnavailableFragment = new ContactsUnavailableFragment();
            mFavoritesUnavailableFragment.setOnContactsUnavailableActionListener(
                    new ContactsUnavailableFragmentListener());
            mAllUnavailableFragment.setOnContactsUnavailableActionListener(
                    new ContactsUnavailableFragmentListener());
            mGroupsUnavailableFragment.setOnContactsUnavailableActionListener(
                    new ContactsUnavailableFragmentListener());

            transaction.add(R.id.tab_pager, mFavoritesFragment, FAVORITE_TAG);
            transaction.add(R.id.tab_pager, mAllFragment, ALL_TAG);
            transaction.add(R.id.tab_pager, mGroupsFragment, GROUPS_TAG);
            transaction.add(R.id.tab_pager, mFavoritesUnavailableFragment,
                    FAVORITE_UNAVAILABLE_TAG);
            transaction.add(R.id.tab_pager, mAllUnavailableFragment, ALL_UNAVAILABLE_TAG);
            transaction.add(R.id.tab_pager, mGroupsUnavailableFragment, GROUPS_UNAVAILABLE_TAG);

        }

        mFavoritesFragment.setListener(mFavoritesFragmentListener);

        mAllFragment.setOnContactListActionListener(new ContactBrowserActionListener());
        mAllFragment.setCheckBoxListListener(new CheckBoxListListener());

        mGroupsFragment.setListener(new GroupBrowserActionListener());

        // Hide all fragments for now.  We adjust visibility when we get onSelectedTabChanged()
        // from ActionBarAdapter.
        transaction.hide(mFavoritesFragment);
        transaction.hide(mAllFragment);
        transaction.hide(mGroupsFragment);
        transaction.hide(mFavoritesUnavailableFragment);
        transaction.hide(mAllUnavailableFragment);
        transaction.hide(mGroupsUnavailableFragment);

        transaction.commitAllowingStateLoss();
        fragmentManager.executePendingTransactions();

        mFavoritesUnavailableFragment.setMessageText(R.string.listTotalAllContactsZeroStarred, -1);
        mGroupsUnavailableFragment.setMessageText(R.string.noGroups,
                areGroupWritableAccountsAvailable() ? -1 : R.string.noAccounts);
        mAllUnavailableFragment.setMessageText(R.string.noContacts, -1);

        // Setting Properties after fragment is created
        mFavoritesFragment.setDisplayType(DisplayType.STREQUENT);

        mActionBarAdapter = new ActionBarAdapter(this, this, getActionBar(),
                portraitViewPagerTabs, landscapeViewPagerTabs, toolbar);
        mActionBarAdapter.initialize(savedState, mRequest, mPageStateCount);
        initializeFabVisibility();
        // Add shadow under toolbar
        ViewUtil.addRectangularOutlineProvider(findViewById(R.id.toolbar_parent), getResources());

        invalidateOptionsMenuIfNeeded();
    }

    @Override
    protected void onStart() {
        if (!mFragmentInitialized) {
            mFragmentInitialized = true;
            /* Configure fragments if we haven't.
             *
             * Note it's a one-shot initialization, so we want to do this in {@link #onCreate}.
             *
             * However, because this method may indirectly touch views in fragments but fragments
             * created in {@link #configureContentView} using a {@link FragmentTransaction} will NOT
             * have views until {@link Activity#onCreate} finishes (they would if they were inflated
             * from a layout), we need to do it here in {@link #onStart()}.
             *
             * (When {@link Fragment#onCreateView} is called is different in the former case and
             * in the latter case, unfortunately.)
             *
             * Also, we skip most of the work in it if the activity is a re-created one.
             * (so the argument.)
             */
            configureFragments(!mIsRecreatedInstance);
        }
        super.onStart();
    }

    @Override
    protected void onPause() {
        InCallPluginHelper.INCALL.get(this).unsubscribe(CALL_METHOD_HELPER_SUBSCRIBER_ID);
        mOptionsMenuContactsAvailable = false;
        mProviderStatusWatcher.stop();
        super.onPause();
        dismissDialog(ImportExportDialogFragment.TAG);
        dismissDialog(SelectAccountDialogFragment.TAG);
    }

    @Override
    protected void onResume() {
        super.onResume();

        onResumeInit();
        if (InCallPluginHelper.subscribe(CALL_METHOD_HELPER_SUBSCRIBER_ID,
                pluginsUpdatedReceiver)) {
            InCallPluginHelper.INCALL.get(this).refreshDynamicItems();
        }
    }

    private synchronized void onResumeInit() {
        mProviderStatusWatcher.start();
        updateViewConfiguration(true);

        // Re-register the listener, which may have been cleared when onSaveInstanceState was
        // called.  See also: onSaveInstanceState
        mActionBarAdapter.setListener(this);
        mDisableOptionItemSelected = false;
        if (mTabPager != null) {
            mTabPager.setOnPageChangeListener(mTabPagerListener);
        }
        // Current tab may have changed since the last onSaveInstanceState().  Make sure
        // the actual contents match the tab.
        updateFragmentsVisibility();
    }

    @Override
    protected void onDestroy() {
        mProviderStatusWatcher.removeListener(this);

        // Some of variables will be null if this Activity redirects Intent.
        // See also onCreate() or other methods called during the Activity's initialization.
        if (mActionBarAdapter != null) {
            mActionBarAdapter.setListener(null);
        }
        if (mContactListFilterController != null) {
            mContactListFilterController.removeListener(this);
        }

        if (mExportToSimCompleteListener != null) {
            unregisterReceiver(mExportToSimCompleteListener);
        }

        super.onDestroy();
    }

    private void dismissDialog(String tag) {
        // when this activity lose focus,dismiss the dialog
        Fragment dialogFragment = getFragmentManager().findFragmentByTag(tag);
        if (dialogFragment != null) {
            if (dialogFragment instanceof DialogFragment) {
                ((DialogFragment) dialogFragment).dismiss();
            }
        }
    }

    private void configureFragments(boolean fromRequest) {
        if (fromRequest) {
            ContactListFilter filter = null;
            int actionCode = mRequest.getActionCode();
            boolean searchMode = mRequest.isSearchMode();
            final int tabToOpen;
            switch (actionCode) {
                case ContactsRequest.ACTION_ALL_CONTACTS:
                    filter = ContactListFilter.createFilterWithType(
                            ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS);
                    tabToOpen = TabState.ALL;
                    break;
                case ContactsRequest.ACTION_CONTACTS_WITH_PHONES:
                    filter = ContactListFilter.createFilterWithType(
                            ContactListFilter.FILTER_TYPE_WITH_PHONE_NUMBERS_ONLY);
                    tabToOpen = TabState.ALL;
                    break;

                case ContactsRequest.ACTION_FREQUENT:
                case ContactsRequest.ACTION_STREQUENT:
                case ContactsRequest.ACTION_STARRED:
                    tabToOpen = TabState.FAVORITES;
                    break;
                case ContactsRequest.ACTION_VIEW_CONTACT:
                    tabToOpen = TabState.ALL;
                    break;
                case ContactsRequest.ACTION_GROUP:
                    tabToOpen = mTabStateGroup;
                    break;
                default:
                    tabToOpen = -1;
                    break;
            }
            if (tabToOpen != -1) {
                mActionBarAdapter.setCurrentTab(tabToOpen, mPageStateCount);
            }

            if (filter != null) {
                mContactListFilterController.setContactListFilter(filter, false);
                searchMode = false;
            }

            if (mRequest.getContactUri() != null) {
                searchMode = false;
            }

            mActionBarAdapter.setSearchMode(searchMode);
            configureContactListFragmentForRequest();
        }

        configureContactListFragment();
        configureGroupListFragment();

        invalidateOptionsMenuIfNeeded();
    }

    private void initializeFabVisibility() {
        final boolean hideFab = mActionBarAdapter.isSearchMode()
                || mActionBarAdapter.isSelectionMode();
        mFloatingActionButtonContainer.setVisibility(hideFab ? View.GONE : View.VISIBLE);
        mFloatingActionButtonController.resetIn();
        wasLastFabAnimationScaleIn = !hideFab;
    }

    private void showFabWithAnimation(boolean showFab) {
        if (mFloatingActionButtonContainer == null) {
            return;
        }
        if (showFab) {
            if (!wasLastFabAnimationScaleIn) {
                mFloatingActionButtonContainer.setVisibility(View.VISIBLE);
                mFloatingActionButtonController.scaleIn(0);
            }
            wasLastFabAnimationScaleIn = true;

        } else {
            if (wasLastFabAnimationScaleIn) {
                mFloatingActionButtonContainer.setVisibility(View.VISIBLE);
                mFloatingActionButtonController.scaleOut();
            }
            wasLastFabAnimationScaleIn = false;
        }
    }

    @Override
    public void onContactListFilterChanged() {
        if (mAllFragment == null || !mAllFragment.isAdded()) {
            return;
        }

        mAllFragment.setFilter(mContactListFilterController.getFilter());

        invalidateOptionsMenuIfNeeded();
    }

    /**
     * Handler for action bar actions.
     */
    @Override
    public void onAction(int action) {
        switch (action) {
            case ActionBarAdapter.Listener.Action.START_SELECTION_MODE:
                mAllFragment.displayCheckBoxes(true);
                // Fall through:
            case ActionBarAdapter.Listener.Action.START_SEARCH_MODE:
                // Tell the fragments that we're in the search mode or selection mode
                configureFragments(false /* from request */);
                updateFragmentsVisibility();
                invalidateOptionsMenu();
                showFabWithAnimation(/* showFabWithAnimation = */ false);
                break;
            case ActionBarAdapter.Listener.Action.BEGIN_STOPPING_SEARCH_AND_SELECTION_MODE:
                showFabWithAnimation(/* showFabWithAnimation = */ true);
                break;
            case ActionBarAdapter.Listener.Action.STOP_SEARCH_AND_SELECTION_MODE:
                setQueryTextToFragment("");
                updateFragmentsVisibility();
                invalidateOptionsMenu();
                showFabWithAnimation(/* showFabWithAnimation = */ true);
                break;
            case ActionBarAdapter.Listener.Action.CHANGE_SEARCH_QUERY:
                final String queryString = mActionBarAdapter.getQueryString();
                setQueryTextToFragment(queryString);
                updateDebugOptionsVisibility(
                        ENABLE_DEBUG_OPTIONS_HIDDEN_CODE.equals(queryString));
                break;
            default:
                throw new IllegalStateException("Unkonwn ActionBarAdapter action: " + action);
        }
    }

    @Override
    public void onSelectedTabChanged() {
        updateFragmentsVisibility();
    }

    @Override
    public void onUpButtonPressed() {
        onBackPressed();
    }

    private void updateDebugOptionsVisibility(boolean visible) {
        if (mEnableDebugMenuOptions != visible) {
            mEnableDebugMenuOptions = visible;
            invalidateOptionsMenu();
        }
    }

    /**
     * Updates the fragment/view visibility according to the current mode, such as
     * {@link ActionBarAdapter#isSearchMode()} and {@link ActionBarAdapter#getCurrentTab()}.
     */
    private synchronized void updateFragmentsVisibility() {
        int tab = mActionBarAdapter.getCurrentTab();

        if (mActionBarAdapter.isSearchMode() || mActionBarAdapter.isSelectionMode()) {
            mTabPagerAdapter.setTabsHidden(true);
        } else {
            // No smooth scrolling if quitting from the search/selection mode.
            final boolean wereTabsHidden = mTabPagerAdapter.areTabsHidden()
                    || mActionBarAdapter.isSelectionMode();
            mTabPagerAdapter.setTabsHidden(false);
            if (mTabPager.getCurrentItem() != tab) {
                mTabPager.setCurrentItem(tab, !wereTabsHidden);
            }
        }
        if (!mActionBarAdapter.isSelectionMode()) {
            mAllFragment.displayCheckBoxes(false);
        }
        invalidateOptionsMenu();
        if (tab == mTabStateGroup) {
            mGroupsFragment.setAddAccountsVisibility(!areGroupWritableAccountsAvailable());
        }
    }

    private class TabPagerListener implements ViewPager.OnPageChangeListener {

        // This package-protected constructor is here because of a possible compiler bug.
        // PeopleActivity$1.class should be generated due to the private outer/inner class access
        // needed here.  But for some reason, PeopleActivity$1.class is missing.
        // Since $1 class is needed as a jvm work around to get access to the inner class,
        // changing the constructor to package-protected or public will solve the problem.
        // To verify whether $1 class is needed, javap PeopleActivity$TabPagerListener and look for
        // references to PeopleActivity$1.
        //
        // When the constructor is private and PeopleActivity$1.class is missing, proguard will
        // correctly catch this and throw warnings and error out the build on user/userdebug builds.
        //
        // All private inner classes below also need this fix.
        TabPagerListener() {}

        @Override
        public void onPageScrollStateChanged(int state) {
            if (!mTabPagerAdapter.areTabsHidden()) {
                mViewPagerTabs.onPageScrollStateChanged(state);
            }
        }

        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            if (!mTabPagerAdapter.areTabsHidden()) {
                mViewPagerTabs.onPageScrolled(position, positionOffset, positionOffsetPixels);
            }
        }

        @Override
        public void onPageSelected(int position) {
            // Make sure not in the search mode, in which case position != TabState.ordinal().
            if (!mTabPagerAdapter.areTabsHidden()) {
                mActionBarAdapter.setCurrentTab(position, mPageStateCount, false);
                mViewPagerTabs.onPageSelected(position);
                if (position == mTabStateGroup) {
                    mGroupsFragment.setAddAccountsVisibility(!areGroupWritableAccountsAvailable());
                }
                invalidateOptionsMenu();
            }
        }
    }

    /**
     * Adapter for the {@link ViewPager}.  Unlike {@link FragmentPagerAdapter},
     * {@link #instantiateItem} returns existing fragments, and {@link #instantiateItem}/
     * {@link #destroyItem} show/hide fragments instead of attaching/detaching.
     *
     * In search mode, we always show the "all" fragment, and disable the swipe.  We change the
     * number of items to 1 to disable the swipe.
     *
     * TODO figure out a more straight way to disable swipe.
     */
    private class TabPagerAdapter extends PagerAdapter {
        private final FragmentManager mFragmentManager;
        private FragmentTransaction mCurTransaction = null;

        private boolean mAreTabsHiddenInTabPager;

        private Fragment mCurrentPrimaryItem;

        public TabPagerAdapter() {
            mFragmentManager = getFragmentManager();
        }

        public boolean areTabsHidden() {
            return mAreTabsHiddenInTabPager;
        }

        public void setTabsHidden(boolean hideTabs) {
            if (hideTabs == mAreTabsHiddenInTabPager) {
                return;
            }
            mAreTabsHiddenInTabPager = hideTabs;
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return mAreTabsHiddenInTabPager ? 1 : mPageStateCount;
        }

        /** Gets called when the number of items changes. */
        @Override
        public int getItemPosition(Object object) {
            if (mAreTabsHiddenInTabPager) {
                if (object == mAllFragment) {
                    return 0; // Only 1 page in search mode
                }
            } else {
                if ((!mAccountUnavailable && object == mFavoritesFragment) ||
                        (mAccountUnavailable && object == mFavoritesUnavailableFragment)) {
                    return getTabPositionForTextDirection(TabState.FAVORITES);
                }
                if ((!mAccountUnavailable && object == mAllFragment) || (mAccountUnavailable &&
                        object == mAllUnavailableFragment)) {
                    return getTabPositionForTextDirection(TabState.ALL);
                }
                for (int i = 0; i < mPluginLength; i++) {
                    if (object == mPluginTabInfo.get(i).mFragment) {
                        return getTabPositionForTextDirection(TabState.GROUPS + i);
                    }
                }
                if ((!mAccountUnavailable && object == mGroupsFragment) || (mAccountUnavailable &&
                        object == mGroupsUnavailableFragment)) {
                    return getTabPositionForTextDirection(mTabStateGroup);
                }
            }
            return POSITION_NONE;
        }

        @Override
        public void startUpdate(ViewGroup container) {
        }

        private Fragment getFragment(int position) {
            position = getTabPositionForTextDirection(position);
            if (mAreTabsHiddenInTabPager) {
                if (position != 0) {
                    // This has only been observed in monkey tests.
                    // Let's log this issue, but not crash
                    Log.w(TAG, "Request fragment at position=" + position + ", eventhough we " +
                            "are in search mode");
                }
                return mAllFragment;
            } else {
                if (position == TabState.FAVORITES) {
                    return mAccountUnavailable ? mFavoritesUnavailableFragment : mFavoritesFragment;
                } else if (position == TabState.ALL) {
                    return mAccountUnavailable ? mAllUnavailableFragment : mAllFragment;
                } else if (position == (mPageStateCount - 1)) {
                    return mAccountUnavailable ? mGroupsUnavailableFragment : mGroupsFragment;
                } else {
                    int pluginOffset = position - TabState.GROUPS;
                    if (pluginOffset >= 0 && pluginOffset < mPluginTabInfo.size()) {
                        return mPluginTabInfo.get(pluginOffset).mFragment;
                    }
                }
            }
            throw new IllegalArgumentException("position: " + position);
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            if (mCurTransaction == null) {
                mCurTransaction = mFragmentManager.beginTransaction();
            }
            Fragment f = getFragment(position);
            mCurTransaction.show(f);

            // Non primary pages are not visible.
            f.setUserVisibleHint(f == mCurrentPrimaryItem);
            return f;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            if (mCurTransaction == null) {
                mCurTransaction = mFragmentManager.beginTransaction();
            }
            mCurTransaction.hide((Fragment) object);
        }

        @Override
        public void finishUpdate(ViewGroup container) {
            if (mCurTransaction != null) {
                mCurTransaction.commitAllowingStateLoss();
                mCurTransaction = null;
                mFragmentManager.executePendingTransactions();
            }
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return ((Fragment) object).getView() == view;
        }

        @Override
        public void setPrimaryItem(ViewGroup container, int position, Object object) {
            Fragment fragment = (Fragment) object;
            if (mCurrentPrimaryItem != fragment) {
                if (mCurrentPrimaryItem != null) {
                    mCurrentPrimaryItem.setUserVisibleHint(false);
                }
                if (fragment != null) {
                    fragment.setUserVisibleHint(true);
                }
                if (mCurrentPrimaryItem instanceof PluginContactBrowseListFragment) {
                    InCallMetricsHelper.increaseImpressionCount(PeopleActivity.this,
                        ((PluginContactBrowseListFragment) mCurrentPrimaryItem).getPluginInfo());
                }
                mCurrentPrimaryItem = fragment;
            }
        }

        @Override
        public Parcelable saveState() {
            return null;
        }

        @Override
        public void restoreState(Parcelable state, ClassLoader loader) {
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return mTabTitles.get(position).mTitle;
        }
    }

    private void setQueryTextToFragment(String query) {
        mAllFragment.setQueryString(query, true);
        mAllFragment.setVisibleScrollbarEnabled(!mAllFragment.isSearchMode());
    }

    private void configureContactListFragmentForRequest() {
        Uri contactUri = mRequest.getContactUri();
        if (contactUri != null) {
            mAllFragment.setSelectedContactUri(contactUri);
        }

        mAllFragment.setFilter(mContactListFilterController.getFilter());
        setQueryTextToFragment(mActionBarAdapter.getQueryString());

        if (mRequest.isDirectorySearchEnabled()) {
            mAllFragment.setDirectorySearchMode(DirectoryListLoader.SEARCH_MODE_DEFAULT);
        } else {
            mAllFragment.setDirectorySearchMode(DirectoryListLoader.SEARCH_MODE_NONE);
        }
    }

    private void configureContactListFragment() {
        // Filter may be changed when this Activity is in background.
        mAllFragment.setFilter(mContactListFilterController.getFilter());

        mAllFragment.setVerticalScrollbarPosition(getScrollBarPosition());
        mAllFragment.setSelectionVisible(false);
    }

    private int getScrollBarPosition() {
        return isRTL() ? View.SCROLLBAR_POSITION_LEFT : View.SCROLLBAR_POSITION_RIGHT;
    }

    private boolean isRTL() {
        final Locale locale = Locale.getDefault();
        return TextUtils.getLayoutDirectionFromLocale(locale) == View.LAYOUT_DIRECTION_RTL;
    }

    private void configureGroupListFragment() {
        mGroupsFragment.setVerticalScrollbarPosition(getScrollBarPosition());
        mGroupsFragment.setSelectionVisible(false);
    }

    @Override
    public void onProviderStatusChange() {
        updateViewConfiguration(false);
    }

    private void updateViewConfiguration(boolean forceUpdate) {
        int providerStatus = mProviderStatusWatcher.getProviderStatus();
        if (!forceUpdate && (mProviderStatus != null)
                && (mProviderStatus.equals(providerStatus))) return;
        mProviderStatus = providerStatus;

        if (mProviderStatus.equals(ProviderStatus.STATUS_NORMAL)) {
            // Ensure that the mTabPager is visible; we may have made it invisible below.
            mAccountUnavailable = false;
            if (mAllFragment != null) {
                mAllFragment.setEnabled(true);
            }
            mTabPagerAdapter.notifyDataSetChanged();
        } else {
            // If there are no accounts on the device and we should show the "no account" prompt
            // (based on {@link SharedPreferences}), then launch the account setup activity so the
            // user can sign-in or create an account.
            //
            // Also check for ability to modify accounts.  In limited user mode, you can't modify
            // accounts so there is no point sending users to account setup activity.
            final UserManager userManager = (UserManager) getSystemService(Context.USER_SERVICE);
            final boolean disallowModifyAccounts = userManager.getUserRestrictions().getBoolean(
                    UserManager.DISALLOW_MODIFY_ACCOUNTS);
            if (!disallowModifyAccounts && !areContactWritableAccountsAvailable() &&
                    AccountPromptUtils.shouldShowAccountPrompt(this)) {
                AccountPromptUtils.neverShowAccountPromptAgain(this);
                AccountPromptUtils.launchAccountPrompt(this);
                return;
            }
            mAccountUnavailable = true;

            // Otherwise, continue setting up the page so that the user can still use the app
            // without an account.
            if (mAllFragment != null) {
                mAllFragment.setEnabled(false);
            }
            mAllUnavailableFragment.updateStatus(mProviderStatus);
            mFavoritesUnavailableFragment.updateStatus(mProviderStatus);
            mGroupsUnavailableFragment.updateStatus(mProviderStatus);
            mTabPagerAdapter.notifyDataSetChanged();
        }

        invalidateOptionsMenuIfNeeded();
    }
    private final class PluginContactBrowserActionListener implements
            OnContactBrowserActionListener {
        PluginContactBrowserActionListener() {}
        @Override
        public void onSelectionChange() {}

        @Override
        public void onViewContactAction(Uri contactLookupUri) {
            final Intent intent = ImplicitIntentsUtil.composeQuickContactIntent(contactLookupUri,
                    QuickContactActivity.MODE_FULLY_EXPANDED);
            ImplicitIntentsUtil.startActivityInApp(PeopleActivity.this, intent);
        }

        @Override
        public void onDeleteContactAction(Uri contactUri) {
            ContactDeletionInteraction.start(PeopleActivity.this, contactUri, false);
        }

        @Override
        public void onFinishAction() {
            onBackPressed();
        }

        @Override
        public void onInvalidSelection() {}
    }

    private final class ContactBrowserActionListener implements OnContactBrowserActionListener {
        ContactBrowserActionListener() {}

        @Override
        public void onSelectionChange() {

        }

        @Override
        public void onViewContactAction(Uri contactLookupUri) {
            final Intent intent = ImplicitIntentsUtil.composeQuickContactIntent(contactLookupUri,
                    QuickContactActivity.MODE_FULLY_EXPANDED);
            ImplicitIntentsUtil.startActivityInApp(PeopleActivity.this, intent);
        }

        @Override
        public void onDeleteContactAction(Uri contactUri) {
            ContactDeletionInteraction.start(PeopleActivity.this, contactUri, false);
        }

        @Override
        public void onFinishAction() {
            onBackPressed();
        }

        @Override
        public void onInvalidSelection() {
            ContactListFilter filter;
            ContactListFilter currentFilter = mAllFragment.getFilter();
            if (currentFilter != null
                    && currentFilter.filterType == ContactListFilter.FILTER_TYPE_SINGLE_CONTACT) {
                filter = ContactListFilter.createFilterWithType(
                        ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS);
                mAllFragment.setFilter(filter);
            } else {
                filter = ContactListFilter.createFilterWithType(
                        ContactListFilter.FILTER_TYPE_SINGLE_CONTACT);
                mAllFragment.setFilter(filter, false);
            }
            mContactListFilterController.setContactListFilter(filter, true);
        }
    }

    private final class CheckBoxListListener implements OnCheckBoxListActionListener {
        @Override
        public void onStartDisplayingCheckBoxes() {
            mActionBarAdapter.setSelectionMode(true);
            invalidateOptionsMenu();
        }

        @Override
        public void onSelectedContactIdsChanged() {
            mActionBarAdapter.setSelectionCount(mAllFragment.getSelectedContactIds().size());
            invalidateOptionsMenu();
        }

        @Override
        public void onStopDisplayingCheckBoxes() {
            mActionBarAdapter.setSelectionMode(false);
        }
    }

    private class ContactsUnavailableFragmentListener
            implements OnContactsUnavailableActionListener {
        ContactsUnavailableFragmentListener() {}

        @Override
        public void onCreateNewContactAction() {
            ImplicitIntentsUtil.startActivityInApp(PeopleActivity.this,
                    EditorIntents.createCompactInsertContactIntent());
        }

        @Override
        public void onAddAccountAction() {
            Intent intent = new Intent(Settings.ACTION_ADD_ACCOUNT);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
            intent.putExtra(Settings.EXTRA_AUTHORITIES,
                    new String[]{ContactsContract.AUTHORITY});
            ImplicitIntentsUtil.startActivityOutsideApp(PeopleActivity.this, intent);
        }

        @Override
        public void onImportContactsFromFileAction() {
            ImportExportDialogFragment.show(getFragmentManager(), areContactsAvailable(),
                    PeopleActivity.class);
        }
    }

    private final class StrequentContactListFragmentListener
            implements ContactTileListFragment.Listener {
        StrequentContactListFragmentListener() {}

        @Override
        public void onContactSelected(Uri contactUri, Rect targetRect) {
            final Intent intent = ImplicitIntentsUtil.composeQuickContactIntent(contactUri,
                    QuickContactActivity.MODE_FULLY_EXPANDED);
            ImplicitIntentsUtil.startActivityInApp(PeopleActivity.this, intent);
        }

        @Override
        public void onCallNumberDirectly(String phoneNumber) {
            // No need to call phone number directly from People app.
            Log.w(TAG, "unexpected invocation of onCallNumberDirectly()");
        }
    }

    private final class GroupBrowserActionListener implements OnGroupBrowserActionListener {

        GroupBrowserActionListener() {}

        @Override
        public void onViewGroupAction(Uri groupUri) {
            Intent intent = new Intent(PeopleActivity.this, GroupDetailActivity.class);
            intent.setData(groupUri);
            startActivity(intent);
        }
    }

    private class GroupDetailFragmentListener implements GroupDetailFragment.Listener {

        GroupDetailFragmentListener() {}

        @Override
        public void onGroupSizeUpdated(String size) {
            // Nothing needs to be done here because the size will be displayed in the detail
            // fragment
        }

        @Override
        public void onGroupTitleUpdated(String title) {
            // Nothing needs to be done here because the title will be displayed in the detail
            // fragment
        }

        @Override
        public void onAccountTypeUpdated(String accountTypeString, String dataSet) {
            // Nothing needs to be done here because the group source will be displayed in the
            // detail fragment
        }

        @Override
        public void onEditRequested(Uri groupUri) {
            final Intent intent = new Intent(PeopleActivity.this, GroupEditorActivity.class);
            intent.setData(groupUri);
            intent.setAction(Intent.ACTION_EDIT);
            startActivityForResult(intent, SUBACTIVITY_EDIT_GROUP);
        }

        @Override
        public void onContactSelected(Uri contactUri) {
            // Nothing needs to be done here because either quickcontact will be displayed
            // or activity will take care of selection
        }
    }

    public void startActivityAndForwardResult(final Intent intent) {
        intent.setFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);

        // Forward extras to the new activity
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            intent.putExtras(extras);
        }
        startActivity(intent);
        finish();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (!areContactsAvailable()) {
            // If contacts aren't available, hide all menu items.
            return false;
        }
        super.onCreateOptionsMenu(menu);

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.people_options, menu);

        return true;
    }

    private void invalidateOptionsMenuIfNeeded() {
        if (isOptionsMenuChanged()) {
            invalidateOptionsMenu();
        }
    }

    public boolean isOptionsMenuChanged() {
        if (mOptionsMenuContactsAvailable != areContactsAvailable()) {
            return true;
        }

        if (mAllFragment != null && mAllFragment.isOptionsMenuChanged()) {
            return true;
        }

        if (mGroupDetailFragment != null && mGroupDetailFragment.isOptionsMenuChanged()) {
            return true;
        }

        return false;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        mOptionsMenuContactsAvailable = areContactsAvailable();
        if (!mOptionsMenuContactsAvailable) {
            return false;
        }

        // Get references to individual menu items in the menu
        final MenuItem contactsFilterMenu = menu.findItem(R.id.menu_contacts_filter);
        MenuItem addGroupMenu = menu.findItem(R.id.menu_add_group);
        final MenuItem clearFrequentsMenu = menu.findItem(R.id.menu_clear_frequents);
        final MenuItem helpMenu = menu.findItem(R.id.menu_help);

        final boolean isSearchOrSelectionMode = mActionBarAdapter.isSearchMode()
                || mActionBarAdapter.isSelectionMode();
        boolean isPlugin = false;
        if (isSearchOrSelectionMode) {
            addGroupMenu.setVisible(false);
            contactsFilterMenu.setVisible(false);
            clearFrequentsMenu.setVisible(false);
            helpMenu.setVisible(false);
            makeMenuItemVisible(menu, R.id.menu_delete, false);
        } else {
            int tabPosition = getTabPositionForTextDirection(mActionBarAdapter.getCurrentTab());
            if (tabPosition == TabState.FAVORITES) {
                addGroupMenu.setVisible(false);
                contactsFilterMenu.setVisible(false);
                clearFrequentsMenu.setVisible(hasFrequents());
            } else if (tabPosition == TabState.ALL) {
                addGroupMenu.setVisible(false);
                contactsFilterMenu.setVisible(true);
                clearFrequentsMenu.setVisible(false);
            } else if (tabPosition == mTabStateGroup) {
                    // Do not display the "new group" button if no accounts are available
                    if (areGroupWritableAccountsAvailable()) {
                        addGroupMenu.setVisible(true);
                    } else {
                        addGroupMenu.setVisible(false);
                    }
                    contactsFilterMenu.setVisible(false);
                    clearFrequentsMenu.setVisible(false);
            } else if (mPluginLength > 0 && tabPosition >= TabState.GROUPS){
                // plugin tab
                int pluginIndex = tabPosition - TabState.GROUPS;
                InCallPluginInfo pluginInfo = mPluginTabInfo.get(pluginIndex);
                // floating button state
                if (pluginInfo.mCallMethodInfo.mIsAuthenticated ||
                        CallMethodUtils.isSoftLoggedOut(this, pluginInfo.mCallMethodInfo)) {
                    mFloatingActionButtonContainer.setVisibility(View.VISIBLE);
                } else {
                    mFloatingActionButtonContainer.setVisibility(View.GONE);
                }
                // menu
                addGroupMenu.setVisible(false);
                contactsFilterMenu.setVisible(false);
                clearFrequentsMenu.setVisible(false);
                makeMenuItemVisible(menu, R.id.menu_delete, false);
                isPlugin = true;
            }
            if (!isPlugin) {
                mFloatingActionButtonContainer.setVisibility(View.VISIBLE);
            }

            helpMenu.setVisible(HelpUtils.isHelpAndFeedbackAvailable());
        }
        final boolean showMiscOptions = !isSearchOrSelectionMode;
        if (!isPlugin) {
            makeMenuItemVisible(menu, R.id.menu_search, showMiscOptions);
            mFloatingActionButton.setImageDrawable(getResources().getDrawable(R.drawable
                    .ic_person_add_24dp));
        } else {
            mFloatingActionButton.setImageDrawable(getResources().getDrawable(R.drawable
                    .ic_add));
        }
        makeMenuItemVisible(menu, R.id.menu_search, showMiscOptions);
        makeMenuItemVisible(menu, R.id.menu_import_export, showMiscOptions);
        makeMenuItemVisible(menu, R.id.menu_accounts, showMiscOptions);
        makeMenuItemVisible(menu, R.id.menu_memory_status, showMiscOptions);
        makeMenuItemVisible(menu, R.id.menu_settings,
                showMiscOptions && !ContactsPreferenceActivity.isEmpty(this));

        final boolean showSelectedContactOptions = mActionBarAdapter.isSelectionMode()
                && mAllFragment.getSelectedContactIds().size() != 0;
        makeMenuItemVisible(menu, R.id.menu_share, showSelectedContactOptions);
        makeMenuItemVisible(menu, R.id.menu_join, showSelectedContactOptions);
        makeMenuItemEnabled(menu, R.id.menu_join, mAllFragment.getSelectedContactIds().size() > 1);

        // Debug options need to be visible even in search mode.
        makeMenuItemVisible(menu, R.id.export_database, mEnableDebugMenuOptions);

        return true;
    }

    /**
     * Returns whether there are any frequently contacted people being displayed
     * @return
     */
    private boolean hasFrequents() {
        return mFavoritesFragment.hasFrequents();
    }

    private void makeMenuItemVisible(Menu menu, int itemId, boolean visible) {
        final MenuItem item = menu.findItem(itemId);
        if (item != null) {
            item.setVisible(visible);
        }
    }

    private void makeMenuItemEnabled(Menu menu, int itemId, boolean visible) {
        final MenuItem item = menu.findItem(itemId);
        if (item != null) {
            item.setEnabled(visible);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (mDisableOptionItemSelected) {
            return false;
        }

        switch (item.getItemId()) {
            case android.R.id.home: {
                // The home icon on the action bar is pressed
                if (mActionBarAdapter.isUpShowing()) {
                    // "UP" icon press -- should be treated as "back".
                    onBackPressed();
                }
                return true;
            }
            case R.id.menu_settings: {
                final Intent intent = new Intent(this, ContactsPreferenceActivity.class);
                // Since there is only one section right now, make sure it is selected on
                // small screens.
                intent.putExtra(PreferenceActivity.EXTRA_SHOW_FRAGMENT,
                        DisplayOptionsPreferenceFragment.class.getName());
                // By default, the title of the activity should be equivalent to the fragment
                // title. We set this argument to avoid this. Because of a bug, the following
                // line isn't necessary. But, once the bug is fixed this may become necessary.
                // b/5045558 refers to this issue, as well as another.
                intent.putExtra(PreferenceActivity.EXTRA_SHOW_FRAGMENT_TITLE,
                        R.string.activity_title_settings);
                startActivity(intent);
                return true;
            }
            case R.id.menu_contacts_filter: {
                AccountFilterUtil.startAccountFilterActivityForResult(
                        this, SUBACTIVITY_ACCOUNT_FILTER,
                        mContactListFilterController.getFilter());
                return true;
            }
            case R.id.menu_search: {
                onSearchRequested();
                return true;
            }
            case R.id.menu_share:
                shareSelectedContacts();
                return true;
            case R.id.menu_join:
                joinSelectedContacts();
                return true;
            case R.id.menu_add_group: {
                createNewGroup();
                return true;
            }
            case R.id.menu_delete: {
                final Intent intent = new Intent(Intent.ACTION_DELETE, Contacts.CONTENT_URI);
                intent.putExtra(EDITABLE_KEY, mActionBarAdapter.getQueryString());

                ContactListFilter filter = ContactListFilter.restoreDefaultPreferences(
                    PreferenceManager.getDefaultSharedPreferences(this));
                intent.putExtra(AccountFilterActivity.KEY_EXTRA_CONTACT_LIST_FILTER, filter);

                startActivity(intent);
                return true;
            }
            case R.id.menu_import_export: {
                ImportExportDialogFragment.show(getFragmentManager(), areContactsAvailable(),
                        PeopleActivity.class);
                return true;
            }
            case R.id.menu_clear_frequents: {
                ClearFrequentsDialog.show(getFragmentManager());
                return true;
            }
            case R.id.menu_help:
                HelpUtils.launchHelpAndFeedbackForMainScreen(this);
                return true;
            case R.id.menu_accounts: {
                final Intent intent = new Intent(Settings.ACTION_SYNC_SETTINGS);
                intent.putExtra(Settings.EXTRA_AUTHORITIES, new String[] {
                    ContactsContract.AUTHORITY
                });
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
                ImplicitIntentsUtil.startActivityInAppIfPossible(this, intent);
                return true;
            }
            case R.id.export_database: {
                final Intent intent = new Intent("com.android.providers.contacts.DUMP_DATABASE");
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
                ImplicitIntentsUtil.startActivityOutsideApp(this, intent);
                return true;
            }

            case R.id.menu_memory_status: {
                final Intent intent = new Intent(this, MemoryStatusActivity.class);
                startActivity(intent);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onSearchRequested() { // Search key pressed.
        if (!mActionBarAdapter.isSelectionMode()) {
            mActionBarAdapter.setSearchMode(true);
        }
        return true;
    }

    /**
     * Share all contacts that are currently selected in mAllFragment. This method is pretty
     * inefficient for handling large numbers of contacts. I don't expect this to be a problem.
     */
    private void shareSelectedContacts() {
        final StringBuilder uriListBuilder = new StringBuilder();
        boolean firstIteration = true;
        for (Long contactId : mAllFragment.getSelectedContactIds()) {
            if (!firstIteration)
                uriListBuilder.append(':');
            final Uri contactUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId);
            final Uri lookupUri = Contacts.getLookupUri(getContentResolver(), contactUri);
            List<String> pathSegments = lookupUri.getPathSegments();
            uriListBuilder.append(Uri.encode(pathSegments.get(pathSegments.size() - 2)));
            firstIteration = false;
        }
        final Uri uri = Uri.withAppendedPath(
                Contacts.CONTENT_MULTI_VCARD_URI,
                Uri.encode(uriListBuilder.toString()));
        final Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(Contacts.CONTENT_VCARD_TYPE);
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        ImplicitIntentsUtil.startActivityOutsideApp(this, intent);
    }
    private void joinSelectedContacts() {
        JoinContactsDialogFragment.start(this, mAllFragment.getSelectedContactIds());
    }

    @Override
    public void onContactsJoined() {
        mActionBarAdapter.setSelectionMode(false);
    }

    private void deleteSelectedContacts() {
        ContactMultiDeletionInteraction.start(PeopleActivity.this,
                mAllFragment.getSelectedContactIds());
    }

    private void createNewGroup() {
        final Intent intent = new Intent(this, GroupEditorActivity.class);
        intent.setAction(Intent.ACTION_INSERT);
        startActivityForResult(intent, SUBACTIVITY_NEW_GROUP);
    }

    @Override
    public void onDeletionFinished() {
        mActionBarAdapter.setSelectionMode(false);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case SUBACTIVITY_ACCOUNT_FILTER: {
                AccountFilterUtil.handleAccountFilterResult(
                        mContactListFilterController, resultCode, data);
                break;
            }
            case SUBACTIVITY_NEW_GROUP:
            case SUBACTIVITY_EDIT_GROUP: {
                if (resultCode == RESULT_OK) {
                    mRequest.setActionCode(ContactsRequest.ACTION_GROUP);
                    mGroupsFragment.setSelectedUri(data.getData());
                }
                break;
            }

            // TODO: Using the new startActivityWithResultFromFragment API this should not be needed
            // anymore
            case ContactEntryListFragment.ACTIVITY_REQUEST_CODE_PICKER:
                if (resultCode == RESULT_OK) {
                    mAllFragment.onPickerResult(data);
                }
                break;

// TODO fix or remove multipicker code
//                else if (resultCode == RESULT_CANCELED && mMode == MODE_PICK_MULTIPLE_PHONES) {
//                    // Finish the activity if the sub activity was canceled as back key is used
//                    // to confirm user selection in MODE_PICK_MULTIPLE_PHONES.
//                    finish();
//                }
//                break;
            case ImportExportDialogFragment.SUBACTIVITY_MULTI_PICK_CONTACT:
                if (resultCode == RESULT_OK) {
                    mContactList = new ArrayList<String[]>();
                    Bundle b = data.getExtras();
                    Bundle choiceSet = b.getBundle(SimContactsConstants.RESULT_KEY);
                    Set<String> set = choiceSet.keySet();
                    Iterator<String> i = set.iterator();
                    while (i.hasNext()) {
                        String contactInfo[] = choiceSet.getStringArray(i.next());
                        mContactList.add(contactInfo);
                    }
                    Log.d(TAG, "return " + mContactList.size() + " contacts");
                    if (!mContactList.isEmpty()) {
                        if (!ImportExportDialogFragment.isExportingToSIM()) {
                            ImportExportDialogFragment.destroyExportToSimThread();
                            mExportThread =
                                new ImportExportDialogFragment().createExportToSimThread(
                                ImportExportDialogFragment.mExportSub, mContactList,
                                PeopleActivity.this);
                            mExportThread.start();
                        }
                    }
                }
                break;
        case ImportExportDialogFragment.SUBACTIVITY_EXPORT_CONTACTS:
            if (resultCode == RESULT_OK) {
                Bundle result = data.getExtras().getBundle(
                        SimContactsConstants.RESULT_KEY);
                Set<String> keySet = result.keySet();
                Iterator<String> it = keySet.iterator();
                StringBuilder selExportBuilder = new StringBuilder();
                while (it.hasNext()) {
                    String id = it.next();
                    if (0 != selExportBuilder.length()) {
                        selExportBuilder.append(",");
                    }
                    selExportBuilder.append(id);
                }
                selExportBuilder.insert(0, "_id IN (");
                selExportBuilder.append(")");
                Intent exportIntent = new Intent(this,
                        ExportVCardActivity.class);
                exportIntent.putExtra("SelExport", selExportBuilder.toString());
                exportIntent.putExtra(
                        VCardCommonArguments.ARG_CALLING_ACTIVITY,
                        PeopleActivity.class.getName());
                this.startActivity(exportIntent);
            }
            break;
        case ImportExportDialogFragment.SUBACTIVITY_SHARE_VISILBLE_CONTACTS:
            if (resultCode == RESULT_OK) {
                Bundle result = data.getExtras().getBundle(
                        SimContactsConstants.RESULT_KEY);
                StringBuilder uriListBuilder = new StringBuilder();
                int index = 0;
                int size = result.keySet().size();
                // The premise of allowing to share contacts is that the
                // amount of those contacts which have been selected to
                // append and will be put into intent as extra data to
                // deliver is not more that 2000, because too long arguments
                // will cause TransactionTooLargeException in binder.
                if (size > ImportExportDialogFragment.MAX_COUNT_ALLOW_SHARE_CONTACT) {
                    Toast.makeText(this, R.string.share_failed,
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                Iterator<String> it = result.keySet().iterator();
                String[] values = null;
                while (it.hasNext()) {
                    if (index != 0) {
                        uriListBuilder.append(':');
                    }
                    values = result.getStringArray(it.next());
                    uriListBuilder.append(values[0]);
                    index++;
                }
                Uri uri = Uri.withAppendedPath(
                        Contacts.CONTENT_MULTI_VCARD_URI,
                        Uri.encode(uriListBuilder.toString()));
                final Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType(Contacts.CONTENT_VCARD_TYPE);
                intent.putExtra(Intent.EXTRA_STREAM, uri);
                startActivity(intent);
            }
            break;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // TODO move to the fragment

        // Bring up the search UI if the user starts typing
        final int unicodeChar = event.getUnicodeChar();
        if ((unicodeChar != 0)
                // If COMBINING_ACCENT is set, it's not a unicode character.
                && ((unicodeChar & KeyCharacterMap.COMBINING_ACCENT) == 0)
                && !Character.isWhitespace(unicodeChar)) {
            if (mActionBarAdapter.isSelectionMode()) {
                // Ignore keyboard input when in selection mode.
                return true;
            }
            String query = new String(new int[]{unicodeChar}, 0, 1);
            if (!mActionBarAdapter.isSearchMode()) {
                mActionBarAdapter.setSearchMode(true);
                mActionBarAdapter.setQueryString(query);
                return true;
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        if (mActionBarAdapter.isSelectionMode()) {
            mActionBarAdapter.setSelectionMode(false);
            mAllFragment.displayCheckBoxes(false);
        } else if (mActionBarAdapter.isSearchMode()) {
            mActionBarAdapter.setSearchMode(false);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mActionBarAdapter.onSaveInstanceState(outState);

        // Clear the listener to make sure we don't get callbacks after onSaveInstanceState,
        // in order to avoid doing fragment transactions after it.
        // TODO Figure out a better way to deal with the issue.
        mDisableOptionItemSelected = true;
        mActionBarAdapter.setListener(null);
        if (mTabPager != null) {
            mTabPager.setOnPageChangeListener(null);
        }
        if (mPluginLength > 0) {
            outState.putParcelableArrayList(KEY_PLUGIN_INFO_LIST, (ArrayList<InCallPluginInfo>)
                    mPluginTabInfo);
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        // In our own lifecycle, the focus is saved and restore but later taken away by the
        // ViewPager. As a hack, we force focus on the SearchView if we know that we are searching.
        // This fixes the keyboard going away on screen rotation
        if (mActionBarAdapter.isSearchMode()) {
            mActionBarAdapter.setFocusOnSearchView();
        }
    }

    @Override
    public DialogManager getDialogManager() {
        return mDialogManager;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.floating_action_button:
                int tabPosition = getTabPositionForTextDirection(mActionBarAdapter.getCurrentTab());
                if (mPluginLength > 0 && tabPosition >= TabState.GROUPS &&
                        tabPosition != mTabStateGroup) {
                    // plugin tab
                    int pluginIndex = tabPosition - TabState.GROUPS;
                    InCallPluginInfo pluginInfo = mPluginTabInfo.get(pluginIndex);
                    if (pluginInfo.mCallMethodInfo.mDefaultDirectorySearchIntent != null) {
                        try {
                            pluginInfo.mCallMethodInfo.mDefaultDirectorySearchIntent.send();
                        } catch (PendingIntent.CanceledException e) {
                            Log.d(TAG, "directory search exception: ", e);
                        }
                    }
                } else {
                    Intent intent = new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI);
                    Bundle extras = getIntent().getExtras();
                    if (extras != null) {
                        intent.putExtras(extras);
                    }
                    try {
                        ImplicitIntentsUtil.startActivityInApp(PeopleActivity.this, intent);
                    } catch (ActivityNotFoundException ex) {
                        Toast.makeText(PeopleActivity.this, R.string.missing_app,
                                Toast.LENGTH_SHORT).show();
                    }
                }
                break;
        default:
            Log.wtf(TAG, "Unexpected onClick event from " + view);
        }
    }

    /**
     * Returns the tab position adjusted for the text direction.
     */
    private int getTabPositionForTextDirection(int position) {
        if (isRTL()) {
            return mPageStateCount - 1 - position;
        }
        return position;
    }

    private class TabEntry {
        public String mTag;
        public String mTitle;

        public TabEntry(String tag, String title) {
            mTag = tag;
            mTitle = title;
        }
    }

    /*
    * peforms a look up using the tab tag among the ViewPagerTabs
    */
    private int lookupTabTag(String tabTag) {
        int size = mTabTitles.size();
        for (int i = 0; i < size; i++) {
            TabEntry tab = mTabTitles.get(i);
            if (TextUtils.equals(tab.mTag, tabTag)) {
                return i;
            }
        }
        return TabState.ALL;
    }

    private InCallPluginHelper.ModChanged pluginsUpdatedReceiver =
            new InCallPluginHelper.ModChanged() {
                @Override
                public void onChanged(HashMap callMethodInfos) {
                    updatePlugins(callMethodInfos);
                }
            };

    // Global CallMethod map that keeps track of the currently displayed plugins
    HashMap<ComponentName, CallMethodInfo> mCallMethodMap =
            new HashMap<ComponentName, CallMethodInfo>();

    private InCallPluginInfo getPluginInfo(ComponentName cm) {
        for (int i = 0; i < mPluginTabInfo.size(); i++) {
            if (mPluginTabInfo.get(i).mCallMethodInfo.mComponent.equals(cm)) {
                return mPluginTabInfo.get(i);
            }
        }
        return null;
    }

    private InCallPluginInfo removePluginInfo(ComponentName cn) {
        for (int i = 0; i < mPluginTabInfo.size(); i++) {
            if (mPluginTabInfo.get(i).mCallMethodInfo.mComponent.equals(cn)) {
                return mPluginTabInfo.remove(i);
            }
        }
        return null;
    }

    private void removeTabTitle(ComponentName cn) {
        for (int i = 0; i < mTabTitles.size(); i++) {
            if (mTabTitles.get(i).mTag.equals(cn.toShortString())) {
                mTabTitles.remove(i);
                return;
            }
        }
    }

    private synchronized void updatePlugins(HashMap<ComponentName, CallMethodInfo>
                                                     callMethodInfo) {
        HashMap<ComponentName, CallMethodInfo> newCmMap = (HashMap<ComponentName,
                CallMethodInfo>) CallMethodFilters.getAllEnabledCallMethods(this);
        if (DEBUG) Log.d(TAG, "updatePlugins newCmMap size:" + newCmMap.size());
        boolean updateTabs = false;
        String lastSelectedTabTag = mTabTitles.get(mActionBarAdapter.getCurrentTab()).mTag;
        boolean executeFragTransact = false;
        FragmentTransaction transaction;
        FragmentManager fragmentManager = getFragmentManager();

        Iterator<Map.Entry<ComponentName, CallMethodInfo>> itr = mCallMethodMap.entrySet()
                .iterator();
        while (itr.hasNext()) {
            Map.Entry<ComponentName, CallMethodInfo> entry = itr.next();
            ComponentName cn = entry.getKey();
            if (newCmMap.containsKey(cn)) {
                // Update plugin info only, the plugin fragment keeps track of its state to
                // determine if a UI update is necessary
                CallMethodInfo newCm = newCmMap.remove(cn);
                InCallPluginInfo pluginInfo = getPluginInfo(cn);
                pluginInfo.mCallMethodInfo = newCm;
                pluginInfo.mFragment.updateInCallPluginInfo(pluginInfo);
                mCallMethodMap.put(cn, newCm);
            } else {
                // Remove the tab associated with a plugin that's no longer available
                updateTabs = true;
                itr.remove();
                removeTabTitle(cn);
                InCallPluginInfo removePlugin = removePluginInfo(cn);
                if (removePlugin != null) {
                    transaction = fragmentManager.beginTransaction();
                    transaction.remove(removePlugin.mFragment);
                    transaction.commitAllowingStateLoss();
                    executeFragTransact = true;
                }
            }
        }
        // add newly added tab from newCmMap
        for (ComponentName cn : newCmMap.keySet()) {
            InCallPluginInfo newInfo = new InCallPluginInfo();
            newInfo.mTabTag = cn.toShortString();
            PluginContactBrowseListFragment frag = (PluginContactBrowseListFragment)
                    getFragmentManager().findFragmentByTag(newInfo.mTabTag);
            if (frag == null) {
                newInfo.mCallMethodInfo = newCmMap.get(cn);
                mCallMethodMap.put(cn, newInfo.mCallMethodInfo);
                mPluginTabInfo.add(0, newInfo);
                newInfo.mFragment = new PluginContactBrowseListFragment();
                transaction = fragmentManager.beginTransaction();
                transaction.add(R.id.tab_pager, newInfo.mFragment, newInfo.mTabTag);
                transaction.hide(newInfo.mFragment);
                transaction.commitAllowingStateLoss();
                newInfo.mFragment.updateInCallPluginInfo(newInfo);
                newInfo.mFragment.setOnContactListActionListener
                        (new PluginContactBrowserActionListener());
                mTabTitles.add(TabState.GROUPS,
                        new TabEntry(newInfo.mTabTag, newInfo.mCallMethodInfo.mName));
                updateTabs = true;
                executeFragTransact = true;
            }
        }
        if (executeFragTransact) {
            fragmentManager.executePendingTransactions();
        }
        // update view pager
        if (updateTabs) {
            mPluginLength = mPluginTabInfo.size();
            mPageStateCount = TabState.COUNT + mPluginLength;
            mTabStateGroup = TabState.GROUPS + mPluginLength;
            // update ViewPager
            mActionBarAdapter.setListener(null);
            if (mTabPager != null) {
                mTabPager.setOnPageChangeListener(null);
            }

            mTabPagerAdapter.notifyDataSetChanged();
            // re-add all the pages to ViewPagerTabs, including the new plugin tabs
            mViewPagerTabs.setViewPager(mTabPager);
            // restore last tab by unique tab tag, if it exsits in the tabs
            mActionBarAdapter.setCurrentTab(lookupTabTag(lastSelectedTabTag), mPageStateCount,
                    false);
            // need to force the ViewPagerTabs to refocus on the right tab, since setViewPager
            // above causes a state loss
            mViewPagerTabs.onPageSelected(mActionBarAdapter.getCurrentTab());
            // force the tabs' underline to be cleared and redrawn
            mViewPagerTabs.onPageScrolled(mActionBarAdapter.getCurrentTab(), 0, 0);
            onResumeInit();
        }
    }
}
