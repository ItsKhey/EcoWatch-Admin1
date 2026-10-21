package com.example.ecowatchadmin1.data.repository;

import android.app.Application;

import androidx.annotation.NonNull;

import com.example.ecowatchadmin1.data.models.Vehicle;
import com.example.ecowatchadmin1.data.remote.AdminApiService;
import com.example.ecowatchadmin1.data.remote.ApiClient;
import com.example.ecowatchadmin1.data.remote.VehicleCreateRequest;
import com.example.ecowatchadmin1.data.remote.VehicleJsonParser;
import com.example.ecowatchadmin1.data.remote.VehicleServiceRequest;
import com.example.ecowatchadmin1.data.remote.VehicleUpdateRequest;
import com.example.ecowatchadmin1.utils.SessionManager;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Vehicle fleet repository, the counterpart to {@link PersonnelRepository}.
 *
 * <p>Every method is backed by the real API; there is no local fallback or
 * seeded sample fleet, so what the screen shows is what the database holds.
 */
public class VehicleRepository {

    /** List plus the three card numbers, read together so they cannot disagree. */
    public static final class FleetSnapshot {
        private final List<Vehicle> vehicles;
        private final VehicleJsonParser.VehicleCounts counts;

        public FleetSnapshot(List<Vehicle> vehicles, VehicleJsonParser.VehicleCounts counts) {
            this.vehicles = vehicles;
            this.counts = counts;
        }

        public List<Vehicle> getVehicles() { return vehicles; }
        public VehicleJsonParser.VehicleCounts getCounts() { return counts; }
    }

    private final AdminApiService apiService;
    private final SessionManager sessionManager;

    public VehicleRepository(@NonNull Application application) {
        apiService = ApiClient.getAdminApiService(application);
        sessionManager = new SessionManager(application);
    }

    /**
     * Reads the fleet and its counts in one round trip.
     *
     * <p>Filtering is left to the client so the chips can switch without a
     * request each; the server still accepts a status so a larger fleet can be
     * narrowed server-side later without changing this contract.
     */
    public void getFleet(FleetCallback callback) {
        if (!requireAdmin(callback)) return;
        apiService.getVehicles(null).enqueue(new Callback<JsonElement>() {
            @Override
            public void onResponse(Call<JsonElement> call, Response<JsonElement> response) {
                if (!isSuccessful(response, callback, "fleet")) return;
                try {
                    JsonElement body = response.body();
                    List<Vehicle> vehicles = VehicleJsonParser.parseList(body);
                    VehicleJsonParser.VehicleCounts counts = VehicleJsonParser.parseCounts(body);
                    callback.onSuccess(new FleetSnapshot(vehicles, counts));
                } catch (RuntimeException error) {
                    callback.onError(userFacing(error));
                }
            }

            @Override
            public void onFailure(Call<JsonElement> call, Throwable throwable) {
                callback.onError(networkMessage(throwable));
            }
        });
    }

    public void addVehicle(String plateNumber, String vehicleType,
                           String ratedLoadCapacity, String status, String notes,
                           VehicleCallback callback) {
        if (!requireAdmin(callback)) return;
        if (isBlank(plateNumber)) {
            callback.onError("Plate number is required.");
            return;
        }
        if (isBlank(vehicleType)) {
            callback.onError("Vehicle type is required.");
            return;
        }

        VehicleCreateRequest request = new VehicleCreateRequest(
                plateNumber.trim(), vehicleType.trim(),
                blankToNull(ratedLoadCapacity),
                status, blankToNull(notes));

        apiService.createVehicle(request).enqueue(new Callback<JsonElement>() {
            @Override
            public void onResponse(Call<JsonElement> call, Response<JsonElement> response) {
                if (!isSuccessful(response, callback, "vehicle registration")) return;
                callback.onSuccess(VehicleJsonParser.parseVehicle(response.body()));
            }

            @Override
            public void onFailure(Call<JsonElement> call, Throwable throwable) {
                callback.onError(networkMessage(throwable));
            }
        });
    }

    public void updateVehicle(String vehicleId, String plateNumber, String vehicleType,
                              String ratedLoadCapacity, String status,
                              String currentReportId, String notes,
                              VehicleCallback callback) {
        if (!requireAdmin(callback)) return;
        if (isBlank(vehicleId)) {
            callback.onError("Vehicle ID is required.");
            return;
        }

        VehicleUpdateRequest request = new VehicleUpdateRequest(
                blankToNull(plateNumber), blankToNull(vehicleType),
                blankToNull(ratedLoadCapacity), blankToNull(status),
                blankToNull(currentReportId), blankToNull(notes));

        apiService.updateVehicle(vehicleId.trim(), request).enqueue(new Callback<JsonElement>() {
            @Override
            public void onResponse(Call<JsonElement> call, Response<JsonElement> response) {
                if (!isSuccessful(response, callback, "vehicle update")) return;
                callback.onSuccess(VehicleJsonParser.parseVehicle(response.body()));
            }

            @Override
            public void onFailure(Call<JsonElement> call, Throwable throwable) {
                callback.onError(networkMessage(throwable));
            }
        });
    }

    public void recordService(String vehicleId, String servicedAt, VehicleCallback callback) {
        if (!requireAdmin(callback)) return;
        if (isBlank(vehicleId)) {
            callback.onError("Vehicle ID is required.");
            return;
        }

        apiService.recordVehicleService(vehicleId.trim(), new VehicleServiceRequest(servicedAt))
                .enqueue(new Callback<JsonElement>() {
                    @Override
                    public void onResponse(Call<JsonElement> call, Response<JsonElement> response) {
                        if (!isSuccessful(response, callback, "service record")) return;
                        callback.onSuccess(VehicleJsonParser.parseVehicle(response.body()));
                    }

                    @Override
                    public void onFailure(Call<JsonElement> call, Throwable throwable) {
                        callback.onError(networkMessage(throwable));
                    }
                });
    }

    public void deleteVehicle(String vehicleId, VehicleCallback callback) {
        if (!requireAdmin(callback)) return;
        if (isBlank(vehicleId)) {
            callback.onError("Vehicle ID is required.");
            return;
        }

        apiService.deleteVehicle(vehicleId.trim()).enqueue(new Callback<JsonElement>() {
            @Override
            public void onResponse(Call<JsonElement> call, Response<JsonElement> response) {
                if (!isSuccessful(response, callback, "vehicle removal")) return;
                callback.onSuccess(null);
            }

            @Override
            public void onFailure(Call<JsonElement> call, Throwable throwable) {
                callback.onError(networkMessage(throwable));
            }
        });
    }

    private boolean isSuccessful(Response<JsonElement> response, ErrorCallback callback,
                                 String operation) {
        if (response.isSuccessful()) return true;
        if (response.code() == 401) sessionManager.clearSession();
        callback.onError(readError(response, operation));
        return false;
    }

    private boolean requireAdmin(ErrorCallback callback) {
        if (!sessionManager.isLoggedIn() || !sessionManager.isAdmin()) {
            callback.onError("Administrator access is required to manage the fleet.");
            return false;
        }
        return true;
    }

    /**
     * Reads the server's own message out of an error body.
     *
     * <p>The fleet server names the vehicle or the plate when it can ("Plate ABC
     * 123 is already registered"), which is far more useful than a generic
     * "request failed". Returns null when the body carries nothing usable, so the
     * caller falls back to its status-specific message.
     */
    private String readServerMessage(Response<?> response) {
        try (okhttp3.ResponseBody body = response.errorBody()) {
            if (body == null) return null;
            String raw = body.string();
            if (raw == null || raw.trim().isEmpty()) return null;

            try {
                JsonElement parsed = new com.google.gson.JsonParser().parse(raw);
                if (!parsed.isJsonObject()) return null;
                JsonObject object = parsed.getAsJsonObject();
                if (object.has("message") && !object.get("message").isJsonNull()) {
                    return object.get("message").getAsString().trim();
                }
                if (object.has("error") && !object.get("error").isJsonNull()) {
                    return object.get("error").getAsString().trim();
                }
            } catch (RuntimeException ignored) {
                // Not JSON; the status-specific message below is better anyway.
            }
        } catch (IOException ignored) {
            // Body already consumed or unreadable; same fallback.
        }
        return null;
    }

    private String readError(Response<?> response, String operation) {
        String message = readServerMessage(response);
        if (message != null && !message.trim().isEmpty()) return message;

        switch (response.code()) {
            case 400:
                return "The " + operation + " request was invalid.";
            case 401:
                return "Your administrator session has expired. Please log in again.";
            case 403:
                return "You are not authorized to manage the fleet.";
            case 404:
                return "That vehicle was not found.";
            case 409:
                // The server names the plate when it can; this is the fallback.
                return "That vehicle already exists.";
            default:
                return response.code() >= 500
                        ? "The EcoWatch server is temporarily unavailable. Try again later."
                        : "The " + operation + " request failed (" + response.code() + ").";
        }
    }

    private String networkMessage(Throwable throwable) {
        if (throwable instanceof SocketTimeoutException) {
            return "The server took too long to respond. Try again.";
        }
        if (throwable instanceof IOException) {
            return "Unable to reach the EcoWatch server. Check your connection and try again.";
        }
        String message = throwable == null ? null : throwable.getMessage();
        return message == null || message.trim().isEmpty()
                ? "Network error occurred. Please try again."
                : "Network error: " + message;
    }

    private String userFacing(RuntimeException error) {
        String message = error == null ? null : error.getMessage();
        return message == null || message.trim().isEmpty()
                ? "The server returned an invalid fleet response."
                : message;
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public interface ErrorCallback {
        void onError(String message);
    }

    public interface FleetCallback extends ErrorCallback {
        void onSuccess(FleetSnapshot snapshot);
    }

    /**
     * A completed write: either the vehicle, or null when there was none to
     * return. A deletion succeeds with null rather than an error.
     */
    public interface VehicleCallback extends ErrorCallback {
        void onSuccess(Vehicle vehicle);
    }

    /**
     * The same result as a single call.
     *
     * <p>Exists because {@link VehicleCallback} carries both an error channel and
     * a success channel, so it cannot be written as a lambda at the call site.
     */
    @FunctionalInterface
    public interface VehicleResultCallback {
        void onResult(Vehicle vehicle, String error);
    }
}
