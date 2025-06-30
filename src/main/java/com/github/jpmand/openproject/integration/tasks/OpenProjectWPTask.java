package com.github.jpmand.openproject.integration.tasks;

import com.github.jpmand.openproject.integration.models.wp.OPWorkPackage;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.tasks.Comment;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskState;
import com.intellij.tasks.TaskType;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Date;

public class OpenProjectWPTask extends Task {
    private final OpenProjectRepository repository;
    private final OPWorkPackage opWPObject;

    public OpenProjectWPTask(OpenProjectRepository repository, OPWorkPackage opWPObject) {
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
        return opWPObject.getDescription().getHtml();
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
        return TaskType.FEATURE;
    }

    @Override
    public @Nullable Date getUpdated() {
        return Date.from(opWPObject.getUpdatedAt().toInstant());
    }

    @Override
    public @Nullable Date getCreated() {
        return Date.from(opWPObject.getCreatedAt().toInstant());
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
        return repository.getUrl() + opWPObject.getLinks().getLinkBy(OPWorkPackage.SELF_LINK).get().getHref();
    }

    @Override
    public @Nullable String getProject() {
        return opWPObject.getLinks().getLinkBy(OPWorkPackage.PROJECT_LINK).get().getTitle();
    }
}
