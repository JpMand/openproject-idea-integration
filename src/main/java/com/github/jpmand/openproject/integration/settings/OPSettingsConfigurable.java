package com.github.jpmand.openproject.integration.settings;

import com.github.jpmand.openproject.integration.util.OPBundle;
import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Objects;

    public class OPSettingsConfigurable implements Configurable {
    private OPSettingsComponent settingsComponent;

    @Override
    public @NlsContexts.ConfigurableName String getDisplayName() {
        return OPBundle.message("title.settings.window");
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
        return settingsComponent.getPreferredFocusedComponent();
    }

    @Override
    public @Nullable JComponent createComponent() {
        settingsComponent = new OPSettingsComponent();
        return settingsComponent.getPanel();
    }

    @Override
    public boolean isModified() {
        OPSettings.State state = Objects.requireNonNull(OPSettings.getInstance().getState());
        boolean basicSettingsModified = !Objects.equals(state.opBaseUrl, settingsComponent.getOpenProjectBaseUrlText());
        CredentialAttributes credentialAttributes = new CredentialAttributes(CredentialAttributesKt.generateServiceName("op-integration", "apikey"));
        PasswordSafe passwordSafe = PasswordSafe.getInstance();
        Credentials credentials = passwordSafe.get(credentialAttributes);
        String apikey = null;
        if (null != credentials) {
            apikey = credentials.getPasswordAsString();
        }
        apikey = passwordSafe.getPassword(credentialAttributes);
        boolean apiKeyModified = !Objects.equals(apikey, settingsComponent.getOpApiKeyText());

        return basicSettingsModified || apiKeyModified;
    }

    @Override
    public void apply() throws ConfigurationException {
        OPSettings.State state = Objects.requireNonNull(OPSettings.getInstance().getState());
        state.opBaseUrl = settingsComponent.getOpenProjectBaseUrlText();
        CredentialAttributes credentialAttributes = new CredentialAttributes(CredentialAttributesKt.generateServiceName("op-integration", "apikey"));
        Credentials credentials = new Credentials("apikey", settingsComponent.getOpApiKeyText());
        PasswordSafe.getInstance().set(credentialAttributes, credentials);
    }

    @Override
    public void reset() {
        OPSettings.State state = Objects.requireNonNull(OPSettings.getInstance().getState());
        settingsComponent.setOpenProjectBaseUrlText(state.opBaseUrl);
        CredentialAttributes credentialAttributes = new CredentialAttributes(CredentialAttributesKt.generateServiceName("op-integration", "apikey"));
        PasswordSafe passwordSafe = PasswordSafe.getInstance();
        Credentials credentials = passwordSafe.get(credentialAttributes);
        String apikey = null;
        if (null != credentials) {
            apikey = credentials.getPasswordAsString();
        }
        apikey = passwordSafe.getPassword(credentialAttributes);
        settingsComponent.setOpApiKeyText(apikey);
    }

    @Override
    public void disposeUIResources() {
        settingsComponent = null;
    }
}
