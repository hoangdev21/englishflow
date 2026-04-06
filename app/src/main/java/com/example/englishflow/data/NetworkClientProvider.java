package com.example.englishflow.data;

import java.util.concurrent.TimeUnit;

import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;

public final class NetworkClientProvider {

    private static final ConnectionPool SHARED_CONNECTION_POOL = new ConnectionPool(8, 5, TimeUnit.MINUTES);

    private static final OkHttpClient BASE_CLIENT = new OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .connectionPool(SHARED_CONNECTION_POOL)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build();

    private static final OkHttpClient AI_CLIENT = BASE_CLIENT.newBuilder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    private NetworkClientProvider() {
    }

    public static OkHttpClient getBaseClient() {
        return BASE_CLIENT;
    }

    public static OkHttpClient getAiClient() {
        return AI_CLIENT;
    }
}
