package com.github.jpmand.openproject.integration.tasks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.tasks.Task;
import com.intellij.tasks.impl.BaseRepository;
import com.intellij.tasks.impl.httpclient.NewBaseRepositoryImpl;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    public @Nullable Task findTask(@NotNull String s) throws Exception {
        return null;
    }

    @Override
    public @NotNull BaseRepository clone() {
        return new OpenProjectRepository(this);
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

