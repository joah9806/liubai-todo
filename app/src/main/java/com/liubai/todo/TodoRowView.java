package com.liubai.todo;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * One content row. Unfinished rows use a right swipe to complete; completed rows
 * use a right swipe to delete. Long-pressing a completed row asks the activity
 * to delete all completed rows after confirmation.
 */
public class TodoRowView extends FrameLayout {
    public interface Listener {
        void onCompleted(Todo todo, TodoRowView row);

        void onDelete(Todo todo, TodoRowView row);

        void onDeleteAllCompletedRequested();

        void onEditRequested(Todo todo, TodoRowView row);
    }

    private final TextView label;
    private final Paint tintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint feedbackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dividerPaint = new Paint();
    private final Path feedbackPath = new Path();
    private final int inkColor;
    private final int doneColor;
    private final int swipeTintColor;
    private final int swipeCheckColor;
    private final int swipeDeleteTintColor;
    private final int swipeDeleteColor;
    private final int touchSlop;

    private Todo todo;
    private boolean done;
    private boolean dividerVisible = true;
    private boolean tracking;
    private boolean horizontal;
    private boolean completing;
    private boolean thresholdNotified;
    private boolean longPressFired;
    private float downX;
    private float downY;
    private float swipe;
    private ValueAnimator swipeAnimator;
    private Listener listener;

    private final Runnable longPressRunnable = () -> {
        if (!done || !tracking || horizontal || swipe > 0f) {
            return;
        }
        tracking = false;
        longPressFired = true;
        cancelPendingLongPress();
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        if (listener != null) {
            listener.onDeleteAllCompletedRequested();
        }
    };

    public TodoRowView(Context context) {
        super(context);
        setWillNotDraw(false);
        inkColor = context.getColor(R.color.ink);
        doneColor = context.getColor(R.color.done);
        swipeTintColor = context.getColor(R.color.swipe_tint);
        swipeCheckColor = context.getColor(R.color.swipe_check);
        swipeDeleteTintColor = context.getColor(R.color.swipe_delete_tint);
        swipeDeleteColor = context.getColor(R.color.swipe_delete);
        setBackgroundColor(context.getColor(R.color.paper));
        setFocusable(false);
        setOnClickListener(view -> {
            if (listener != null && todo != null) {
                listener.onEditRequested(todo, this);
            }
        });

        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        label = new TextView(context);
        label.setTextColor(inkColor);
        label.setTextSize(17f);
        label.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        label.setLineSpacing(Ui.dp(context, 3), 1f);
        label.setIncludeFontPadding(false);
        label.setPadding(
                Ui.dp(context, 26),
                Ui.dp(context, 20),
                Ui.dp(context, 26),
                Ui.dp(context, 20));
        label.setGravity(android.view.Gravity.START);
        addView(label, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        setMinimumHeight(Ui.dp(context, 64));

        feedbackPaint.setStyle(Paint.Style.STROKE);
        feedbackPaint.setStrokeWidth(Ui.dp(context, 2.2f));
        feedbackPaint.setStrokeCap(Paint.Cap.ROUND);
        feedbackPaint.setStrokeJoin(Paint.Join.ROUND);

        dividerPaint.setColor(context.getColor(R.color.divider));
        dividerPaint.setStrokeWidth(Math.max(1f, Ui.dp(context, 0.75f)));
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public Todo getTodo() {
        return todo;
    }

    public void bind(Todo todo) {
        this.todo = todo;
        this.done = todo.done;
        resetSwipe();

        label.setText(todo.text);
        if (todo.done) {
            label.setTextColor(doneColor);
            label.setPaintFlags(label.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            label.setContentDescription(
                    getContext().getString(R.string.a11y_todo_done, todo.text));
        } else {
            label.setTextColor(inkColor);
            label.setPaintFlags(label.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            label.setContentDescription(
                    getContext().getString(R.string.a11y_todo_active, todo.text));
        }
        invalidate();
    }

    public void setDividerVisible(boolean visible) {
        if (dividerVisible != visible) {
            dividerVisible = visible;
            invalidate();
        }
    }

    public void resetSwipe() {
        cancelPendingLongPress();
        if (swipeAnimator != null) {
            swipeAnimator.cancel();
            swipeAnimator = null;
        }
        swipe = 0f;
        tracking = false;
        horizontal = false;
        completing = false;
        thresholdNotified = false;
        longPressFired = false;
        label.setTranslationX(0f);
        label.setAlpha(1f);
        invalidate();
    }

    private void cancelPendingLongPress() {
        removeCallbacks(longPressRunnable);
    }

    private float actionThreshold() {
        return Math.max(Ui.dp(getContext(), 64), getWidth() * 0.28f);
    }

    private void setSwipe(float raw) {
        float value = Math.max(0f, raw);
        float softLimit = getWidth() * 0.55f;
        if (value > softLimit) {
            value = softLimit + (value - softLimit) * 0.3f;
        }
        swipe = value;
        label.setTranslationX(value);
        float threshold = actionThreshold();
        if (!thresholdNotified && value >= threshold) {
            thresholdNotified = true;
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (swipe <= 0f) {
            return;
        }
        float width = Math.max(1f, getWidth());
        float progress = Math.min(1f, swipe / Math.max(1f, width * 0.3f));
        int tintColor = done ? swipeDeleteTintColor : swipeTintColor;
        tintPaint.setColor(Color.argb(
                (int) (230 * progress),
                Color.red(tintColor),
                Color.green(tintColor),
                Color.blue(tintColor)));
        canvas.drawRect(0f, 0f, width, getHeight(), tintPaint);

        feedbackPaint.setColor(done ? swipeDeleteColor : swipeCheckColor);
        feedbackPaint.setAlpha((int) (255 * Math.min(1f, progress * 1.6f)));
        float centerX = Ui.dp(getContext(), 28);
        float centerY = getHeight() / 2f;
        float unit = Ui.dp(getContext(), 1);
        feedbackPath.reset();
        if (done) {
            feedbackPath.moveTo(centerX - 5 * unit, centerY - 5 * unit);
            feedbackPath.lineTo(centerX + 5 * unit, centerY + 5 * unit);
            feedbackPath.moveTo(centerX + 5 * unit, centerY - 5 * unit);
            feedbackPath.lineTo(centerX - 5 * unit, centerY + 5 * unit);
        } else {
            feedbackPath.moveTo(centerX - 6 * unit, centerY);
            feedbackPath.lineTo(centerX - 1.5f * unit, centerY + 5 * unit);
            feedbackPath.lineTo(centerX + 7 * unit, centerY - 5 * unit);
        }
        canvas.drawPath(feedbackPath, feedbackPaint);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (dividerVisible) {
            float halfStroke = dividerPaint.getStrokeWidth() / 2f;
            float y = getHeight() - halfStroke;
            canvas.drawLine(0f, y, getWidth(), y, dividerPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            cancelSwipeAnimation();
            if (swipe != 0f) {
                swipe = 0f;
                label.setTranslationX(0f);
                invalidate();
            }
            label.setAlpha(1f);
            tracking = true;
            horizontal = false;
            longPressFired = false;
            thresholdNotified = false;
            downX = event.getX();
            downY = event.getY();
            if (done) {
                removeCallbacks(longPressRunnable);
                postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout());
            }
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            if (!tracking) {
                return false;
            }
            float dx = event.getX() - downX;
            float dy = event.getY() - downY;
            if (!horizontal) {
                if (Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy)) {
                    cancelPendingLongPress();
                    horizontal = true;
                    ViewParent parent = getParent();
                    if (parent != null) {
                        parent.requestDisallowInterceptTouchEvent(true);
                    }
                } else if (Math.abs(dy) > touchSlop && Math.abs(dy) > Math.abs(dx)) {
                    cancelPendingLongPress();
                    tracking = false;
                    return false;
                }
            }
            if (horizontal) {
                setSwipe(dx);
                return true;
            }
            return true;
        }
        if (action == MotionEvent.ACTION_UP) {
            cancelPendingLongPress();
            if (!tracking) {
                return longPressFired;
            }
            tracking = false;
            if (horizontal) {
                if (swipe >= actionThreshold()) {
                    animateSwipeAction();
                } else {
                    springBack();
                }
            } else if (!longPressFired) {
                performClick();
            }
            return true;
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            cancelPendingLongPress();
            tracking = false;
            longPressFired = false;
            if (horizontal) {
                springBack();
            }
            return true;
        }
        return true;
    }

    private void cancelSwipeAnimation() {
        if (swipeAnimator != null) {
            swipeAnimator.cancel();
            swipeAnimator = null;
        }
    }

    private void springBack() {
        cancelSwipeAnimation();
        final float from = swipe;
        final ValueAnimator animator = ValueAnimator.ofFloat(from, 0f);
        animator.setDuration(240);
        animator.setInterpolator(new OvershootInterpolator(1.1f));
        animator.addUpdateListener(animation -> setSwipe((float) animation.getAnimatedValue()));
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (swipeAnimator == animation) {
                    swipeAnimator = null;
                }
                setSwipe(0f);
                label.setAlpha(1f);
            }
        });
        swipeAnimator = animator;
        animator.start();
    }

    private void animateSwipeAction() {
        if (completing) {
            return;
        }
        completing = true;
        cancelSwipeAnimation();
        final boolean delete = done;
        final float from = swipe;
        final float to = Math.max(getWidth() + Ui.dp(getContext(), 24), from + 1f);
        final ValueAnimator animator = ValueAnimator.ofFloat(from, to);
        animator.setDuration(170);
        animator.setInterpolator(new DecelerateInterpolator(1.6f));
        animator.addUpdateListener(animation -> {
            float value = (float) animation.getAnimatedValue();
            setSwipe(value);
            float fade = 1f - Math.min(1f, (value - from) / Math.max(1f, to - from));
            label.setAlpha(fade);
        });
        animator.addListener(new AnimatorListenerAdapter() {
            private boolean canceled;

            @Override
            public void onAnimationCancel(Animator animation) {
                canceled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (swipeAnimator == animation) {
                    swipeAnimator = null;
                }
                completing = false;
                if (canceled) {
                    resetSwipe();
                    return;
                }
                if (listener != null && todo != null) {
                    if (delete) {
                        listener.onDelete(todo, TodoRowView.this);
                    } else {
                        listener.onCompleted(todo, TodoRowView.this);
                    }
                } else {
                    resetSwipe();
                }
            }
        });
        swipeAnimator = animator;
        animator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelPendingLongPress();
        cancelSwipeAnimation();
        super.onDetachedFromWindow();
    }
}
