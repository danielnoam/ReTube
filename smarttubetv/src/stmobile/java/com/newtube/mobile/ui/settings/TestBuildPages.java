package com.newtube.mobile.ui.settings;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.tv.R;
import com.newtube.mobile.ui.common.MobileSnackbar;
import com.newtube.mobile.update.TestBuilds;

import java.util.ArrayList;
import java.util.List;

/** Settings > About > Test builds: the branches CI built for labelled pull requests (see TestBuilds). */
final class TestBuildPages {
    private TestBuildPages() {
    }

    static SettingsPages.Page testBuilds(Context context) {
        List<SettingsRow> rows = new ArrayList<>();
        rows.add(SettingsRow.note(context.getString(R.string.mobile_settings_test_builds_note)));

        if (TestBuilds.loadState() == TestBuilds.Load.LOADING || TestBuilds.loadState() == TestBuilds.Load.IDLE) {
            rows.add(SettingsRow.note(context.getString(R.string.mobile_settings_test_builds_loading)));
        } else if (TestBuilds.loadState() == TestBuilds.Load.FAILED) {
            rows.add(SettingsRow.action(context.getString(R.string.mobile_settings_test_builds_failed),
                    context.getString(R.string.mobile_settings_test_builds_retry), page -> TestBuilds.load()));
        } else {
            if (TestBuilds.builds().isEmpty()) {
                rows.add(SettingsRow.action(context.getString(R.string.mobile_settings_test_builds_none),
                        context.getString(R.string.mobile_settings_test_builds_none_summary), page -> TestBuilds.load()));
            }
            for (TestBuilds.Build build : TestBuilds.builds()) {
                rows.add(SettingsRow.action(build.title, rowSummary(context, build, false), page -> tap(page, build)));
            }
        }

        TestBuilds.Build stable = TestBuilds.stable();
        if (TestBuilds.isTestBuild() && stable != null) {
            rows.add(SettingsRow.divider());
            // Android takes an equal versionCode over the app, not a lower one: a branch that raised the
            // version number can't go back without an uninstall.
            boolean stuck = installedCode(context) > stable.versionCode;
            rows.add(SettingsRow.action(context.getString(R.string.mobile_settings_test_build_back),
                    stuck ? context.getString(R.string.mobile_settings_test_build_uninstall_first, stable.versionName)
                            : rowSummary(context, stable, true),
                    page -> {
                        if (!stuck) {
                            tap(page, stable);
                        }
                    }));
        }
        return new SettingsPages.Page(context.getString(R.string.mobile_settings_test_builds), rows);
    }

    private static String rowSummary(Context context, TestBuilds.Build build, boolean release) {
        if (build.apkUrl == null) {
            return context.getString(R.string.mobile_settings_test_build_no_apk);
        }
        if (build.tag.equals(TestBuilds.dlTag())) {
            switch (TestBuilds.dlState()) {
                case DOWNLOADING:
                    return context.getString(R.string.mobile_settings_test_build_downloading, TestBuilds.dlBytes() / (1024 * 1024));
                case READY:
                    return context.getString(R.string.mobile_settings_test_build_ready);
                case FAILED:
                    return context.getString(R.string.mobile_settings_test_build_failed);
                default:
                    break;
            }
        }
        if (!release && TestBuilds.isTestBuild() && build.commit.startsWith(TestBuilds.installedCommit()) && !build.commit.isEmpty()) {
            return context.getString(R.string.mobile_settings_test_build_installed);
        }
        String meta = release ? build.versionName
                : context.getString(R.string.mobile_settings_test_build_meta, build.pr, build.branch, build.shortCommit());
        return meta + " · " + context.getString(R.string.mobile_settings_test_build_download);
    }

    private static void tap(SettingsPageFragment page, TestBuilds.Build build) {
        Context context = page.requireContext();
        if (build.apkUrl == null || TestBuilds.dlState() == TestBuilds.Dl.DOWNLOADING) {
            return;
        }
        if (build.tag.equals(TestBuilds.dlTag()) && TestBuilds.dlState() == TestBuilds.Dl.READY) {
            if (!TestBuilds.canInstallPackages(context)) {
                MobileSnackbar.show(context, R.string.mobile_settings_test_build_permission);
                TestBuilds.openInstallPermission(page.requireActivity());
            } else if (!TestBuilds.install(page.requireActivity())) {
                MobileSnackbar.show(context, R.string.mobile_settings_test_build_no_installer);
            }
            return;
        }
        TestBuilds.download(context, build);
    }

    private static int installedCode(Context context) {
        try {
            return (int) androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(
                    context.getPackageManager().getPackageInfo(context.getPackageName(), 0));
        } catch (Exception e) {
            return 0;
        }
    }
}
