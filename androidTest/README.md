To run the espresso tests:

mm

adb install -r $OUT/target/product/[device]/data/app/ContactsAndroidTests/ContactsAndroidTests.apk

User action tests (precondition: sign out InCall plugin first)
-INAPP_NUDGE_CONTACTS_TAB_LOGIN
-DIRECTORY_SEARCH
adb shell am instrument -w -e class com.android.contacts.androidtest.InCallMetricsPeopleActivityTest com.android.contacts.androidtest/android.support.test.runner.AndroidJUnitRunner