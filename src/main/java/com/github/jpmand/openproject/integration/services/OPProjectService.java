package com.github.jpmand.openproject.integration.services;


import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;

@Service(Service.Level.PROJECT)
public final class OPProjectService {

    private final Project project;

    public OPProjectService(Project project) {
        this.project = project;
    }

}
