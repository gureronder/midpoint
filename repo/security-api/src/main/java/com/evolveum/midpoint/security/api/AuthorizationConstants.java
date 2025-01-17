/*
 * Copyright (c) 2010-2015 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.evolveum.midpoint.security.api;

import javax.xml.namespace.QName;

import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.util.QNameUtil;

/**
 * @author semancik
 *
 */
public class AuthorizationConstants {
	
	public static final String NS_SECURITY_PREFIX = SchemaConstants.NS_MIDPOINT_PUBLIC_PREFIX + "security/";
	public static final String NS_AUTHORIZATION = NS_SECURITY_PREFIX + "authorization-3";
	public static final String NS_AUTHORIZATION_UI = NS_SECURITY_PREFIX + "authorization-ui-3";
	public static final String NS_AUTHORIZATION_WS = NS_SECURITY_PREFIX + "authorization-ws-3";
	public static final String NS_AUTHORIZATION_REST = NS_SECURITY_PREFIX + "authorization-rest-3";
	public static final String NS_AUTHORIZATION_MODEL = NS_SECURITY_PREFIX + "authorization-model-3";
	
	public static final QName AUTZ_ALL_QNAME = new QName(NS_AUTHORIZATION, "all");
	public static final String AUTZ_ALL_URL = QNameUtil.qNameToUri(AUTZ_ALL_QNAME);
	
	public static final QName AUTZ_REST_ALL_QNAME = new QName(NS_AUTHORIZATION_REST, "all");
	public static final String AUTZ_REST_ALL_URL = QNameUtil.qNameToUri(AUTZ_REST_ALL_QNAME);
	
	public static final QName AUTZ_WS_ALL_QNAME = new QName(NS_AUTHORIZATION_WS, "all");
	public static final String AUTZ_WS_ALL_URL = QNameUtil.qNameToUri(AUTZ_WS_ALL_QNAME);
	
//	public static final QName AUTZ_DEVEL_QNAME = new QName(NS_AUTHORIZATION, "devel");
	public static final String AUTZ_NO_ACCESS_URL = NS_AUTHORIZATION + "#noAccess";
//	public static final String AUTZ_DEVEL_URL = QNameUtil.qNameToUri(AUTZ_DEVEL_QNAME);
	
	public static final QName AUTZ_DENY_ALL_QNAME = new QName(NS_AUTHORIZATION, "denyAll");
	public static final String AUTZ_DENY_ALL_URL = QNameUtil.qNameToUri(AUTZ_DENY_ALL_QNAME);
    public static final String AUTZ_DENY_ALL = NS_AUTHORIZATION + "#denyAll";

    public static final QName AUTZ_GUI_ALL_QNAME = new QName(NS_AUTHORIZATION_UI, "all");
    public static final String AUTZ_GUI_ALL_URL = QNameUtil.qNameToUri(AUTZ_GUI_ALL_QNAME);
    public static final String AUTZ_GUI_ALL_LABEL = "Authorization.constants.guiAll.label";
    public static final String AUTZ_GUI_ALL_DESCRIPTION = "Authorization.constants.guiAll.description";
    
    public static final QName AUTZ_GUI_ALL_DEPRECATED_QNAME = new QName(NS_AUTHORIZATION, "guiAll");
    public static final String AUTZ_GUI_ALL_DEPRECATED_URL = QNameUtil.qNameToUri(AUTZ_GUI_ALL_DEPRECATED_QNAME);
    
    
    // Following constants are ugly ... but they have to be.
    // Expressions such as:
    // public static final String AUTZ_UI_TASKS_ALL_URL = QNameUtil.qNameToUri(AUTZ_UI_TASKS_ALL_QNAME);
    // are not constant enough for use in annotations (e.g. in GUI pages)
    
    //user
	public static final QName AUTZ_UI_USERS_ALL_QNAME = new QName(NS_AUTHORIZATION_UI, "usersAll");
	public static final String AUTZ_UI_USERS_ALL_URL = NS_AUTHORIZATION_UI + "#usersAll";
	
	public static final QName AUTZ_UI_USERS_QNAME = new QName(NS_AUTHORIZATION_UI, "users");
	public static final String AUTZ_UI_USERS_URL = NS_AUTHORIZATION_UI + "#users";

    public static final QName AUTZ_UI_FIND_USERS_QNAME = new QName(NS_AUTHORIZATION_UI, "findUsers");
    public static final String AUTZ_UI_FIND_USERS_URL = NS_AUTHORIZATION_UI + "#findUsers";
	
	public static final QName AUTZ_UI_USER_QNAME = new QName(NS_AUTHORIZATION_UI, "user");
	public static final String AUTZ_UI_USER_URL = NS_AUTHORIZATION_UI + "#user";
	
	public static final QName AUTZ_UI_USER_DETAILS_QNAME = new QName(NS_AUTHORIZATION_UI, "userDetails");
	public static final String AUTZ_UI_USER_DETAILS_URL = NS_AUTHORIZATION_UI + "#userDetails";
	
	public static final QName AUTZ_UI_ORG_STRUCT_QNAME = new QName(NS_AUTHORIZATION_UI, "orgStruct");
	public static final String AUTZ_UI_ORG_STRUCT_URL = NS_AUTHORIZATION_UI + "#orgStruct";

	public static final QName AUTZ_UI_ORG_ALL_QNAME = new QName(NS_AUTHORIZATION_UI, "orgAll");
	public static final String AUTZ_UI_ORG_ALL_URL = NS_AUTHORIZATION_UI + "#orgAll";

	public static final QName AUTZ_UI_ORG_TREE_QNAME = new QName(NS_AUTHORIZATION_UI, "orgTree");
	public static final String AUTZ_UI_ORG_TREE_URL = NS_AUTHORIZATION_UI + "#orgTree";

	public static final QName AUTZ_UI_ORG_UNIT_QNAME = new QName(NS_AUTHORIZATION_UI, "orgUnit");
	public static final String AUTZ_UI_ORG_UNIT_URL = NS_AUTHORIZATION_UI + "#orgUnit";

	
	//resources
	public static final QName AUTZ_UI_RESOURCES_ALL_QNAME = new QName(NS_AUTHORIZATION_UI, "resourcesAll");
	public static final String AUTZ_UI_RESOURCES_ALL_URL = NS_AUTHORIZATION_UI + "#resourcesAll";
	
	public static final QName AUTZ_UI_RESOURCES_QNAME = new QName(NS_AUTHORIZATION_UI, "resources");
	public static final String AUTZ_UI_RESOURCES_URL = NS_AUTHORIZATION_UI + "#resources";

	public static final QName AUTZ_UI_RESOURCE_QNAME = new QName(NS_AUTHORIZATION_UI, "resource");
	public static final String AUTZ_UI_RESOURCE_URL = NS_AUTHORIZATION_UI + "#resource";
	
	public static final QName AUTZ_UI_RESOURCE_DETAILS_QNAME = new QName(NS_AUTHORIZATION_UI, "resourceDetails");
	public static final String AUTZ_UI_RESOURCE_DETAILS_URL = NS_AUTHORIZATION_UI + "#resourceDetails";
	
	public static final QName AUTZ_UI_RESOURCE_EDIT_QNAME = new QName(NS_AUTHORIZATION_UI, "resourceEdit");
	public static final String AUTZ_UI_RESOURCE_EDIT_URL = NS_AUTHORIZATION_UI + "#resourceEdit";
	
	public static final QName AUTZ_UI_RESOURCES_ACCOUNT_QNAME = new QName(NS_AUTHORIZATION_UI, "resourcesAccount");
	public static final String AUTZ_UI_RESOURCES_ACCOUNT_URL = NS_AUTHORIZATION_UI + "#resourcesAccount";
	
	public static final QName AUTZ_UI_RESOURCES_CONTENT_ACCOUNTS_QNAME = new QName(NS_AUTHORIZATION_UI, "resourcesContentAccount");
	public static final String AUTZ_UI_RESOURCES_CONTENT_ACCOUNTS_URL = NS_AUTHORIZATION_UI + "#resourcesContentAccount";
	
	public static final QName AUTZ_UI_RESOURCES_CONTENT_ENTITLEMENTS_QNAME = new QName(NS_AUTHORIZATION_UI, "resourcesContentEntitlements");
	public static final String AUTZ_UI_RESOURCES_CONTENT_ENTITLEMENTS_URL = NS_AUTHORIZATION_UI + "#resourcesContentEntitlements";
	
	
	//Configuration
	public static final QName AUTZ_UI_CONFIGURATION_ALL_QNAME = new QName(NS_AUTHORIZATION_UI, "configurationAll");
	public static final String AUTZ_UI_CONFIGURATION_ALL_URL = NS_AUTHORIZATION_UI + "#configurationAll";
	
	public static final QName AUTZ_UI_CONFIGURATION_ABOUT_QNAME = new QName(NS_AUTHORIZATION_UI, "configAbout");
	public static final String AUTZ_UI_CONFIGURATION_ABOUT_URL = NS_AUTHORIZATION_UI + "#configAbout";
	
	public static final QName AUTZ_UI_CONFIGURATION_SYNCHRONIZATION_ACCOUNTS_QNAME = new QName(NS_AUTHORIZATION_UI, "configSyncAccounts");
	public static final String AUTZ_UI_CONFIGURATION_SYNCHRONIZATION_ACCOUNTS_URL = NS_AUTHORIZATION_UI + "#configSyncAccounts";
	
	public static final QName AUTZ_UI_CONFIGURATION_QNAME = new QName(NS_AUTHORIZATION_UI, "configuration");
	public static final String AUTZ_UI_CONFIGURATION_URL = NS_AUTHORIZATION_UI + "#configuration";
	
	public static final QName AUTZ_UI_CONFIGURATION_DEBUG_QNAME = new QName(NS_AUTHORIZATION_UI, "debug");
	public static final String AUTZ_UI_CONFIGURATION_DEBUG_URL = NS_AUTHORIZATION_UI + "#debug";
	
	public static final QName AUTZ_UI_CONFIGURATION_DEBUGS_QNAME = new QName(NS_AUTHORIZATION_UI, "debugs");
	public static final String AUTZ_UI_CONFIGURATION_DEBUGS_URL = NS_AUTHORIZATION_UI + "#debugs";
	
	public static final QName AUTZ_UI_CONFIGURATION_IMPORT_QNAME = new QName(NS_AUTHORIZATION_UI, "configImport");
	public static final String AUTZ_UI_CONFIGURATION_IMPORT_URL = NS_AUTHORIZATION_UI + "#configImport";
	
	public static final QName AUTZ_UI_CONFIGURATION_LOGGING_QNAME = new QName(NS_AUTHORIZATION_UI, "configLogging");
	public static final String AUTZ_UI_CONFIGURATION_LOGGING_URL = NS_AUTHORIZATION_UI + "#configLogging";

    public static final QName AUTZ_UI_CONFIGURATION_SYSTEM_CONFIG_QNAME = new QName(NS_AUTHORIZATION_UI, "configSystemConfiguration");
    public static final String AUTZ_UI_CONFIGURATION_SYSTEM_CONFIG_URL = NS_AUTHORIZATION_UI + "#configSystemConfiguration";
    
    public static final QName AUTZ_UI_CONFIGURATION_INTERNALS_QNAME = new QName(NS_AUTHORIZATION_UI, "configInternals");
	public static final String AUTZ_UI_CONFIGURATION_INTERNALS_URL = NS_AUTHORIZATION_UI + "#configInternals";
	
	//Roles
	public static final QName AUTZ_UI_ROLES_ALL_QNAME = new QName(NS_AUTHORIZATION_UI, "rolesAll");
	public static final String AUTZ_UI_ROLES_ALL_URL = NS_AUTHORIZATION_UI + "#rolesAll";
	
	public static final QName AUTZ_UI_ROLES_QNAME = new QName(NS_AUTHORIZATION_UI, "roles");
	public static final String AUTZ_UI_ROLES_URL = NS_AUTHORIZATION_UI + "#roles";
	
	public static final QName AUTZ_UI_ROLE_QNAME = new QName(NS_AUTHORIZATION_UI, "role");
	public static final String AUTZ_UI_ROLE_URL = NS_AUTHORIZATION_UI + "#role";
	
	public static final QName AUTZ_UI_ROLE_DETAILS_QNAME = new QName(NS_AUTHORIZATION_UI, "roleDetails");
	public static final String AUTZ_UI_ROLE_DETAILS_URL = NS_AUTHORIZATION_UI + "#roleDetails";
	
	
	//Work Items
	public static final QName AUTZ_UI_WORK_ITEMS_ALL_QNAME = new QName(NS_AUTHORIZATION_UI, "workItemsAll");
	public static final String AUTZ_UI_WORK_ITEMS_ALL_URL = NS_AUTHORIZATION_UI + "#workItemsAll";
	
	public static final QName AUTZ_UI_WORK_ITEMS_QNAME = new QName(NS_AUTHORIZATION_UI, "workItems");
	public static final String AUTZ_UI_WORK_ITEMS_URL = NS_AUTHORIZATION_UI + "#workItems";
	
	public static final QName AUTZ_UI_WORK_ITEM_QNAME = new QName(NS_AUTHORIZATION_UI, "workItem");
	public static final String AUTZ_UI_WORK_ITEM_URL = NS_AUTHORIZATION_UI + "#workItem";
	
	public static final QName AUTZ_UI_WORK_ITEMS_ALL_REQUESTS_QNAME = new QName(NS_AUTHORIZATION_UI, "workItemsAllRequests");
	public static final String AUTZ_UI_WORK_ITEMS_ALL_REQUESTS_URL = NS_AUTHORIZATION_UI + "#workItemsAllRequests";
	
	public static final QName AUTZ_UI_WORK_ITEMS_MY_REQUESTS_QNAME = new QName(NS_AUTHORIZATION_UI, "workItemsMyRequests");
	public static final String AUTZ_UI_WORK_ITEMS_MY_REQUESTS_URL = NS_AUTHORIZATION_UI + "#workItemsMyRequests";
	
	public static final QName AUTZ_UI_WORK_ITEMS_ABOUT_ME_REQUESTS_QNAME = new QName(NS_AUTHORIZATION_UI, "workItemsAboutMeRequests");
	public static final String AUTZ_UI_WORK_ITEMS_ABOUT_ME_REQUESTS_URL = NS_AUTHORIZATION_UI + "#workItemsAboutMeRequests";
	
	public static final QName AUTZ_UI_WORK_ITEMS_PROCESS_INSTANCE_QNAME = new QName(NS_AUTHORIZATION_UI, "workItemsProcessInstance");
	public static final String AUTZ_UI_WORK_ITEMS_PROCESS_INSTANCE_URL = NS_AUTHORIZATION_UI + "#workItemsProcessInstance";

    public static final QName AUTZ_UI_WORK_ITEMS_APPROVE_OTHERS_ITEMS_QNAME = new QName(NS_AUTHORIZATION_UI, "workItemsApproveOthersItems");
    public static final String AUTZ_UI_WORK_ITEMS_APPROVE_OTHERS_ITEMS_URL = NS_AUTHORIZATION_UI + "#workItemsApproveOthersItems";
    
	//Tasks
	public static final QName AUTZ_UI_TASKS_ALL_QNAME = new QName(NS_AUTHORIZATION_UI, "tasksAll");
	public static final String AUTZ_UI_TASKS_ALL_URL = NS_AUTHORIZATION_UI + "#tasksAll";
	
	public static final QName AUTZ_UI_TASKS_QNAME = new QName(NS_AUTHORIZATION_UI, "tasks");
	public static final String AUTZ_UI_TASKS_URL = NS_AUTHORIZATION_UI + "#tasks";
	
	public static final QName AUTZ_UI_TASK_QNAME = new QName(NS_AUTHORIZATION_UI, "task");
	public static final String AUTZ_UI_TASK_URL = NS_AUTHORIZATION_UI + "#task";
	
	public static final QName AUTZ_UI_TASK_DETAIL_QNAME = new QName(NS_AUTHORIZATION_UI, "taskDetails");
	public static final String AUTZ_UI_TASK_DETAIL_URL = NS_AUTHORIZATION_UI + "#taskDetails";
	
	public static final QName AUTZ_UI_TASK_ADD_QNAME = new QName(NS_AUTHORIZATION_UI, "taskAdd");
	public static final String AUTZ_UI_TASK_ADD_URL = NS_AUTHORIZATION_UI + "#taskAdd";
	
	
	//Reports
	public static final QName AUTZ_UI_REPORTS_QNAME = new QName(NS_AUTHORIZATION_UI, "reports");
	public static final String AUTZ_UI_REPORTS_URL = NS_AUTHORIZATION_UI + "#reports";

	public static final QName AUTZ_UI_REPORT_QNAME = new QName(NS_AUTHORIZATION_UI, "report");
	public static final String AUTZ_UI_REPORT_URL = NS_AUTHORIZATION_UI + "#report";

	public static final QName AUTZ_UI_REPORTS_ALL_QNAME = new QName(NS_AUTHORIZATION_UI, "reportsAll");
	public static final String AUTZ_UI_REPORTS_ALL_URL = NS_AUTHORIZATION_UI + "#reportsAll";

	public static final QName AUTZ_UI_REPORTS_CREATED_REPORTS_QNAME = new QName(NS_AUTHORIZATION_UI, "createdReports");
	public static final String AUTZ_UI_REPORTS_CREATED_REPORTS_URL = NS_AUTHORIZATION_UI + "#createdReports";

	public static final QName AUTZ_UI_REPORTS_REPORT_CREATE_QNAME = new QName(NS_AUTHORIZATION_UI, "reportCreate");
	public static final String AUTZ_UI_REPORTS_REPORT_CREATE_URL = NS_AUTHORIZATION_UI + "#reportCreate";

    //Reports
    public static final QName AUTZ_UI_CERTIFICATION_QNAME = new QName(NS_AUTHORIZATION_UI, "certification");
    public static final String AUTZ_UI_CERTIFICATION_URL = NS_AUTHORIZATION_UI + "#certification";

    public static final QName AUTZ_UI_CERTIFICATION_ALL_QNAME = new QName(NS_AUTHORIZATION_UI, "certificationAll");
    public static final String AUTZ_UI_CERTIFICATION_ALL_URL = NS_AUTHORIZATION_UI + "#certificationAll";

    
    //Home
	public static final QName AUTZ_UI_HOME_ALL_QNAME = new QName(NS_AUTHORIZATION_UI, "home");
	public static final String AUTZ_UI_HOME_ALL_URL = NS_AUTHORIZATION_UI + "#home";

	public static final QName AUTZ_UI_DASHBOARD_QNAME = new QName(NS_AUTHORIZATION_UI, "dashboard");
	public static final String AUTZ_UI_DASHBOARD_URL = NS_AUTHORIZATION_UI + "#dashboard";
	
	public static final QName AUTZ_UI_MY_PASSWORDS_QNAME = new QName(NS_AUTHORIZATION_UI, "myPasswords");
	public static final String AUTZ_UI_MY_PASSWORDS_URL = NS_AUTHORIZATION_UI + "#myPasswords";
	
	public static final QName AUTZ_UI_MY_QUESTIONS_QNAME = new QName(NS_AUTHORIZATION_UI, "myQuestions");
	public static final String AUTZ_UI_MY_QUESTIONS_URL = NS_AUTHORIZATION_UI + "#myQuestions";
	
	
	public static final QName AUTZ_UI_BULK_ACTION_QNAME = new QName(NS_AUTHORIZATION_UI, "bulkAction");
	public static final String AUTZ_UI_BULK_ACTION_URL = NS_AUTHORIZATION_UI + "#bulkAction";
	
	
	public static final QName AUTZ_UI_CONTACTS_QNAME = new QName(NS_AUTHORIZATION_UI, "contacts");
	public static final String AUTZ_UI_CONTACTS_URL = NS_AUTHORIZATION_UI + "#contacts";
	
	
	//permitAll  - it means, no authorization is required, everyone can access these resources, this is user for about system and about midpoint pages..
	public static final QName AUTZ_UI_PERMIT_ALL_QNAME = new QName(NS_AUTHORIZATION_UI, "permitAll");
	public static final String AUTZ_UI_PERMIT_ALL_URL = QNameUtil.qNameToUri(AUTZ_UI_PERMIT_ALL_QNAME);
    public static final String AUTZ_UI_PERMIT_ALL = NS_AUTHORIZATION + "#permitAll";
	
	

	
	//About
//	public static final QName AUTZ_UI_ABOUT_MIDPOINT_QNAME = new QName(NS_AUTHORIZATION, "aboutMidpoint");
//	public static final String AUTZ_UI_ABOUT_MIDPOINT_URL = QNameUtil.qNameToUri(AUTZ_UI_ABOUT_MIDPOINT_QNAME);
//	
//	public static final QName AUTZ_UI_ABOUT_SYSTEM_QNAME = new QName(NS_AUTHORIZATION, "abountSystem");
//	public static final String AUTZ_UI_ABOUT_SYSTEM_URL = QNameUtil.qNameToUri(AUTZ_UI_ABOUT_SYSTEM_QNAME);
	
}
