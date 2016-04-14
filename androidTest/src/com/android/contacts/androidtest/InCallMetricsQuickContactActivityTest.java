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

import android.content.Context;
import android.content.res.Resources;
import android.support.test.InstrumentationRegistry;
import android.support.test.espresso.NoMatchingViewException;
import android.support.test.runner.AndroidJUnit4;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject;
import android.support.test.uiautomator.UiSelector;
import android.support.test.uiautomator.UiObjectNotFoundException;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.Until;
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


import org.hamcrest.Matcher;
import org.junit.After;
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
    private String mTesterPhone1;
    private String mTesterPhone2;

    @Rule
    public ActivityTestRule<QuickContactActivity> mActivityRule
            = new ActivityTestRule<>(QuickContactActivity.class, false, false);

    @Before
    public void setup() {
        // get Activity handle under test
        mActivity = (QuickContactActivity) mActivityRule.launchActivity(null);
        // get CallMethodInfo under test
        InCallMetricsTestUtils.waitFor(500);
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

        // Initialize UiDevice instance
        mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        mTesterPhone1 = "777-777-7777";
        mTesterPhone2 = "123-456-7890";
    }

    private static final String BASIC_SAMPLE_PACKAGE = "com.android.contacts";
    private static final int LAUNCH_TIMEOUT = 5000;
    private UiDevice mDevice;

    /*
     * test metrics: CONTACTS_MANUAL_MERGED
     * precondition : signed in
     */
    @Test
    public void test1() {
        String testName = InCallMetricsHelper.Events.CONTACTS_MANUAL_MERGED.value();
        Log.d(TAG, "-----test1 start -----" + testName);
        /////////////
        InCallMetricsTestUtils.setInCallPluginAuthState(mActivity, true);
        InCallMetricsTestUtils.waitFor(500);

        // clear out db
        Assert.assertTrue(InCallMetricsTestDbUtils.clearAllEntries(mActivity));
        // check current impression count
        // show contacts tab login 2x
        // select InCall plugin tab
        ContentResolver cr = mActivity.getContentResolver();
        Uri contactUri1 = InCallMetricsTestUtils.createContact(null, "Tester1", mTesterPhone1, cr);
        Uri contactUri2 = InCallMetricsTestUtils.createContact(null, "Tester2", mTesterPhone2, cr);

        InCallMetricsTestUtils.openContactCard(mActivity, contactUri1);
        InCallMetricsTestUtils.waitFor(1000);

        ///////////
        // Click on edit
        UiObject editButton = mDevice.findObject(new UiSelector()
                .resourceId("com.android.contacts:id/menu_edit"));
        try {
            if (editButton.exists()) {
                editButton.click();
                InCallMetricsTestUtils.waitFor(1000);
                // Open menu
                mDevice.pressMenu();
                InCallMetricsTestUtils.waitFor(1000);
                // Click on merge
                UiObject mergeMenu = mDevice.findObject(new UiSelector()
                        .text("Merge"));
                if (mergeMenu.exists()) {
                    mergeMenu.click();
                    InCallMetricsTestUtils.waitFor(1000);
                    // Select the merge target contact
                    UiObject mergeContact = mDevice.findObject(new UiSelector()
                        .text("Tester2"));
                    if (mergeContact.exists()) {
                        mergeContact.click();
                    } else {
                        Log.d(TAG, "ERROR: mergeCount does not exist");
                    }
                    InCallMetricsTestUtils.waitFor(2000);
                }
            } else {
                Log.d(TAG, "ERROR: edit button not found");
            }
            InCallMetricsTestUtils.waitFor(500);
            ContentValues entry = InCallMetricsTestDbUtils.getEntry(mActivity,
                    InCallMetricsDbHelper.Tables.USER_ACTIONS_TABLE,
                    InCallMetricsHelper.Events.CONTACTS_MANUAL_MERGED);
            InCallMetricsTestUtils.verifyUserActionsMetrics(testName, entry, 1);
        } catch (UiObjectNotFoundException e) {
            Log.d(TAG, "ERROR: No matching view");
        }

        // TODO: delete merged account
        // clean up contact
        InCallMetricsTestUtils.deleteContact(contactUri2, cr);
        InCallMetricsTestUtils.deleteContact(contactUri1, cr);
        Log.d(TAG, "-----test1 finish -----");
    }

    /*
     * test metrics: CONTACTS_AUTO_MERGED
     * precondition : signed in
     */
    @Test
    public void test2() {
        String testName = InCallMetricsHelper.Events.CONTACTS_AUTO_MERGED.value();
        Log.d(TAG, "-----test2 start -----" + testName);
        InCallMetricsTestUtils.setInCallPluginAuthState(mActivity, true);
        InCallMetricsTestUtils.waitFor(500);
        // clear out db
        Assert.assertTrue(InCallMetricsTestDbUtils.clearAllEntries(mActivity));

        // query
        ContentResolver cr = mActivity.getContentResolver();
        Uri matchUri = InCallMetricsTestUtils.createContact(null, "MergeTester", mTesterPhone1, cr);
        Uri contactUri = InCallMetricsTestUtils.createContact(null, "MergeTester", mTesterPhone1,
                cr);

        InCallMetricsTestUtils.waitFor(500);

        // check count
        ContentValues entry = InCallMetricsTestDbUtils.getEntry(mActivity,
                InCallMetricsDbHelper.Tables.USER_ACTIONS_TABLE,
                InCallMetricsHelper.Events.CONTACTS_AUTO_MERGED);
        InCallMetricsTestUtils.verifyUserActionsMetrics(testName, entry, 1);

        // clean up contact
        InCallMetricsTestUtils.deleteContact(contactUri, cr);
        InCallMetricsTestUtils.deleteContact(matchUri, cr);
        Log.d(TAG, "-----test2 finish -----");
    }

    /*
     * test metrics: INVITES_SENT
     * precondition : signed in
     */
    @Test
    public void test3() {
        String testName = InCallMetricsHelper.Events.INVITES_SENT.value();
        Log.d(TAG, "-----test3 start -----" + testName);
        // precondition signed in
        InCallMetricsTestUtils.setInCallPluginAuthState(mActivity, true);
        InCallMetricsTestUtils.waitFor(500);
        // clear out db
        Assert.assertTrue(InCallMetricsTestDbUtils.clearAllEntries(mActivity));

        // launch activity
        ContentResolver cr = mActivity.getContentResolver();
        Uri contactUri = InCallMetricsTestUtils.createContact(null, "Tester3", mTesterPhone1, cr);
        InCallMetricsTestUtils.openContactCard(mActivity, contactUri);
        InCallMetricsTestUtils.waitFor(1000);
        try {
            onView(withText(R.string.incall_plugin_invite)).perform(click());
            InCallMetricsTestUtils.waitFor(2000);
            // check count
            ContentValues entry = InCallMetricsTestDbUtils.getEntry(mActivity,
                    InCallMetricsDbHelper.Tables.USER_ACTIONS_TABLE,
                    InCallMetricsHelper.Events.INVITES_SENT);
            InCallMetricsTestUtils.verifyUserActionsMetrics(testName, entry, 1);
        } catch (NoMatchingViewException e) {
            Log.d(TAG, "ERROR: No matching view");
        }
        // clean up contact
        InCallMetricsTestUtils.deleteContact(contactUri, cr);
        Log.d(TAG, "-----test3 finish -----");
    }

    /*
     * test metrics: DIRECTORY_SEARCH
     * precondition : signed in
     */
    @Test
    public void test4() {
        String testName = InCallMetricsHelper.Events.DIRECTORY_SEARCH.value();
        Log.d(TAG, "-----test4 start -----" + testName);
        // precondition: signed in
        InCallMetricsTestUtils.setInCallPluginAuthState(mActivity, true);
        InCallMetricsTestUtils.waitFor(500);

        Assert.assertTrue(InCallMetricsTestDbUtils.clearAllEntries(mActivity));
        ContentResolver cr = mActivity.getContentResolver();
        Uri contactUri = InCallMetricsTestUtils.createContact(null, "Tester4", mTesterPhone1, cr);
        InCallMetricsTestUtils.openContactCard(mActivity, contactUri);
        InCallMetricsTestUtils.waitFor(1000);
        try {
            onView(withText(
                    mActivity.getResources().getString(R.string.incall_plugin_directory_search,
                            mCm.mName))).perform(click());
            InCallMetricsTestUtils.waitFor(2000);
            // check count
            ContentValues entry = InCallMetricsTestDbUtils.getEntry(mActivity,
                    InCallMetricsDbHelper.Tables.USER_ACTIONS_TABLE,
                    InCallMetricsHelper.Events.DIRECTORY_SEARCH);
            InCallMetricsTestUtils.verifyUserActionsMetrics(testName, entry, 1);
        } catch (NoMatchingViewException e) {
            Log.d(TAG, "ERROR: No matching view");
        }
        // clean up contact
        InCallMetricsTestUtils.deleteContact(contactUri, cr);
        Log.d(TAG, "-----test4 finish -----");
    }
    /*
     * test metrics: INAPP_NUDGE_CONTACTS_LOGIN
     * precondition : signed out
     */
    @Test
    public void test5() {
        String testName = InCallMetricsHelper.Events.INAPP_NUDGE_CONTACTS_LOGIN.value();
        Log.d(TAG, "-----test5 start -----" + testName);

        // sign out
        InCallMetricsTestUtils.setInCallPluginAuthState(mActivity, false);
        InCallMetricsTestUtils.waitFor(500);

        ContentResolver cr = mActivity.getContentResolver();
        Assert.assertTrue(InCallMetricsTestDbUtils.clearAllEntries(mActivity));
        Uri contactUri = InCallMetricsTestUtils.createContact(null, "Tester5", mTesterPhone1, cr);
        InCallMetricsTestUtils.openContactCard(mActivity, contactUri);
        InCallMetricsTestUtils.waitFor(1000);
        try {
            onView(withText(containsString("Sign in"))).perform(click());
            InCallMetricsTestUtils.waitFor(2000);

            // check count
            ContentValues entry = InCallMetricsTestDbUtils.getEntry(mActivity,
                    InCallMetricsDbHelper.Tables.INAPP_TABLE,
                    InCallMetricsHelper.Events.INAPP_NUDGE_CONTACTS_LOGIN);
            InCallMetricsTestUtils.verifyUserActionsMetrics(testName, entry, 1);
        } catch (NoMatchingViewException e) {
            Log.d(TAG, "ERROR: No matching view");
        }
        // clean up contact
        InCallMetricsTestUtils.deleteContact(contactUri, cr);

        Log.d(TAG, "-----test5 finish -----");
    }

    /*
     * test metrics: INAPP_NUDGE_CONTACTS_INSTALL
     * precondition : plugin hidden (dependency not available)
     */
    @Test
    public void test6() {
        String testName = InCallMetricsHelper.Events.INAPP_NUDGE_CONTACTS_INSTALL.value();
        Log.d(TAG, "-----test6 start -----" + testName);
        Log.d(TAG, "Please uninsatll InCall plugin dependency package...(waiting 10 seconds)");
        InCallMetricsTestUtils.setInCallPluginAuthState(mActivity, true);
        InCallMetricsTestUtils.waitFor(10000);

        ContentResolver cr = mActivity.getContentResolver();
        InCallMetricsTestDbUtils.clearAllEntries(mActivity);
        Uri contactUri = InCallMetricsTestUtils.createContact(null, "Tester6", mTesterPhone1, cr);
        Intent intent = InCallMetricsTestUtils.getContactCardIntent(contactUri);
        mActivityRule.launchActivity(intent);
        InCallMetricsTestUtils.waitFor(1000);
        try {
            onView(withText(containsString("Get"))).perform(click());
            InCallMetricsTestUtils.waitFor(2000);
            // check count
            ContentValues entry = InCallMetricsTestDbUtils.getEntry(mActivity,
                    InCallMetricsDbHelper.Tables.INAPP_TABLE,
                    InCallMetricsHelper.Events.INAPP_NUDGE_CONTACTS_INSTALL);
            InCallMetricsTestUtils.verifyUserActionsMetrics(testName, entry, 1);
        } catch (NoMatchingViewException e) {
            Log.d(TAG, "ERROR: No matching View");
        }
        // clean up contact
        InCallMetricsTestUtils.deleteContact(contactUri, cr);

        Log.d(TAG, "-----test6 finish -----");
    }

    @After
    public void cleanup() {
        InCallMetricsTestUtils.setInCallPluginAuthState(mActivity, true);
    }
}

