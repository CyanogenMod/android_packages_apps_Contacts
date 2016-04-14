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

import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.util.Log;

import com.android.contacts.quickcontact.QuickContactActivity;

import java.util.ArrayList;

public class InCallMetricsTestUtils {
    private static final String TAG = InCallMetricsTestUtils.class.getSimpleName();

    public static void waitFor(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {

        }
    }

    public static void setInCallPluginAuthState(Context context, boolean state) {
        Intent intent = new Intent("com.android.contacts.androidtest.AUTH_STATE");
        intent.putExtra("state", state);
        context.sendBroadcast(intent);

    }

    public static void setInCallPluginDependency(Context context, boolean state) {
        Intent intent = new Intent("com.android.contacts.androidtest.DEPENDENCY");
        intent.putExtra("state", state);
        context.sendBroadcast(intent);
    }

    public static Intent getContactCardIntent(Uri uri) {
        final Intent intent = new Intent(ContactsContract.QuickContact.ACTION_QUICK_CONTACT);
        intent.setData(uri);
        intent.putExtra(ContactsContract.QuickContact.EXTRA_MODE,
                QuickContactActivity.MODE_FULLY_EXPANDED);
        // Make sure not to show QuickContacts on top of another QuickContacts.
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return intent;
    }

    public static Uri createContact(Uri matchUri, String displayName, ContentResolver
            contentResolver) {
        String name = displayName;
        String phone = "777-777-7777";
        int phoneType = ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE;
        if (matchUri != null) {
            // look up matchUri
            String selection = ContactsContract.Data.MIMETYPE + " =? OR " +
                    ContactsContract.Data.MIMETYPE + " =?";
            Cursor cursor = contentResolver.query(
                    Uri.withAppendedPath(matchUri, ContactsContract.Contacts.Entity
                            .CONTENT_DIRECTORY),
                    null,
                    selection,
                    new String[] {
                            ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
                            ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE},
                    null);
            boolean foundName = false;
            boolean foundPhone = false;
            while (cursor.moveToNext()) {
                Log.d(TAG, "found matchUri");
                if (cursor.getString(cursor.getColumnIndex(ContactsContract.Data.MIMETYPE))
                        .equals(ContactsContract.CommonDataKinds.StructuredName
                                .CONTENT_ITEM_TYPE)) {
                    name = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts
                            .DISPLAY_NAME));
                    foundPhone = true;
                }
                if (cursor.getString(cursor.getColumnIndex(ContactsContract.Data.MIMETYPE))
                        .equals(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)) {
                    phone = cursor.getString(cursor.getColumnIndex(ContactsContract
                            .CommonDataKinds.Phone.NUMBER));
                    phoneType = cursor.getInt(cursor.getColumnIndex(ContactsContract
                            .CommonDataKinds.Phone.TYPE));
                    foundPhone = true;
                }
                if (foundName && foundPhone) {
                    Log.d(TAG, "found matchUri name:" + name + " phone:"+phone);
                    break;
                }
            }
            cursor.close();
        }

        ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();
        ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, "com.android.localphone")
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, "PHONE")
                .build());
        ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                .build());
        ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, phoneType)
                .build());


        // Ask the Contact provider to create a new contact
        Log.i(TAG,"Creating contact: " + name);
        try {
            contentResolver.applyBatch(ContactsContract.AUTHORITY, ops);
        } catch (Exception e) {
            // Display warning
            Log.e(TAG, "Exceptoin encoutered while inserting contact: " + e);
        }

        // get uri and return uri
        Uri contactUri = null;
        String[] projection = {
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.LOOKUP_KEY};
        String selection = ContactsContract.Contacts.DISPLAY_NAME + " =?";
        Cursor cursor = contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                projection,
                selection,
                new String[] {name},
                null);
        if (cursor != null && cursor.moveToFirst()) {
            contactUri = ContactsContract.Contacts.getLookupUri(
                    cursor.getLong(0), cursor.getString(1));
        }
        cursor.close();
        return contactUri;
    }

    public static void deleteContact(Uri contactUri, ContentResolver contentResolver) {
        //ContentProviderOperation.newDelete(contactUri).build();
        contentResolver.delete(contactUri, null, null);
    }

    public static Uri findFirstContactWithPhoneNumber(String accountType,
            ContentResolver contentResolver) {
        Uri contactUri = null;
        Log.d(TAG, "RawContact accountType:" + accountType);
        String selection = ContactsContract.RawContacts.ACCOUNT_TYPE + " =?";
        Cursor cursor = contentResolver.query(
                ContactsContract.RawContacts.CONTENT_URI,
                null,
                selection,
                new String[] {accountType},
                null);

        while (cursor.moveToNext()) {
            int rawId = cursor.getInt(cursor.getColumnIndex(ContactsContract.RawContacts._ID));
            int contactId = cursor.getInt(cursor.getColumnIndex(ContactsContract.RawContacts
                    .CONTACT_ID));
            Log.d(TAG, "RawContact rawId " + rawId + " contactId:" + contactId);
            Cursor dataCursor = contentResolver.query(
                    ContactsContract.Data.CONTENT_URI,
                    null,
                    ContactsContract.Data.RAW_CONTACT_ID + " =? AND " + ContactsContract.Data
                            .MIMETYPE + " =?",
                    new String[] {String.valueOf(rawId), ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE},
                    null);
            while (dataCursor.moveToNext()) {
                Log.d(TAG, "dataCursor found" + dataCursor.getInt(0));
                contactUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI,
                        String.valueOf(contactId));
                cursor.close();
                dataCursor.close();
                return contactUri;
            }
            dataCursor.close();
        }
        cursor.close();
        return contactUri;
    }
}
