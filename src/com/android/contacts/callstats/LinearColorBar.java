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
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.widget.LinearLayout;

public class LinearColorBar extends LinearLayout {
    static int BLUE_COLOR = 0xff33b5e5;
    static int GREEN_COLOR = 0xff99cc00;
    static int BG_COLOR = 0x88888888;
    static int RED_COLOR = 0xffff4444;

    private float mFirstRatio;
    private float mSecondRatio;
    private float mThirdRatio;

    private boolean mShowingThird;
    private boolean mUseRed;

    final Rect mRect = new Rect();
    final Paint mPaint = new Paint();

    int mLastInterestingLeft, mLastInterestingRight;
    int mLineWidth;

    final Path mColorPath = new Path();
    final Path mEdgePath = new Path();
    final Paint mColorGradientPaint = new Paint();
    final Paint mEdgeGradientPaint = new Paint();

    public LinearColorBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
        mPaint.setStyle(Paint.Style.FILL);
        mColorGradientPaint.setStyle(Paint.Style.FILL);
        mColorGradientPaint.setAntiAlias(true);
        mEdgeGradientPaint.setStyle(Paint.Style.STROKE);
        mLineWidth = getResources().getDisplayMetrics().densityDpi >= DisplayMetrics.DENSITY_HIGH
                ? 2 : 1;
        mEdgeGradientPaint.setStrokeWidth(mLineWidth);
        mEdgeGradientPaint.setAntiAlias(true);
    }

    public void redIsTheNewBlue(boolean use) {
        mUseRed = use;
    }

    public void setRatios(float red, float yellow, float green) {
        mFirstRatio = red;
        mSecondRatio = yellow;
        mThirdRatio = green;
        invalidate();
    }

    public void setShowingThird(boolean showingThird) {
        if (mShowingThird != showingThird) {
            mShowingThird = showingThird;
            updateIndicator();
            invalidate();
        }
    }

    private void updateIndicator() {
        int off = getPaddingTop() - getPaddingBottom();
        if (off < 0)
            off = 0;
        mRect.top = off;
        mRect.bottom = getHeight();
        if (mShowingThird) {
            mColorGradientPaint.setShader(new LinearGradient(
                    0, 0, 0, off - 2, BG_COLOR & 0xffffff, BG_COLOR, Shader.TileMode.CLAMP));
        } else {
            mColorGradientPaint.setShader(new LinearGradient(
                    0, 0, 0, off - 2, GREEN_COLOR & 0xffffff, GREEN_COLOR, Shader.TileMode.CLAMP));
        }
        mEdgeGradientPaint.setShader(new LinearGradient(
                0, 0, 0, off / 2, 0x00a0a0a0, 0xffa0a0a0, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateIndicator();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();

        int left = 0;

        int right = left + (int) (width * mFirstRatio);
        int right2 = right + (int) (width * mSecondRatio);
        int right3 = right2 + (int) (width * mThirdRatio);

        int indicatorLeft, indicatorRight;
        if (mShowingThird) {
            indicatorLeft = right2;
            indicatorRight = right3;
        } else {
            indicatorLeft = right;
            indicatorRight = right2;
        }

        if (mLastInterestingLeft != indicatorLeft || mLastInterestingRight != indicatorRight) {
            mColorPath.reset();
            mEdgePath.reset();
            if (indicatorLeft < indicatorRight) {
                final int midTopY = mRect.top;
                final int midBottomY = 0;
                final int xoff = 2;
                mColorPath.moveTo(indicatorLeft, mRect.top);
                mColorPath.cubicTo(indicatorLeft, midBottomY,
                        -xoff, midTopY,
                        -xoff, 0);
                mColorPath.lineTo(width + xoff - 1, 0);
                mColorPath.cubicTo(width + xoff - 1, midTopY,
                        indicatorRight, midBottomY,
                        indicatorRight, mRect.top);
                mColorPath.close();
                final float lineOffset = mLineWidth + .5f;
                mEdgePath.moveTo(-xoff + lineOffset, 0);
                mEdgePath.cubicTo(-xoff + lineOffset, midTopY,
                        indicatorLeft + lineOffset, midBottomY,
                        indicatorLeft + lineOffset, mRect.top);
                mEdgePath.moveTo(width + xoff - 1 - lineOffset, 0);
                mEdgePath.cubicTo(width + xoff - 1 - lineOffset, midTopY,
                        indicatorRight - lineOffset, midBottomY,
                        indicatorRight - lineOffset, mRect.top);
            }
            mLastInterestingLeft = indicatorLeft;
            mLastInterestingRight = indicatorRight;
        }

        if (!mEdgePath.isEmpty()) {
            canvas.drawPath(mEdgePath, mEdgeGradientPaint);
            canvas.drawPath(mColorPath, mColorGradientPaint);
        }

        if (left < right) {
            mRect.left = left;
            mRect.right = right;
            mPaint.setColor(mUseRed ? RED_COLOR : BLUE_COLOR);
            canvas.drawRect(mRect, mPaint);
            width -= (right - left);
            left = right;
        }

        right = right2;

        if (left < right) {
            mRect.left = left;
            mRect.right = right;
            mPaint.setColor(GREEN_COLOR);
            canvas.drawRect(mRect, mPaint);
            width -= (right - left);
            left = right;
        }

        right = left + width;
        if (left < right) {
            mRect.left = left;
            mRect.right = right;
            mPaint.setColor(BG_COLOR);
            canvas.drawRect(mRect, mPaint);
        }
    }
}
