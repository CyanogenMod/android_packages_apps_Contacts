To run the espresso tests:

# Preparation
## 1) Build the binary first
packages/apps/Contacts/androidtest/mm
## 2) Install the test apk on target device
adb install -r $OUT/target/product/[device]/data/app/ContactsAndroidTests/ContactsAndroidTests.apk
## 3) Install InCall dependency package (an InCall app) and sign in

# Execute test commands
## For the following metrics:
* INAPP_NUDGE_CONTACTS_TAB_LOGIN
* DIRECTORY_SEARCH
adb shell am instrument -w -e class com.android.contacts.androidtest.InCallMetricsPeopleActivityTest com.android.contacts.androidtest/android.support.test.runner.AndroidJUnitRunner

## For the following metrics:
* CONTACTS_AUTO_MERGED
* INVITES_SENT
* DIRECTORY_SEARCH
* INAPP_NUDGE_CONTACTS_LOGIN
* INAPP_NUDGE_CONTACTS_INSTALL
adb shell am instrument -w -e class com.android.contacts.androidtest.InCallMetricsQuickContactActivityTest com.android.contacts.androidtest/android.support.test.runner.AndroidJUnitRunner