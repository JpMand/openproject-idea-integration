package com.github.jpmand.openproject.integration.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.SettingsCategory;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(
        name = "com.github.jpmand.openproject.integration.settings.OPSettings",
        storages = @Storage("openprojectSettings.xml"),
        category = SettingsCategory.TOOLS
)
public class OPSettings implements PersistentStateComponent<OPSettings.State> {

    static class State {
        @NonNls
        public String opBaseUrl;
        public Integer opProjectId;
    }

    private State state = new State();

    static OPSettings getInstance() {
        return ApplicationManager.getApplication().getService(OPSettings.class);
    }

    @Override
    public @Nullable State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
    }
}
