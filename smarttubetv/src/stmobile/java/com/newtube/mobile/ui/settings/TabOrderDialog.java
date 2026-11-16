package com.newtube.mobile.ui.settings;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.newtube.mobile.ui.browse.TabOrder;

import java.util.Collections;
import java.util.List;

/** Settings > Tabs > Order: drag the bottom-bar tabs that are on into the order they show in. */
final class TabOrderDialog {
    private TabOrderDialog() {
    }

    static CharSequence summary(Context context) {
        StringBuilder summary = new StringBuilder();
        for (int id : TabOrder.enabled(context)) {
            if (summary.length() > 0) {
                summary.append(", ");
            }
            summary.append(context.getString(TabOrder.titleRes(id)));
        }
        return summary;
    }

    static void show(@NonNull SettingsPageFragment page) {
        Context context = page.requireContext();
        List<Integer> order = TabOrder.enabled(context);

        RecyclerView list = new RecyclerView(context);
        list.setLayoutManager(new LinearLayoutManager(context));
        list.setPadding(0, dp(context, 8), 0, 0);
        Adapter adapter = new Adapter(order);
        list.setAdapter(adapter);

        ItemTouchHelper dragger = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder from,
                                  @NonNull RecyclerView.ViewHolder to) {
                int fromPos = from.getBindingAdapterPosition();
                int toPos = to.getBindingAdapterPosition();
                if (fromPos < 0 || toPos < 0) {
                    return false;
                }
                Collections.swap(order, fromPos, toPos);
                adapter.notifyItemMoved(fromPos, toPos);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder holder, int direction) {
            }
        });
        dragger.attachToRecyclerView(list);
        adapter.dragger = dragger;

        page.showDialog(new MaterialAlertDialogBuilder(context, R.style.MobileAlertDialog)
                .setTitle(R.string.mobile_settings_tabs_order_title)
                .setMessage(R.string.mobile_settings_tabs_order_hint)
                .setView(list)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    TabOrder.setEnabled(context, order);
                    page.rebuild();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show());
    }

    private static int dp(Context context, int dp) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                context.getResources().getDisplayMetrics()));
    }

    private static final class Adapter extends RecyclerView.Adapter<Holder> {
        private final List<Integer> mOrder;
        ItemTouchHelper dragger;

        Adapter(List<Integer> order) {
            mOrder = order;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(parent.getContext(), this);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            holder.title.setText(TabOrder.titleRes(mOrder.get(position)));
        }

        @Override
        public int getItemCount() {
            return mOrder.size();
        }
    }

    private static final class Holder extends RecyclerView.ViewHolder {
        final TextView title;

        @SuppressLint("ClickableViewAccessibility")
        Holder(Context context, Adapter adapter) {
            super(new LinearLayout(context));
            LinearLayout row = (LinearLayout) itemView;
            row.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(context, 52));
            row.setPaddingRelative(dp(context, 24), 0, dp(context, 12), 0);

            title = new TextView(context);
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            title.setTextColor(ContextCompat.getColor(context, R.color.mobile_color_on_surface));
            row.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            ImageView handle = new ImageView(context);
            handle.setImageResource(R.drawable.ic_queue_drag);
            handle.setScaleType(ImageView.ScaleType.CENTER);
            handle.setContentDescription(null);
            row.addView(handle, new LinearLayout.LayoutParams(dp(context, 48), dp(context, 48)));

            handle.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN && adapter.dragger != null) {
                    adapter.dragger.startDrag(this);
                }
                return false;
            });
        }
    }
}
