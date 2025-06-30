package com.github.jpmand.openproject.integration.tasks;

import com.github.jpmand.openproject.integration.models.abstracts.OPCollectionObject;
import com.github.jpmand.openproject.integration.models.wp.OPWorkPackage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.CustomTaskState;
import com.intellij.tasks.Task;
import com.intellij.tasks.impl.BaseRepository;
import com.intellij.tasks.impl.httpclient.NewBaseRepositoryImpl;
import com.intellij.util.xmlb.annotations.Tag;
import de.otto.edison.hal.HalParser;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Tag("OpenProject")
public class OpenProjectRepository extends NewBaseRepositoryImpl {
    private static final Logger LOG = Logger.getInstance(OpenProjectRepository.class);


    public OpenProjectRepository() {
        super();
    }

    public OpenProjectRepository(OpenProjectRepositoryType type) {
        super(type);
    }

    public OpenProjectRepository(OpenProjectRepository other) {
        super(other);
        setPassword(other.getPassword());
        setUrl(other.getUrl());
    }

    @Override
    public boolean isConfigured() {
        //TODO CHECK CONFIGURE CHECKS
        return super.isConfigured() && StringUtil.isNotEmpty(getUrl()) && StringUtil.isNotEmpty(getPassword());
    }

    @Override
    public @Nullable Task findTask(@NotNull String id) throws Exception {
        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("offset", "1"));
        params.add(new BasicNameValuePair("sortBy", "[[\"id\", \"asc\"]]"));
        params.add(new BasicNameValuePair("filters", "[{ \"id\": { \"operator\": \"=\", \"values\": [\"" + id.trim() + "\"]}}]"));
        String response = apiCall("work_packages", params);
        List<OPWorkPackage> wps = HalParser.parse(response).as(OPCollectionObject.class).getEmbedded().getItemsBy("elements", OPWorkPackage.class);
        if (!wps.isEmpty()) {
            return new OpenProjectWPTask(this, wps.getFirst());
        }
        return null;
    }

    @Override
    public Task[] getIssues(@Nullable String query, int offset, int limit, boolean withClosed) throws Exception {
        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("offset", Integer.toString(offset)));
        params.add(new BasicNameValuePair("pageSize", Integer.toString(limit)));
        params.add(new BasicNameValuePair("sortBy", "[[\"id\", \"asc\"]]"));
        List<String> filters = new ArrayList<>();
        filters.add("{\"assignee\":{\"operator\":\"=\",\"values\":[\"me\"]}}");
        if (!withClosed) {
            filters.add("{\"status\":{\"operator\":\"o\",\"values\":[]}}");
        }
        params.add(new BasicNameValuePair("filters", "[".concat(String.join(",", filters)).concat("]")));
        String response = apiCall("work_packages", params);
        List<OPWorkPackage> wps = HalParser.parse(response).as(OPCollectionObject.class).getEmbedded().getItemsBy("elements", OPWorkPackage.class);
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

    private HttpRequestBase apiGetRequest(String apiMethod, List<NameValuePair> params) throws URISyntaxException {
        HttpGet post = new HttpGet(getRestApiUrl(apiMethod));

        final String basicAuth = "Basic " + org.apache.commons.codec.binary.Base64.encodeBase64String(("apikey:" + myPassword).getBytes());
        post.setHeader("Authorization", basicAuth);
        post.setHeader("Accept", "*/*");
        URI uri = new URIBuilder(post.getURI()).addParameters(params).setCharset(StandardCharsets.UTF_8).build();
        post.setURI(uri);

        return post;
    }


    private String apiCall(String apiMethod, List<NameValuePair> params) throws Exception {
        final HttpResponse response = getHttpClient().execute(apiGetRequest(apiMethod, params));
        return EntityUtils.toString(response.getEntity());
    }
}

