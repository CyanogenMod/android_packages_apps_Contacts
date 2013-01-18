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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.DatePicker.OnDateChangedListener;

import java.lang.System;
import java.util.Calendar;

import com.android.contacts.R;

/**
 * Alertdialog with two date pickers - one for a start and one for an end date.
 * Used to filter the callstats query.
 */
public class DoubleDatePickerDialog extends AlertDialog
        implements OnClickListener, OnDateChangedListener {

    private static final String TAG = "DoubleDatePickerDialog";

    public interface OnDateSetListener {
        void onDateSet(long from, long to);
    }

    private static final String YEAR = "year";
    private static final String MONTH = "month";
    private static final String DAY = "day";

    private final DatePicker mDatePickerFrom;
    private final DatePicker mDatePickerTo;
    private final OnDateSetListener mCallBack;
    private Button mOkButton;

    public DoubleDatePickerDialog(Context context,
            OnDateSetListener callBack) {
        super(context);

        mCallBack = callBack;

        setTitle(R.string.call_stats_filter_picker_title);
        setButton(BUTTON_NEGATIVE, context.getString(android.R.string.cancel), this);
        setButton(BUTTON_POSITIVE, context.getString(android.R.string.ok), this);
        setIcon(0);

        LayoutInflater inflater =
                (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.double_date_picker_dialog, null);
        setView(view);

        mDatePickerFrom = (DatePicker) view.findViewById(R.id.datePickerFrom);
        mDatePickerTo = (DatePicker) view.findViewById(R.id.datePickerTo);
        resetPickers();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mOkButton = getButton(DialogInterface.BUTTON_POSITIVE);
        updateOkButtonState();
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        switch (which) {
            case BUTTON_POSITIVE:
                tryNotifyDateSet();
                break;
            case BUTTON_NEGATIVE:
                break;
        }
    }

    public void onDateChanged(DatePicker view, int year,
            int month, int day) {
        view.init(year, month, day, this);
        updateOkButtonState();
    }

    public void setValues(long millisFrom, long millisTo) {
        setPicker(mDatePickerFrom, millisFrom);
        setPicker(mDatePickerTo, millisTo);
        updateOkButtonState();
    }

    public void resetPickers() {
        long millis = System.currentTimeMillis();
        setPicker(mDatePickerFrom, millis);
        setPicker(mDatePickerTo, millis);
        updateOkButtonState();
    }

    private void setPicker(DatePicker picker, long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);

        int year = c.get(Calendar.YEAR);
        int month = c.get(Calendar.MONTH);
        int day = c.get(Calendar.DAY_OF_MONTH);

        picker.init(year, month, day, this);
    }

    private long getMillisForPicker(DatePicker picker, boolean endOfDay) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.YEAR, picker.getYear());
        c.set(Calendar.MONTH, picker.getMonth());
        c.set(Calendar.DAY_OF_MONTH, picker.getDayOfMonth());
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);

        long millis = c.getTimeInMillis();
        if (endOfDay) {
            millis += 24L * 60L * 60L * 1000L - 1L;
        }

        return millis;
    }

    private void updateOkButtonState() {
        if (mOkButton != null) {
            long millisFrom = getMillisForPicker(mDatePickerFrom, false);
            long millisTo = getMillisForPicker(mDatePickerTo, true);
            mOkButton.setEnabled(millisFrom < millisTo);
        }
    }

    private void tryNotifyDateSet() {
        if (mCallBack != null) {
            mDatePickerFrom.clearFocus();
            mDatePickerTo.clearFocus();

            long millisFrom = getMillisForPicker(mDatePickerFrom, false);
            long millisTo = getMillisForPicker(mDatePickerTo, true);

            mCallBack.onDateSet(millisFrom, millisTo);
        }
    }

    // users like to play with it, so save the state and don't reset each time
    @Override
    public Bundle onSaveInstanceState() {
        Bundle state = super.onSaveInstanceState();
        state.putInt("F_" + YEAR, mDatePickerFrom.getYear());
        state.putInt("F_" + MONTH, mDatePickerFrom.getMonth());
        state.putInt("F_" + DAY, mDatePickerFrom.getDayOfMonth());
        state.putInt("T_" + YEAR, mDatePickerTo.getYear());
        state.putInt("T_" + MONTH, mDatePickerTo.getMonth());
        state.putInt("T_" + DAY, mDatePickerTo.getDayOfMonth());
        return state;
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        int fyear = savedInstanceState.getInt("F_" + YEAR);
        int fmonth = savedInstanceState.getInt("F_" + MONTH);
        int fday = savedInstanceState.getInt("F_" + DAY);
        int tyear = savedInstanceState.getInt("T_" + YEAR);
        int tmonth = savedInstanceState.getInt("T_" + MONTH);
        int tday = savedInstanceState.getInt("T_" + DAY);
        mDatePickerFrom.init(fyear, fmonth, fday, this);
        mDatePickerTo.init(tyear, tmonth, tday, this);
    }
}
