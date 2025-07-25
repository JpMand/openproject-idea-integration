package com.intellij.tasks.openproject;

import com.github.jpmand.openproject.integration.api.OPApiClient;
import com.github.jpmand.openproject.integration.api.enums.FilterOperator;
import com.github.jpmand.openproject.integration.api.enums.SortEnum;
import com.github.jpmand.openproject.integration.api.models.OPWorkPackageModel;
import com.github.jpmand.openproject.integration.api.models.base.AbstractOPCollection;
import com.github.jpmand.openproject.integration.api.models.filters.OPFilterObject;
import com.github.jpmand.openproject.integration.api.models.filters.OPFilterValue;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.CustomTaskState;
import com.intellij.tasks.Task;
import com.intellij.tasks.impl.BaseRepository;
import com.intellij.tasks.impl.httpclient.NewBaseRepositoryImpl;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@Tag("OpenProject")
public class OpenProjectRepository extends NewBaseRepositoryImpl {
    private static final Logger LOG = Logger.getInstance(OpenProjectRepository.class);

    private OPApiClient client = null;

    private String selectedProjectId;


    public OPApiClient getOPApiClient() {
        if (null == client) {
            client = new OPApiClient(this.getUrl(), this.getPassword());
        }
        return client;
    }

    public OpenProjectRepository() {
        super();
    }

    public OpenProjectRepository(OpenProjectRepositoryType type) {
        super(type);
    }

    public OpenProjectRepository(OpenProjectRepository other) {
        super(other);
        setUrl(other.getUrl());
        setPassword(other.getPassword());
        client = new OPApiClient(other.getUrl(), other.getPassword());
        setSelectedProjectId(other.getSelectedProjectId());
    }

    @Override
    public boolean isConfigured() {
        return super.isConfigured() && StringUtil.isNotEmpty(getUrl()) && StringUtil.isNotEmpty(getPassword());
    }

    @Override
    public @Nullable OpenProjectWPTask findTask(@NotNull String id) throws Exception {

        OPWorkPackageModel model = getOPApiClient().getWorkPackageById(Integer.valueOf(id));
        if (null != model) {
            return new OpenProjectWPTask(this, model);
        } else {
            return null;
        }
    }

    @Override
    public OpenProjectWPTask[] getIssues(@Nullable String query, int offset, int limit, boolean withClosed) throws Exception {
        List<List<String>> sorts = new ArrayList<>();
        sorts.add(Arrays.asList("id", SortEnum.ASC.toString()));

        List<Map<String, OPFilterValue>> filters = new ArrayList<>();
        filters.add(OPFilterObject.from("assignee", OPFilterValue.of(FilterOperator.EQUALS, "me")));
        if (!withClosed) {
            filters.add(OPFilterObject.from("status", OPFilterValue.of(FilterOperator.WK_OPEN)));
        }

        AbstractOPCollection<OPWorkPackageModel> workpackages = getOPApiClient().getWorkPackages(offset, limit, sorts, filters);
        return workpackages.getElements().stream().map(wp -> new OpenProjectWPTask(this, wp)).toArray(OpenProjectWPTask[]::new);
    }

    @Override
    public @NotNull BaseRepository clone() {
        return new OpenProjectRepository(this);
    }

    @Override
    public @NotNull String getRestApiPathPrefix() {
        return "/api/v3";
    }

    @Override
    protected int getFeatures() {
        //TODO CHECK FEATURES
        return NATIVE_SEARCH | STATE_UPDATING | TIME_MANAGEMENT;
    }

    @Override
    public @Nullable CancellableConnection createCancellableConnection() {
        return new CancellableConnection() {
            @Override
            protected void doTest() throws Exception {
                getOPApiClient().getRoot();
            }

            @Override
            public void cancel() {
                //do nothing
            }
        };
    }

    @Override
    public @NotNull Set<CustomTaskState> getAvailableTaskStates(@NotNull Task task) throws Exception {
        //TODO GET CURR PROJECT AVAILABLE TASK STATES
        return super.getAvailableTaskStates(task);
    }

    @Override
    public void setTaskState(@NotNull Task task, @NotNull CustomTaskState state) throws Exception {

        super.setTaskState(task, state);
    }

    @Override
    public boolean isUseHttpAuthentication() {
        return true;
    }

    @Override
    public boolean isUseProxy() {
        return false;
    }

    @Attribute("SelectedProjectId")
    public String getSelectedProjectId(){
        return selectedProjectId;
    }

    public void setSelectedProjectId(String selectedProjectId){
        this.selectedProjectId = selectedProjectId;
    }
}

