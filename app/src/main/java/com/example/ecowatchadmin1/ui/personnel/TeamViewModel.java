package com.example.ecowatchadmin1.ui.personnel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.ecowatchadmin1.data.models.CleaningTeam;
import com.example.ecowatchadmin1.data.repository.CleaningTeamRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Holds the cleaning crews behind the Teams half of the Personnel screen.
 *
 * <p>Mirrors {@link VehicleViewModel}: the whole list is held once and the
 * chips filter it locally, so switching between All / Ready / No leader /
 * Understaffed is instant and costs no request. A refresh is the only thing
 * that goes back to the server.
 *
 * <p>The list endpoint omits member lists, so a team's details are read on
 * demand through {@link #loadTeam}. That read is also what the details dialog
 * refreshes from after a member is added or removed.
 */
public class TeamViewModel extends AndroidViewModel {

    private final CleaningTeamRepository repository;
    private final MutableLiveData<List<CleaningTeam>> teams =
            new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<String> message = new MutableLiveData<>();

    /**
     * Guards against a slow first response landing after a pull-to-refresh and
     * replacing fresher data with staler data.
     */
    private final AtomicLong requestVersion = new AtomicLong(0);
    private final AtomicBoolean requestInFlight = new AtomicBoolean(false);

    public TeamViewModel(@NonNull Application application) {
        super(application);
        repository = new CleaningTeamRepository(application);
    }

    public LiveData<List<CleaningTeam>> getTeams() { return teams; }
    public LiveData<Boolean> getLoading() { return loading; }
    public LiveData<String> getError() { return error; }
    public LiveData<String> getMessage() { return message; }

    public void loadTeams() { request(false); }

    public void refreshTeams() { request(true); }

    private void request(boolean force) {
        // A second request only replaces the first when it is a deliberate
        // refresh; otherwise the in-flight one is left to finish on its own.
        if (!force && !requestInFlight.compareAndSet(false, true)) return;

        loading.setValue(true);
        final long version = requestVersion.incrementAndGet();

        repository.getTeams(new CleaningTeamRepository.TeamListCallback() {
            @Override
            public void onSuccess(List<CleaningTeam> result) {
                requestInFlight.set(false);
                if (version != requestVersion.get()) return;
                teams.setValue(result == null ? new ArrayList<>() : result);
                loading.setValue(false);
            }

            @Override
            public void onError(String failure) {
                requestInFlight.set(false);
                if (version != requestVersion.get()) return;
                loading.setValue(false);
                error.setValue(failure);
            }
        });
    }

    /** Reads one crew together with its members. */
    public void loadTeam(String teamId, CleaningTeamRepository.TeamCallback callback) {
        repository.getTeam(teamId, callback);
    }

    public void loadTeamTaskHistory(String teamId, int page, int limit,
            CleaningTeamRepository.TeamTaskHistoryCallback callback) {
        repository.getTeamTaskHistory(teamId, page, limit, callback);
    }

    public void createTeam(String teamName, String description, String label, String leaderId,
                           List<String> memberIds,
                           CleaningTeamRepository.TeamCallback callback) {
        repository.createTeam(teamName, description, label, leaderId, memberIds,
                mutationCallback(callback, "Team created."));
    }

    public void updateTeam(String teamId, String teamName, String description, String label,
                           String leaderId, List<String> memberIds,
                           CleaningTeamRepository.TeamCallback callback) {
        repository.updateTeam(teamId, teamName, description, label, leaderId, memberIds,
                mutationCallback(callback, "Team updated."));
    }

    public void deleteTeam(String teamId, CleaningTeamRepository.TeamCallback callback) {
        repository.deleteTeam(teamId, mutationCallback(callback, "Team deleted."));
    }

    public void addMember(String teamId, String personnelId,
                          CleaningTeamRepository.TeamCallback callback) {
        repository.addMember(teamId, personnelId,
                mutationCallback(callback, "Member added to the team."));
    }

    public void removeMember(String teamId, String personnelId,
                             CleaningTeamRepository.TeamCallback callback) {
        repository.removeMember(teamId, personnelId,
                mutationCallback(callback, "Member removed from the team."));
    }

    /**
     * Wraps a mutation so a success or failure line is surfaced without every
     * dialog having to repeat that step. Refreshing the list is left to the
     * caller's callback, which is also what repaints the dialog's own context.
     */
    private CleaningTeamRepository.TeamCallback mutationCallback(
            CleaningTeamRepository.TeamCallback callback, String successMessage) {
        return new CleaningTeamRepository.TeamCallback() {
            @Override
            public void onSuccess(CleaningTeam team) {
                message.setValue(successMessage);
                if (callback != null) callback.onSuccess(team);
            }

            @Override
            public void onError(String failure) {
                error.setValue(failure);
                if (callback != null) callback.onError(failure);
            }
        };
    }

    /**
     * Clears the one-shot error so a rotation does not re-show the same toast.
     *
     * <p>Needed because this is a plain string LiveData rather than an Event:
     * without it the failure would be delivered again on every re-subscribe.
     */
    public void clearError() {
        error.setValue(null);
    }

    /** Clears a one-shot message so a rotation does not re-show the same toast. */
    public void clearMessage() {
        message.setValue(null);
    }
}
