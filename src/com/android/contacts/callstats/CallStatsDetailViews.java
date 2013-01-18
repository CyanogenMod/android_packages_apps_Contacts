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
import android.widget.TextView;

import com.android.contacts.R;

public final class CallStatsDetailViews {
    public final TextView nameView;
    public final TextView numberView;
    public final TextView labelView;
    public final TextView percentView;
    public final LinearColorBar barView;

    private CallStatsDetailViews(TextView nameView, TextView numberView,
            TextView labelView, TextView percentView, LinearColorBar barView) {
        this.nameView = nameView;
        this.numberView = numberView;
        this.labelView = labelView;
        this.percentView = percentView;
        this.barView = barView;
    }

    public static CallStatsDetailViews fromView(View view) {
        return new CallStatsDetailViews(
                (TextView) view.findViewById(R.id.name),
                (TextView) view.findViewById(R.id.number),
                (TextView) view.findViewById(R.id.label),
                (TextView) view.findViewById(R.id.percent),
                (LinearColorBar) view.findViewById(R.id.percent_bar));
    }
}
