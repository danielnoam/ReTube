package com.newtube.mobile.productivity;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.lang.ref.WeakReference;

/**
 * Counts the time the app is on screen toward the daily limit (Settings > Productivity) and acts
 * when it is used up. Time spent in productivity mode is skipped when the user chose "only outside
 * productivity mode". Registered once from the Application.
 */
public final class UsageClock implements Application.ActivityLifecycleCallbacks {
    private static final long TICK_MS = 5_000L;

    private final Application mApp;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private int mStarted;
    private long mLastTick;
    @Nullable private WeakReference<Activity> mTop;
    private boolean mDialogShowing;

    private final Runnable mTickRunnable = new Runnable() {
        @Override
        public void run() {
            tick();
            if (mStarted > 0) {
                mHandler.postDelayed(this, TICK_MS);
            }
        }
    };

    private UsageClock(Application app) {
        mApp = app;
    }

    public static void install(Application app) {
        app.registerActivityLifecycleCallbacks(new UsageClock(app));
    }

    /**
     * Turning the mode off is refused once the limit is spent and the user chose "only productivity
     * mode" for that moment (the limit only runs outside the mode, so leaving it would be a way round).
     */
    public static boolean canTurnOff(android.content.Context context) {
        return !(ProductivityPrefs.isLimitReached(context)
                && ProductivityPrefs.getOnLimit(context) == ProductivityPrefs.ON_LIMIT_PRODUCTIVITY_ONLY
                && ProductivityPrefs.getCountMode(context) == ProductivityPrefs.COUNT_OUTSIDE);
    }

    private void tick() {
        long now = SystemClock.elapsedRealtime();
        long delta = mLastTick == 0 ? 0 : Math.min(now - mLastTick, TICK_MS * 2);
        mLastTick = now;
        boolean inMode = ProductivityPrefs.isEnabled(mApp);
        boolean counts = ProductivityPrefs.getCountMode(mApp) == ProductivityPrefs.COUNT_ALL || !inMode;
        if (counts && ProductivityPrefs.getLimitMinutes(mApp) > 0) {
            ProductivityPrefs.addUsedMs(mApp, delta);
        }
        if (ProductivityPrefs.isLimitReached(mApp)) {
            onLimitReached(inMode);
        }
    }

    private void onLimitReached(boolean inMode) {
        boolean modeOnly = ProductivityPrefs.getOnLimit(mApp) == ProductivityPrefs.ON_LIMIT_PRODUCTIVITY_ONLY
                && ProductivityPrefs.getCountMode(mApp) == ProductivityPrefs.COUNT_OUTSIDE;
        if (modeOnly) {
            if (!inMode) {
                ProductivityPrefs.setEnabled(mApp, true);
                showDialog(true);
            }
        } else {
            showDialog(false);
        }
    }

    private void showDialog(boolean switchedToMode) {
        Activity activity = mTop != null ? mTop.get() : null;
        if (activity == null || activity.isFinishing() || mDialogShowing) {
            return;
        }
        mDialogShowing = true;
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.mobile_productivity_times_up)
                .setMessage(switchedToMode ? R.string.mobile_productivity_times_up_mode
                        : R.string.mobile_productivity_times_up_close)
                .setCancelable(switchedToMode)
                .setOnDismissListener(d -> mDialogShowing = false);
        if (switchedToMode) {
            builder.setPositiveButton(android.R.string.ok, null);
        } else {
            builder.setPositiveButton(R.string.mobile_productivity_close_app, (d, w) -> activity.finishAffinity());
        }
        builder.show();
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        if (mStarted++ == 0) {
            mLastTick = SystemClock.elapsedRealtime();
            mHandler.postDelayed(mTickRunnable, TICK_MS);
        }
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
        if (--mStarted <= 0) {
            mStarted = 0;
            tick();
            mHandler.removeCallbacks(mTickRunnable);
            mLastTick = 0;
        }
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        mTop = new WeakReference<>(activity);
        // A limit that ran out while the app was closed: act as soon as it is back on screen.
        if (ProductivityPrefs.isLimitReached(mApp)) {
            onLimitReached(ProductivityPrefs.isEnabled(mApp));
        }
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
    }
}
