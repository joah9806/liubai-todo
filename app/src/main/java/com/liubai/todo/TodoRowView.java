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
 * One content row. Swiping right pulls the text aside, revealing a faint check,
 * and completing the gesture hands the row back to the activity so it can be
 * appended to the completed section.
 */
public class TodoRowView extends FrameLayout {
    public interface OnCompleteListener {
        void onCompleted(Todo todo, TodoRowView row);
    }

    private final TextView label;
    private final Paint tintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint checkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dividerPaint = new Paint();
    private final Path checkPath = new Path();
    private final int touchSlop;

    private Todo todo;
    private boolean done;
    private boolean dividerVisible = true;
    private boolean tracking;
    private boolean horizontal;
    private boolean completing;
    private boolean thresholdNotified;
    private float downX;
    private float downY;
    private float swipe;
    private ValueAnimator swipeAnimator;
    private OnCompleteListener completeListener;

    public TodoRowView(Context context) {
        super(context);
        setWillNotDraw(false);
        setBackgroundColor(Ui.PAPER);
        setFocusable(false);
        setClickable(false);

        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        label = new TextView(context);
        label.setTextColor(Ui.INK);
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

        checkPaint.setColor(Ui.SWIPE_CHECK);
        checkPaint.setStyle(Paint.Style.STROKE);
        checkPaint.setStrokeWidth(Ui.dp(context, 2.2f));
        checkPaint.setStrokeCap(Paint.Cap.ROUND);
        checkPaint.setStrokeJoin(Paint.Join.ROUND);

        dividerPaint.setColor(Ui.DIVIDER);
        dividerPaint.setStrokeWidth(Math.max(1f, Ui.dp(context, 0.75f)));
    }

    public void setOnCompleteListener(OnCompleteListener listener) {
        completeListener = listener;
    }

    public void bind(Todo todo) {
        this.todo = todo;
        this.done = todo.done;
        resetSwipe();

        label.setText(todo.text);
        if (todo.done) {
            label.setTextColor(Ui.DONE);
            label.setPaintFlags(label.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            label.setContentDescription(
                    getContext().getString(R.string.a11y_todo_done, todo.text));
        } else {
            label.setTextColor(Ui.INK);
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
        if (swipeAnimator != null) {
            swipeAnimator.cancel();
            swipeAnimator = null;
        }
        swipe = 0f;
        tracking = false;
        horizontal = false;
        completing = false;
        thresholdNotified = false;
        label.setTranslationX(0f);
        label.setAlpha(1f);
        invalidate();
    }

    private float completeThreshold() {
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
        float threshold = completeThreshold();
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
        tintPaint.setColor(Color.argb(
                (int) (230 * progress),
                Ui.SWIPE_TINT_R,
                Ui.SWIPE_TINT_G,
                Ui.SWIPE_TINT_B));
        canvas.drawRect(0f, 0f, width, getHeight(), tintPaint);

        checkPaint.setAlpha((int) (255 * Math.min(1f, progress * 1.6f)));
        float centerX = Ui.dp(getContext(), 28);
        float centerY = getHeight() / 2f;
        float unit = Ui.dp(getContext(), 1);
        checkPath.reset();
        checkPath.moveTo(centerX - 6 * unit, centerY);
        checkPath.lineTo(centerX - 1.5f * unit, centerY + 5 * unit);
        checkPath.lineTo(centerX + 7 * unit, centerY - 5 * unit);
        canvas.drawPath(checkPath, checkPaint);
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
        if (done) {
            return false;
        }
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
            thresholdNotified = false;
            downX = event.getX();
            downY = event.getY();
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
                    horizontal = true;
                    ViewParent parent = getParent();
                    if (parent != null) {
                        parent.requestDisallowInterceptTouchEvent(true);
                    }
                } else if (Math.abs(dy) > touchSlop && Math.abs(dy) > Math.abs(dx)) {
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
            if (!tracking) {
                return false;
            }
            tracking = false;
            if (horizontal) {
                if (swipe >= completeThreshold()) {
                    animateComplete();
                } else {
                    springBack();
                }
            }
            return true;
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            tracking = false;
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

    private void animateComplete() {
        if (completing) {
            return;
        }
        completing = true;
        cancelSwipeAnimation();
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
                if (completeListener != null && todo != null) {
                    completeListener.onCompleted(todo, TodoRowView.this);
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
        cancelSwipeAnimation();
        super.onDetachedFromWindow();
    }
}
