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
import com.android.phone.common.incall.CallMethodHelper;
//import com.cyanogen.ambient.incall.extension.StatusCodes;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.action.ViewActions.pressBack;
import static android.support.test.espresso.action.ViewActions.swipeLeft;
import static android.support.test.espresso.action.ViewActions.swipeRight;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import android.content.ComponentName;

import android.content.ContentValues;
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
    private PeopleActivity mPeopleActivity;
    private CallMethodInfo mCm;

    @Rule
    public ActivityTestRule<PeopleActivity> mPeopleActivityRule = new ActivityTestRule<>
            (PeopleActivity.class, true, true); // specify activity is launched at all times

    @Before
    public void setUp() {
        // get Activity handle under test
        mPeopleActivity = (PeopleActivity) mPeopleActivityRule.getActivity();
        Assert.assertNotNull(mPeopleActivity);
        // get CallMethodInfo under test
        InCallMetricsTestUtils.waitFor(2000);
        HashMap<ComponentName, CallMethodInfo> cmMap = (HashMap<ComponentName,
                CallMethodInfo>) CallMethodHelper.getAllEnabledCallMethods();
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
        InCallMetricsTestUtils.setInCallPluginAuthState(mPeopleActivity, false);
    }

    /*
     * test metrics: INAPP_NUDGE_CONTACTS_TAB_LOGIN
     */
    @Test
    public void test1() {
        // clear out db
        Assert.assertTrue(InCallMetricsTestDbUtils.clearAllEntries(mPeopleActivity));
        Log.d(TAG, "inAppNudgeContactsTabLogin cm:" + mCm.mName);
        // make sure we're logged out
        Assert.assertTrue(!mCm.mIsAuthenticated);
        // check current impression count
        // show contacts tab login 2x
        // select InCall plugin tab
        onView(withId(R.id.tab_pager)).perform(swipeLeft());
        InCallMetricsTestUtils.waitFor(1000);
        // swipe away
        onView(withId(R.id.tab_pager)).perform(swipeRight());
        // select InCall plugin tab again
        onView(withId(R.id.tab_pager)).perform(swipeLeft());
        InCallMetricsTestUtils.waitFor(1000);

        onView(withId(R.id.plugin_login_button)).perform(click());
        InCallMetricsTestUtils.waitFor(500);
        ContentValues entry = InCallMetricsTestDbUtils.getEntry(mPeopleActivity,
                InCallMetricsDbHelper.Tables.INAPP_TABLE,
                InCallMetricsHelper.Events.INAPP_NUDGE_CONTACTS_TAB_LOGIN);

        int newCount = entry.containsKey(InCallMetricsDbHelper.InAppColumns.COUNT) ?
                entry.getAsInteger(InCallMetricsDbHelper.InAppColumns.COUNT) : 0;
        Log.d(TAG, "inAppNudgContactsTabLogin newCount:" + newCount);
        Assert.assertEquals(2, newCount);
        // prepare for the next test sign in
        InCallMetricsTestUtils.setInCallPluginAuthState(mPeopleActivity, true);
        InCallMetricsTestUtils.waitFor(500);
    }

    /*
     * test metrics: DIRECTORY_SEARCH (from contacts plugin tab)
     */
    @Test
    public void test2() {
        // clear out db
        Assert.assertTrue(InCallMetricsTestDbUtils.clearAllEntries(mPeopleActivity));
        // focus on plugin tab
        onView(withId(R.id.tab_pager)).perform(swipeLeft());
        InCallMetricsTestUtils.waitFor(500);
        // click on directory search button
        onView(withId(R.id.floating_action_button)).perform(click());
        InCallMetricsTestUtils.waitFor(500);
        ContentValues entry = InCallMetricsTestDbUtils.getEntry(mPeopleActivity,
                InCallMetricsDbHelper.Tables.USER_ACTIONS_TABLE,
                InCallMetricsHelper.Events.DIRECTORY_SEARCH);
        int newCount = entry.containsKey(InCallMetricsDbHelper.UserActionsColumns.COUNT) ?
                entry.getAsInteger(InCallMetricsDbHelper.UserActionsColumns.COUNT) : 0;
        Log.d(TAG, "userActionDirectorySearchContactsTab newCount:" + newCount);
        Assert.assertEquals(1, newCount);
    }
}

