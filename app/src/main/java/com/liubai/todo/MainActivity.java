package com.liubai.todo;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;

public class MainActivity extends Activity
        implements PullToAddLayout.Listener, TodoRowView.Listener {

    static final int MAX_TODO_LENGTH = 200;

    private TodoStore store;
    private final ArrayList<Todo> todos = new ArrayList<>();

    private PullToAddLayout root;
    private ScrollView scrollView;
    private LinearLayout listView;
    private LinearLayout emptyBox;
    private AlertDialog deleteAllDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindow();

        store = new TodoStore(this);
        todos.addAll(store.load());
        buildInterface();
        setContentView(root);
        applyWindowInsets();
    }

    private void configureWindow() {
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
            WindowInsetsController controller = window.getDecorView().getWindowInsetsController();
            if (controller != null) {
                int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                int appearance = Ui.isNight(this) ? 0 : mask;
                controller.setSystemBarsAppearance(appearance, mask);
            }
        } else {
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            if (!Ui.isNight(this)) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                        | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            window.getDecorView().setSystemUiVisibility(flags);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
    }

    private void buildInterface() {
        scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setVerticalFadingEdgeEnabled(false);
        scrollView.setClipToPadding(false);

        listView = new LinearLayout(this);
        listView.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(listView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

        root = new PullToAddLayout(this, scrollView);
        root.setListener(this);

        for (Todo todo : todos) {
            listView.addView(makeRow(todo), rowLayoutParams());
        }

        emptyBox = buildEmptyState();
        root.addView(emptyBox, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        EditText editor = root.getEditor();
        editor.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                commitEditor();
                return true;
            }
            if (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    commitEditor();
                }
                return true;
            }
            return false;
        });

        refreshDividers();
        refreshEmptyState();
    }

    private LinearLayout buildEmptyState() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(Ui.dp(this, 32), 0, Ui.dp(this, 32), 0);
        box.setClickable(true);
        box.setFocusable(true);
        box.setContentDescription(getString(R.string.empty_primary));
        box.setOnClickListener(v -> root.openAddPanel());

        TextView primary = new TextView(this);
        primary.setText(R.string.empty_primary);
        primary.setTextColor(getColor(R.color.empty_primary));
        primary.setTextSize(16f);
        primary.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        primary.setGravity(Gravity.CENTER);
        primary.setLetterSpacing(0.04f);
        primary.setIncludeFontPadding(false);
        box.addView(primary, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView secondary = new TextView(this);
        secondary.setText(R.string.empty_secondary);
        secondary.setTextColor(getColor(R.color.empty_secondary));
        secondary.setTextSize(12f);
        secondary.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        secondary.setGravity(Gravity.CENTER);
        secondary.setLetterSpacing(0.04f);
        secondary.setIncludeFontPadding(false);
        LinearLayout.LayoutParams secondaryParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        secondaryParams.topMargin = Ui.dp(this, 10);
        box.addView(secondary, secondaryParams);

        box.setTranslationY(-Ui.dp(this, 36));
        return box;
    }

    private LinearLayout.LayoutParams rowLayoutParams() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private TodoRowView makeRow(Todo todo) {
        TodoRowView row = new TodoRowView(this);
        row.bind(todo);
        row.setListener(this);
        return row;
    }

    private void applyWindowInsets() {
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int top;
            int bottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Insets bars = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                Insets ime = insets.getInsets(WindowInsets.Type.ime());
                top = bars.top;
                bottom = Math.max(bars.bottom, ime.bottom);
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = Math.max(insets.getSystemWindowInsetBottom(), 0);
            }
            if (view.getPaddingTop() != top || view.getPaddingBottom() != bottom) {
                view.setPadding(0, top, 0, bottom);
            }
            return insets;
        });
        root.requestApplyInsets();
    }

    private void refreshDividers() {
        int count = listView.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = listView.getChildAt(i);
            if (child instanceof TodoRowView) {
                ((TodoRowView) child).setDividerVisible(i < count - 1);
            }
        }
    }

    private void refreshEmptyState() {
        if (emptyBox == null) {
            return;
        }
        boolean show = todos.isEmpty() && !root.isAddOpen();
        emptyBox.animate().cancel();
        if (show) {
            if (emptyBox.getVisibility() != View.VISIBLE) {
                emptyBox.setAlpha(0f);
                emptyBox.setVisibility(View.VISIBLE);
                emptyBox.animate().alpha(1f).setDuration(220)
                        .setInterpolator(new DecelerateInterpolator()).start();
            } else {
                emptyBox.animate().alpha(1f).setDuration(140).start();
            }
        } else {
            emptyBox.setVisibility(View.GONE);
            emptyBox.setAlpha(1f);
        }
    }

    private void commitEditor() {
        EditText editor = root.getEditor();
        String text = editor.getText().toString().trim();
        if (!text.isEmpty()) {
            addTodo(text);
        }
        editor.setText("");
        editor.clearFocus();
        root.closeAddPanel();
    }

    private void addTodo(String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        if (trimmed.length() > MAX_TODO_LENGTH) {
            trimmed = trimmed.substring(0, MAX_TODO_LENGTH);
        }

        Todo todo = new Todo(
                nextId(),
                trimmed,
                false,
                System.currentTimeMillis(),
                0L);
        todos.add(0, todo);

        TodoRowView row = makeRow(todo);
        row.setAlpha(0f);
        row.setTranslationY(-Ui.dp(this, 6));
        listView.addView(row, 0, rowLayoutParams());

        refreshDividers();
        store.save(todos);
        refreshEmptyState();

        row.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(240)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        scrollView.post(() -> scrollView.smoothScrollTo(0, 0));
    }

    private long nextId() {
        long id = System.currentTimeMillis();
        boolean collision;
        do {
            collision = false;
            for (Todo todo : todos) {
                if (todo.id == id) {
                    collision = true;
                    id++;
                    break;
                }
            }
        } while (collision);
        return id;
    }

    @Override
    public void onCompleted(Todo todo, TodoRowView row) {
        int index = todos.indexOf(todo);
        if (index < 0) {
            return;
        }

        todos.remove(index);
        todo.done = true;
        todo.completedAt = System.currentTimeMillis();
        todos.add(todo);
        store.save(todos);

        listView.removeView(row);
        row.bind(todo);
        row.setAlpha(0f);
        row.setTranslationY(Ui.dp(this, 8));
        listView.addView(row, rowLayoutParams());

        refreshDividers();
        refreshEmptyState();
        row.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(240)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    @Override
    public void onDelete(Todo todo, TodoRowView row) {
        if (!todo.done) {
            return;
        }
        int index = todos.indexOf(todo);
        if (index < 0) {
            return;
        }
        todos.remove(index);
        store.save(todos);
        listView.removeView(row);
        refreshDividers();
        refreshEmptyState();
    }

    @Override
    public void onDeleteAllCompletedRequested() {
        int count = countCompleted();
        if (count == 0) {
            return;
        }
        if (deleteAllDialog != null && deleteAllDialog.isShowing()) {
            return;
        }

        deleteAllDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.delete_all_completed_title)
                .setMessage(getString(R.string.delete_all_completed_message, count))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(
                        R.string.delete_all_completed_confirm,
                        (dialog, which) -> deleteAllCompleted())
                .create();
        deleteAllDialog.setOnDismissListener(dialog -> deleteAllDialog = null);
        deleteAllDialog.show();
        if (deleteAllDialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
            deleteAllDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setTextColor(getColor(R.color.danger));
        }
    }

    private int countCompleted() {
        int count = 0;
        for (Todo todo : todos) {
            if (todo.done) {
                count++;
            }
        }
        return count;
    }

    private void deleteAllCompleted() {
        for (int i = listView.getChildCount() - 1; i >= 0; i--) {
            View child = listView.getChildAt(i);
            if (child instanceof TodoRowView) {
                Todo todo = ((TodoRowView) child).getTodo();
                if (todo != null && todo.done) {
                    listView.removeViewAt(i);
                }
            }
        }
        for (int i = todos.size() - 1; i >= 0; i--) {
            if (todos.get(i).done) {
                todos.remove(i);
            }
        }
        store.save(todos);
        refreshDividers();
        refreshEmptyState();
    }

    @Override
    public void onAddPanelOpened() {
        emptyBox.animate().cancel();
        emptyBox.animate()
                .alpha(0f)
                .setDuration(100)
                .withEndAction(() -> {
                    if (root.isAddOpen()) {
                        emptyBox.setVisibility(View.GONE);
                    }
                })
                .start();
    }

    @Override
    public void onAddPanelClosed() {
        emptyBox.animate().cancel();
        emptyBox.setAlpha(1f);
        refreshEmptyState();
    }

    @Override
    public void onDismissRequested() {
        commitEditor();
    }

    @Override
    public void onBackPressed() {
        if (root != null && root.isAddOpen()) {
            commitEditor();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (deleteAllDialog != null) {
            deleteAllDialog.dismiss();
            deleteAllDialog = null;
        }
        super.onDestroy();
    }
}
