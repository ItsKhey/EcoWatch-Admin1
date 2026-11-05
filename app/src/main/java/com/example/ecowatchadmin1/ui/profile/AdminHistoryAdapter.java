package com.example.ecowatchadmin1.ui.profile;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.example.ecowatchadmin1.R;
import com.example.ecowatchadmin1.data.models.History;
import com.example.ecowatchadmin1.utils.AdminHistoryClassifier;
import com.example.ecowatchadmin1.utils.AdminHistoryDateFormatter;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Renders the compact Admin History cards.
 *
 * <p>The only adapter on this screen. Its predecessor was a resident-facing list
 * that bound the same {@link History} entity but carried its own selection model
 * keyed to the trash actions. Both are gone: an administrator's trail is a single
 * list read from the admin audit route, so there is nothing left for a second
 * adapter to serve.
 *
 * <p>Each card carries its own removal actions rather than relying on the header's
 * bulk controls. An audit trail is read by row, and a bulk-only affordance would
 * leave an individual record with no way to be moved aside.
 *
 * <p>The two views share one layout: {@link #setTrashMode(boolean)} decides whether
 * the row action means "move to trash" or "restore", and whether permanent
 * deletion is offered at all.
 *
 * <p>Nothing that acts on a selection is drawn until {@link #setSelectionMode} is
 * turned on, which the fragment does on a long press. See {@link OnLongPressListener}.
 */
public class AdminHistoryAdapter extends RecyclerView.Adapter<AdminHistoryAdapter.RowHolder> {

    /** Notified when the View Details affordance on a card is tapped. */
    public interface OnDetailsClickListener {
        void onViewDetails(History history);
    }

    /**
     * Notified when a row's own remove action is used.
     *
     * <p>Kept as three separate calls rather than one "delete" because the meaning
     * of the same button depends on the view, and the screen owns which view is
     * showing. The adapter reports what was tapped; the fragment decides what it
     * means.
     */
    public interface OnRowActionListener {
        void onRowMoveToTrash(History history);

        void onRowRestore(History history);

        void onRowDeletePermanently(History history);
    }

    private List<AdminHistoryRow> rows = new ArrayList<>();

    /**
     * Locally selected rows, by Room primary key.
     *
     * <p>Tracked explicitly because {@link #getSelectedIds()} previously returned
     * every row on screen. Any caller treating that as "the selection" would have
     * operated on the whole tab, which for a delete is data loss rather than an
     * annoyance.
     */
    private final Set<Integer> selectedIds = new LinkedHashSet<>();

    /**
     * Notified when a row's checkbox is toggled, so the header can enable or
     * disable its bulk controls and keep Select All in step with the cards.
     */
    public interface OnSelectionChangedListener {
        void onSelectionChanged(int selectedCount, int totalCount);
    }

    /**
     * Notified when a card is long pressed.
     *
     * <p>A long press is the only way into selection mode. The screen is read far
     * more often than it is edited, and a permanently visible set of checkboxes,
     * a Select All toggle and two bulk buttons would put five action affordances on
     * every card of an audit trail that is usually only being read. A long press
     * keeps them out of the way until someone actually intends to change something.
     *
     * <p>The row id is passed so the screen can enter the mode already holding
     * that record selected, which is what the press was asking for.
     */
    public interface OnLongPressListener {
        void onRowLongPressed(int rowId);
    }

    @Nullable
    private final OnDetailsClickListener detailsListener;

    @Nullable
    private final OnRowActionListener rowActionListener;

    @Nullable
    private final OnSelectionChangedListener selectionListener;

    @Nullable
    private final OnLongPressListener longPressListener;

    private boolean trashMode = false;

    /**
     * Whether the selection affordances are on screen.
     *
     * <p>Off until a card is long pressed. While it is off, each card's checkbox
     * is not drawn at all rather than drawn disabled, because a greyed-out box
     * still reads as a control the screen failed to wire up.
     */
    private boolean selectionMode = false;

    public AdminHistoryAdapter(@Nullable OnDetailsClickListener detailsListener) {
        this(detailsListener, null, null, null);
    }

    public AdminHistoryAdapter(@Nullable OnDetailsClickListener detailsListener,
                               @Nullable OnRowActionListener rowActionListener,
                               @Nullable OnSelectionChangedListener selectionListener) {
        this(detailsListener, rowActionListener, selectionListener, null);
    }

    public AdminHistoryAdapter(@Nullable OnDetailsClickListener detailsListener,
                               @Nullable OnRowActionListener rowActionListener,
                               @Nullable OnSelectionChangedListener selectionListener,
                               @Nullable OnLongPressListener longPressListener) {
        this.detailsListener = detailsListener;
        this.rowActionListener = rowActionListener;
        this.selectionListener = selectionListener;
        this.longPressListener = longPressListener;
    }

    /**
     * Shows or hides everything that acts on a selection.
     *
     * <p>Turning it off also drops the selection, because a selection the admin can
     * no longer see or adjust would still be acted on by a later bulk press.
     *
     * @param selectionMode true to reveal the checkboxes and let the screen show
     *                     its bulk bar
     */
    public void setSelectionMode(boolean selectionMode) {
        if (this.selectionMode == selectionMode) return;
        this.selectionMode = selectionMode;
        if (!selectionMode && !selectedIds.isEmpty()) {
            selectedIds.clear();
        }
        notifyDataSetChanged();
        notifySelectionChanged();
    }

    public boolean isSelectionMode() {
        return selectionMode;
    }

    /**
     * Switches between the active feed and the trash.
     *
     * <p>In the active feed the row action moves a record to the trash and
     * permanent deletion is withheld: a record still in the audit trail should not
     * be destroyable in one tap from the list it is being read in. In the trash the
     * row action restores instead, and permanent deletion becomes available
     * because the record is already out of the trail.
     */
    public void setTrashMode(boolean trashMode) {
        if (this.trashMode == trashMode) return;
        this.trashMode = trashMode;
        notifyDataSetChanged();
    }

    public boolean isTrashMode() {
        return trashMode;
    }

    public void submit(@NonNull List<History> historyList) {
        List<AdminHistoryRow> mapped = new ArrayList<>();
        if (historyList != null) {
            for (History history : historyList) {
                if (history != null) mapped.add(AdminHistoryRow.from(history));
            }
        }
        rows = mapped;

        // A selection cannot outlive the rows it referred to: a re-filtered or
        // re-pulled list may no longer contain them, and acting on a stale id would
        // delete whatever now sits at that key.
        selectedIds.retainAll(visibleLocalIds());

        notifyDataSetChanged();
    }

    /**
     * Room primary keys of the rows the admin has explicitly selected.
     *
     * <p>Empty until something selects, so a bulk action with no selection is a
     * no-op rather than an operation on every visible record.
     */
    public List<Integer> getSelectedIds() {
        List<Integer> ids = new ArrayList<>();
        for (AdminHistoryRow row : rows) {
            if (selectedIds.contains(row.getId()) && row.getId() > 0) ids.add(row.getId());
        }
        return ids;
    }

    /** The selected rows themselves, which carry the server ids a write needs. */
    public List<History> getSelectedRows() {
        List<History> selected = new ArrayList<>();
        for (AdminHistoryRow row : rows) {
            if (selectedIds.contains(row.getId())) selected.add(row.getHistory());
        }
        return selected;
    }

    public void setSelected(int localId, boolean selected) {
        if (localId <= 0) return;
        if (selected) {
            selectedIds.add(localId);
        } else {
            selectedIds.remove(localId);
        }
    }

    /** Selects every row currently on screen. */
    public void selectAllVisible() {
        selectedIds.clear();
        for (AdminHistoryRow row : rows) {
            if (row.getId() > 0) selectedIds.add(row.getId());
        }
        notifyDataSetChanged();
        notifySelectionChanged();
    }

    public void clearSelection() {
        if (selectedIds.isEmpty()) return;
        selectedIds.clear();
        notifyDataSetChanged();
        notifySelectionChanged();
    }

    public int getSelectedCount() {
        return selectedIds.size();
    }

    /** Whether every visible row is selected, which is what Select All reflects. */
    public boolean isEverythingSelected() {
        return !rows.isEmpty() && selectedIds.size() == rows.size();
    }

    private void notifySelectionChanged() {
        if (selectionListener != null) {
            selectionListener.onSelectionChanged(selectedIds.size(), rows.size());
        }
    }

    private Set<Integer> visibleLocalIds() {
        Set<Integer> visible = new LinkedHashSet<>();
        for (AdminHistoryRow row : rows) {
            if (row.getId() > 0) visible.add(row.getId());
        }
        return visible;
    }

    @NonNull
    @Override
    public RowHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_admin_history, parent, false);
        return new RowHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RowHolder holder, int position) {
        AdminHistoryRow row = rows.get(position);
        History history = row.getHistory();

        holder.icon.setText(row.getIcon());
        holder.title.setText(row.getTitle());

        // The subtitle is optional: a row with no report, actor or description
        // would otherwise render a blank line and inflate the card's height.
        String subtitle = row.getSubtitle();
        boolean hasSubtitle = subtitle != null && !subtitle.isEmpty();
        holder.subtitle.setText(hasSubtitle ? subtitle : "");
        holder.subtitle.setVisibility(hasSubtitle ? View.VISIBLE : View.GONE);

        // The report reference gets its own line when it is not already the
        // subtitle, so it is never the only place the report number appears.
        String reportId = history.getReportId();
        boolean hasReportRef = reportId != null && !reportId.trim().isEmpty();
        holder.reportRef.setVisibility(hasReportRef ? View.VISIBLE : View.GONE);
        if (hasReportRef) holder.reportRef.setText("Report #" + reportId.trim());

        holder.timestamp.setText(AdminHistoryDateFormatter.format(row.getTimestamp()));

        bindRowActions(holder, history);
        bindSelection(holder, row);

        holder.detailsBtn.setOnClickListener(v -> {
            if (detailsListener != null) detailsListener.onViewDetails(history);
        });

        // The whole card is the long-press target rather than the checkbox, so the
        // gesture works whether or not selection mode is already showing. The
        // listener returns true to consume the press, which stops it from also
        // being treated as a tap on the card.
        holder.itemView.setOnLongClickListener(v -> {
            if (longPressListener == null) return false;
            longPressListener.onRowLongPressed(row.getId());
            return true;
        });
    }

    /** Points the row action at whichever meaning the current view calls for. */
    private void bindRowActions(RowHolder holder, History history) {
        if (trashMode) {
            holder.rowActionBtn.setImageResource(R.drawable.ic_restore);
            holder.rowActionBtn.setContentDescription("Restore this record");
            holder.rowActionBtn.setOnClickListener(v -> {
                if (rowActionListener != null) rowActionListener.onRowRestore(history);
            });
            holder.hardDeleteBtn.setVisibility(View.VISIBLE);
        } else {
            holder.rowActionBtn.setImageResource(R.drawable.ic_delete_trash);
            holder.rowActionBtn.setContentDescription("Move this record to trash");
            holder.rowActionBtn.setOnClickListener(v -> {
                if (rowActionListener != null) rowActionListener.onRowMoveToTrash(history);
            });
            // Withheld here on purpose; see setTrashMode().
            holder.hardDeleteBtn.setVisibility(View.GONE);
        }

        holder.hardDeleteBtn.setOnClickListener(v -> {
            if (rowActionListener != null) {
                rowActionListener.onRowDeletePermanently(history);
            }
        });
    }

    /**
     * Marks a selected card and wires its checkbox.
     *
     * <p>The listener is detached before the state is set. A recycled holder still
     * carries the previous row's checked state, and assigning to it would otherwise
     * fire that row's listener and toggle the selection the admin just made.
     *
     * <p>The checkbox itself is only given a visibility while selection mode is on.
     * A recycled holder is reused across the mode boundary, so it is set on every
     * bind rather than once at creation.
     */
    private void bindSelection(RowHolder holder, AdminHistoryRow row) {
        boolean selected = selectionMode && selectedIds.contains(row.getId());

        holder.selectBox.setVisibility(selectionMode ? View.VISIBLE : View.GONE);
        holder.selectBox.setOnCheckedChangeListener(null);
        holder.selectBox.setChecked(selected);
        if (selectionMode) {
            holder.selectBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                int id = row.getId();
                if (isChecked) {
                    selectedIds.add(id);
                } else {
                    selectedIds.remove(id);
                }
                holder.itemView.setAlpha(isChecked ? 0.6f : 1f);
                notifySelectionChanged();
            });
        }

        holder.itemView.setActivated(selected);
        holder.itemView.setAlpha(selected ? 0.6f : 1f);
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class RowHolder extends RecyclerView.ViewHolder {
        final TextView icon;
        final TextView title;
        final TextView subtitle;
        final TextView reportRef;
        final TextView timestamp;
        final CheckBox selectBox;
        final ImageButton detailsBtn;
        final ImageButton rowActionBtn;
        final ImageButton hardDeleteBtn;

        RowHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.adminHistoryIcon);
            title = itemView.findViewById(R.id.adminHistoryTitle);
            subtitle = itemView.findViewById(R.id.adminHistorySubtitle);
            reportRef = itemView.findViewById(R.id.adminHistoryReportRef);
            timestamp = itemView.findViewById(R.id.adminHistoryTimestamp);
            selectBox = itemView.findViewById(R.id.adminHistorySelectBox);
            detailsBtn = itemView.findViewById(R.id.adminHistoryDetailsBtn);
            rowActionBtn = itemView.findViewById(R.id.adminHistorySoftDeleteBtn);
            hardDeleteBtn = itemView.findViewById(R.id.adminHistoryHardDeleteBtn);
        }
    }
}