package com.newtube.mobile.ui.settings;

import android.content.Context;
import android.text.InputType;
import android.widget.EditText;

import androidx.annotation.NonNull;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.newtube.mobile.productivity.ProductivityPrefs;
import com.newtube.mobile.productivity.UsageClock;
import com.newtube.mobile.ui.common.MobileSnackbar;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Settings > App > Productivity: the mode's topics and the daily time limit. */
final class ProductivityPages {
    private static final int[] LIMITS = {0, 15, 30, 60, 90, 120, 180};

    private ProductivityPages() {
    }

    static String summary(Context context) {
        String state = context.getString(ProductivityPrefs.isEnabled(context)
                ? R.string.mobile_productivity_on : R.string.mobile_productivity_off);
        int limit = ProductivityPrefs.getLimitMinutes(context);
        return limit == 0 ? state : context.getString(R.string.mobile_productivity_summary_limit, state, duration(context, limit));
    }

    static String duration(Context context, int minutes) {
        return minutes == 0 ? context.getString(R.string.mobile_productivity_limit_off) : plain(minutes);
    }

    private static String plain(int minutes) {
        int h = minutes / 60;
        int m = minutes % 60;
        if (h == 0) {
            return String.format(Locale.US, "%d min", m);
        }
        return m == 0 ? String.format(Locale.US, "%d h", h) : String.format(Locale.US, "%d h %d min", h, m);
    }

    static SettingsPages.Page productivity(Context context) {
        List<SettingsRow> rows = new ArrayList<>();

        rows.add(SettingsRow.toggle(context.getString(R.string.mobile_productivity_mode),
                context.getString(R.string.mobile_productivity_mode_summary),
                () -> ProductivityPrefs.isEnabled(context),
                on -> {
                    if (!on && !UsageClock.canTurnOff(context)) {
                        MobileSnackbar.show(context, R.string.mobile_productivity_locked);
                        ProductivityPrefs.setEnabled(context, true);
                        return;
                    }
                    ProductivityPrefs.setEnabled(context, on);
                }));

        rows.add(SettingsRow.header(context.getString(R.string.mobile_productivity_watch_header)));
        rows.add(SettingsRow.page(context.getString(R.string.mobile_productivity_topics),
                () -> topicsSummary(context), SettingsPages.PRODUCTIVITY_TOPICS));

        rows.add(SettingsRow.action(0, context.getString(R.string.mobile_productivity_channels),
                () -> context.getString(R.string.mobile_productivity_channels_summary,
                        ProductivityPrefs.getChannels(context).size()),
                page -> {
                    if (ProductivityPrefs.getChannels(context).isEmpty()) {
                        return;
                    }
                    new MaterialAlertDialogBuilder(page.requireActivity())
                            .setTitle(R.string.mobile_productivity_channels)
                            .setMessage(R.string.mobile_productivity_channels_clear)
                            .setPositiveButton(R.string.mobile_productivity_channels_clear_action, (d, w) -> {
                                ProductivityPrefs.setChannels(context, new LinkedHashSet<>());
                                page.rebuild();
                            })
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                }));

        rows.add(SettingsRow.header(context.getString(R.string.mobile_productivity_limit_header)));
        SettingsRow.Choice<Integer> limit = SettingsRow.choice(context.getString(R.string.mobile_productivity_daily_limit));
        for (int minutes : LIMITS) {
            limit.option(duration(context, minutes), minutes);
        }
        int current = ProductivityPrefs.getLimitMinutes(context);
        boolean listed = false;
        for (int minutes : LIMITS) {
            listed |= minutes == current;
        }
        if (!listed) {
            limit.option(duration(context, current), current);
        }
        rows.add(limit.bind(() -> ProductivityPrefs.getLimitMinutes(context),
                minutes -> ProductivityPrefs.setLimitMinutes(context, minutes)));

        rows.add(SettingsRow.<Integer>choice(context.getString(R.string.mobile_productivity_counts))
                .option(context.getString(R.string.mobile_productivity_counts_all),
                        context.getString(R.string.mobile_productivity_counts_all_desc), ProductivityPrefs.COUNT_ALL)
                .option(context.getString(R.string.mobile_productivity_counts_outside),
                        context.getString(R.string.mobile_productivity_counts_outside_desc), ProductivityPrefs.COUNT_OUTSIDE)
                .bind(() -> ProductivityPrefs.getCountMode(context), mode -> ProductivityPrefs.setCountMode(context, mode)));

        rows.add(SettingsRow.<Integer>choice(context.getString(R.string.mobile_productivity_when_up))
                .option(context.getString(R.string.mobile_productivity_when_up_mode), ProductivityPrefs.ON_LIMIT_PRODUCTIVITY_ONLY)
                .option(context.getString(R.string.mobile_productivity_when_up_close), ProductivityPrefs.ON_LIMIT_CLOSE)
                .summary(context.getString(R.string.mobile_productivity_when_up_summary))
                .bind(() -> ProductivityPrefs.getOnLimit(context), action -> ProductivityPrefs.setOnLimit(context, action))
                .enabledWhen(() -> ProductivityPrefs.getLimitMinutes(context) > 0));

        rows.add(SettingsRow.action(context.getString(R.string.mobile_productivity_today), usedToday(context), page -> { }));

        rows.add(SettingsRow.header(context.getString(R.string.mobile_productivity_leaving_header)));
        rows.add(SettingsRow.toggle(context.getString(R.string.mobile_productivity_hold),
                context.getString(R.string.mobile_productivity_hold_summary),
                () -> ProductivityPrefs.isHoldToExit(context), on -> ProductivityPrefs.setHoldToExit(context, on)));

        return new SettingsPages.Page(context.getString(R.string.mobile_productivity), rows);
    }

    private static String usedToday(Context context) {
        int usedMin = (int) (ProductivityPrefs.getUsedTodayMs(context) / 60_000L);
        int limit = ProductivityPrefs.getLimitMinutes(context);
        return limit == 0 ? context.getString(R.string.mobile_productivity_today_used, plain(usedMin))
                : context.getString(R.string.mobile_productivity_today_left, plain(usedMin),
                plain(Math.max(0, limit - usedMin)));
    }

    private static String topicsSummary(Context context) {
        int categories = ProductivityPrefs.getCategories(context).size();
        int keywords = ProductivityPrefs.getKeywords(context).size();
        return context.getString(R.string.mobile_productivity_topics_summary, categories, keywords);
    }

    static SettingsPages.Page topics(Context context) {
        List<SettingsRow> rows = new ArrayList<>();
        rows.add(SettingsRow.note(context.getString(R.string.mobile_productivity_topics_note)));

        rows.add(SettingsRow.header(context.getString(R.string.mobile_productivity_categories_header)));
        for (String category : ProductivityPrefs.ALL_CATEGORIES) {
            rows.add(SettingsRow.toggle(category, null,
                    () -> ProductivityPrefs.getCategories(context).contains(category),
                    on -> {
                        Set<String> set = ProductivityPrefs.getCategories(context);
                        if (on) {
                            set.add(category);
                        } else {
                            set.remove(category);
                        }
                        ProductivityPrefs.setCategories(context, set);
                    }));
        }

        rows.add(SettingsRow.header(context.getString(R.string.mobile_productivity_keywords_header)));
        rows.add(SettingsRow.action(context.getString(R.string.mobile_productivity_add_keyword), null,
                page -> addKeyword(page)));
        for (String keyword : ProductivityPrefs.getKeywords(context)) {
            rows.add(SettingsRow.action(keyword, context.getString(R.string.mobile_productivity_remove_keyword), page -> {
                Set<String> set = ProductivityPrefs.getKeywords(context);
                set.remove(keyword);
                ProductivityPrefs.setKeywords(context, set);
                page.rebuild();
            }));
        }

        rows.add(SettingsRow.header(context.getString(R.string.mobile_productivity_matching_header)));
        rows.add(SettingsRow.toggle(context.getString(R.string.mobile_productivity_check_category),
                context.getString(R.string.mobile_productivity_check_category_summary),
                () -> ProductivityPrefs.isCategoryMatchEnabled(context),
                on -> ProductivityPrefs.setCategoryMatchEnabled(context, on)));

        return new SettingsPages.Page(context.getString(R.string.mobile_productivity_topics), rows);
    }

    private static void addKeyword(@NonNull SettingsPageFragment page) {
        Context context = page.requireContext();
        EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setSingleLine();
        input.setHint(R.string.mobile_productivity_keyword_hint);
        int pad = (int) (24 * context.getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad / 2, pad, pad / 2);
        new MaterialAlertDialogBuilder(page.requireActivity())
                .setTitle(R.string.mobile_productivity_add_keyword)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String keyword = input.getText().toString().trim();
                    if (!keyword.isEmpty()) {
                        Set<String> set = new LinkedHashSet<>(ProductivityPrefs.getKeywords(context));
                        set.add(keyword);
                        ProductivityPrefs.setKeywords(context, set);
                        page.rebuild();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
