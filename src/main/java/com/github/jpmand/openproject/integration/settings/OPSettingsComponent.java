package com.github.jpmand.openproject.integration.settings;

import com.github.jpmand.openproject.integration.util.OPBundle;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class OPSettingsComponent {
    private final JPanel mainPanel;

    private final JBTextField openProjectBaseUrlField = new JBTextField();
    private final JBTextField openProjectPrefixField = new JBTextField();

    private final JBPasswordField opApiKeyField = new JBPasswordField();

    public OPSettingsComponent() {
        mainPanel = FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel(OPBundle.message("label.settings.url")), openProjectBaseUrlField, 1, false)
                .addLabeledComponent(new JBLabel(OPBundle.message("label.settings.version-prefix")), openProjectPrefixField, 1, false)
                .addLabeledComponent(new JBLabel(OPBundle.message("label.settings.apikey")), opApiKeyField, 1, true)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
    }

    public JPanel getPanel() {
        return mainPanel;
    }

    public JComponent getPreferredFocusedComponent() {
        return openProjectBaseUrlField;
    }

    @NotNull
    public String getOpenProjectBaseUrlText() {
        return openProjectBaseUrlField.getText();
    }

    public void setOpenProjectBaseUrlText(@NotNull String newText) {
        openProjectBaseUrlField.setText(newText);
    }

    @NotNull
    public JBTextField getOpenProjectPrefixField() {
        return openProjectPrefixField;
    }

    public void setOpenProjectPrefixField(@NotNull String newText) {
        openProjectPrefixField.setText(newText);
    }

    @NotNull
    public String getOpApiKeyText() {
        return String.valueOf(opApiKeyField.getPassword());
    }

    public void setOpApiKeyText(@NotNull String newText) {
        opApiKeyField.setText(newText);
    }
}
