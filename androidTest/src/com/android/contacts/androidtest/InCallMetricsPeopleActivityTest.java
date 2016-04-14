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

import com.android.contacts.activities.PeopleActivity;
import com.android.contacts.incall.InCallMetricsDbHelper;
import com.android.contacts.incall.InCallMetricsHelper;

import com.android.phone.common.incall.CallMethodInfo;
import com.android.phone.common.incall.ContactsDataSubscription;
import com.android.phone.common.incall.utils.CallMethodFilters;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.action.ViewActions.swipeLeft;
import static android.support.test.espresso.action.ViewActions.swipeRight;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import android.content.ComponentName;

import android.content.ContentValues;
import android.support.test.espresso.NoMatchingViewException;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.test.suitebuilder.annotation.MediumTest;
import android.util.Log;


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
public class InCallMetricsPeopleActivityTest {
    private static final String TAG = InCallMetricsPeopleActivityTest.class.getSimpleName();
    private PeopleActivity mActivity;
    private CallMethodInfo mCm;

    @Rule
    public ActivityTestRule<PeopleActivity> mActivityRule = new ActivityTestRule<>
            (PeopleActivity.class, true, true); // specify activity is launched at all times

    @Before
    public void setUp() {
        // get Activity handle under test
        mActivity = (PeopleActivity) mActivityRule.getActivity();
        Assert.assertNotNull(mActivity);
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
        InCallMetricsTestUtils.setInCallPluginAuthState(mActivity, false);
    }

    /*
     * test metrics: INAPP_NUDGE_CONTACTS_TAB_LOGIN
     * precondition : hard signed out
     */
    @Test
    public void test1() {
        String testName = InCallMetricsHelper.Events.INAPP_NUDGE_CONTACTS_TAB_LOGIN.value();
        Log.d(TAG, "-----test1 start -----" + testName);
        Log.d(TAG, "Please hard sign out...");
        // clear out db
        Assert.assertTrue(InCallMetricsTestDbUtils.clearAllEntries(mActivity));
        Log.d(TAG, "inAppNudgeContactsTabLogin cm:" + mCm.mName);
        // make sure we're logged out
        Assert.assertTrue(!mCm.mIsAuthenticated);
        // check current impression count
        // show contacts tab login 2x
        // select InCall plugin tab
        try {
            onView(withId(R.id.tab_pager)).perform(swipeLeft());
            InCallMetricsTestUtils.waitFor(1000);
            // swipe away
            onView(withId(R.id.tab_pager)).perform(swipeRight());
            // select InCall plugin tab again
            onView(withId(R.id.tab_pager)).perform(swipeLeft());
            InCallMetricsTestUtils.waitFor(1000);

            long currentTime = System.currentTimeMillis();

            onView(withId(R.id.plugin_login_button)).perform(click());
            InCallMetricsTestUtils.waitFor(500);
            ContentValues entry = InCallMetricsTestDbUtils.getEntry(mActivity,
                    InCallMetricsDbHelper.Tables.INAPP_TABLE,
                    InCallMetricsHelper.Events.INAPP_NUDGE_CONTACTS_TAB_LOGIN);
            InCallMetricsTestUtils.verifyInAppMetrics(testName, entry, 2, currentTime);

        } catch (NoMatchingViewException e) {
            Log.d(TAG, "ERROR: No matching view");
        }
        Log.d(TAG, "-----test1 finish -----");
    }

    /*
     * test metrics: DIRECTORY_SEARCH (from contacts plugin tab)
     * precondition : signed in
     */
    @Test
    public void test2() {
        String testName = InCallMetricsHelper.Events.DIRECTORY_SEARCH.value();
        Log.d(TAG, "-----test2 start -----" + testName);
        // clear out db
        Assert.assertTrue(InCallMetricsTestDbUtils.clearAllEntries(mActivity));
        InCallMetricsTestUtils.setInCallPluginAuthState(mActivity, true);
        InCallMetricsTestUtils.waitFor(500);
        try {
            // focus on plugin tab
            onView(withId(R.id.tab_pager)).perform(swipeLeft());
            InCallMetricsTestUtils.waitFor(500);

            // click on directory search button
            onView(withId(R.id.floating_action_button)).perform(click());
            InCallMetricsTestUtils.waitFor(500);
            ContentValues entry = InCallMetricsTestDbUtils.getEntry(mActivity,
                    InCallMetricsDbHelper.Tables.USER_ACTIONS_TABLE,
                    InCallMetricsHelper.Events.DIRECTORY_SEARCH);
            InCallMetricsTestUtils.verifyUserActionsMetrics(testName, entry, 1);
        } catch (NoMatchingViewException e) {
            Log.d(TAG, "ERROR: No matching view");
        }
        Log.d(TAG, "-----test2 finish -----");
    }
}

