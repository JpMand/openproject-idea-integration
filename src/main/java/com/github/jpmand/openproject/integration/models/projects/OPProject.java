package com.github.jpmand.openproject.integration.models.projects;

import com.github.jpmand.openproject.integration.models.abstracts.OPObject;

public class OPProject extends OPObject {

    public static final String UPDATE_ACTION = "update";
    public static final String UPDATEIMMEDIATELY_ACTION = "updateImmediately";
    public static final String DELETE_ACTION = "delete";
    public static final String CREATEWORKPACKAGE_ACTION = "createWorkPackage";
    public static final String CREATEWORKPACKAGEIMMEDIATELY_ACTION = "createWorkPackageImmediately";

    private static final String SELF_LINK = "self";
    private static final String ANCESTORS_LINK = "ancestors";
    private static final String CATEGORIES_LINK = "categories";
    private static final String TYPES_LINK = "types";
    private static final String VERSIONS_LINK = "versions";
    private static final String MEMBERSHIPS_LINK = "memberships";
    private static final String WORKPACKAGES_LINK = "workPackages";
    private static final String PARENT_LINK = "parent";
    private static final String STATUS_LINK = "status";

}
