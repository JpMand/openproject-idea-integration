package com.intellij.tasks.openproject;

import com.github.jpmand.openproject.integration.api.models.OPProjectModel;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.config.BaseRepositoryEditor;
import com.intellij.util.Consumer;

import javax.swing.*;

public class OpenProjectRepositoryEditor extends BaseRepositoryEditor<OpenProjectRepository> {
    private static final Logger LOG = Logger.getInstance(OpenProjectRepositoryEditor.class);

    private ComboBox<OPProjectModel> projectComboBox;

    public OpenProjectRepositoryEditor(Project project, OpenProjectRepository repository, Consumer<? super OpenProjectRepository> changeListener) {
        super(project, repository, changeListener);

        myUserNameText.setText("apikey");
        myUserNameText.setEnabled(false);

        myShareUrlCheckBox.setVisible(false);

        myTestButton.setEnabled(StringUtil.isNotEmpty(myRepository.getPassword()));
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        return myPasswordText;
    }



    @Override
    public void apply() {
        super.apply();
        myTestButton.setEnabled(myRepository.isConfigured());
    }
}
