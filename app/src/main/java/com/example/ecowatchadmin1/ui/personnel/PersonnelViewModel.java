package com.example.ecowatchadmin1.ui.personnel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.ecowatchadmin1.data.models.Personnel;
import com.example.ecowatchadmin1.data.remote.PersonnelJsonParser;
import com.example.ecowatchadmin1.data.repository.PersonnelRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** MVVM state holder for the personnel dashboard and account actions. */
public class PersonnelViewModel extends AndroidViewModel {
    private final PersonnelRepository repository;
    private final MutableLiveData<List<Personnel>> personnel = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<String> warning = new MutableLiveData<>();
    private final MutableLiveData<Action> action = new MutableLiveData<>();
    private final AtomicLong listRequestVersion = new AtomicLong();

    public PersonnelViewModel(@NonNull Application application) {
        super(application);
        repository = new PersonnelRepository(application);
    }

    public LiveData<List<Personnel>> getPersonnel() {
        return personnel;
    }

    public LiveData<Boolean> getLoading() {
        return loading;
    }

    public LiveData<String> getError() {
        return error;
    }

    public LiveData<Action> getAction() {
        return action;
    }

    /** Non-fatal outcomes, e.g. an account that was created without its photo. */
    public LiveData<String> getWarning() {
        return warning;
    }

    public void clearWarning() {
        warning.setValue(null);
    }

    public void loadPersonnel() {
        if (Boolean.TRUE.equals(loading.getValue())) return;
        requestList(false);
    }

    public void refreshPersonnel() {
        requestList(true);
    }

    private void requestList(boolean force) {
        if (!force && Boolean.TRUE.equals(loading.getValue())) return;
        final long requestVersion = listRequestVersion.incrementAndGet();
        loading.setValue(true);
        repository.getPersonnel(new PersonnelRepository.PersonnelListCallback() {
            @Override
            public void onSuccess(List<Personnel> result) {
                if (requestVersion != listRequestVersion.get()) return;
                personnel.postValue(result == null ? new ArrayList<>() : result);
                loading.postValue(false);
            }

            @Override
            public void onError(String message) {
                if (requestVersion != listRequestVersion.get()) return;
                loading.postValue(false);
                error.postValue(message);
            }
        });
    }

    public void getDetails(String personnelId, PersonnelRepository.PersonnelCallback callback) {
        repository.getDetails(personnelId, callback);
    }

    /**
     * Creates the account and, when the administrator took a photograph,
     * stores it right after the server assigns the personnel ID.
     *
     * <p>The photo step is deliberately not transactional with the creation:
     * the account must exist before its photo can be attached, and a photo
     * that fails to upload leaves a valid account behind, reported through
     * {@link #getWarning()} instead of an error that would look like the
     * registration itself failed.
     *
     * <p>{@code assignedVehicleId} travels with the create rather than as a
     * follow-up assignment, so a driver is never briefly recorded as driving
     * nothing between the account existing and the truck being attached.
     *
     * @param occupation Regular or Driver.
     * @param assignedVehicleId the truck chosen for a driver, or null.
     */
    public void createPersonnel(String firstName, String lastName, String address,
                                String gmail, String username, String password,
                                String phoneNumber, String messengerLink,
                                String occupation, String assignedVehicleId,
                                byte[] photoBytes) {
        repository.createPersonnel(firstName, lastName, address, gmail, username, password,
                phoneNumber, messengerLink, occupation, assignedVehicleId,
                new PersonnelRepository.PersonnelMutationCallback() {
                    @Override
                    public void onSuccess(PersonnelJsonParser.MutationResult result) {
                        Personnel created = result.getPersonnel();
                        boolean hasPhoto = photoBytes != null && photoBytes.length > 0;
                        boolean hasId = created != null
                                && created.getPersonnelId() != null
                                && !created.getPersonnelId().trim().isEmpty();
                        if (!hasPhoto || !hasId) {
                            finishCreation(result);
                            return;
                        }
                        savePhoto(created.getPersonnelId(), photoBytes, result);
                    }

                    @Override
                    public void onError(String message) {
                        error.postValue(message);
                    }
                });
    }

    private void savePhoto(String personnelId, byte[] photoBytes,
                           PersonnelJsonParser.MutationResult creationResult) {
        repository.uploadPhoto(personnelId, photoBytes,
                new PersonnelRepository.PersonnelCallback() {
                    @Override
                    public void onSuccess(Personnel updated) {
                        finishCreation(new PersonnelJsonParser.MutationResult(
                                updated == null ? creationResult.getPersonnel() : updated,
                                creationResult.getPassword()));
                    }

                    @Override
                    public void onError(String message) {
                        warning.postValue(message);
                        finishCreation(creationResult);
                    }
                });
    }

    private void finishCreation(PersonnelJsonParser.MutationResult result) {
        action.postValue(new Action(ActionType.CREATED, result.getPersonnel(),
                result.getPassword(), "Personnel account created."));
        refreshPersonnel();
    }

    public void updatePersonnel(Personnel person, String firstName, String lastName,
                                String address, String gmail, String username,
                                String phoneNumber, String messengerLink) {
        if (person == null) {
            error.setValue("Personnel record is missing.");
            return;
        }
        repository.updatePersonnel(person.getPersonnelId(), firstName, lastName, address,
                gmail, username, phoneNumber, messengerLink, new PersonnelRepository.PersonnelCallback() {
                    @Override
                    public void onSuccess(Personnel updated) {
                        action.postValue(new Action(ActionType.UPDATED, updated, null,
                                "Personnel details updated."));
                        refreshPersonnel();
                    }

                    @Override
                    public void onError(String message) {
                        error.postValue(message);
                    }
                });
    }

    public void resetPassword(Personnel person, String newPassword) {
        if (person == null) {
            error.setValue("Personnel record is missing.");
            return;
        }
        repository.resetPassword(person.getPersonnelId(), newPassword,
                new PersonnelRepository.PersonnelMutationCallback() {
                    @Override
                    public void onSuccess(PersonnelJsonParser.MutationResult result) {
                        action.postValue(new Action(ActionType.PASSWORD_RESET,
                                result.getPersonnel() == null ? person : result.getPersonnel(),
                                result.getPassword(), "Password reset successfully."));
                    }

                    @Override
                    public void onError(String message) {
                        error.postValue(message);
                    }
                });
    }

    public void updateStatus(String personnelId, Boolean online, Boolean cleaning, String status) {
        repository.updateStatus(personnelId, online, cleaning, status,
                new PersonnelRepository.PersonnelCallback() {
                    @Override
                    public void onSuccess(Personnel updated) {
                        action.postValue(new Action(ActionType.STATUS_UPDATED, updated, null,
                                "Personnel status updated."));
                        refreshPersonnel();
                    }

                    @Override
                    public void onError(String message) {
                        error.postValue(message);
                    }
                });
    }

    public void disablePersonnel(Personnel person) {
        if (person == null) {
            error.setValue("Personnel record is missing.");
            return;
        }
        repository.disablePersonnel(person.getPersonnelId(), new PersonnelRepository.PersonnelCallback() {
            @Override
            public void onSuccess(Personnel updated) {
                action.postValue(new Action(ActionType.DISABLED, updated == null ? person : updated,
                        null, "Personnel account disabled."));
                refreshPersonnel();
            }

            @Override
            public void onError(String message) {
                error.postValue(message);
            }
        });
    }

    public void clearError() {
        error.setValue(null);
    }

    public void clearAction() {
        action.setValue(null);
    }

    public enum ActionType {
        CREATED,
        UPDATED,
        PASSWORD_RESET,
        STATUS_UPDATED,
        DISABLED
    }

    public static final class Action {
        private final ActionType type;
        private final Personnel personnel;
        private final String password;
        private final String message;

        public Action(ActionType type, Personnel personnel, String password, String message) {
            this.type = type;
            this.personnel = personnel;
            this.password = password;
            this.message = message;
        }

        public ActionType getType() {
            return type;
        }

        public Personnel getPersonnel() {
            return personnel;
        }

        public String getPassword() {
            return password;
        }

        public String getMessage() {
            return message;
        }
    }
}
