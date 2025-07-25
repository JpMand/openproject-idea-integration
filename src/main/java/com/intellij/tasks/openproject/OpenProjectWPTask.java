package com.intellij.tasks.openproject;

import com.github.jpmand.openproject.integration.api.models.OPWorkPackageModel;
import com.github.jpmand.openproject.integration.api.models.base.OPBaseResource;
import com.intellij.icons.AllIcons;
import com.intellij.tasks.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Date;

public class OpenProjectWPTask extends Task {
    private final OpenProjectRepository repository;
    private final OPWorkPackageModel opWPObject;

    public OpenProjectWPTask(OpenProjectRepository repository, OPWorkPackageModel opWPObject) {
        this.repository = repository;
        this.opWPObject = opWPObject;
    }

    @Override
    public @NotNull String getId() {
        return opWPObject.getId().toString();
    }

    @Override
    public @Nls @NotNull String getSummary() {
        return opWPObject.getSubject();
    }

    @Override
    public @Nls @Nullable String getDescription() {
        return opWPObject.getDescription().getRaw();
    }

    @Override
    public Comment @NotNull [] getComments() {
        return new Comment[0];
    }

    @Override
    public @NotNull Icon getIcon() {
        return AllIcons.Plugins.PluginLogo;
    }

    @Override
    public @NotNull TaskType getType() {
        return TaskType.OTHER;
    }

    @Override
    public @Nullable Date getUpdated() {
        return null == opWPObject.getUpdatedAt() ? null : Date.from(opWPObject.getUpdatedAt().toInstant());
    }

    @Override
    public @Nullable Date getCreated() {
        return null == opWPObject.getCreatedAt() ? null : Date.from(opWPObject.getCreatedAt().toInstant());
    }

    @Override
    public boolean isClosed() {
        return false;
    }

    @Override
    public boolean isIssue() {
        return false;
    }

    @Override
    public @Nullable OpenProjectRepository getRepository() {
        return repository;
    }

    @Override
    public @Nullable TaskState getState() {
        //TODO
        return super.getState();
    }


    @Override
    public @Nullable String getIssueUrl() {
        return repository.getUrl().concat(opWPObject.getLink(OPBaseResource.SELF_LINK).getFirst().getHref());
    }

    @Override
    public @Nullable String getProject() {
        return opWPObject.getLink(OPWorkPackageModel.PROJECT_LINK).getFirst().getTitle();
    }
}
