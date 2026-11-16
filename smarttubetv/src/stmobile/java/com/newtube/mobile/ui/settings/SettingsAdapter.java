package com.newtube.mobile.ui.settings;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Renders a page's {@link SettingsRow}s. Clicks go back to the page, which applies them. */
final class SettingsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    /** Re-read the values (summaries, switch states) without rebuilding the views. */
    static final Object PAYLOAD_VALUES = new Object();

    interface Listener {
        void onRowClicked(@NonNull SettingsRow row);
    }

    private final Listener mListener;
    private List<SettingsRow> mRows = new ArrayList<>();
    private ItemTouchHelper mDragger;

    SettingsAdapter(Listener listener) {
        mListener = listener;
    }

    void submit(List<SettingsRow> rows) {
        if (rows.size() == mRows.size() && sameShape(mRows, rows)) {
            mRows = rows;
            notifyItemRangeChanged(0, rows.size(), PAYLOAD_VALUES);
        } else {
            mRows = rows;
            notifyDataSetChanged();
        }
    }

    /** Lets rows with a handle ({@link SettingsRow#reorderable}) be dragged within their group. */
    void attachDragging(@NonNull RecyclerView list) {
        mDragger = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            private SettingsRow mDragged;

            @Override
            public int getDragDirs(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder holder) {
                SettingsRow row = rowAt(holder.getBindingAdapterPosition());
                return row != null && row.reorder != null ? super.getDragDirs(recyclerView, holder) : 0;
            }

            @Override
            public boolean canDropOver(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder current,
                                       @NonNull RecyclerView.ViewHolder target) {
                SettingsRow from = rowAt(current.getBindingAdapterPosition());
                SettingsRow to = rowAt(target.getBindingAdapterPosition());
                return from != null && to != null && from.reorder != null && from.reorder == to.reorder;
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder from,
                                  @NonNull RecyclerView.ViewHolder to) {
                int fromPos = from.getBindingAdapterPosition();
                int toPos = to.getBindingAdapterPosition();
                if (fromPos < 0 || toPos < 0) {
                    return false;
                }
                Collections.swap(mRows, fromPos, toPos);
                notifyItemMoved(fromPos, toPos);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder holder, int direction) {
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return false;
            }

            @Override
            public void onSelectedChanged(RecyclerView.ViewHolder holder, int actionState) {
                super.onSelectedChanged(holder, actionState);
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && holder != null) {
                    mDragged = rowAt(holder.getBindingAdapterPosition());
                } else if (actionState == ItemTouchHelper.ACTION_STATE_IDLE && mDragged != null) {
                    SettingsRow dragged = mDragged;
                    mDragged = null;
                    List<Integer> order = new ArrayList<>();
                    for (SettingsRow row : mRows) {
                        if (row.reorder == dragged.reorder) {
                            order.add(row.reorderId);
                        }
                    }
                    dragged.reorder.accept(order);
                }
            }
        });
        mDragger.attachToRecyclerView(list);
    }

    private SettingsRow rowAt(int position) {
        return position >= 0 && position < mRows.size() ? mRows.get(position) : null;
    }

    /** Same kinds and titles in the same places: a value changed, not the page. */
    private static boolean sameShape(List<SettingsRow> a, List<SettingsRow> b) {
        for (int i = 0; i < a.size(); i++) {
            SettingsRow x = a.get(i);
            SettingsRow y = b.get(i);
            if (x.kind != y.kind || !String.valueOf(x.title).equals(String.valueOf(y.title))) {
                return false;
            }
        }
        return true;
    }

    /** Where the row with this title is (a search result pointing at it), or -1. */
    int indexOf(@NonNull String title) {
        for (int i = 0; i < mRows.size(); i++) {
            SettingsRow row = mRows.get(i);
            if (row.kind != SettingsRow.KIND_HEADER && row.kind != SettingsRow.KIND_NOTE
                    && title.equals(String.valueOf(row.title))) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int getItemViewType(int position) {
        return mRows.get(position).kind;
    }

    @Override
    public int getItemCount() {
        return mRows.size();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case SettingsRow.KIND_HEADER:
                return new TextHolder(inflater.inflate(R.layout.item_mobile_settings_header, parent, false));
            case SettingsRow.KIND_NOTE:
                return new TextHolder(inflater.inflate(R.layout.item_mobile_settings_note, parent, false));
            case SettingsRow.KIND_DIVIDER:
                return new RecyclerView.ViewHolder(inflater.inflate(R.layout.item_mobile_settings_divider, parent, false)) {};
            default:
                return new RowHolder(inflater.inflate(R.layout.item_mobile_settings_row, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        SettingsRow row = mRows.get(position);
        if (holder instanceof TextHolder) {
            ((TextHolder) holder).text.setText(row.title);
        } else if (holder instanceof RowHolder) {
            ((RowHolder) holder).bind(row, mListener, mDragger);
        }
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        holder.itemView.setForeground(null); // a search result's highlight, if it was still glowing
    }


    private static final class TextHolder extends RecyclerView.ViewHolder {
        final TextView text;

        TextHolder(@NonNull View itemView) {
            super(itemView);
            text = (TextView) itemView;
        }
    }

    private static final class RowHolder extends RecyclerView.ViewHolder {
        private final ImageView icon;
        private final TextView title;
        private final TextView summary;
        private final SwitchMaterial toggle;
        private final ImageView drag;

        RowHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.settings_row_icon);
            title = itemView.findViewById(R.id.settings_row_title);
            summary = itemView.findViewById(R.id.settings_row_summary);
            toggle = itemView.findViewById(R.id.settings_row_switch);
            drag = itemView.findViewById(R.id.settings_row_drag);
        }

        @SuppressLint("ClickableViewAccessibility")
        void bind(SettingsRow row, Listener listener, ItemTouchHelper dragger) {
            if (row.icon != 0) {
                icon.setImageResource(row.icon);
                icon.setVisibility(View.VISIBLE);
            } else {
                icon.setVisibility(View.GONE);
            }

            title.setText(row.title);
            title.setTextColor(ContextCompat.getColor(itemView.getContext(),
                    row.destructive ? R.color.mobile_color_primary : R.color.mobile_color_on_surface));

            CharSequence text = row.summaryText();
            if (text != null && text.length() > 0) {
                summary.setText(text);
                summary.setVisibility(View.VISIBLE);
            } else {
                summary.setVisibility(View.GONE);
            }

            boolean isSwitch = row.kind == SettingsRow.KIND_SWITCH;
            toggle.setVisibility(isSwitch ? View.VISIBLE : View.GONE);
            if (isSwitch && row.checked != null) {
                boolean on = row.checked.getAsBoolean();
                if (toggle.isChecked() != on) {
                    toggle.setChecked(on);
                }
                itemView.setContentDescription(row.title + (text != null ? ", " + text : ""));
            } else {
                itemView.setContentDescription(null);
            }
            itemView.setAccessibilityDelegate(isSwitch ? SwitchRole.INSTANCE : null);

            boolean enabled = row.isEnabled();
            itemView.setEnabled(enabled);
            itemView.setAlpha(enabled ? 1f : 0.38f);
            toggle.setEnabled(enabled);
            itemView.setOnClickListener(enabled ? v -> listener.onRowClicked(row) : null);
            itemView.setClickable(enabled);

            boolean draggable = row.reorder != null && dragger != null;
            drag.setVisibility(draggable ? View.VISIBLE : View.GONE);
            drag.setOnTouchListener(draggable ? (v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    dragger.startDrag(this);
                    return true;
                }
                return false;
            } : null);
        }
    }

    /** TalkBack reads a switch row as a switch with its state, like a platform preference. */
    private static final class SwitchRole extends View.AccessibilityDelegate {
        static final SwitchRole INSTANCE = new SwitchRole();

        @Override
        public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(host, info);
            SwitchMaterial toggle = host.findViewById(R.id.settings_row_switch);
            info.setClassName(android.widget.Switch.class.getName());
            info.setCheckable(true);
            info.setChecked(toggle != null && toggle.isChecked());
        }
    }
}
