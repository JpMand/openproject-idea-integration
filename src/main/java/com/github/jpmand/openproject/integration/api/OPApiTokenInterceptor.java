package com.github.jpmand.openproject.integration.api;

import com.intellij.openapi.diagnostic.Logger;
import okhttp3.Credentials;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class OPApiTokenInterceptor implements Interceptor {
    private final Logger logger = Logger.getInstance(OPApiTokenInterceptor.class);

    private static final String USERNAME = "apikey";

    public OPApiTokenInterceptor(String token) {
        this.token = token;
    }

    private String token;

    @Override
    public @NotNull Response intercept(@NotNull Interceptor.Chain chain) throws IOException {
        logger.debug("applying api token to request {}", chain.request().url().toString());
        Request request = chain.request().newBuilder().header("Authorization", Credentials.basic(USERNAME, token)).build();
        return chain.proceed(request);
    }
}
