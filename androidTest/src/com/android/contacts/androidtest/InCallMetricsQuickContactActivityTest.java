/*
 * Copyright (C) 2016 The CyanogenMod Project
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

package com.android.contacts.androidtest;

import com.android.contacts.quickcontact.QuickContactActivity;
import com.android.contacts.incall.InCallMetricsDbHelper;
import com.android.contacts.incall.InCallMetricsHelper;

import com.android.phone.common.incall.CallMethodInfo;
import com.android.phone.common.incall.ContactsDataSubscription;
import com.android.phone.common.incall.utils.CallMethodFilters;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.containsString;
import android.content.ComponentName;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.test.suitebuilder.annotation.MediumTest;
import android.util.Log;
import android.view.View;


import org.hamcrest.Matcher;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.junit.Test;
import org.junit.FixMethodOrder;

import java.util.HashMap;
import java.util.Set;

import com.android.contacts.R;

@RunWith(AndroidJUnit4.class)
@MediumTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class InCallMetricsQuickContactActivityTest {
    private static final String TAG = InCallMetricsQuickContactActivityTest.class.getSimpleName();
    private QuickContactActivity mActivity;
    private CallMethodInfo mCm;

    @Rule
    public ActivityTestRule<QuickContactActivity> mActivityRule = new ActivityTestRule<>
            (QuickContactActivity.class, false, false); // specify activity is launched at all times

    @Before
    public void setUp() {
        // get Activity handle under test
        mActivity = (QuickContactActivity) mActivityRule.launchActivity(null);
        Assert.assertNotNull(mActivity);
        // get CallMethodInfo under test
        InCallMetricsTestUtils.waitFor(2000);
        HashMap<ComponentName, CallMethodInfo> cmMap =
                CallMethodFilters.getAllEnabledAndHiddenCallMethods(
                        ContactsDataSubscription.get(mActivity));
        Assert.assertNotNull(cmMap);
        Set<ComponentName> cmKeySet = cmMap.keySet();
        if (cmKeySet.size() == 0) {
            Log.d(TAG, "No InCall plugin installed");
            return;
        }
        // test the first one
        ComponentName cn = cmKeySet.iterator().next();
        mCm = cmMap.get(cn);
        Assert.assertNotNull(mCm);
        // Prepre next test case : signed in
    }

    /*
     * test metrics: CONTACTS_AUgt_MERGED
     * precondition : signed in
     */
    @Test
    public void test1() {
        Log.d(TAG, "-----test1 start -----");
        InCallMetricsTestUtils.setInCallPluginAuthState(mActivity, true);
        InCallMetricsTestUtils.waitFor(500);
        // clear out db
        Assert.assertTrue(InCallMetricsTestDbUtils.clearAllEntries(mActivity));

        // query
        ContentResolver cr = mActivity.getContentResolver();
        //Uri matchUri = InCallMetricsTestUtils.findFirstContactWithPhoneNumber(mCm.mAccountType,cr);
        // create a contact with the same name and phone number
        Uri matchUri = InCallMetricsTestUtils.createContact(null, "Tester1", cr);
        Uri contactUri = InCallMetricsTestUtils.createContact(null, "Tester2", cr);

        InCallMetricsTestUtils.waitFor(500);

        // check count
        ContentValues entry = InCallMetricsTestDbUtils.getEntry(mActivity,
                InCallMetricsDbHelper.Tables.USER_ACTIONS_TABLE,
                InCallMetricsHelper.Events.CONTACTS_AUTO_MERGED);
        int newCount = entry.containsKey(InCallMetricsDbHelper.UserActionsColumns.COUNT) ?
                entry.getAsInteger(InCallMetricsDbHelper.UserActionsColumns.COUNT) : 0;
        Log.d(TAG, "CONTACTS_AUTO_MERGED newCount:" + newCount);
        Assert.assertEquals(1, newCount);

        // clean up contact
        InCallMetricsTestUtils.deleteContact(matchUri, cr);
        InCallMetricsTestUtils.deleteContact(contactUri, cr);
        Log.d(TAG, "-----test1 finish -----");
    }

    /*
     * test metrics: INVITES_SENT
     * precondition : signed in
     */
    @Test
    public void test2() {
        Log.d(TAG, "-----test2 start -----");
        // precondition signed in
        InCallMetricsTestUtils.setInCallPluginAuthState(mActivity, true);
        InCallMetricsTestUtils.waitFor(500);
        // clear out db
        Assert.assertTrue(InCallMetricsTestDbUtils.clearAllEntries(mActivity));

        // launch activity
        ContentResolver cr = mActivity.getContentResolver();
        Uri contactUri = InCallMetricsTestUtils.createContact(null, "Tester1", cr);
        Intent intent = InCallMetricsTestUtils.getContactCardIntent(contactUri);
        mActivityRule.launchActivity(intent);
        InCallMetricsTestUtils.waitFor(1000);
        onView(withText(R.string.incall_plugin_invite)).perform(click());

        // check count
        ContentValues entry = InCallMetricsTestDbUtils.getEntry(mActivity,
                InCallMetricsDbHelper.Tables.USER_ACTIONS_TABLE,
                InCallMetricsHelper.Events.INVITES_SENT);
        int newCount = entry.containsKey(InCallMetricsDbHelper.UserActionsColumns.COUNT) ?
                entry.getAsInteger(InCallMetricsDbHelper.UserActionsColumns.COUNT) : 0;
        Log.d(TAG, "INVITES_SENT newCount:" + newCount);
        Assert.assertEquals(1, newCount);

        // clean up contact
        InCallMetricsTestUtils.deleteContact(contactUri, cr);
        Log.d(TAG, "-----test2 finish -----");
    }

    /*
     * test metrics: DIRECTORY_SEARCH
     * precondition : signed in
     */
    @Test
    public void test3() {
        Log.d(TAG, "-----test3 start -----");
        // precondition: signed in
        InCallMetricsTestUtils.setInCallPluginAuthState(mActivity, true);
        InCallMetricsTestUtils.waitFor(500);

        Assert.assertTrue(InCallMetricsTestDbUtils.clearAllEntries(mActivity));
        ContentResolver cr = mActivity.getContentResolver();
        Uri contactUri = InCallMetricsTestUtils.createContact(null, "Tester1", cr);
        Intent intent = InCallMetricsTestUtils.getContactCardIntent(contactUri);
        mActivityRule.launchActivity(intent);
        InCallMetricsTestUtils.waitFor(1000);
        onView(withText(mActivity.getResources().getString(R.string.incall_plugin_directory_search,
                mCm.mName))).perform(click());

        // check count
        ContentValues entry = InCallMetricsTestDbUtils.getEntry(mActivity,
                InCallMetricsDbHelper.Tables.USER_ACTIONS_TABLE,
                InCallMetricsHelper.Events.DIRECTORY_SEARCH);
        int newCount = entry.containsKey(InCallMetricsDbHelper.UserActionsColumns.COUNT) ?
                entry.getAsInteger(InCallMetricsDbHelper.UserActionsColumns.COUNT) : 0;
        Log.d(TAG, "DIRECTORY_SEARCH newCount:" + newCount);
        Assert.assertEquals(1, newCount);

        // clean up contact
        InCallMetricsTestUtils.deleteContact(contactUri, cr);
        Log.d(TAG, "-----test3 finish -----");
    }
    /*
     * test metrics: INAPP_NUDGE_CONTACTS_LOGIN
     * precondition : signed out
     */
    @Test
    public void test4() {
        Log.d(TAG, "-----test4 start -----");
        // sign out
        InCallMetricsTestUtils.setInCallPluginAuthState(mActivity, false);
        InCallMetricsTestUtils.waitFor(500);

        ContentResolver cr = mActivity.getContentResolver();
        Assert.assertTrue(InCallMetricsTestDbUtils.clearAllEntries(mActivity));
        Uri contactUri = InCallMetricsTestUtils.createContact(null, "Tester1", cr);
        Intent intent = InCallMetricsTestUtils.getContactCardIntent(contactUri);
        mActivityRule.launchActivity(intent);
        InCallMetricsTestUtils.waitFor(1000);
        onView(withText(containsString("Sign in"))).perform(click());

        // check count
        ContentValues entry = InCallMetricsTestDbUtils.getEntry(mActivity,
                InCallMetricsDbHelper.Tables.INAPP_TABLE,
                InCallMetricsHelper.Events.INAPP_NUDGE_CONTACTS_LOGIN);
        int newCount = entry.containsKey(InCallMetricsDbHelper.InAppColumns.COUNT) ?
                entry.getAsInteger(InCallMetricsDbHelper.InAppColumns.COUNT) : 0;
        Log.d(TAG, "INAPP_NUDGE_CONTACTS_LOGIN newCount:" + newCount);
        Assert.assertEquals(1, newCount);

        // clean up contact
        InCallMetricsTestUtils.deleteContact(contactUri, cr);
        Log.d(TAG, "-----test4 finish -----");
    }

    /*
     * test metrics: INAPP_NUDGE_CONTACTS_INSTALL
     * precondition : plugin hidden (dependency not available)
     */
    @Test
    public void test5() {
        Log.d(TAG, "-----test5 start -----");
        InCallMetricsTestUtils.setInCallPluginDependency(mActivity, false);
        InCallMetricsTestUtils.waitFor(500);

        ContentResolver cr = mActivity.getContentResolver();
        Assert.assertTrue(InCallMetricsTestDbUtils.clearAllEntries(mActivity));
        Uri contactUri = InCallMetricsTestUtils.createContact(null, "Tester1", cr);
        Intent intent = InCallMetricsTestUtils.getContactCardIntent(contactUri);
        mActivityRule.launchActivity(intent);
        InCallMetricsTestUtils.waitFor(1000);
        onView(withText(containsString("Sign in"))).perform(click());

        // check count
        ContentValues entry = InCallMetricsTestDbUtils.getEntry(mActivity,
                InCallMetricsDbHelper.Tables.INAPP_TABLE,
                InCallMetricsHelper.Events.INAPP_NUDGE_CONTACTS_INSTALL);
        int newCount = entry.containsKey(InCallMetricsDbHelper.InAppColumns.COUNT) ?
                entry.getAsInteger(InCallMetricsDbHelper.InAppColumns.COUNT) : 0;
        Log.d(TAG, "INAPP_NUDGE_CONTACTS_LOGIN newCount:" + newCount);
        Assert.assertEquals(1, newCount);

        // clean up contact
        InCallMetricsTestUtils.deleteContact(contactUri, cr);
        Log.d(TAG, "-----test5 finish -----");
    }
}

