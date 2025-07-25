package com.github.jpmand.openproject.integration.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.ReferenceType;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.jpmand.openproject.integration.api.models.OPWorkPackageModel;
import com.github.jpmand.openproject.integration.api.models.base.AbstractOPCollection;
import com.github.jpmand.openproject.integration.api.models.base.OPBaseResource;
import com.github.jpmand.openproject.integration.api.models.filters.OPFilterValue;
import com.intellij.openapi.diagnostic.Logger;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class OPApiClient {
    private String baseUrl;
    private String apiToken;
    private OkHttpClient client;
    private ObjectMapper mapper;

    private HttpUrl getBaseHttpUrl(String baseUrl) {
        return HttpUrl.get(baseUrl).newBuilder()
                .addPathSegment("api")
                .addPathSegment("v3")
                .build();
    }

    public OPApiClient(String baseUrl, String apiToken) {
        this.baseUrl = baseUrl;
        this.apiToken = apiToken;
        this.client = new OkHttpClient.Builder()
                .addInterceptor(new OPApiTokenInterceptor(apiToken))
                .build();
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
    }

    public OkHttpClient getClient() {
        return client;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public OPBaseResource getRoot(){
        Request request = new Request.Builder().url(baseUrl).build();
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                return mapper.readValue(response.body().bytes(), OPBaseResource.class);
            } else {
                return null;
            }
        } catch (IOException | IllegalStateException ex) {
            return null;
        }
    }

    public OPWorkPackageModel getWorkPackageById(Integer id) {
        HttpUrl url = getBaseHttpUrl(baseUrl).newBuilder()
                .addPathSegment("work_packages")
                .addPathSegment(String.valueOf(id))
                .build();
        Request request = new Request.Builder().url(url).build();
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                return mapper.readValue(response.body().bytes(), OPWorkPackageModel.class);
            } else {
                return null;
            }
        } catch (IOException | IllegalStateException ex) {
            return null;
        }
    }

    public AbstractOPCollection<OPWorkPackageModel> getWorkPackages(Integer offset, Integer pageSize, List<List<String>> sorts, List<Map<String, OPFilterValue>> filters) throws JsonProcessingException {
        HttpUrl url = getBaseHttpUrl(baseUrl).newBuilder()
                .addPathSegment("work_packages")
                .addQueryParameter("offset", String.valueOf(offset))
                .addQueryParameter("pageSize", String.valueOf(pageSize))
                .addQueryParameter("sortBy", mapper.writeValueAsString(sorts))
                .addQueryParameter("filters", mapper.writeValueAsString(filters))
                .build();
        Request request = new Request.Builder().url(url)
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                AbstractOPCollection<OPWorkPackageModel> responseValue = mapper.readValue(response.body().string(), new TypeReference<AbstractOPCollection<OPWorkPackageModel>>(){});
                return responseValue;
            } else {
                return null;
            }
        } catch (IOException | IllegalStateException ex) {
            return null;
        }
    }
}
