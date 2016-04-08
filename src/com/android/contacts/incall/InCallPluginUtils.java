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

package com.android.contacts.incall;

import android.content.ComponentName;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.Log;

import com.android.contacts.common.ContactPresenceIconUtil;
import com.android.contacts.common.ContactStatusUtil;
import com.android.contacts.common.model.Contact;
import com.android.contacts.common.model.RawContact;
import com.android.contacts.common.model.dataitem.DataItem;
import com.android.contacts.common.util.DataStatus;
import com.android.contacts.common.util.UriUtils;
import com.android.phone.common.incall.CallMethodInfo;
import com.android.phone.common.incall.utils.CallMethodFilters;
import com.cyanogen.ambient.incall.extension.InCallContactInfo;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

public class InCallPluginUtils {
    private static final String TAG = InCallPluginUtils.class.getSimpleName();
    private static boolean DEBUG = false;

    // key to store data id for InCall plugin callable entries
    public static final String KEY_DATA_ID = InCallPluginUtils.class.getPackage().getName() +
            ".data_id";
    public static final String KEY_MIMETYPE = InCallPluginUtils.class.getPackage().getName() +
            ".mimetype";
    public static final String KEY_COMPONENT = InCallPluginUtils.class.getPackage().getName() +
            ".component";
    public static final String KEY_NAME = InCallPluginUtils.class.getPackage().getName() + ".name";
    public static final String KEY_NUMBER = InCallPluginUtils.class.getPackage().getName() +
            ".number";
    public static final String KEY_NUDGE_KEY = InCallPluginUtils.class.getPackage().getName() + ""
            + ".nudge_key";

    public static Drawable getDrawable(Context context, int resourceId,
            ComponentName componentName) {
        Resources pluginRes = null;
        Drawable drawable = null;

        if (resourceId == 0) {
            return null;
        }
        try {
            pluginRes = context.getPackageManager().getResourcesForApplication(
                    componentName.getPackageName());
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Plugin not installed: " + componentName, e);
            return null;
        }
        try {
            drawable = pluginRes.getDrawable(resourceId);
        } catch (Resources.NotFoundException e) {
            Log.e(TAG, "Plugin does not define login icon: " + componentName, e);
        }
        return drawable;
    }

    public static Intent getVoiceMimeIntent(String mimeType, DataItem dataItem, CallMethodInfo cmi,
            String contactAccountHandle) {
        Intent intent = new Intent();
        intent.putExtra(KEY_DATA_ID, dataItem.getId());
        intent.putExtra(KEY_MIMETYPE, mimeType);
        intent.putExtra(KEY_COMPONENT, cmi.mComponent.flattenToString());
        intent.putExtra(KEY_NAME, cmi.mName);
        intent.putExtra(KEY_NUMBER, contactAccountHandle);
        return intent;
    }

    public static Intent getImMimeIntent(String mimeType, RawContact rawContact) {
        DataItem imDataItem = null;
        long dataId;
        for (DataItem dataItem : rawContact.getDataItems()) {
            if (dataItem.getMimeType().equals(mimeType)) {
                imDataItem = dataItem;
                break;
            }
        }
        dataId = imDataItem == null ? -1 : imDataItem.getId();
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.putExtra(KEY_DATA_ID, dataId);
        intent.setDataAndType(ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI,
                dataId), mimeType);
        return intent;
    }

    public static class PresenceInfo {
        public Drawable mPresenceIcon;
        public String mStatusMsg;
    }
    // targetRawContact is the raw contact with the same account type as the InCall plugin
    public static PresenceInfo lookupPresenceInfo(Context context, Contact contact, RawContact
            targetRawContact) {
        // When ContactLoader loads a list of raw contact in the ImmutableList, each raw contact's
        // associated presence data is placed in the ImmutableMap in the same order. ImmutableMap
        // iterates in the same order items were originally placed in it.
        PresenceInfo presenceInfo = new PresenceInfo();
        ImmutableList<RawContact> rawContacts = contact.getRawContacts();
        ImmutableMap<Long, DataStatus> statusMap = contact.getStatuses();
        if (rawContacts == null || statusMap == null) {
            return presenceInfo;
        }
        // Look for the position of the raw contact that corresponds to the plugin
        int index = rawContacts.indexOf(targetRawContact);
        if (index < 0 || rawContacts.size() != statusMap.size()) {
            // target raw contact entry not found or the list and map sizes mismatch (we may risk
            // the iterator not finding the matching presence data entry)
            return presenceInfo;
        }
        // move the iterator to the same offset as the raw contact to retrieve the presence data
        Iterator it = statusMap.entrySet().iterator();
        ImmutableMap.Entry<Long, DataStatus> mapEntry = null;
        while (it.hasNext()) {
            if (index == 0) {
                mapEntry = (ImmutableMap.Entry<Long, DataStatus>)it.next();
                break;
            }
            index--;
        }
        if (mapEntry == null) {
            return presenceInfo;
        }
        DataStatus presenceStatus = mapEntry.getValue();
        int presence = presenceStatus == null ? ContactsContract.StatusUpdates.OFFLINE :
                presenceStatus.getPresence();
        presenceInfo.mPresenceIcon = ContactPresenceIconUtil.getPresenceIcon(context, presence);
        presenceInfo.mStatusMsg = ContactStatusUtil.getStatusString(context, presence);
        return presenceInfo;
    }

    public static ArrayList<ComponentName> gatherPluginHistory(Context context) {
        ArrayList<ComponentName> cnList = new ArrayList<ComponentName>();
        HashMap<ComponentName, CallMethodInfo> plugins = CallMethodFilters
                .getAllEnabledCallMethods(context);
        for (ComponentName cn : plugins.keySet()) {
            cnList.add(cn);
        }
        return cnList;
    }

    // Retrieve the first contact data entry
    public static String lookupContactData(Contact contact, String dataMimeType, String dataKind) {
        String dataValue = "";

        if (TextUtils.isEmpty(dataMimeType) || TextUtils.isEmpty(dataKind)) {
            return null;
        }
        for (RawContact raw: contact.getRawContacts()) {
            for (DataItem dataItem : raw.getDataItems()) {
                final ContentValues entryValues = dataItem.getContentValues();
                final String mimeType = dataItem.getMimeType();

                if (mimeType == null) continue;

                if (mimeType.equals(dataMimeType)) {
                    return entryValues.getAsString(dataKind);
                }
            }
        }
        return dataValue;
    }

    public static InCallContactInfo getInCallContactInfo(Contact contact) {
        String phoneNumber = InCallPluginUtils.lookupContactData(contact,
                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.Phone.NUMBER);
        Uri lookupUri = contact.getLookupUri();
        if (UriUtils.isEncodedContactUri(lookupUri)) {
            lookupUri = Uri.parse(TextUtils.isEmpty(contact.getDisplayName()) ? phoneNumber :
                    contact.getDisplayName());
        }
        return new InCallContactInfo(contact.getDisplayName(),
                phoneNumber, lookupUri);

    }
}
