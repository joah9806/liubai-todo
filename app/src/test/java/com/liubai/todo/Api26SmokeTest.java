package com.liubai.todo;

import static org.junit.Assert.assertEquals;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Looper;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;
import java.time.Duration;

/**
 * Verifies the older edge-to-edge/inset code path on the app's minimum API.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 26, qualifiers = "w360dp-h740dp-xhdpi")
public class Api26SmokeTest {
    @Test
    public void launchesAndAddsOnApi26() throws Exception {
        Context app = RuntimeEnvironment.getApplication();
        app.getSharedPreferences("todo_store", Context.MODE_PRIVATE).edit().clear().commit();

        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        PullToAddLayout root = (PullToAddLayout) field(activity, "root");
        LinearLayout listView = (LinearLayout) field(activity, "listView");

        root.openAddPanel();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500));
        root.getEditor().setText("API 26");
        root.getEditor().onEditorAction(EditorInfo.IME_ACTION_DONE);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(700));

        assertEquals(1, listView.getChildCount());
        assertEquals("API 26",
                ((TextView) ((TodoRowView) listView.getChildAt(0)).getChildAt(0))
                        .getText().toString());
    }

    private static Object field(Object target, String name) throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
