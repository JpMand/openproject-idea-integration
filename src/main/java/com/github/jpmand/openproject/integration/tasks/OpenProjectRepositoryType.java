package com.github.jpmand.openproject.integration.tasks;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.config.TaskRepositoryEditor;
import com.intellij.tasks.impl.BaseRepositoryType;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class OpenProjectRepositoryType extends BaseRepositoryType<OpenProjectRepository> {
    private static final Logger LOG = Logger.getInstance(OpenProjectRepositoryType.class);
    private static final Icon ICON = AllIcons.Plugins.PluginLogo;

    @Override
    public @NotNull String getName() {
        return "OpenProject";
    }

    @Override
    public @NotNull Icon getIcon() {
        return ICON;
    }

    @Override
    public @NotNull TaskRepository createRepository() {
        return new OpenProjectRepository(this);
    }

    @Override
    public Class<OpenProjectRepository> getRepositoryClass() {
        return OpenProjectRepository.class;
    }

    @Override
    public @NotNull TaskRepositoryEditor createEditor(OpenProjectRepository repository, Project project, Consumer<? super OpenProjectRepository> changeListener) {
        return new OpenProjectRepositoryEditor(project, repository, changeListener);
    }
}
