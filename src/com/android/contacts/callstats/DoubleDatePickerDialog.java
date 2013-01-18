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
    private final Calendar mCalendar;
    private Calendar fromCalendar;
    private Calendar toCalendar;

    public DoubleDatePickerDialog(Context context,
            OnDateSetListener callBack) {
        super(context, 2);

        mCallBack = callBack;

        mCalendar = Calendar.getInstance();
        fromCalendar = Calendar.getInstance();
        toCalendar = Calendar.getInstance();

        int year = mCalendar.get(Calendar.YEAR);
        int month = mCalendar.get(Calendar.MONTH);
        int day = mCalendar.get(Calendar.DAY_OF_MONTH);

        setTitle(R.string.call_stats_filter_picker_title);
        setButton(BUTTON_NEGATIVE,
                context.getResources().getString(R.string.call_stats_filter_picker_reset),
                this);
        setButton(BUTTON_POSITIVE,
                context.getResources().getString(R.string.call_stats_filter_picker_done),
                this);
        setIcon(0);

        LayoutInflater inflater =
                (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.double_date_picker_dialog, null);
        setView(view);
        mDatePickerFrom = (DatePicker) view.findViewById(R.id.datePickerFrom);
        mDatePickerFrom.init(year, month, day, this);

        mDatePickerTo = (DatePicker) view.findViewById(R.id.datePickerTo);
        mDatePickerTo.init(year, month, day, this);
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        switch (which) {
            case BUTTON_POSITIVE:
                tryNotifyDateSet();
                break;
            case BUTTON_NEGATIVE:
                resetPickers();
                mCallBack.onDateSet(-1, -1);
                break;
        }
    }

    public void onDateChanged(DatePicker view, int year,
            int month, int day) {
        view.init(year, month, day, this);
    }

    public void resetPickers() {
        mCalendar.setTimeInMillis(System.currentTimeMillis());
        int year = mCalendar.get(Calendar.YEAR);
        int month = mCalendar.get(Calendar.MONTH);
        int day = mCalendar.get(Calendar.DAY_OF_MONTH);
        mDatePickerTo.init(year, month, day, this);
        mDatePickerFrom.init(year, month, day, this);
    }

    private void tryNotifyDateSet() {
        if (mCallBack != null) {
            mDatePickerFrom.clearFocus();
            mDatePickerTo.clearFocus();
            fromCalendar.set(Calendar.YEAR, mDatePickerFrom.getYear());
            fromCalendar.set(Calendar.MONTH, mDatePickerFrom.getMonth());
            fromCalendar.set(Calendar.DAY_OF_MONTH, mDatePickerFrom.getDayOfMonth());
            toCalendar.set(Calendar.YEAR, mDatePickerTo.getYear());
            toCalendar.set(Calendar.MONTH, mDatePickerTo.getMonth());
            toCalendar.set(Calendar.DAY_OF_MONTH, mDatePickerTo.getDayOfMonth());
            setTimes();
            mCallBack.onDateSet(fromCalendar.getTimeInMillis(), toCalendar.getTimeInMillis());
        }
    }

    // to avoid ignoring calls for a simple day
    private void setTimes() {
        fromCalendar.set(Calendar.HOUR_OF_DAY, 0);
        fromCalendar.set(Calendar.MINUTE, 0);
        fromCalendar.set(Calendar.SECOND, 0);
        toCalendar.set(Calendar.HOUR_OF_DAY, 23);
        toCalendar.set(Calendar.MINUTE, 59);
        toCalendar.set(Calendar.SECOND, 59);
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
