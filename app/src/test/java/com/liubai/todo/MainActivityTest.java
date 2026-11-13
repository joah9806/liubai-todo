package com.liubai.todo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
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
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30, qualifiers = "w393dp-h851dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class MainActivityTest {
    private Context app;
    private MainActivity activity;
    private PullToAddLayout root;
    private LinearLayout listView;
    private EditText editor;
    private File screenshotDir;

    @Before
    public void setUp() throws Exception {
        app = RuntimeEnvironment.getApplication();
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
    public void pullDownOpensTheEditorAndTypingAddsATodo() {
        // Mirror the real pull gesture on the empty surface.
        dispatchTouch(root, MotionEvent.ACTION_DOWN, 200, 260);
        dispatchTouch(root, MotionEvent.ACTION_MOVE, 200, 430);
        dispatchTouch(root, MotionEvent.ACTION_MOVE, 200, 600);
        dispatchTouch(root, MotionEvent.ACTION_UP, 200, 600);
        idle(700);
        assertTrue(root.isAddOpen());

        editor.setText("买牛奶");
        editor.onEditorAction(EditorInfo.IME_ACTION_DONE);
        idle(800);
        layoutRoot();

        assertEquals(1, listView.getChildCount());
        TodoRowView row = (TodoRowView) listView.getChildAt(0);
        TextView label = (TextView) row.getChildAt(0);
        assertEquals("买牛奶", label.getText().toString());
        assertEquals(0, label.getPaintFlags() & Paint.STRIKE_THRU_TEXT_FLAG);
        assertEquals(1, new TodoStore(app).load().size());
    }

    @Test
    public void swipeRightCompletesAndMovesToBottom() throws Exception {
        addTodo("买牛奶");
        addTodo("写周报");
        addTodo("预约体检");
        idle(400);
        layoutRoot();
        screenshot("01-active.png");

        assertEquals(3, listView.getChildCount());
        assertEquals("预约体检", textOf(listView.getChildAt(0)));

        // Swipe the top row right, past the completion threshold.
        TodoRowView topRow = (TodoRowView) listView.getChildAt(0);
        int y = topRow.getHeight() / 2;
        dispatchTouch(topRow, MotionEvent.ACTION_DOWN, 60, y);
        dispatchTouch(topRow, MotionEvent.ACTION_MOVE, 220, y);
        dispatchTouch(topRow, MotionEvent.ACTION_MOVE, 460, y);
        screenshot("03-swipe-right.png");
        dispatchTouch(topRow, MotionEvent.ACTION_UP, 460, y);
        idle(1200);
        layoutRoot();

        // The completed row is now last and carries the visual treatment.
        assertEquals(3, listView.getChildCount());
        assertEquals("写周报", textOf(listView.getChildAt(0)));
        assertEquals("买牛奶", textOf(listView.getChildAt(1)));
        assertEquals("预约体检", textOf(listView.getChildAt(2)));
        TodoRowView doneRow = (TodoRowView) listView.getChildAt(2);
        TextView doneLabel = (TextView) doneRow.getChildAt(0);
        assertTrue((doneLabel.getPaintFlags() & Paint.STRIKE_THRU_TEXT_FLAG) != 0);
        assertEquals(Ui.DONE, doneLabel.getCurrentTextColor());

        // Persistence writes unfinished items first and the completed item last.
        List<Todo> stored = new TodoStore(app).load();
        assertEquals(3, stored.size());
        assertFalse(stored.get(0).done);
        assertFalse(stored.get(1).done);
        assertTrue(stored.get(2).done);
        assertEquals("预约体检", stored.get(2).text);

        screenshot("02-completed-at-bottom.png");

        // Relaunching keeps the order and the completed style.
        activity.finish();
        activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        root = (PullToAddLayout) field(activity, "root");
        listView = (LinearLayout) field(activity, "listView");
        root.setPadding(0, dp(24), 0, dp(20));
        layoutRoot();
        idle(300);
        assertEquals(3, listView.getChildCount());
        assertEquals("预约体检", textOf(listView.getChildAt(2)));
        TextView persistedDone = (TextView) ((TodoRowView) listView.getChildAt(2)).getChildAt(0);
        assertTrue((persistedDone.getPaintFlags() & Paint.STRIKE_THRU_TEXT_FLAG) != 0);
    }

    @Test
    public void emptyStateScreenshot() {
        idle(400);
        layoutRoot();
        screenshot("00-empty.png");
        assertTrue(listView.getChildCount() == 0);

        root.openAddPanel();
        idle(500);
        layoutRoot();
        assertTrue(root.isAddOpen());
        screenshot("04-add-panel.png");
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
