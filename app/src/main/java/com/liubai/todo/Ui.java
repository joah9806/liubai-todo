package com.liubai.todo;

import android.content.Context;
import android.util.TypedValue;

final class Ui {
    static final int PAPER = 0xFFFFFFFF;
    static final int INK = 0xFF1B1B1D;
    static final int DONE = 0xFFA2A2A7;
    static final int HINT = 0xFFB4B4B8;
    static final int DIVIDER = 0xFFEAEAEC;
    static final int EMPTY_PRIMARY = 0xFF9E9EA3;
    static final int EMPTY_SECONDARY = 0xFFC5C5C9;
    static final int SWIPE_CHECK = 0xFF7F9782;
    static final int SWIPE_TINT_R = 0xE9;
    static final int SWIPE_TINT_G = 0xF2;
    static final int SWIPE_TINT_B = 0xE9;

    private Ui() {
    }

    static int dp(Context context, float value) {
        float px = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, context.getResources().getDisplayMetrics());
        return Math.max(value > 0f ? 1 : 0, Math.round(px));
    }
}
