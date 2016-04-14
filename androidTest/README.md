To run the espresso tests:

# Preparation
## 1) Build the binary first
packages/apps/Contacts/androidtest/mm
## 2) Install the test apk on target device
adb install -r $OUT/target/product/[device]/data/app/ContactsAndroidTests/ContactsAndroidTests.apk
## 3) Install InCall dependency package (an InCall app) and sign in (some test cases have its own
 precondition, please run them individually)

# Execute test commands
## For the following metrics:
* test1: INAPP_NUDGE_CONTACTS_TAB_LOGIN (precondition: hard signed out)
* test2: DIRECTORY_SEARCH
adb shell am instrument -w -e class com.android.contacts.androidtest.InCallMetricsPeopleActivityTest com.android.contacts.androidtest/android.support.test.runner.AndroidJUnitRunner

## For the following metrics:
* test1: CONTACTS_MANUAL_MERGED
* test2: CONTACTS_AUTO_MERGED
* test3: INVITES_SENT
* test4: DIRECTORY_SEARCH
* test5: INAPP_NUDGE_CONTACTS_LOGIN
* test6: INAPP_NUDGE_CONTACTS_INSTALL (precondition: uninstall dependency)
adb shell am instrument -w -e class com.android.contacts.androidtest.InCallMetricsQuickContactActivityTest com.android.contacts.androidtest/android.support.test.runner.AndroidJUnitRunner

Note. to run individual test cases (eg. test1:
adb shell am instrument -w -e class com.android.contacts.androidtest.InCallMetricsQuickContactActivityTest#test com.android.contacts.androidtest/android.support.test.runner.AndroidJUnitRunner