package com.newtube.mobile.stats;

import android.app.Activity;
import android.app.Dialog;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * "Keep stats on this phone?": asked on the first app start with Stats in it (from the home
 * screen), and again on the Stats page while it has no answer. Back or a tap outside leaves it
 * unanswered, so the next app start asks again; an answer is final until the page's switch.
 */
public final class StatsConsent {
    /** After the home screen has settled, not over its first frames. */
    private static final long HOME_DELAY_MS = 1_200;
    private static boolean sAskedThisRun;
    @Nullable private static Dialog sShowing;

    private StatsConsent() {
    }

    /** Home screen: once per app run while unanswered. */
    public static void maybeAskOnStart(@NonNull Activity activity) {
        if (sAskedThisRun || LocalStats.getConsent(activity) != LocalStats.CONSENT_UNASKED) {
            return;
        }
        sAskedThisRun = true;
        activity.getWindow().getDecorView().postDelayed(() -> {
            if (!activity.isFinishing() && !activity.isDestroyed() && activity.hasWindowFocus()) {
                ask(activity, null);
            } else {
                sAskedThisRun = false; // something else was in front: try on the next resume
            }
        }, HOME_DELAY_MS);
    }

    /** {@code onAnswer}: run after either button. */
    @Nullable
    public static Dialog ask(@NonNull Activity activity, @Nullable Runnable onAnswer) {
        if (isShowing() || LocalStats.getConsent(activity) != LocalStats.CONSENT_UNASKED) {
            return null;
        }
        sShowing = new MaterialAlertDialogBuilder(activity, R.style.MobileAlertDialog)
                .setTitle(R.string.mobile_stats_consent_title)
                .setMessage(R.string.mobile_stats_consent_body)
                .setPositiveButton(R.string.mobile_stats_consent_yes, (dialog, which) -> answer(activity, true, onAnswer))
                .setNegativeButton(R.string.mobile_stats_consent_no, (dialog, which) -> answer(activity, false, onAnswer))
                .setOnDismissListener(dialog -> sShowing = null)
                .show();
        return sShowing;
    }

    public static boolean isShowing() {
        return sShowing != null && sShowing.isShowing();
    }

    private static void answer(Activity activity, boolean keep, @Nullable Runnable onAnswer) {
        LocalStats.setRecording(activity, keep);
        if (onAnswer != null) {
            onAnswer.run();
        }
    }
}
