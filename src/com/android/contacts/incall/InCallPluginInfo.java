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

import android.app.PendingIntent;
import android.content.ComponentName;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import com.android.contacts.common.list.ContactListFilter;
import com.android.contacts.list.PluginContactBrowseListFragment;
import com.android.phone.common.incall.CallMethodInfo;

import java.util.Objects;


public class InCallPluginInfo implements Parcelable {
    private static String TAG = InCallPluginInfo.class.getSimpleName();
    private boolean DEBUG = false;

    public CallMethodInfo mCallMethodInfo = new CallMethodInfo();
    public String mTabTag; // uniquely identifies a ViewPagerTab and also serves as fragment ID
    public PluginContactBrowseListFragment mFragment; // This reference will not be saved


    public static final Parcelable.Creator<InCallPluginInfo> CREATOR
            = new Parcelable.Creator<InCallPluginInfo>() {
        public InCallPluginInfo createFromParcel(Parcel in) {
            return new InCallPluginInfo(in);
        }

        public InCallPluginInfo[] newArray(int size) {
            return new InCallPluginInfo[size];
        }
    };

    public InCallPluginInfo() {}

    private InCallPluginInfo(Parcel in) {
        mCallMethodInfo.mAccountType = in.readString();
        mCallMethodInfo.mAccountHandle = in.readString();
        mCallMethodInfo.mIsAuthenticated = in.readInt() == 1;
        mCallMethodInfo.mStatus = in.readInt();
        mCallMethodInfo.mDefaultDirectorySearchIntent = in.readParcelable(PendingIntent.class
                .getClassLoader());
        mCallMethodInfo.mDirectorySearchIntent =
                in.readParcelable(PendingIntent.class.getClassLoader());
        mCallMethodInfo.mLoginIntent = in.readParcelable(PendingIntent.class.getClassLoader());
        mCallMethodInfo.mName = in.readString();
        mCallMethodInfo.mBrandIconId = in.readInt();
        mCallMethodInfo.mLoginIconId = in.readInt();
        mTabTag = in.readString();
        mCallMethodInfo.mComponent = in.readParcelable(ComponentName.class.getClassLoader());
        mCallMethodInfo.mDependentPackage = in.readString();
        mCallMethodInfo.mMimeType = in.readString();
        mCallMethodInfo.mImMimeType = in.readString();
        mCallMethodInfo.mVideoCallableMimeType = in.readString();
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mCallMethodInfo.mAccountType);
        dest.writeString(mCallMethodInfo.mAccountHandle);
        dest.writeInt(mCallMethodInfo.mIsAuthenticated ? 1 : 0);
        dest.writeInt(mCallMethodInfo.mStatus);
        dest.writeParcelable(mCallMethodInfo.mDefaultDirectorySearchIntent, flags);
        dest.writeParcelable(mCallMethodInfo.mDirectorySearchIntent, flags);
        dest.writeParcelable(mCallMethodInfo.mLoginIntent, flags);
        dest.writeString(mCallMethodInfo.mName);
        dest.writeInt(mCallMethodInfo.mBrandIconId);
        dest.writeInt(mCallMethodInfo.mLoginIconId);
        dest.writeString(mTabTag);
        dest.writeParcelable(mCallMethodInfo.mComponent, flags);
        dest.writeString(mCallMethodInfo.mDependentPackage);
        dest.writeString(mCallMethodInfo.mMimeType);
        dest.writeString(mCallMethodInfo.mImMimeType);
        dest.writeString(mCallMethodInfo.mVideoCallableMimeType);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null) {
            return false;
        }
        if (!(other instanceof InCallPluginInfo)) {
            return false;
        }
        InCallPluginInfo otherObj = (InCallPluginInfo) other;
        return this.mCallMethodInfo.equals(otherObj.mCallMethodInfo);
    }

    @Override
    public int hashCode() {
        return mTabTag.hashCode();
    }
}
