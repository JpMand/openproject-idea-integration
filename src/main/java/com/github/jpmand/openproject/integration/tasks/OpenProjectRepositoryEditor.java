package com.github.jpmand.openproject.integration.tasks;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.config.BaseRepositoryEditor;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class OpenProjectRepositoryEditor extends BaseRepositoryEditor<OpenProjectRepository> {
    private static final Logger LOG = Logger.getInstance(OpenProjectRepositoryEditor.class);

    private JBPasswordField opApiKeyField;

    public OpenProjectRepositoryEditor(Project project, OpenProjectRepository repository, Consumer<? super OpenProjectRepository> changeListener) {
        super(project, repository, changeListener);

        myUrlLabel.setVisible(false);
        myURLText.setVisible(false);
        myUsernameLabel.setVisible(false);
        myUserNameText.setVisible(false);
        myPasswordLabel.setVisible(false);
        myPasswordText.setVisible(false);
        myShareUrlCheckBox.setVisible(false);

        myTestButton.setEnabled(StringUtil.isNotEmpty(myRepository.getPassword()));
    }


    @Override
    protected @Nullable JComponent createCustomPanel() {
        return super.createCustomPanel();
    }

    @Override
    public void apply() {
        super.apply();
    }


}
