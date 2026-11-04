package com.example.ecowatchadmin1.ui.personnel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.ecowatchadmin1.data.models.Vehicle;
import com.example.ecowatchadmin1.data.remote.VehicleJsonParser;
import com.example.ecowatchadmin1.data.repository.VehicleRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Holds the fleet behind the Vehicles half of the Personnel screen.
 *
 * <p>Mirrors {@link PersonnelViewModel}: the whole fleet is held once and the
 * chips filter it locally, so switching between All / Available / Assigned /
 * Maintenance is instant and costs no request. A refresh is the only thing that
 * goes back to the server.
 */
public class VehicleViewModel extends AndroidViewModel {

    private final VehicleRepository repository;
    private final MutableLiveData<List<Vehicle>> vehicles = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<VehicleJsonParser.VehicleCounts> counts = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<String> message = new MutableLiveData<>();

    /**
     * Guards against a slow first response landing after a pull-to-refresh and
     * replacing fresher data with staler data.
     */
    private final AtomicLong requestVersion = new AtomicLong(0);
    private final AtomicBoolean requestInFlight = new AtomicBoolean(false);

    public VehicleViewModel(@NonNull Application application) {
        super(application);
        repository = new VehicleRepository(application);
    }

    public LiveData<List<Vehicle>> getVehicles() { return vehicles; }
    public LiveData<VehicleJsonParser.VehicleCounts> getCounts() { return counts; }
    public LiveData<Boolean> getLoading() { return loading; }
    public LiveData<String> getError() { return error; }
    public LiveData<String> getMessage() { return message; }

    public void loadFleet() { request(false); }

    public void refreshFleet() { request(true); }

    public void loadVehicleTaskCounts(String vehicleId,
            VehicleRepository.VehicleTaskCountsCallback callback) {
        repository.getVehicleTaskCounts(vehicleId, callback);
    }

    public void loadVehicleTaskHistory(String vehicleId, int page, int limit,
            VehicleRepository.VehicleTaskHistoryCallback callback) {
        repository.getVehicleTaskHistory(vehicleId, page, limit, callback);
    }

    private void request(boolean force) {
        // A second request only replaces the first when it is a deliberate
        // refresh; otherwise the in-flight one is left to finish on its own.
        if (!force && !requestInFlight.compareAndSet(false, true)) return;

        loading.setValue(true);
        final long version = requestVersion.incrementAndGet();

        repository.getFleet(new VehicleRepository.FleetCallback() {
            @Override
            public void onSuccess(VehicleRepository.FleetSnapshot snapshot) {
                requestInFlight.set(false);
                if (version != requestVersion.get()) return;

                vehicles.setValue(snapshot.getVehicles() == null
                        ? new ArrayList<>() : snapshot.getVehicles());
                counts.setValue(snapshot.getCounts());
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

    public void addVehicle(String plateNumber, String vehicleType,
                           String ratedLoadCapacity, String notes,
                           VehicleRepository.VehicleResultCallback callback) {
        // New vehicles arrive Available. The server refuses anything else, and a
        // form that offered the choice would only ever produce an error.
        repository.addVehicle(plateNumber, vehicleType, ratedLoadCapacity,
                Vehicle.STATUS_AVAILABLE, notes, toResultCallback(callback));
    }

    public void updateVehicle(String vehicleId, String plateNumber, String vehicleType,
                              String ratedLoadCapacity, String status,
                              String currentReportId, String notes,
                              VehicleRepository.VehicleResultCallback callback) {
        repository.updateVehicle(vehicleId, plateNumber, vehicleType, ratedLoadCapacity,
                status, currentReportId, notes, toResultCallback(callback));
    }

    public void recordService(String vehicleId, String servicedAt,
                              VehicleRepository.VehicleResultCallback callback) {
        repository.recordService(vehicleId, servicedAt, toResultCallback(callback));
    }

    public void deleteVehicle(String vehicleId,
                              VehicleRepository.VehicleResultCallback callback) {
        repository.deleteVehicle(vehicleId, toResultCallback(callback));
    }

    /**
     * Collapses the repository's two channels into one call the UI can write as
     * a lambda.
     *
     * <p>Errors also go out as a message so the ViewModel's error LiveData keeps
     * working for the list-level failures the repository reports itself.
     */
    private VehicleRepository.VehicleCallback toResultCallback(
            final VehicleRepository.VehicleResultCallback callback) {
        // Anonymous rather than a lambda: VehicleCallback declares both an error
        // and a success channel, so it is not a functional interface.
        return new VehicleRepository.VehicleCallback() {
            @Override
            public void onSuccess(Vehicle vehicle) {
                if (callback != null) callback.onResult(vehicle, null);
            }

            @Override
            public void onError(String failure) {
                error.setValue(failure);
                if (callback != null) callback.onResult(null, failure);
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
    public void clearMessage() { message.setValue(null); }
}
