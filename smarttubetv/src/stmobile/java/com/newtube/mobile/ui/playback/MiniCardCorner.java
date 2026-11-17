package com.newtube.mobile.ui.playback;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * NEWTUBE(mini-corner): which corner of the screen the docked mini card sits in. The card is
 * dragged there (MiniCardSwipe) and every host shows it in the same one; the choice is kept
 * across launches.
 *
 * <p>The corner is real layout, not a translation: the hosts' own animations (entry, close, swipe
 * away) and the bounds the player morphs to and from all read the card's laid-out position, and
 * all of them reset translation to 0. The top corners sit under the host's top bar
 * ({@link #attach}'s {@code topBars}), the bottom ones where the layout put the card.</p>
 */
public final class MiniCardCorner {
    public static final int TOP_LEFT = 0;
    public static final int TOP_RIGHT = 1;
    public static final int BOTTOM_LEFT = 2;
    public static final int BOTTOM_RIGHT = 3;

    private static final int UNSET = -1;
    private static final String PREFS_NAME = "newtube_mini_card";
    private static final String KEY_CORNER = "corner";
    private static final int MARGIN_DP = 12;

    private static int sCorner = UNSET;

    private final View mCard;
    private final View[] mTopBars;
    /** ConstraintLayout hosts: the bottom constraint the layout gave the card. */
    private final int mBottomToTop;
    private final int mBottomToBottom;

    private MiniCardCorner(View card, View[] topBars) {
        mCard = card;
        mTopBars = topBars;
        ViewGroup.LayoutParams lp = card.getLayoutParams();
        if (lp instanceof ConstraintLayout.LayoutParams) {
            mBottomToTop = ((ConstraintLayout.LayoutParams) lp).bottomToTop;
            mBottomToBottom = ((ConstraintLayout.LayoutParams) lp).bottomToBottom;
        } else {
            mBottomToTop = ConstraintLayout.LayoutParams.UNSET;
            mBottomToBottom = ConstraintLayout.LayoutParams.UNSET;
        }
    }

    /**
     * Place {@code card} in the saved corner now and whenever its parent lays out again (a top bar
     * that appears, a rotation). {@code topBars}: the views the top corners stay below; the lowest
     * visible one counts.
     */
    public static void attach(@Nullable View card, View... topBars) {
        if (card == null || !(card.getParent() instanceof ViewGroup)) {
            return;
        }
        MiniCardCorner corner = new MiniCardCorner(card, topBars);
        card.setTag(R.id.mobile_mini_player, corner);
        ((ViewGroup) card.getParent()).addOnLayoutChangeListener(
                (v, l, t, r, b, ol, ot, or, ob) -> corner.applyLayout());
        corner.applyLayout();
    }

    /** Re-read the saved corner: another screen's card may have been moved since. */
    public static void apply(@Nullable View card) {
        MiniCardCorner corner = of(card);
        if (corner != null) {
            corner.applyLayout();
        }
    }

    @Nullable
    static MiniCardCorner of(@Nullable View card) {
        Object tag = card != null ? card.getTag(R.id.mobile_mini_player) : null;
        return tag instanceof MiniCardCorner ? (MiniCardCorner) tag : null;
    }

    public static int get(Context context) {
        if (sCorner == UNSET) {
            sCorner = prefs(context).getInt(KEY_CORNER, UNSET);
        }
        return sCorner;
    }

    public static boolean isTop(Context context) {
        int corner = get(context);
        return corner == TOP_LEFT || corner == TOP_RIGHT;
    }

    /** Unset until the card is first moved: the layout's own bottom-end, which RTL mirrors. */
    public static boolean isLeft(Context context, boolean rtl) {
        int corner = get(context);
        return corner == UNSET ? rtl : corner == TOP_LEFT || corner == BOTTOM_LEFT;
    }

    static int of(boolean top, boolean left) {
        return top ? (left ? TOP_LEFT : TOP_RIGHT) : (left ? BOTTOM_LEFT : BOTTOM_RIGHT);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** Save {@code corner} and move this card's layout there. */
    void set(int corner) {
        if (corner != sCorner) {
            sCorner = corner;
            prefs(mCard.getContext()).edit().putInt(KEY_CORNER, corner).apply();
        }
        applyLayout();
    }

    boolean isLeft() {
        return isLeft(mCard.getContext(), mCard.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL);
    }

    int margin() {
        return Math.round(MARGIN_DP * mCard.getResources().getDisplayMetrics().density);
    }

    /** The area the card's corners are in, in its parent's coordinates (margins not taken off). */
    Rect area() {
        ViewGroup parent = (ViewGroup) mCard.getParent();
        Rect area = new Rect(parent.getPaddingLeft(), topBarBottom(parent),
                parent.getWidth() - parent.getPaddingRight(), parent.getHeight() - parent.getPaddingBottom());
        int anchorId = mBottomToTop != ConstraintLayout.LayoutParams.UNSET ? mBottomToTop : View.NO_ID;
        View anchor = anchorId != View.NO_ID ? parent.findViewById(anchorId) : null;
        if (anchor != null && anchor.getVisibility() != View.GONE) {
            area.bottom = anchor.getTop();
        }
        return area;
    }

    /** Screen y of a top-corner card's top edge: where the player's minimize morph lands it. */
    public static int topOnScreen(@Nullable View card) {
        MiniCardCorner corner = of(card);
        if (corner == null) {
            return -1;
        }
        int[] location = new int[2];
        ((View) card.getParent()).getLocationOnScreen(location);
        return location[1] + corner.area().top + corner.margin();
    }

    private int topBarBottom(ViewGroup parent) {
        int bottom = parent.getPaddingTop();
        Rect rect = new Rect();
        for (View bar : mTopBars) {
            if (bar == null || bar.getVisibility() == View.GONE || !isDescendant(parent, bar)) {
                continue;
            }
            rect.set(0, 0, bar.getWidth(), bar.getHeight());
            parent.offsetDescendantRectToMyCoords(bar, rect);
            bottom = Math.max(bottom, rect.bottom);
        }
        return bottom;
    }

    private static boolean isDescendant(ViewGroup parent, View view) {
        for (Object p = view.getParent(); p instanceof View; p = ((View) p).getParent()) {
            if (p == parent) {
                return true;
            }
        }
        return false;
    }

    private void applyLayout() {
        boolean top = isTop(mCard.getContext());
        boolean left = isLeft();
        int margin = margin();
        ViewGroup.LayoutParams params = mCard.getLayoutParams();
        boolean changed;
        if (params instanceof ConstraintLayout.LayoutParams) {
            changed = applyConstraints((ConstraintLayout.LayoutParams) params, top, left, margin);
        } else if (params instanceof FrameLayout.LayoutParams) {
            changed = applyGravity((FrameLayout.LayoutParams) params, top, left, margin);
        } else {
            return;
        }
        if (changed) {
            mCard.setLayoutParams(params);
        }
    }

    private boolean applyConstraints(ConstraintLayout.LayoutParams lp, boolean top, boolean left, int margin) {
        int unset = ConstraintLayout.LayoutParams.UNSET;
        int parent = ConstraintLayout.LayoutParams.PARENT_ID;
        View topBar = lowestTopBar();
        int topToBottom = top && topBar != null && topBar.getId() != View.NO_ID ? topBar.getId() : unset;
        int topToTop = top && topToBottom == unset ? parent : unset;
        int bottomToTop = top ? unset : mBottomToTop;
        int bottomToBottom = top ? unset : mBottomToBottom;
        int leftToLeft = left ? parent : unset;
        int rightToRight = left ? unset : parent;
        boolean changed = lp.topToBottom != topToBottom || lp.topToTop != topToTop
                || lp.bottomToTop != bottomToTop || lp.bottomToBottom != bottomToBottom
                || lp.leftToLeft != leftToLeft || lp.rightToRight != rightToRight
                || lp.startToStart != unset || lp.endToEnd != unset
                || lp.topMargin != (top ? margin : 0) || lp.bottomMargin != (top ? 0 : margin)
                || lp.leftMargin != margin || lp.rightMargin != margin;
        lp.topToBottom = topToBottom;
        lp.topToTop = topToTop;
        lp.bottomToTop = bottomToTop;
        lp.bottomToBottom = bottomToBottom;
        lp.leftToLeft = leftToLeft;
        lp.rightToRight = rightToRight;
        lp.startToStart = unset;
        lp.endToEnd = unset;
        setMargins(lp, top ? margin : 0, top ? 0 : margin, margin);
        lp.validate();
        return changed;
    }

    private boolean applyGravity(FrameLayout.LayoutParams lp, boolean top, boolean left, int margin) {
        int gravity = (top ? Gravity.TOP : Gravity.BOTTOM) | (left ? Gravity.LEFT : Gravity.RIGHT);
        int topMargin = top ? topBarBottom((ViewGroup) mCard.getParent())
                - ((ViewGroup) mCard.getParent()).getPaddingTop() + margin : 0;
        int bottomMargin = top ? 0 : margin;
        boolean changed = lp.gravity != gravity || lp.topMargin != topMargin
                || lp.bottomMargin != bottomMargin || lp.leftMargin != margin || lp.rightMargin != margin;
        lp.gravity = gravity;
        setMargins(lp, topMargin, bottomMargin, margin);
        return changed;
    }

    private static void setMargins(ViewGroup.MarginLayoutParams lp, int top, int bottom, int side) {
        lp.topMargin = top;
        lp.bottomMargin = bottom;
        lp.leftMargin = side;
        lp.rightMargin = side;
        lp.setMarginStart(side);
        lp.setMarginEnd(side);
    }

    @Nullable
    private View lowestTopBar() {
        ViewGroup parent = (ViewGroup) mCard.getParent();
        View lowest = null;
        int lowestBottom = Integer.MIN_VALUE;
        Rect rect = new Rect();
        for (View bar : mTopBars) {
            if (bar == null || bar.getVisibility() == View.GONE || bar.getParent() != parent) {
                continue;
            }
            rect.set(0, 0, bar.getWidth(), bar.getHeight());
            parent.offsetDescendantRectToMyCoords(bar, rect);
            if (rect.bottom > lowestBottom) {
                lowestBottom = rect.bottom;
                lowest = bar;
            }
        }
        return lowest;
    }
}
