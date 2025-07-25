package com.github.jpmand.openproject.integration.services;


import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.tasks.openproject.OpenProjectRepository;
import com.intellij.tasks.TaskManager;
import com.intellij.tasks.TaskRepository;

import java.util.Optional;

@Service(Service.Level.PROJECT)
public final class OPProjectService {
    private final Logger logger = Logger.getInstance(OPProjectService.class);

    private final Project project;

    public OPProjectService(Project project) {
        logger.debug("[OpenProject-Plugin] Initializing OPProjectService");
        this.project = project;
    }

    public Optional<OpenProjectRepository> getRepository() {
        logger.debug("[OpenProject-Plugin] getRepository");
        TaskManager manager = TaskManager.getManager(project);
        TaskRepository[] repos = manager.getAllRepositories();
        for (TaskRepository repository : repos) {
            if(repository instanceof OpenProjectRepository) {
                return Optional.of((OpenProjectRepository) repository);
            }
        }
        return Optional.empty();
    }
}
