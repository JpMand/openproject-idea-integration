package com.github.jpmand.openproject.integration.tasks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.CustomTaskState;
import com.intellij.tasks.Task;
import com.intellij.tasks.impl.BaseRepository;
import com.intellij.tasks.impl.httpclient.NewBaseRepositoryImpl;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

@Tag("OpenProject")
public class OpenProjectRepository extends NewBaseRepositoryImpl {
    private static final Logger LOG = Logger.getInstance(OpenProjectRepository.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    private int projectId;

    private boolean onlyAssignedToMe;

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
        //TODO
        return null;
    }

    @Override
    public Task[] getIssues(@Nullable String query, int offset, int limit, boolean withClosed) throws Exception {
        //TODO
        return super.getIssues(query, offset, limit, withClosed);
    }

    @Override
    public @NotNull BaseRepository clone() {
        return new OpenProjectRepository(this);
    }

    @Override
    public @NotNull String getRestApiPathPrefix() {
        return "api/v3";
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
}

