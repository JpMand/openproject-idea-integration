package com.github.jpmand.openproject.integration.api;

import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class OPApiBasicAuthenticator implements Authenticator {

    private final String username;
    private final String password;

    public OPApiBasicAuthenticator(String username, String password) {
        this.username = username;
        this.password = password;
    }

    @Override
    public @Nullable Request authenticate(@Nullable Route route, @NotNull Response response) {

        String cred = Credentials.basic(username, password);

        return response.request().newBuilder()
                .addHeader("Authorization", cred)
                .build();
    }
}
