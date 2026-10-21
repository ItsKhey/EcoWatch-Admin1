package com.example.ecowatchadmin1.data.remote;

import android.content.Context;

/**
 * Backward-compatible facade for repositories that still reference the old
 * client name. All Retrofit and OkHttp instances are created by ApiClient.
 */
public final class RetrofitClient {
    private RetrofitClient() {
        // Utility class.
    }

    public static ApiService getApiService(Context context) {
        return ApiClient.getApiService(context);
    }
}
