package com.liubai.todo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.time.Duration;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30, qualifiers = "w393dp-h851dp-night-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class NightModeTest {
    private MainActivity activity;
    private PullToAddLayout root;
    private LinearLayout listView;
    private EditText editor;
    private File screenshotDir;

    @Before
    public void setUp() throws Exception {
        Context app = RuntimeEnvironment.getApplication();
        SharedPreferences preferences = app.getSharedPreferences("todo_store", Context.MODE_PRIVATE);
        preferences.edit().clear().commit();
        activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        root = (PullToAddLayout) field(activity, "root");
        listView = (LinearLayout) field(activity, "listView");
        editor = root.getEditor();
        screenshotDir = new File(System.getProperty("user.dir"), "build/screenshots");
        if (!screenshotDir.exists() && !screenshotDir.mkdirs()) {
            throw new IllegalStateException("Cannot create screenshot dir: " + screenshotDir);
        }
        root.setPadding(0, dp(24), 0, dp(20));
        layoutRoot();
        idle(300);
    }

    @Test
    public void nightModeUsesDarkPaletteAndCompletedStyle() throws Exception {
        assertTrue(Ui.isNight(activity));
        int paper = activity.getColor(R.color.paper);
        int ink = activity.getColor(R.color.ink);
        assertTrue(Color.red(paper) < 40);
        assertTrue(Color.red(ink) > 200);
        assertTrue(root.getBackground() instanceof ColorDrawable);
        assertEquals(paper, ((ColorDrawable) root.getBackground()).getColor());
        screenshot("dark-00-empty.png");

        addTodo("整理这周的项目笔记");
        addTodo("给妈妈打电话");
        addTodo("预约周四的牙医");
        layoutRoot();
        idle(350);
        screenshot("dark-01-list.png");

        TodoRowView topRow = (TodoRowView) listView.getChildAt(0);
        int y = topRow.getHeight() / 2;
        dispatchTouch(topRow, MotionEvent.ACTION_DOWN, 60, y);
        dispatchTouch(topRow, MotionEvent.ACTION_MOVE, 220, y);
        dispatchTouch(topRow, MotionEvent.ACTION_MOVE, 460, y);
        dispatchTouch(topRow, MotionEvent.ACTION_UP, 460, y);
        idle(1200);
        layoutRoot();

        assertEquals(3, listView.getChildCount());
        assertEquals("预约周四的牙医", textOf(listView.getChildAt(2)));
        TextView doneLabel = (TextView) ((TodoRowView) listView.getChildAt(2)).getChildAt(0);
        assertEquals(activity.getColor(R.color.done), doneLabel.getCurrentTextColor());
        assertTrue((doneLabel.getPaintFlags() & Paint.STRIKE_THRU_TEXT_FLAG) != 0);
        assertFalse(doneLabel.getCurrentTextColor() == ink);
        screenshot("dark-02-completed.png");
    }

    private void addTodo(String text) {
        root.openAddPanel();
        idle(500);
        editor.setText(text);
        editor.onEditorAction(EditorInfo.IME_ACTION_DONE);
        idle(600);
        layoutRoot();
    }

    private void layoutRoot() {
        DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        int width = metrics.widthPixels;
        int height = metrics.heightPixels;
        root.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, width, height);
    }

    private String textOf(View row) {
        return ((TextView) ((TodoRowView) row).getChildAt(0)).getText().toString();
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private void idle(long millis) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis));
    }

    private void screenshot(String name) {
        layoutRoot();
        idle(80);
        Bitmap bitmap = Bitmap.createBitmap(
                root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        root.draw(canvas);
        try (FileOutputStream stream = new FileOutputStream(new File(screenshotDir, name))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        } catch (Exception error) {
            throw new RuntimeException(error);
        } finally {
            bitmap.recycle();
        }
        System.out.println("SCREENSHOT " + new File(screenshotDir, name).getAbsolutePath());
    }

    private static void dispatchTouch(View view, int action, float x, float y) {
        long now = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(now, now, action, x, y, 0);
        event.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
        view.dispatchTouchEvent(event);
        event.recycle();
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
