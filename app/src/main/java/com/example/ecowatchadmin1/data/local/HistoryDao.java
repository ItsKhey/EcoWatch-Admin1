package com.example.ecowatchadmin1.data.local;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.ecowatchadmin1.data.models.History;

import java.util.List;

@Dao
public interface HistoryDao {

    /**
     * How many rows a cache-backed feed query will return.
     *
     * <p>Android allocates one {@code CursorWindow} for a whole cursor, not one
     * per row, and that window is around 2 MB shared with the rest of the process.
     * Reading more rows than fit raises {@code SQLiteBlobTooBigException} from
     * inside Room's refresh runnable, which surfaces as a crash rather than an
     * empty list.
     *
     * <p>500 is comfortably inside the window for this entity and still exceeds
     * the server's own page size, so the cache holds everything a session can
     * actually page in. It is declared here rather than inlined in the query
     * because it has to be a compile-time constant for the annotation.
     */
    int ADMIN_FEED_LIMIT = 500;

    /** The trash is bounded for the same reason as the feed. */
    int ADMIN_TRASH_LIMIT = 500;

    /**
     * Every active row of the administrator's audit trail, newest first.
     *
     * <p>Restricted to rows that carry a server id. This app only ever writes
     * history that the audit endpoint returned, and every such row has its
     * {@code history_id} in {@code mongoId}, so the predicate is what separates a
     * real recorded decision from a row a client invented.
     *
     * <p>Deliberately unfiltered by category and observed exactly once for the
     * life of the screen. The previous version took a category argument and the
     * screen re-observed it on every tab tap, which produced two defects:
     *
     * <ul>
     *   <li>ALL is a view state, not a stored category. No row carries
     *       {@code category = 'ALL'}, so {@code category = :category} matched
     *       nothing and the All tab was always empty.</li>
     *   <li>Each tap registered another observer that was never removed. They all
     *       wrote the same list, and Room posts their results in an order that is
     *       not guaranteed, so one tab's rows could be overwritten by another's
     *       before the screen drew them. Re-tapping the tab registered a further
     *       copy that landed last, which is why a second tap appeared to fix it.</li>
     * </ul>
     *
     * <p>One observation plus a local filter is both correct and instant: the tab
     * changes without a query, and the rows cannot disagree with each other.
     *
     * <p>Bounded at {@link #ADMIN_FEED_LIMIT}. Android backs a cursor with one
     * shared {@code CursorWindow} of roughly 2 MB for the entire result, not per
     * row, so an unbounded {@code SELECT *} over a table that has grown large
     * fails with {@code SQLiteBlobTooBigException} and takes the screen down with
     * it. The limit keeps the result well inside the window while still covering
     * more rows than any one page can supply.
     */
    @Query("SELECT * FROM history WHERE isDeleted = 0 AND mongoId IS NOT NULL "
            + "ORDER BY timestampValue DESC, id DESC "
            + "LIMIT " + ADMIN_FEED_LIMIT)
    LiveData<List<History>> observeAdminFeed();

    /**
     * The audit records the server has moved to the trash, newest removal first.
     *
     * <p>Bounded for the same {@code CursorWindow} reason as the feed, and
     * restricted to server rows for the same reason: a trashed row is only
     * meaningful if the server knows about it.
     */
    @Query("SELECT * FROM history WHERE isDeleted = 1 AND mongoId IS NOT NULL "
            + "ORDER BY COALESCE(deletedAt, timestampValue) DESC, id DESC "
            + "LIMIT " + ADMIN_TRASH_LIMIT)
    LiveData<List<History>> observeAdminTrash();

    /**
     * Removes the rows no client is allowed to show.
     *
     * <p>A row without a server id is one this app invented: a locally fabricated
     * status change, a locally fabricated submission, or a record the resident-scoped
     * {@code my-history} route returned before that route was dropped. None of them
     * is a recorded decision, and an audit trail that shows them is lying about what
     * happened. They are purged rather than merely filtered so they cannot reappear
     * through any other query.
     *
     * @return the number of fabricated rows removed
     */
    @Query("DELETE FROM history WHERE mongoId IS NULL")
    int deleteLocalDuplicates();

    @Query("SELECT * FROM history WHERE mongoId = :mongoId LIMIT 1")
    History getHistoryByMongoId(String mongoId);

    /**
     * Collapses the duplicate copies a past sync left behind, keeping the newest.
     *
     * <p>A build cached the feed by inserting each row under a fresh local key,
     * so {@code INSERT OR REPLACE} could never match the row it meant to update
     * and every pull-to-refresh appended a full copy. After enough refreshes the
     * table grew past what Android's {@code CursorWindow} can hold and the feed
     * query began throwing {@code SQLiteBlobTooBigException} on open.
     *
     * <p>Only rows carrying a server id are considered. A row without one was
     * written by the resident feed and has no server counterpart to collapse
     * against, so it is left alone rather than being deleted as collateral.
     *
     * @return the number of redundant rows removed
     */
    @Query("DELETE FROM history WHERE mongoId IS NOT NULL "
            + "AND id NOT IN (SELECT MAX(id) FROM history "
            + "WHERE mongoId IS NOT NULL GROUP BY mongoId)")
    int collapseDuplicateHistoryRows();

    /**
     * Upserts a page of the admin feed.
     *
     * <p>Keyed on the server's history id, which the admin route sends as
     * {@code history_id}. {@link #deleteByMongoIds} runs immediately before this,
     * because Room's REPLACE resolves on the primary key and the local key is
     * deliberately left at 0 for Room to assign. Without that delete, every sync
     * appended a fresh duplicate of each row instead of updating it in place, so
     * the counts grew on each pull and deleting one id removed only one copy.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAdminHistory(List<History> historyList);

    /** Removes the cached copies of the given server ids, so a re-insert replaces. */
    @Query("DELETE FROM history WHERE mongoId IN (:mongoIds)")
    void deleteByMongoIds(List<String> mongoIds);

    @Query("DELETE FROM history")
    void deleteAllHistory();

    @Query("DELETE FROM history WHERE id IN (:ids)")
    void deleteHistoryByIds(List<Integer> ids);

    @Query("UPDATE history SET isDeleted = 1, deletedAt = :deletedAt WHERE id IN (:ids)")
    void bulkSoftDelete(List<Integer> ids, long deletedAt);

    @Query("UPDATE history SET isDeleted = 0, deletedAt = NULL WHERE id IN (:ids)")
    void bulkRestore(List<Integer> ids);

    @Query("SELECT * FROM history WHERE id IN (:ids)")
    List<History> getHistoryByIds(List<Integer> ids);
}
