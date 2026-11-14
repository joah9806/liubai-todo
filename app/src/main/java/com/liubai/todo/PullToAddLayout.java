package com.liubai.todo;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Typeface;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

/**
 * A quiet pull-to-add surface. When the list is at the top, a downward drag
 * uncovers a single-line editor. Releasing past the threshold opens it and
 * raises the keyboard.
 */
public class PullToAddLayout extends FrameLayout {
    public interface Listener {
        void onAddPanelOpened();

        void onAddPanelClosed();

        void onDismissRequested();
    }

    private static final int PANEL_HEIGHT_DP = 76;
    private static final int OPEN_THRESHOLD_DP = 46;
    private static final int MAX_EXTRA_PULL_DP = 28;

    private final LinearLayout column;
    private final FrameLayout addPanel;
    private final EditText editor;
    private final ScrollView scrollView;
    private final int panelHeight;
    private final int openThreshold;
    private final int maxPullHeight;
    private final int touchSlop;

    private Listener listener;
    private ValueAnimator headerAnimator;
    private int headerHeight;
    private boolean addOpen;
    private boolean settling;
    private boolean pullCandidate;
    private boolean pulling;
    private float downX;
    private float downY;

    public PullToAddLayout(Context context, ScrollView scrollView) {
        super(context);
        this.scrollView = scrollView;
        setBackgroundColor(context.getColor(R.color.paper));

        panelHeight = Ui.dp(context, PANEL_HEIGHT_DP);
        openThreshold = Ui.dp(context, OPEN_THRESHOLD_DP);
        maxPullHeight = panelHeight + Ui.dp(context, MAX_EXTRA_PULL_DP);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        addView(column, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        addPanel = new FrameLayout(context);
        addPanel.setBackgroundColor(context.getColor(R.color.paper));
        addPanel.setClipChildren(true);
        addPanel.setVisibility(GONE);
        addPanel.setClickable(true);
        addPanel.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0));

        editor = new EditText(context);
        editor.setBackground(null);
        editor.setHint(R.string.add_hint);
        editor.setHintTextColor(context.getColor(R.color.hint));
        editor.setTextColor(context.getColor(R.color.ink));
        editor.setTextSize(18f);
        editor.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        editor.setGravity(Gravity.CENTER_VERTICAL);
        editor.setSingleLine(true);
        editor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        editor.setImeOptions(EditorInfo.IME_ACTION_DONE);
        editor.setFilters(new InputFilter[]{
                new InputFilter.LengthFilter(MainActivity.MAX_TODO_LENGTH)
        });
        editor.setPadding(Ui.dp(context, 26), 0, Ui.dp(context, 26), 0);
        editor.setIncludeFontPadding(false);
        editor.setContentDescription(context.getString(R.string.a11y_new_todo));
        addPanel.addView(editor, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        addPanel.setOnClickListener(v -> {
            if (addOpen) {
                editor.requestFocus();
                showIme();
            }
        });

        View divider = new View(context);
        divider.setBackgroundColor(context.getColor(R.color.divider));
        addPanel.addView(divider, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                Math.max(1, Ui.dp(context, 0.75f)),
                Gravity.BOTTOM));

        column.addView(addPanel);
        column.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public EditText getEditor() {
        return editor;
    }

    public boolean isAddOpen() {
        return addOpen;
    }

    public void openAddPanel() {
        openAddPanel(true);
    }

    public void openAddPanel(boolean scrollToTop) {
        if (addOpen || settling) {
            return;
        }
        if (scrollToTop && scrollView.getScrollY() != 0) {
            scrollView.smoothScrollTo(0, 0);
        }
        addOpen = true;
        addPanel.setVisibility(VISIBLE);
        if (headerHeight <= 0) {
            setHeaderHeight(0);
        }
        animateHeaderTo(panelHeight, true);
    }

    public void closeAddPanel() {
        if (!addOpen && headerHeight == 0) {
            return;
        }
        if (!addOpen && settling) {
            return;
        }
        addOpen = false;
        editor.clearFocus();
        hideIme();
        animateHeaderTo(0, false);
    }

    private void animateHeaderTo(final int target, final boolean opening) {
        if (headerAnimator != null) {
            headerAnimator.cancel();
            headerAnimator = null;
        }
        settling = true;
        int from = headerHeight;
        if (from == target) {
            completeTransform(target, opening);
            return;
        }

        final ValueAnimator animator = ValueAnimator.ofInt(from, target);
        animator.setDuration(opening ? 230 : 190);
        animator.setInterpolator(new DecelerateInterpolator(1.4f));
        animator.addUpdateListener(animation -> setHeaderHeight((int) animation.getAnimatedValue()));
        animator.addListener(new AnimatorListenerAdapter() {
            private boolean canceled;

            @Override
            public void onAnimationCancel(Animator animation) {
                canceled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (headerAnimator == animation) {
                    headerAnimator = null;
                }
                if (canceled) {
                    return;
                }
                completeTransform(target, opening);
            }
        });
        headerAnimator = animator;
        animator.start();
    }

    private void completeTransform(int target, boolean opening) {
        settling = false;
        setHeaderHeight(target);
        if (opening) {
            if (!addOpen) {
                return;
            }
            editor.setAlpha(1f);
            editor.setTranslationY(0f);
            editor.requestFocus();
            postDelayed(() -> {
                if (addOpen) {
                    showIme();
                }
            }, 60);
            if (listener != null) {
                listener.onAddPanelOpened();
            }
        } else {
            if (addOpen) {
                return;
            }
            addPanel.setVisibility(GONE);
            if (listener != null) {
                listener.onAddPanelClosed();
            }
        }
    }

    private void setHeaderHeight(int height) {
        int clamped = Math.max(0, Math.min(height, maxPullHeight));
        headerHeight = clamped;
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) addPanel.getLayoutParams();
        if (params.height != clamped) {
            params.height = clamped;
            addPanel.setLayoutParams(params);
        }
        if (clamped > 0 || addOpen) {
            if (addPanel.getVisibility() != VISIBLE) {
                addPanel.setVisibility(VISIBLE);
            }
        } else if (!settling) {
            addPanel.setVisibility(GONE);
        }
        float progress = Math.min(1f, clamped / (float) Math.max(1, panelHeight));
        editor.setAlpha(0.25f + 0.75f * progress);
        editor.setTranslationY(Ui.dp(getContext(), (1f - progress) * 5f));
    }

    private void updatePullHeight(float y) {
        float raw = Math.max(0f, y - downY - touchSlop);
        float height;
        if (raw <= panelHeight) {
            height = raw;
        } else {
            height = panelHeight + (raw - panelHeight) * 0.3f;
        }
        setHeaderHeight((int) Math.min(maxPullHeight, height));
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (addOpen || settling) {
            return false;
        }
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            downX = event.getX();
            downY = event.getY();
            pulling = false;
            pullCandidate = headerHeight == 0 && scrollView.getScrollY() == 0;
            return false;
        }
        if (action == MotionEvent.ACTION_MOVE && pullCandidate) {
            float dy = event.getY() - downY;
            float dx = event.getX() - downX;
            if (dy > touchSlop && dy > Math.abs(dx) * 1.15f) {
                pullCandidate = false;
                pulling = true;
                addPanel.setVisibility(VISIBLE);
                updatePullHeight(event.getY());
                return true;
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            pullCandidate = false;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_MOVE && pulling) {
            updatePullHeight(event.getY());
            return true;
        }
        if (action == MotionEvent.ACTION_UP && pulling) {
            pulling = false;
            if (headerHeight >= openThreshold) {
                addOpen = true;
                animateHeaderTo(panelHeight, true);
            } else {
                animateHeaderTo(0, false);
            }
            return true;
        }
        if (action == MotionEvent.ACTION_CANCEL && pulling) {
            pulling = false;
            animateHeaderTo(0, false);
            return true;
        }
        return pulling;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (addOpen && event.getActionMasked() == MotionEvent.ACTION_DOWN
                && !isInsideAddPanel(event.getX(), event.getY())) {
            if (listener != null) {
                listener.onDismissRequested();
                return true;
            }
        }
        return super.dispatchTouchEvent(event);
    }

    private boolean isInsideAddPanel(float x, float y) {
        return x >= addPanel.getLeft() && x <= addPanel.getRight()
                && y >= addPanel.getTop() && y <= addPanel.getBottom();
    }

    public void showIme() {
        if (!isAttachedToWindow()) {
            return;
        }
        InputMethodManager manager =
                (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null) {
            manager.showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void hideIme() {
        InputMethodManager manager =
                (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null) {
            manager.hideSoftInputFromWindow(editor.getWindowToken(), 0);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (headerAnimator != null) {
            headerAnimator.cancel();
            headerAnimator = null;
        }
        hideIme();
        super.onDetachedFromWindow();
    }
}
