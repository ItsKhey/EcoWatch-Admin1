package com.example.ecowatchadmin1.data.remote;

import android.content.Context;

import com.example.ecowatchadmin1.utils.SessionManager;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Central Retrofit/OkHttp client for the EcoWatch Android app.
 *
 * The JWT is read for every request so a token refresh or logout is picked up
 * without rebuilding Retrofit. Login requests are deliberately sent without a
 * stale Authorization header.
 */
public final class ApiClient {
    private static final Object CLIENT_LOCK = new Object();

    private static volatile Retrofit retrofit;

    /**
     * Discards the cached Retrofit instance.
     *
     * Called when the server address changes, so the next request is built
     * against the new address instead of the one captured at first use.
     */
    public static void reset() {
        synchronized (CLIENT_LOCK) {
            retrofit = null;
        }
    }

    private ApiClient() {
        // Utility class.
    }

    public static ApiService getApiService(Context context) {
        return getRetrofit(context).create(ApiService.class);
    }

    public static AdminApiService getAdminApiService(Context context) {
        return getRetrofit(context).create(AdminApiService.class);
    }

    private static Retrofit getRetrofit(Context context) {
        if (retrofit == null) {
            synchronized (CLIENT_LOCK) {
                if (retrofit == null) {
                    Context applicationContext = context.getApplicationContext();
                    SessionManager sessionManager = new SessionManager(applicationContext);

                    HttpLoggingInterceptor loggingInterceptor =
                            new HttpLoggingInterceptor();
                    // BASIC logs request/status lines but never bodies or headers,
                    // which avoids accidentally logging credentials or JWTs.
                    loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BASIC);

                    OkHttpClient httpClient = new OkHttpClient.Builder()
                            .connectTimeout(30, TimeUnit.SECONDS)
                            .readTimeout(45, TimeUnit.SECONDS)
                            .writeTimeout(45, TimeUnit.SECONDS)
                            .retryOnConnectionFailure(true)
                            .addInterceptor(new AuthInterceptor(sessionManager))
                            .addInterceptor(loggingInterceptor)
                            .build();

                    retrofit = new Retrofit.Builder()
                            .baseUrl(ServerConfig.apiBaseUrl(applicationContext))
                            .client(httpClient)
                            .addConverterFactory(GsonConverterFactory.create())
                            .build();
                }
            }
        }
        return retrofit;
    }

    private static final class AuthInterceptor implements Interceptor {
        private final SessionManager sessionManager;

        private AuthInterceptor(SessionManager sessionManager) {
            this.sessionManager = sessionManager;
        }

        @Override
        public Response intercept(Chain chain) throws IOException {
            Request original = chain.request();
            String path = original.url().encodedPath();
            boolean isLoginRequest = path.endsWith("/admin/login")
                    || path.endsWith("/auth/login")
                    || path.endsWith("/personnel/login");

            Request.Builder requestBuilder = original.newBuilder()
                    .removeHeader("Authorization");

            if (!isLoginRequest) {
                String token = sessionManager.fetchAuthToken();
                if (token != null && !token.trim().isEmpty()
                        && !token.regionMatches(true, 0, "mock_", 0, 5)) {
                    requestBuilder.header("Authorization", "Bearer " + token.trim());
                }
            }

            Response response = chain.proceed(requestBuilder.build());
            if (!isLoginRequest && response.code() == 401) {
                // A rejected token must not continue to be used by later polls.
                sessionManager.clearSession();
            }
            return response;
        }
    }
}
