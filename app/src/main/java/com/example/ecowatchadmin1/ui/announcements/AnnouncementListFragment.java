package com.example.ecowatchadmin1.ui.announcements;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.ecowatchadmin1.R;
import com.example.ecowatchadmin1.data.models.Announcement;
import com.example.ecowatchadmin1.data.repository.AnnouncementRepository;
import com.example.ecowatchadmin1.ui.home.DashboardActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * Every announcement the administrator has published, newest first.
 *
 * <p>Reads {@code GET /api/announcements}, the admin-scoped feed, so it shows
 * what the administrator published rather than what a resident received. The
 * dashboard's "Your Announcements" preview links here for the complete list.
 *
 * <p>Nothing is cached locally: the list reflects the backend on every visit so
 * the administrator never sees a withdrawn announcement presented as live.
 *
 * <p>Paged by appending until a short page comes back. A single capped call
 * would silently hide anything past the first page, which for a long-lived
 * announcement history means announcements that were published are simply absent.
 */
public class AnnouncementListFragment extends Fragment {

    private LinearLayout announcementList;
    private TextView emptyText;
    private TextView errorText;
    private TextView titleView;
    private TextView loadMoreView;
    private TextView endOfListView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private AnnouncementRepository repository;

    /** Rows accumulated across pages, newest first. */
    private final List<Announcement> announcements = new ArrayList<>();

    /** True when the last page came back full, so another one may exist. */
    private boolean hasMorePages;

    /** Guards against a second page request while one is already in flight. */
    private boolean loading;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_announcements, container, false);

        repository = new AnnouncementRepository(requireActivity().getApplication());

        announcementList = view.findViewById(R.id.announcementsList);
        emptyText = view.findViewById(R.id.announcementsEmptyText);
        errorText = view.findViewById(R.id.announcementsErrorText);
        titleView = view.findViewById(R.id.announcementsTitle);
        loadMoreView = view.findViewById(R.id.announcementsLoadMore);
        endOfListView = view.findViewById(R.id.announcementsEndOfList);
        swipeRefreshLayout = view.findViewById(R.id.announcementsSwipeRefresh);

        swipeRefreshLayout.setColorSchemeColors(
                getResources().getColor(R.color.green, null));
        swipeRefreshLayout.setOnRefreshListener(this::reloadFromStart);

        loadMoreView.setOnClickListener(v -> loadNextPage());

        ImageButton btnBack = view.findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> {
                if (getParentFragmentManager().getBackStackEntryCount() > 0) {
                    getParentFragmentManager().popBackStack();
                } else if (getActivity() instanceof DashboardActivity) {
                    ((DashboardActivity) getActivity()).navigateToHome();
                } else if (getActivity() != null) {
                    getActivity().finish();
                }
            });
        }

        reloadFromStart();

        return view;
    }

    /**
     * Discards what is loaded and refetches from the first page.
     *
     * <p>Used by pull-to-refresh and on resume, so a publish or a delete from
     * elsewhere is picked up rather than leaving a stale list on screen.
     */
    private void reloadFromStart() {
        announcements.clear();
        hasMorePages = true;
        loadNextPage();
    }

    private void loadNextPage() {
        if (loading || !hasMorePages) return;
        loading = true;
        loadMoreView.setVisibility(View.GONE);

        // The offset is the number already held, so appending a page never
        // re-fetches or skips a row.
        final int offset = announcements.size();

        repository.getAnnouncements(
                AnnouncementRepository.DEFAULT_PAGE_SIZE,
                offset,
                new AnnouncementRepository.AnnouncementListCallback() {
                    @Override
                    public void onSuccess(List<Announcement> page) {
                        if (getView() == null) return;
                        loading = false;

                        int received = page == null ? 0 : page.size();
                        if (page != null) announcements.addAll(page);

                        // A short page means the end of the history. A full page
                        // only means there might be more, so the control stays.
                        hasMorePages = received
                                >= AnnouncementRepository.DEFAULT_PAGE_SIZE;

                        render();
                        stopRefreshing();
                    }

                    @Override
                    public void onError(String error) {
                        if (getView() == null) return;
                        loading = false;
                        // The pages already loaded stay on screen; the error only
                        // explains why the next one never arrived.
                        errorText.setText(error);
                        errorText.setVisibility(View.VISIBLE);
                        render();
                        stopRefreshing();
                    }
                });
    }

    private void render() {
        announcementList.removeAllViews();

        int total = announcements.size();
        emptyText.setVisibility(total == 0 ? View.VISIBLE : View.GONE);
        titleView.setText(getString(R.string.announcements) + " (" + total + ")");

        for (int i = 0; i < total; i++) {
            Announcement announcement = announcements.get(i);
            if (announcement == null) continue;

            View row = getLayoutInflater().inflate(
                    R.layout.item_announcement, announcementList, false);
            AnnouncementRowBinder.bind(row, announcement);
            attachDelete(row, announcement);
            announcementList.addView(row);
        }

        // The footer only appears once there is something to act on, so the list
        // does not end in a dead control.
        boolean canLoadMore = hasMorePages && total > 0;
        loadMoreView.setVisibility(canLoadMore ? View.VISIBLE : View.GONE);
        endOfListView.setVisibility(!hasMorePages && total > 0 ? View.VISIBLE : View.GONE);
    }

    /**
     * Wires the row's delete control, behind a confirmation.
     *
     * <p>The dialog states plainly that this is permanent. Announcements have no
     * trash table, so there is no undo and no recovery, and a single tap must not
     * be able to remove a notice residents may still be reading.
     */
    private void attachDelete(@NonNull View row, @NonNull Announcement announcement) {
        View deleteView = row.findViewById(R.id.announcementDelete);
        if (deleteView == null) return;

        final String announcementId = announcement.getAnnouncementId();
        if (announcementId == null || announcementId.trim().isEmpty()) {
            // Without a server id there is no row to delete, so the control is
            // hidden rather than left as a button that cannot work.
            deleteView.setVisibility(View.GONE);
            return;
        }

        deleteView.setOnClickListener(v -> new AlertDialog.Builder(requireContext())
                .setTitle(R.string.announcement_delete_confirm_title)
                .setMessage(getString(
                        R.string.announcement_delete_confirm_message,
                        announcement.getTitle() == null
                                ? getString(R.string.announcement_untitled)
                                : announcement.getTitle()))
                .setPositiveButton(R.string.announcement_delete, (dialog, which) ->
                        confirmDelete(announcementId))
                .setNegativeButton(android.R.string.cancel, null)
                .show());
    }

    private void confirmDelete(@NonNull String announcementId) {
        repository.deleteAnnouncement(announcementId,
                new AnnouncementRepository.AnnouncementDeleteCallback() {
                    @Override
                    public void onSuccess() {
                        if (getView() == null) return;
                        Toast.makeText(requireContext(), R.string.announcement_deleted,
                                Toast.LENGTH_SHORT).show();
                        // Reloaded rather than removed locally: an announcement
                        // may have expired in the meantime, and the server is the
                        // authority on what still exists.
                        reloadFromStart();
                    }

                    @Override
                    public void onError(String error) {
                        if (getView() == null) return;
                        Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void stopRefreshing() {
        if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Picks up anything published from the composer or removed elsewhere.
        reloadFromStart();
    }
}
