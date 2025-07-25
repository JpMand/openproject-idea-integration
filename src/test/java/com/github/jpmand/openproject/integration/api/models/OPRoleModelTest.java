package com.github.jpmand.openproject.integration.api.models;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jpmand.openproject.integration.api.models.base.AbstractOPCollection;
import org.junit.Assert;
import org.junit.Test;

public class OPRoleModelTest {

    @Test
    public void testOPRoleModelJson() throws JsonProcessingException {
        String json = "{\"_type\":\"Role\",\"id\":13,\"name\":\"Standard global role\",\"_links\":{\"self\":{\"href\":\"/api/v3/roles/13\",\"title\":\"Standard global role\"}}}";
        ObjectMapper mapper = new ObjectMapper();
        OPRoleModel model =  mapper.readValue(json, OPRoleModel.class);
        Assert.assertNotNull(model);
        Assert.assertEquals(Integer.valueOf(13), model.getId());
        System.out.println(model.toString());
    }

    @Test
    public void testOPRoleModelCollectionJson() throws JsonProcessingException {
        String json = "{\"_type\":\"Collection\",\"total\":13,\"count\":13,\"_embedded\":{\"elements\":[{\"_type\":\"Role\",\"id\":13,\"name\":\"Standard global role\",\"_links\":{\"self\":{\"href\":\"/api/v3/roles/13\",\"title\":\"Standard global role\"}}},{\"_type\":\"Role\",\"id\":12,\"name\":\"Project query editor\",\"_links\":{\"self\":{\"href\":\"/api/v3/roles/12\",\"title\":\"Project query editor\"}}},{\"_type\":\"Role\",\"id\":11,\"name\":\"Project query viewer\",\"_links\":{\"self\":{\"href\":\"/api/v3/roles/11\",\"title\":\"Project query viewer\"}}},{\"_type\":\"Role\",\"id\":10,\"name\":\"Work package viewer\",\"_links\":{\"self\":{\"href\":\"/api/v3/roles/10\",\"title\":\"Work package viewer\"}}},{\"_type\":\"Role\",\"id\":9,\"name\":\"Work package commenter\",\"_links\":{\"self\":{\"href\":\"/api/v3/roles/9\",\"title\":\"Work package commenter\"}}},{\"_type\":\"Role\",\"id\":8,\"name\":\"Work package editor\",\"_links\":{\"self\":{\"href\":\"/api/v3/roles/8\",\"title\":\"Work package editor\"}}},{\"_type\":\"Role\",\"id\":7,\"name\":\"Github\",\"_links\":{\"self\":{\"href\":\"/api/v3/roles/7\",\"title\":\"Github\"}}},{\"_type\":\"Role\",\"id\":6,\"name\":\"Staff and projects manager\",\"_links\":{\"self\":{\"href\":\"/api/v3/roles/6\",\"title\":\"Staff and projects manager\"}}},{\"_type\":\"Role\",\"id\":5,\"name\":\"Reader\",\"_links\":{\"self\":{\"href\":\"/api/v3/roles/5\",\"title\":\"Reader\"}}},{\"_type\":\"Role\",\"id\":4,\"name\":\"Member\",\"_links\":{\"self\":{\"href\":\"/api/v3/roles/4\",\"title\":\"Member\"}}},{\"_type\":\"Role\",\"id\":3,\"name\":\"Project admin\",\"_links\":{\"self\":{\"href\":\"/api/v3/roles/3\",\"title\":\"Project admin\"}}},{\"_type\":\"Role\",\"id\":2,\"name\":\"Anonymous\",\"_links\":{\"self\":{\"href\":\"/api/v3/roles/2\",\"title\":\"Anonymous\"}}},{\"_type\":\"Role\",\"id\":1,\"name\":\"Non member\",\"_links\":{\"self\":{\"href\":\"/api/v3/roles/1\",\"title\":\"Non member\"}}}]},\"_links\":{\"self\":{\"href\":\"/api/v3/roles?filters=%5B%5D\"}}}";
        ObjectMapper mapper = new ObjectMapper();
        AbstractOPCollection<OPRoleModel> models =  mapper.readValue(json, new TypeReference<AbstractOPCollection<OPRoleModel>>(){});
        Assert.assertNotNull(models);
        Assert.assertEquals(Integer.valueOf(13), models.getTotal());
        Assert.assertNotNull(models.getElements());
        Assert.assertEquals(13, models.getElements().size());
        System.out.println(models.toString());
    }
}
