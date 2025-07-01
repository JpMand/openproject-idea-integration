package com.github.jpmand.openproject.integration.tasks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jpmand.openproject.integration.api.OPApiBasicAuthenticator;
import com.github.jpmand.openproject.integration.models.abstracts.OPCollectionObject;
import com.github.jpmand.openproject.integration.models.enums.FilterOperator;
import com.github.jpmand.openproject.integration.models.filters.OPFilterObject;
import com.github.jpmand.openproject.integration.models.filters.OPFilterValue;
import com.github.jpmand.openproject.integration.models.wp.OPWorkPackage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.CustomTaskState;
import com.intellij.tasks.Task;
import com.intellij.tasks.impl.BaseRepository;
import com.intellij.tasks.impl.httpclient.NewBaseRepositoryImpl;
import com.intellij.util.xmlb.annotations.Tag;
import de.otto.edison.hal.HalParser;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@Tag("OpenProject")
public class OpenProjectRepository extends NewBaseRepositoryImpl {
    private static final Logger LOG = Logger.getInstance(OpenProjectRepository.class);
    private OkHttpClient client = null;

    public OpenProjectRepository() {
        super();
        getHttpClient(getPassword());
    }

    public OpenProjectRepository(OpenProjectRepositoryType type) {
        super(type);
        getHttpClient(getPassword());
    }

    public OpenProjectRepository(OpenProjectRepository other) {
        super(other);
        setPassword(other.getPassword());
        setUrl(other.getUrl());
        getHttpClient(getPassword());
    }

    private OkHttpClient getHttpClient(String apiKey) {
        if (null == client) {
            client = new OkHttpClient.Builder()
                    .authenticator(new OPApiBasicAuthenticator("apikey", apiKey))
                    .build();
        }
        return client;
    }

    @Override
    public boolean isConfigured() {
        return super.isConfigured() && StringUtil.isNotEmpty(getUrl()) && StringUtil.isNotEmpty(getPassword());
    }

    @Override
    public @Nullable Task findTask(@NotNull String id) throws Exception {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("offset", "0");
        parameters.put("pageSize", "1");
        List<Map<String, OPFilterValue>> filters = new ArrayList<>();
        filters.add(OPFilterObject.from("id", OPFilterValue.of(FilterOperator.EQUALS, id)));
        parameters.put("filters", new ObjectMapper().writeValueAsString(filters));

        Response response = getRequest("work_packages", parameters);

        if (response.code() != 200 || null == response.body()) {
            return null;
        }
        String body = response.body().string();

        List<OPWorkPackage> wps = HalParser.parse(body).as(OPCollectionObject.class).getEmbedded().getItemsBy("elements", OPWorkPackage.class);
        if (!wps.isEmpty()) {
            return new OpenProjectWPTask(this, wps.getFirst());
        }
        return null;
    }

    @Override
    public Task[] getIssues(@Nullable String query, int offset, int limit, boolean withClosed) throws Exception {

        Map<String, String> parameters = new HashMap<>();
        parameters.put("offset", Integer.toString(offset));
        parameters.put("pageSize", Integer.toString(limit));
        parameters.put("sortBy", "[[\"id\", \"asc\"]]");

        List<Map<String, OPFilterValue>> filters = new ArrayList<>();
        filters.add(OPFilterObject.from("assignee", OPFilterValue.of(FilterOperator.EQUALS, "me")));
        if (!withClosed) {
            filters.add(OPFilterObject.from("status", OPFilterValue.of(FilterOperator.WK_OPEN)));
        }
        parameters.put("filters", new ObjectMapper().writeValueAsString(filters));

        Response response = getRequest("work_packages", parameters);

        if (response.code() != 200 || null == response.body()) {
            return new Task[0];
        }
        String body = response.body().string();
        List<OPWorkPackage> wps = HalParser.parse(body).as(OPCollectionObject.class).getEmbedded().getItemsBy("elements", OPWorkPackage.class);
        return wps.stream().map(wp -> new OpenProjectWPTask(this, wp)).toArray(Task[]::new);
    }

    @Override
    public @NotNull BaseRepository clone() {
        return new OpenProjectRepository(this);
    }

    @Override
    public @NotNull String getRestApiPathPrefix() {
        return "/api/v3";
    }

    @Override
    protected int getFeatures() {
        //TODO CHECK FEATURES
        return NATIVE_SEARCH | STATE_UPDATING | TIME_MANAGEMENT;
    }

    @Override
    public @Nullable CancellableConnection createCancellableConnection() {
        //TODO
        return super.createCancellableConnection();
    }

    @Override
    public @NotNull Set<CustomTaskState> getAvailableTaskStates(@NotNull Task task) throws Exception {
        //TODO GET CURR PROJECT AVAILABLE TASK STATES
        return super.getAvailableTaskStates(task);
    }

    @Override
    public void setTaskState(@NotNull Task task, @NotNull CustomTaskState state) throws Exception {
        //TODO
        super.setTaskState(task, state);
    }

    @Override
    public boolean isUseHttpAuthentication() {
        return true;
    }

    @Override
    public boolean isUseProxy() {
        return false;
    }

    private Response getRequest(String apiMethod, Map<String, String> params) throws Exception {

        final HttpUrl.Builder urlBuilder = HttpUrl.parse(getRestApiUrl(apiMethod)).newBuilder();

        params.entrySet().stream().forEach(entry -> urlBuilder.addQueryParameter(entry.getKey(), entry.getValue()));

        final HttpUrl url = urlBuilder.build();

        return makeRequest(url, "GET", null);
    }

    private Response postRequest(String apiMethod, Map<String, String> params, Object body) throws Exception {

        final HttpUrl.Builder urlBuilder = HttpUrl.parse(getRestApiUrl(apiMethod)).newBuilder();

        params.entrySet().stream().forEach(entry -> urlBuilder.addQueryParameter(entry.getKey(), entry.getValue()));

        final HttpUrl url = urlBuilder.build();

        return makeRequest(url, "POST", body);
    }

    private Response makeRequest(HttpUrl url, String method, Object body) throws Exception {

        RequestBody reqBody = null;
        if (null != body) {
            reqBody = RequestBody.create(new ObjectMapper().writeValueAsString(body).getBytes());
        }

        final Request req = new Request.Builder()
                .url(url)
                .method(method, reqBody)
                .build();

        return getHttpClient(getPassword()).newCall(req).execute();
    }

}

