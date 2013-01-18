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
import android.content.Intent;

import com.android.contacts.ContactsUtils;

/**
 * Class to get intents for a phone call or for a detailed statistical view
 */
public abstract class IntentProvider {
    public abstract Intent getIntent(Context context);

    public static IntentProvider getReturnCallIntentProvider(final String number) {
        return new IntentProvider() {
            @Override
            public Intent getIntent(Context context) {
                return ContactsUtils.getCallIntent(number);
            }
        };
    }

    public static IntentProvider getCallStatsDetailIntentProvider(final CallStatsDetails item,
            final long from, final long to) {
        return new IntentProvider() {
            @Override
            public Intent getIntent(Context context) {
                Intent intent = item.getIntentWithExtras(context);
                intent.putExtra("from", from);
                intent.putExtra("to", to);
                return intent;
            }
        };
    }
}
