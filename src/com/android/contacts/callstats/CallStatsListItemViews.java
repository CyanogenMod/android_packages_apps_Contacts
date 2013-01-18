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

import android.view.View;
import android.widget.QuickContactBadge;

import com.android.contacts.R;

/**
 * Simple value object containing the various views within a call stat entry.
 */
public final class CallStatsListItemViews {
    /** The quick contact badge for the contact. */
    public final QuickContactBadge quickContactView;
    /** The primary action view of the entry. */
    public final View primaryActionView;
    /** The details of the phone call. */
    public final CallStatsDetailViews callStatsDetailViews;
    /** The divider to be shown below items. */
    public final View bottomDivider;

    private CallStatsListItemViews(QuickContactBadge quickContactView, View primaryActionView,
            CallStatsDetailViews callStatsDetailViews,
            View bottomDivider) {
        this.quickContactView = quickContactView;
        this.primaryActionView = primaryActionView;
        this.callStatsDetailViews = callStatsDetailViews;
        this.bottomDivider = bottomDivider;
    }

    public static CallStatsListItemViews fromView(View view) {
        return new CallStatsListItemViews(
                (QuickContactBadge) view.findViewById(R.id.quick_contact_photo),
                view.findViewById(R.id.primary_action_view),
                CallStatsDetailViews.fromView(view),
                view.findViewById(R.id.call_stats_divider));
    }

}
