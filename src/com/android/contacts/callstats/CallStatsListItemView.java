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

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;

public class CallStatsListItemView extends LinearLayout {
    public CallStatsListItemView(Context context) {
        super(context);
    }

    public CallStatsListItemView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CallStatsListItemView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void requestLayout() {
        // We will assume that once measured this will not need to resize
        // itself, so there is no need to pass the layout request to the parent
        // view (ListView).
        forceLayout();
    }
}
