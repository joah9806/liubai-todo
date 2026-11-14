package com.liubai.todo;

import android.content.Context;
import android.content.res.Configuration;
import android.util.TypedValue;

final class Ui {
    private Ui() {
    }

    static int dp(Context context, float value) {
        float px = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, context.getResources().getDisplayMetrics());
        return Math.max(value > 0f ? 1 : 0, Math.round(px));
    }

    static boolean isNight(Context context) {
        int mode = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }
}
