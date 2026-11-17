package com.newtube.mobile.ui.playback;

import android.annotation.SuppressLint;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;

import androidx.annotation.Nullable;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;

import com.newtube.mobile.ui.common.Haptics;
import com.newtube.mobile.ui.common.MagneticDrag;

/**
 * NEWTUBE(motion): swipe the docked mini card sideways to close it, like YouTube's. The card
 * fades with the distance; let go past the point where it lets go (or flick it) and it flies off
 * that side, keeping the finger's speed, and the host closes the session; otherwise it springs
 * back. Taps are untouched: the swipe only claims a touch once it is clearly sideways, and then
 * cancels the card's pending click so the release does not also expand it.
 *
 * <p>NEWTUBE(haptics): it swipes like a notification on the Pixel (MagneticDrag): stuck at half
 * the finger's distance with a faint grain, then a click at 72 dp - 40% of the card, the old
 * commit point - where it catches up with the finger; back under 56 dp it sticks again, with the
 * same click. The spring back has SystemUI's small bounce.</p>
 *
 * <p>NEWTUBE(mini-corner): any other drag - up, down, or sideways away from the screen edge the
 * card is docked on - moves the card instead. It follows the finger and, let go, springs to the
 * corner it was heading for (MiniCardCorner), which every screen then shows it in. Sideways
 * toward that edge still swipes it away.</p>
 *
 * <p>Shared by Home's card (MobileBrowseActivity) and the Search/Channel one
 * (MobileMiniPlayerController); each host does its own closing in {@link Callback}.</p>
 */
public final class MiniCardSwipe implements View.OnTouchListener {
    public interface Callback {
        /** False while the card must not move (closing, entering, no session). */
        boolean canSwipe();

        /** Committed: fly the card to {@code toTranslationX} over {@code durationMs}, then close. */
        void onSwipedAway(float toTranslationX, long durationMs);
    }

    private static final float FLING_VELOCITY_DP = 450f;
    /** SystemUI's snap-back spring for a notification that did not go. */
    private static final float SNAP_BACK_STIFFNESS = 550f;
    private static final float SNAP_BACK_DAMPING = 0.6f;
    private static final long FLY_MIN_MS = 110;
    private static final long FLY_MAX_MS = 240;
    /** How far ahead (s) a released card's speed is projected to pick its corner. */
    private static final float MOVE_PROJECTION_S = 0.15f;
    private static final float MOVE_STIFFNESS = 600f;
    private static final float MOVE_DAMPING = 0.8f;

    private final View mCard;
    private final Callback mCallback;
    private final int mTouchSlop;
    private final float mFlingVelocity;
    private final MagneticDrag mMagnet;
    @Nullable
    private SpringAnimation mSnapBack;
    private float mDownRawX;
    private float mDownRawY;
    private boolean mDragging;
    /** NEWTUBE(mini-corner): the touch is moving the card to another corner. */
    private boolean mMoving;
    private float mMoveStartX;
    private float mMoveStartY;
    @Nullable
    private SpringAnimation mMoveX;
    @Nullable
    private SpringAnimation mMoveY;
    /** The corner a released card is springing to; committed once it lands. */
    private int mMoveTarget = NO_CORNER;
    private static final int NO_CORNER = -1;
    /** The touch stopped being a swipe (a second finger): consume it to the end. */
    private boolean mIgnoring;
    @Nullable
    private VelocityTracker mVelocity;

    /** Returns the swipe, so the host can {@link #stop} it before moving the card itself. */
    public static MiniCardSwipe attach(View card, Callback callback, View... touchTargets) {
        MiniCardSwipe swipe = new MiniCardSwipe(card, callback);
        for (View target : touchTargets) {
            if (target != null) {
                target.setOnTouchListener(swipe);
            }
        }
        return swipe;
    }

    /**
     * NEWTUBE(haptics): the host is about to move the card itself (close, fold, show, enter): stop
     * the swipe's springs, which a card.animate().cancel() does not reach, and ignore the rest of a
     * touch still on it. A spring back still running kept restoring the card's alpha and position
     * under the host's own fade.
     */
    public void stop() {
        if (mDragging || mMoving) {
            mDragging = false;
            mMoving = false;
            mIgnoring = true;
            recycleVelocity();
        }
        mMagnet.finish();
        cancelSnapBack();
        if (mMoveTarget != NO_CORNER) {
            cancelMove();
            commitMove(); // it lands where it was going, under the host's own animation
        }
    }

    private MiniCardSwipe(View card, Callback callback) {
        mCard = card;
        mCallback = callback;
        mTouchSlop = ViewConfiguration.get(card.getContext()).getScaledTouchSlop();
        mFlingVelocity = FLING_VELOCITY_DP * card.getResources().getDisplayMetrics().density;
        mMagnet = new MagneticDrag(card, position -> {
            mCard.setTranslationX(position);
            mCard.setAlpha(alphaFor(position));
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouch(View view, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDownRawX = event.getRawX();
                mDownRawY = event.getRawY();
                mDragging = false;
                mMoving = false;
                mIgnoring = false;
                recycleVelocity();
                mVelocity = VelocityTracker.obtain();
                trackVelocity(event);
                return false; // the view still sees the press (ripple, click)
            case MotionEvent.ACTION_MOVE: {
                if (mIgnoring) {
                    return true;
                }
                trackVelocity(event);
                float dx = event.getRawX() - mDownRawX;
                float dy = event.getRawY() - mDownRawY;
                if (mMoving) {
                    mCard.setTranslationX(mMoveStartX + dx);
                    mCard.setTranslationY(mMoveStartY + dy);
                    return true;
                }
                if (!mDragging) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) <= mTouchSlop || !mCallback.canSwipe()) {
                        return false;
                    }
                    MiniCardCorner corner = MiniCardCorner.of(mCard);
                    boolean sideways = Math.abs(dx) > Math.abs(dy) * 1.5f;
                    boolean outward = corner == null || (corner.isLeft() ? dx < 0 : dx > 0);
                    if (sideways && outward) {
                        mDragging = true;
                    } else if (corner != null) {
                        mMoving = true;
                    } else {
                        return false;
                    }
                    // Drop the press and its pending click: this touch is a swipe now.
                    MotionEvent cancel = MotionEvent.obtain(event);
                    cancel.setAction(MotionEvent.ACTION_CANCEL);
                    view.onTouchEvent(cancel);
                    cancel.recycle();
                    ViewParent parent = mCard.getParent();
                    if (parent != null) {
                        parent.requestDisallowInterceptTouchEvent(true);
                    }
                    mCard.animate().cancel();
                    cancelSnapBack();
                    if (mMoving) {
                        cancelMove(); // grabbed while landing: carry on from where it is
                        mMoveTarget = NO_CORNER;
                        mMoveStartX = mCard.getTranslationX() - dx;
                        mMoveStartY = mCard.getTranslationY() - dy;
                        mCard.setAlpha(1f);
                        return true;
                    }
                    mMagnet.start();
                }
                mMagnet.move(dx);
                return true;
            }
            case MotionEvent.ACTION_UP: {
                if (mIgnoring) {
                    mIgnoring = false;
                    return true;
                }
                if (mMoving) {
                    trackVelocity(event);
                    float velocityX = 0f;
                    float velocityY = 0f;
                    if (mVelocity != null) {
                        mVelocity.computeCurrentVelocity(1000);
                        velocityX = mVelocity.getXVelocity();
                        velocityY = mVelocity.getYVelocity();
                    }
                    recycleVelocity();
                    mMoving = false;
                    settleInCorner(velocityX, velocityY);
                    return true;
                }
                if (!mDragging) {
                    recycleVelocity();
                    return false;
                }
                trackVelocity(event);
                float velocityX = 0f;
                if (mVelocity != null) {
                    mVelocity.computeCurrentVelocity(1000);
                    velocityX = mVelocity.getXVelocity();
                }
                recycleVelocity();
                mDragging = false;
                float dx = event.getRawX() - mDownRawX;
                mMagnet.move(dx); // where the finger lifted, which a last MOVE may not have said
                mMagnet.finish();
                release(mMagnet.isDetached(), dx, velocityX);
                return true;
            }
            case MotionEvent.ACTION_POINTER_DOWN:
                // A second finger: not a swipe any more. The card goes back; the rest of the touch
                // is ignored (no index-0 jump when the first finger lifts).
                if (mDragging) {
                    mDragging = false;
                    mIgnoring = true;
                    recycleVelocity();
                    mMagnet.finish();
                    springBack(0f);
                } else if (mMoving) {
                    mMoving = false;
                    mIgnoring = true;
                    recycleVelocity();
                    settleInCorner(0f, 0f);
                }
                return mIgnoring;
            case MotionEvent.ACTION_CANCEL:
                recycleVelocity();
                mIgnoring = false;
                if (mDragging) {
                    mDragging = false;
                    mMagnet.finish();
                    springBack(0f);
                    return true;
                }
                if (mMoving) {
                    mMoving = false;
                    settleInCorner(0f, 0f);
                    return true;
                }
                return false;
            default:
                return mDragging || mMoving;
        }
    }

    /**
     * Decided on the finger ({@code dx}), animated from the card: right after the click the card
     * still trails the finger (its gap springs shut), and a fast swipe let go within a frame of it
     * clicked but read the card's 0 and sprang back.
     */
    private void release(boolean detached, float dx, float velocityX) {
        boolean flung = Math.abs(velocityX) > mFlingVelocity && Math.signum(velocityX) == Math.signum(dx);
        boolean flungBack = Math.abs(velocityX) > mFlingVelocity && Math.signum(velocityX) == -Math.signum(dx);
        if (dx == 0f || flungBack || (!detached && !flung) || !mCallback.canSwipe()) {
            springBack(velocityX);
            return;
        }
        if (!detached) {
            Haptics.threshold(mCard, true); // flung before it let go: the click it skipped
        }
        // Off that side of the screen: at least the card's width plus its distance to the edge.
        float screenWidth = mCard.getResources().getDisplayMetrics().widthPixels;
        float target = Math.signum(dx) * screenWidth;
        float remaining = Math.abs(target - mCard.getTranslationX());
        float speed = Math.max(Math.abs(velocityX), mFlingVelocity * 2f);
        long durationMs = Math.round(1000f * remaining / speed);
        durationMs = Math.max(FLY_MIN_MS, Math.min(FLY_MAX_MS, durationMs));
        mCallback.onSwipedAway(target, durationMs);
    }

    private void springBack(float velocityX) {
        mCard.animate().cancel();
        if (mSnapBack == null) {
            mSnapBack = new SpringAnimation(mCard, DynamicAnimation.TRANSLATION_X, 0f);
            mSnapBack.getSpring().setStiffness(SNAP_BACK_STIFFNESS).setDampingRatio(SNAP_BACK_DAMPING);
            mSnapBack.addUpdateListener((animation, value, velocity) -> mCard.setAlpha(alphaFor(value)));
        }
        mSnapBack.cancel();
        mSnapBack.setStartVelocity(velocityX);
        mSnapBack.animateToFinalPosition(0f);
    }

    /**
     * NEWTUBE(mini-corner): spring to the corner the card was heading for - its position plus a
     * short projection of its speed, so a flick carries it across - and dock it there.
     */
    private void settleInCorner(float velocityX, float velocityY) {
        MiniCardCorner corner = MiniCardCorner.of(mCard);
        if (corner == null) {
            return;
        }
        android.graphics.Rect area = corner.area();
        int margin = corner.margin();
        int width = mCard.getWidth();
        int height = mCard.getHeight();
        float centerX = mCard.getLeft() + mCard.getTranslationX() + width / 2f + velocityX * MOVE_PROJECTION_S;
        float centerY = mCard.getTop() + mCard.getTranslationY() + height / 2f + velocityY * MOVE_PROJECTION_S;
        boolean left = centerX < area.centerX();
        boolean top = centerY < area.centerY();
        float targetX = (left ? area.left + margin : area.right - margin - width) - mCard.getLeft();
        float targetY = (top ? area.top + margin : area.bottom - margin - height) - mCard.getTop();
        int target = MiniCardCorner.of(top, left);
        if (target != MiniCardCorner.get(mCard.getContext())) {
            Haptics.click(mCard);
        }
        mMoveTarget = target;
        if (mMoveX == null) {
            mMoveX = moveSpring(DynamicAnimation.TRANSLATION_X);
            mMoveY = moveSpring(DynamicAnimation.TRANSLATION_Y);
        }
        mMoveX.setStartVelocity(velocityX);
        mMoveY.setStartVelocity(velocityY);
        mMoveX.animateToFinalPosition(targetX);
        mMoveY.animateToFinalPosition(targetY);
    }

    private SpringAnimation moveSpring(DynamicAnimation.ViewProperty property) {
        SpringAnimation spring = new SpringAnimation(mCard, property, 0f);
        spring.getSpring().setStiffness(MOVE_STIFFNESS).setDampingRatio(MOVE_DAMPING);
        spring.addEndListener((animation, canceled, value, velocity) -> {
            if (!canceled && mMoveTarget != NO_CORNER && !mMoveX.isRunning() && !mMoveY.isRunning()) {
                commitMove();
            }
        });
        return spring;
    }

    private void cancelMove() {
        if (mMoveX != null) {
            mMoveX.cancel();
            mMoveY.cancel();
        }
    }

    /** Landed: the corner becomes the card's layout and its translation goes back to 0. */
    private void commitMove() {
        int target = mMoveTarget;
        mMoveTarget = NO_CORNER;
        MiniCardCorner corner = MiniCardCorner.of(mCard);
        if (corner != null && target != NO_CORNER) {
            corner.set(target);
        }
        mCard.setTranslationX(0f);
        mCard.setTranslationY(0f);
    }

    private void cancelSnapBack() {
        if (mSnapBack != null) {
            mSnapBack.cancel();
        }
    }

    private float alphaFor(float dx) {
        float fraction = Math.min(1f, Math.abs(dx) / (Math.max(1, mCard.getWidth()) * 1.2f));
        return 1f - 0.85f * fraction;
    }

    private void trackVelocity(MotionEvent event) {
        if (mVelocity == null) {
            return;
        }
        // Raw coordinates: the card moves under the finger, so view-local ones would lag.
        MotionEvent raw = MotionEvent.obtain(event);
        raw.setLocation(event.getRawX(), event.getRawY());
        mVelocity.addMovement(raw);
        raw.recycle();
    }

    private void recycleVelocity() {
        if (mVelocity != null) {
            mVelocity.recycle();
            mVelocity = null;
        }
    }
}
