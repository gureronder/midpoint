/**
 * Copyright (c) 2015 Evolveum
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
package com.evolveum.midpoint.testing.conntest;

import static org.testng.AssertJUnit.assertNotNull;
import static com.evolveum.midpoint.test.IntegrationTestTools.display;
import static org.testng.AssertJUnit.assertTrue;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import javax.xml.namespace.QName;

import org.apache.directory.api.ldap.model.cursor.CursorException;
import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.exception.LdapException;
import org.apache.directory.ldap.client.api.LdapNetworkConnection;
import org.testng.AssertJUnit;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

import com.evolveum.midpoint.common.refinery.RefinedResourceSchema;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.delta.PropertyDelta;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.query.EqualFilter;
import com.evolveum.midpoint.prism.query.ObjectFilter;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.prism.util.PrismAsserts;
import com.evolveum.midpoint.prism.util.PrismTestUtil;
import com.evolveum.midpoint.schema.SearchResultList;
import com.evolveum.midpoint.schema.SearchResultMetadata;
import com.evolveum.midpoint.schema.constants.MidPointConstants;
import com.evolveum.midpoint.schema.processor.ResourceAttribute;
import com.evolveum.midpoint.schema.processor.ResourceAttributeDefinition;
import com.evolveum.midpoint.schema.processor.ResourceSchema;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.MiscSchemaUtil;
import com.evolveum.midpoint.schema.util.ObjectQueryUtil;
import com.evolveum.midpoint.schema.util.ResourceTypeUtil;
import com.evolveum.midpoint.schema.util.ShadowUtil;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.test.IntegrationTestTools;
import com.evolveum.midpoint.test.util.MidPointTestConstants;
import com.evolveum.midpoint.test.util.TestUtil;
import com.evolveum.midpoint.util.DOMUtil;
import com.evolveum.midpoint.util.MiscUtil;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ActivationStatusType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ActivationType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AssignmentType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ConnectorHostType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ConnectorType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.CredentialsType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.LockoutStatusType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectReferenceType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.OrgType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.PasswordType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ResourceType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.RoleType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.UserType;
import com.evolveum.prism.xml.ns._public.types_3.PolyStringType;
import com.evolveum.prism.xml.ns._public.types_3.ProtectedStringType;

/**
 * @author semancik
 *
 */
@Listeners({com.evolveum.midpoint.tools.testng.AlphabeticalMethodInterceptor.class})
public abstract class AbstractAdTest extends AbstractLdapTest {
	
	protected static final File TEST_DIR = new File(MidPointTestConstants.TEST_RESOURCES_DIR, "ad");

	
	protected ConnectorHostType connectorHostType;
	
	
	
	protected static final File ROLE_PIRATES_FILE = new File(TEST_DIR, "role-pirate.xml");
	protected static final String ROLE_PIRATES_OID = "5dd034e8-41d2-11e5-a123-001e8c717e5b";
	
	protected static final File ROLE_META_ORG_FILE = new File(TEST_DIR, "role-meta-org.xml");
	protected static final String ROLE_META_ORG_OID = "f2ad0ace-45d7-11e5-af54-001e8c717e5b";
	
	public static final String ATTRIBUTE_LOCKOUT_LOCKED_NAME = "lockedByIntruder";
	public static final String ATTRIBUTE_LOCKOUT_RESET_TIME_NAME = "loginIntruderResetTime";
	public static final String ATTRIBUTE_GROUP_MEMBERSHIP_NAME = "groupMembership";
	public static final String ATTRIBUTE_EQUIVALENT_TO_ME_NAME = "equivalentToMe";
	public static final String ATTRIBUTE_SECURITY_EQUALS_NAME = "securityEquals";
	
	protected static final String ACCOUNT_JACK_UID = "jack";
	protected static final String ACCOUNT_JACK_PASSWORD = "qwe123";
	
	private static final String GROUP_PIRATES_NAME = "pirates";
	private static final String GROUP_MELEE_ISLAND_NAME = "Mêlée Island";
	
	protected static final int NUMBER_OF_ACCOUNTS = 4;
	protected static final int LOCKOUT_EXPIRATION_SECONDS = 65;
	private static final String ASSOCIATION_GROUP_NAME = "group";
	
	protected String jackAccountOid;
	protected String groupPiratesOid;
	protected long jackLockoutTimestamp;
	private String accountBarbossaOid;
	private String orgMeleeIslandOid;
	protected String groupMeleeOid;
	
	@Override
	public String getStartSystemCommand() {
		return null;
	}

	@Override
	public String getStopSystemCommand() {
		return null;
	}

	@Override
	protected File getBaseDir() {
		return TEST_DIR;
	}
	
	@Override
	protected String getResourceOid() {
		return "188ec322-4bd7-11e5-b919-001e8c717e5b";
	}
	
	@Override
	protected File getResourceFile() {
		return new File(getBaseDir(), "resource-medusa.xml");
	}
	
	protected String getConnectorHostOid() {
		return "08e687b6-4bd7-11e5-8484-001e8c717e5b";
	}
	
	protected abstract File getConnectorHostFile();

	@Override
	protected String getSyncTaskOid() {
		return null;
	}
	
	@Override
	protected boolean useSsl() {
		return true;
	}

	@Override
	protected String getLdapSuffix() {
		return "dc=win,dc=evolveum,dc=com";
	}

	@Override
	protected String getLdapBindDn() {
		return "CN=IDM Administrator,CN=Users,DC=win,DC=evolveum,DC=com";
	}

	@Override
	protected String getLdapBindPassword() {
		return "Secret123";
	}

	@Override
	protected int getSearchSizeLimit() {
		return -1;
	}
	
	@Override
	public String getPrimaryIdentifierAttributeName() {
		return "GUID";
	}
	
	@Override
	protected QName getAccountObjectClass() {
		return new QName(MidPointConstants.NS_RI,  "AccountObjectClass");
	}

	@Override
	protected String getLdapGroupObjectClass() {
		return "groupOfNames";
	}

	@Override
	protected String getLdapGroupMemberAttribute() {
		return "member";
	}
	
	private QName getAssociationGroupQName() {
		return new QName(MidPointConstants.NS_RI, ASSOCIATION_GROUP_NAME);
	}
	
	@Override
	protected boolean isImportResourceAtInit() {
		return false;
	}
	
	@Override
	public void initSystem(Task initTask, OperationResult initResult) throws Exception {
		super.initSystem(initTask, initResult);
		
//		binaryAttributeDetector.addBinaryAttribute("GUID");

		// Connector host
		connectorHostType = repoAddObjectFromFile(getConnectorHostFile(), ConnectorHostType.class, initResult).asObjectable();
		
		// Users
		repoAddObjectFromFile(USER_BARBOSSA_FILE, UserType.class, initResult);
		repoAddObjectFromFile(USER_GUYBRUSH_FILE, UserType.class, initResult);
		
		// Roles
//		repoAddObjectFromFile(ROLE_PIRATES_FILE, RoleType.class, initResult);
//		repoAddObjectFromFile(ROLE_META_ORG_FILE, RoleType.class, initResult);
		
	}
	
	@Test
    public void test000Sanity() throws Exception {
//		assertLdapPassword(ACCOUNT_JACK_UID, ACCOUNT_JACK_PASSWORD);
//		assertEDirGroupMember(ACCOUNT_JACK_UID, GROUP_PIRATES_NAME);
//		cleanupDelete(toDn(USER_BARBOSSA_USERNAME));
//		cleanupDelete(toDn(USER_CPTBARBOSSA_USERNAME));
//		cleanupDelete(toDn(USER_GUYBRUSH_USERNAME));
//		cleanupDelete(toGroupDn("Mêlée Island"));
	}

	@Test
    public void test001ConnectorHostDiscovery() throws Exception {
		final String TEST_NAME = "test001ConnectorHostDiscovery";
        TestUtil.displayTestTile(this, TEST_NAME);
        
        Task task = taskManager.createTaskInstance(this.getClass().getName() + "." + TEST_NAME);
        OperationResult result = task.getResult();
        
        // WHEN
        TestUtil.displayWhen(TEST_NAME);
        modelService.discoverConnectors(connectorHostType, task, result);
        
        // THEN
 		result.computeStatus();
 		TestUtil.assertSuccess(result);
 		
 		SearchResultList<PrismObject<ConnectorType>> connectors = modelService.searchObjects(ConnectorType.class, null, null, task, result);
 		
 		boolean found = false;
 		for (PrismObject<ConnectorType> connector: connectors) {
 			if (CONNECTOR_AD_TYPE.equals(connector.asObjectable().getConnectorType())) {
 				display("Found connector", connector);
 				found = true;
 			}
 		}
 		assertTrue("AD Connector not found", found);
	}
	
	@Test
    public void test002ImportResource() throws Exception {
		final String TEST_NAME = "test002ImportResource";
        TestUtil.displayTestTile(this, TEST_NAME);
        
        Task task = taskManager.createTaskInstance(this.getClass().getName() + "." + TEST_NAME);
        OperationResult result = task.getResult();
        
        // WHEN
        TestUtil.displayWhen(TEST_NAME);
        resource = importAndGetObjectFromFile(ResourceType.class, getResourceFile(), getResourceOid(), task, result);
        
        // THEN
 		result.computeStatus();
 		TestUtil.assertSuccess(result);
 		
 		resourceType = resource.asObjectable();
	}
	
	@Test
    public void test020Schema() throws Exception {
		final String TEST_NAME = "test020Schema";
        TestUtil.displayTestTile(this, TEST_NAME);
        
        // GIVEN        
        ResourceSchema resourceSchema = RefinedResourceSchema.getResourceSchema(resource, prismContext);
        display("Resource schema", resourceSchema);
        
        RefinedResourceSchema refinedSchema = RefinedResourceSchema.getRefinedSchema(resource);
        display("Refined schema", refinedSchema);
        accountObjectClassDefinition = refinedSchema.findObjectClassDefinition(getAccountObjectClass());
        assertNotNull("No definition for object class "+getAccountObjectClass(), accountObjectClassDefinition);
        display("Account object class def", accountObjectClassDefinition);
        
        ResourceAttributeDefinition<String> cnDef = accountObjectClassDefinition.findAttributeDefinition("cn");
        PrismAsserts.assertDefinition(cnDef, new QName(MidPointConstants.NS_RI, "cn"), DOMUtil.XSD_STRING, 0, 1);
        assertTrue("cn read", cnDef.canRead());
    	assertFalse("cn modify", cnDef.canModify());
    	assertFalse("cn add", cnDef.canAdd());
        
        ResourceAttributeDefinition<String> userPrincipalNameDef = accountObjectClassDefinition.findAttributeDefinition("userPrincipalName");
        PrismAsserts.assertDefinition(userPrincipalNameDef, new QName(MidPointConstants.NS_RI, "userPrincipalName"), DOMUtil.XSD_STRING, 0, 1);
        assertTrue("o read", userPrincipalNameDef.canRead());
        assertTrue("o modify", userPrincipalNameDef.canModify());
        assertTrue("o add", userPrincipalNameDef.canAdd());
        
	}

	
	@Test
    public void test050Capabilities() throws Exception {
		final String TEST_NAME = "test050Capabilities";
        TestUtil.displayTestTile(this, TEST_NAME);
        
        Collection<Object> nativeCapabilitiesCollection = ResourceTypeUtil.getNativeCapabilitiesCollection(resourceType);
        display("Native capabilities", nativeCapabilitiesCollection);
        
        assertTrue("No native activation capability", ResourceTypeUtil.hasResourceNativeActivationCapability(resourceType));
        assertTrue("No native activation status capability", ResourceTypeUtil.hasResourceNativeActivationStatusCapability(resourceType));
        assertTrue("No native lockout capability", ResourceTypeUtil.hasResourceNativeActivationLockoutCapability(resourceType));
        assertTrue("No native credentias capability", ResourceTypeUtil.hasCredentialsCapability(resourceType));
	}

	
//	@Test
//    public void test100SeachJackByLdapUid() throws Exception {
//		final String TEST_NAME = "test100SeachJackByLdapUid";
//        TestUtil.displayTestTile(this, TEST_NAME);
//        
//        // GIVEN
//        Task task = taskManager.createTaskInstance(this.getClass().getName() + "." + TEST_NAME);
//        OperationResult result = task.getResult();
//        
//        ObjectQuery query = createUidQuery(ACCOUNT_JACK_UID);
//        
//		rememberConnectorOperationCount();
//		rememberConnectorSimulatedPagingSearchCount();
//		
//        // WHEN
//        TestUtil.displayWhen(TEST_NAME);
//		SearchResultList<PrismObject<ShadowType>> shadows = modelService.searchObjects(ShadowType.class, query, null, task, result);
//        
//		// THEN
//		result.computeStatus();
//		TestUtil.assertSuccess(result);
//		
//        assertEquals("Unexpected search result: "+shadows, 1, shadows.size());
//        
//        PrismObject<ShadowType> shadow = shadows.get(0);
//        display("Shadow", shadow);
//        assertAccountShadow(shadow, toDn(ACCOUNT_JACK_UID));
//        assertLockout(shadow, LockoutStatusType.NORMAL);
//        jackAccountOid = shadow.getOid();
//        
//        assertConnectorOperationIncrement(2);
//        assertConnectorSimulatedPagingSearchIncrement(0);
//        
//        SearchResultMetadata metadata = shadows.getMetadata();
//        if (metadata != null) {
//        	assertFalse(metadata.isPartialResults());
//        }
//	}
//	
//	@Test
//    public void test105SeachPiratesByCn() throws Exception {
//		final String TEST_NAME = "test105SeachPiratesByCn";
//        TestUtil.displayTestTile(this, TEST_NAME);
//        
//        // GIVEN
//        Task task = taskManager.createTaskInstance(this.getClass().getName() + "." + TEST_NAME);
//        OperationResult result = task.getResult();
//        
//        ObjectQuery query = ObjectQueryUtil.createResourceAndObjectClassQuery(getResourceOid(), getGroupObjectClass(), prismContext);
//		ObjectQueryUtil.filterAnd(query.getFilter(), createAttributeFilter("cn", GROUP_PIRATES_NAME));
//        
//		rememberConnectorOperationCount();
//		rememberConnectorSimulatedPagingSearchCount();
//		
//        // WHEN
//        TestUtil.displayWhen(TEST_NAME);
//		SearchResultList<PrismObject<ShadowType>> shadows = modelService.searchObjects(ShadowType.class, query, null, task, result);
//        
//		// THEN
//		result.computeStatus();
//		TestUtil.assertSuccess(result);
//		
//        assertEquals("Unexpected search result: "+shadows, 1, shadows.size());
//        
//        PrismObject<ShadowType> shadow = shadows.get(0);
//        display("Shadow", shadow);
//        groupPiratesOid = shadow.getOid();
//        
//        assertConnectorOperationIncrement(1);
//        assertConnectorSimulatedPagingSearchIncrement(0);
//        
//        SearchResultMetadata metadata = shadows.getMetadata();
//        if (metadata != null) {
//        	assertFalse(metadata.isPartialResults());
//        }
//	}
//	
//	@Test
//    public void test110GetJack() throws Exception {
//		final String TEST_NAME = "test110GetJack";
//        TestUtil.displayTestTile(this, TEST_NAME);
//        
//        // GIVEN
//        Task task = taskManager.createTaskInstance(this.getClass().getName() + "." + TEST_NAME);
//        OperationResult result = task.getResult();
//        
//        ObjectQuery query = createUidQuery(ACCOUNT_JACK_UID);
//        
//		rememberConnectorOperationCount();
//		rememberConnectorSimulatedPagingSearchCount();
//		
//        // WHEN
//        TestUtil.displayWhen(TEST_NAME);
//        PrismObject<ShadowType> shadow = modelService.getObject(ShadowType.class, jackAccountOid, null, task, result);
//        
//		// THEN
//		result.computeStatus();
//		TestUtil.assertSuccess(result);		
//        display("Shadow", shadow);
//        assertAccountShadow(shadow, toDn(ACCOUNT_JACK_UID));
//        assertLockout(shadow, LockoutStatusType.NORMAL);
//        jackAccountOid = shadow.getOid();
//        
//        IntegrationTestTools.assertAssociation(shadow, getAssociationGroupQName(), groupPiratesOid);
//        
//        assertConnectorOperationIncrement(1);
//        assertConnectorSimulatedPagingSearchIncrement(0);
//	}
//	
//	@Test
//    public void test120JackLockout() throws Exception {
//		final String TEST_NAME = "test120JackLockout";
//        TestUtil.displayTestTile(this, TEST_NAME);
//        
//        // GIVEN
//        Task task = taskManager.createTaskInstance(this.getClass().getName() + "." + TEST_NAME);
//        OperationResult result = task.getResult();
//        
//        makeBadLoginAttempt(ACCOUNT_JACK_UID);
//        makeBadLoginAttempt(ACCOUNT_JACK_UID);
//        makeBadLoginAttempt(ACCOUNT_JACK_UID);
//        makeBadLoginAttempt(ACCOUNT_JACK_UID);
//        
//        jackLockoutTimestamp = System.currentTimeMillis();
//        
//        ObjectQuery query = createUidQuery(ACCOUNT_JACK_UID);
//        
//		rememberConnectorOperationCount();
//		rememberConnectorSimulatedPagingSearchCount();
//		
//        // WHEN
//        TestUtil.displayWhen(TEST_NAME);
//		SearchResultList<PrismObject<ShadowType>> shadows = modelService.searchObjects(ShadowType.class, query, null, task, result);
//        
//		// THEN
//		TestUtil.displayThen(TEST_NAME);
//		result.computeStatus();
//		TestUtil.assertSuccess(result);
//		
//        assertEquals("Unexpected search result: "+shadows, 1, shadows.size());
//        
//        PrismObject<ShadowType> shadow = shadows.get(0);
//        display("Shadow", shadow);
//        assertAccountShadow(shadow, toDn(ACCOUNT_JACK_UID));
//        assertLockout(shadow, LockoutStatusType.LOCKED);
//        
//        assertConnectorOperationIncrement(1);
//        assertConnectorSimulatedPagingSearchIncrement(0);
//        
//        SearchResultMetadata metadata = shadows.getMetadata();
//        if (metadata != null) {
//        	assertFalse(metadata.isPartialResults());
//        }
//	}
//	
//	/**
//	 * No paging. It should return all accounts.
//	 */
//	@Test
//    public void test150SeachAllAccounts() throws Exception {
//		final String TEST_NAME = "test150SeachAllAccounts";
//        TestUtil.displayTestTile(this, TEST_NAME);
//        
//        // GIVEN
//        Task task = taskManager.createTaskInstance(this.getClass().getName() + "." + TEST_NAME);
//        OperationResult result = task.getResult();
//        
//        ObjectQuery query = ObjectQueryUtil.createResourceAndObjectClassQuery(getResourceOid(), getAccountObjectClass(), prismContext);
//        
//        SearchResultList<PrismObject<ShadowType>> searchResultList = doSearch(TEST_NAME, query, NUMBER_OF_ACCOUNTS, task, result);
//        
//        assertConnectorOperationIncrement(1);
//        assertConnectorSimulatedPagingSearchIncrement(0);
//        
//        SearchResultMetadata metadata = searchResultList.getMetadata();
//        if (metadata != null) {
//        	assertFalse(metadata.isPartialResults());
//        }
//    }
//	
//	@Test
//    public void test190SeachLockedAccounts() throws Exception {
//		final String TEST_NAME = "test190SeachLockedAccounts";
//        TestUtil.displayTestTile(this, TEST_NAME);
//        
//        // GIVEN
//        Task task = taskManager.createTaskInstance(this.getClass().getName() + "." + TEST_NAME);
//        OperationResult result = task.getResult();
//        
//        ObjectQuery query = ObjectQueryUtil.createResourceAndObjectClassQuery(getResourceOid(), getAccountObjectClass(), prismContext);
//        ObjectQueryUtil.filterAnd(query.getFilter(), 
//        		EqualFilter.createEqual(new ItemPath(ShadowType.F_ACTIVATION, ActivationType.F_LOCKOUT_STATUS), getShadowDefinition(), LockoutStatusType.LOCKED));
//        
//        SearchResultList<PrismObject<ShadowType>> searchResultList = doSearch(TEST_NAME, query, 1, task, result);
//        
//        assertConnectorOperationIncrement(1);
//        assertConnectorSimulatedPagingSearchIncrement(0);
//        
//        PrismObject<ShadowType> shadow = searchResultList.get(0);
//        display("Shadow", shadow);
//        assertAccountShadow(shadow, toDn(ACCOUNT_JACK_UID));
//        assertLockout(shadow, LockoutStatusType.LOCKED);
//        
//        SearchResultMetadata metadata = searchResultList.getMetadata();
//        if (metadata != null) {
//        	assertFalse(metadata.isPartialResults());
//        }
//    }
//	
//	@Test
//    public void test200AssignAccountBarbossa() throws Exception {
//		final String TEST_NAME = "test200AssignAccountBarbossa";
//        TestUtil.displayTestTile(this, TEST_NAME);
//        
//        // GIVEN
//        Task task = taskManager.createTaskInstance(this.getClass().getName() + "." + TEST_NAME);
//        OperationResult result = task.getResult();
//        long tsStart = System.currentTimeMillis();
//        
//        // WHEN
//        TestUtil.displayWhen(TEST_NAME);
//        assignAccount(USER_BARBOSSA_OID, getResourceOid(), null, task, result);
//        
//        // THEN
//        TestUtil.displayThen(TEST_NAME);
//        result.computeStatus();
//        TestUtil.assertSuccess(result);
//        
//        long tsEnd = System.currentTimeMillis();
//
//        Entry entry = assertLdapAccount(USER_BARBOSSA_USERNAME, USER_BARBOSSA_FULL_NAME);
//        assertAttribute(entry, "title", null);
//        
//        PrismObject<UserType> user = getUser(USER_BARBOSSA_OID);
//        String shadowOid = getSingleLinkOid(user);
//        PrismObject<ShadowType> shadow = getShadowModel(shadowOid);
//        display("Shadow (model)", shadow);
//        accountBarbossaOid = shadow.getOid();
//        Collection<ResourceAttribute<?>> identifiers = ShadowUtil.getIdentifiers(shadow);
//        String accountBarbossaIcfUid = (String) identifiers.iterator().next().getRealValue();
//        assertNotNull("No identifier in "+shadow, accountBarbossaIcfUid);
//        
//        assertEquals("Wrong ICFS UID", MiscUtil.binaryToHex(entry.get(getPrimaryIdentifierAttributeName()).getBytes()), accountBarbossaIcfUid);
//        
//        assertLdapPassword(USER_BARBOSSA_USERNAME, "deadjacktellnotales");
//        
//        ResourceAttribute<Long> createTimestampAttribute = ShadowUtil.getAttribute(shadow, new QName(MidPointConstants.NS_RI, "createTimestamp"));
//        assertNotNull("No createTimestamp in "+shadow, createTimestampAttribute);
//        Long createTimestamp = createTimestampAttribute.getRealValue();
//        // LDAP server may be on a different host. Allow for some clock offset.
//        TestUtil.assertBetween("Wrong createTimestamp in "+shadow, roundTsDown(tsStart)-1000, roundTsUp(tsEnd)+1000, createTimestamp);
//	}
//	
//	@Test
//    public void test210ModifyAccountBarbossaTitle() throws Exception {
//		final String TEST_NAME = "test210ModifyAccountBarbossaTitle";
//        TestUtil.displayTestTile(this, TEST_NAME);
//
//        // GIVEN
//        Task task = taskManager.createTaskInstance(this.getClass().getName() + "." + TEST_NAME);
//        OperationResult result = task.getResult();
//        
//        ObjectDelta<ShadowType> delta = ObjectDelta.createEmptyModifyDelta(ShadowType.class, accountBarbossaOid, prismContext);
//        QName attrQName = new QName(MidPointConstants.NS_RI, "title");
//        ResourceAttributeDefinition<String> attrDef = accountObjectClassDefinition.findAttributeDefinition(attrQName);
//        PropertyDelta<String> attrDelta = PropertyDelta.createModificationReplaceProperty(
//        		new ItemPath(ShadowType.F_ATTRIBUTES, attrQName), attrDef, "Captain");
//        delta.addModification(attrDelta);
//        
//        // WHEN
//        TestUtil.displayWhen(TEST_NAME);
//        modelService.executeChanges(MiscSchemaUtil.createCollection(delta), null, task, result);
//        
//        // THEN
//        TestUtil.displayThen(TEST_NAME);
//        result.computeStatus();
//        TestUtil.assertSuccess(result);
//
//        Entry entry = assertLdapAccount(USER_BARBOSSA_USERNAME, USER_BARBOSSA_FULL_NAME);
//        assertAttribute(entry, "title", "Captain");
//        
//        PrismObject<UserType> user = getUser(USER_BARBOSSA_OID);
//        String shadowOid = getSingleLinkOid(user);
//        assertEquals("Shadows have moved", accountBarbossaOid, shadowOid);
//	}
//	
//	@Test
//    public void test220ModifyUserBarbossaPassword() throws Exception {
//		final String TEST_NAME = "test220ModifyUserBarbossaPassword";
//        TestUtil.displayTestTile(this, TEST_NAME);
//
//        // GIVEN
//        Task task = taskManager.createTaskInstance(this.getClass().getName() + "." + TEST_NAME);
//        OperationResult result = task.getResult();
//        
//        ProtectedStringType userPasswordPs = new ProtectedStringType();
//        userPasswordPs.setClearValue("hereThereBeMonsters");
//        
//        // WHEN
//        TestUtil.displayWhen(TEST_NAME);
//        modifyUserReplace(USER_BARBOSSA_OID, 
//        		new ItemPath(UserType.F_CREDENTIALS,  CredentialsType.F_PASSWORD, PasswordType.F_VALUE), 
//        		task, result, userPasswordPs);
//        
//        // THEN
//        TestUtil.displayThen(TEST_NAME);
//        result.computeStatus();
//        TestUtil.assertSuccess(result);
//
//        Entry entry = assertLdapAccount(USER_BARBOSSA_USERNAME, USER_BARBOSSA_FULL_NAME);
//        assertAttribute(entry, "title", "Captain");
//        assertLdapPassword(USER_BARBOSSA_USERNAME, "hereThereBeMonsters");
//        
//        PrismObject<UserType> user = getUser(USER_BARBOSSA_OID);
//        String shadowOid = getSingleLinkOid(user);
//        assertEquals("Shadows have moved", accountBarbossaOid, shadowOid);
//	}
//	
//	@Test
//    public void test230DisableBarbossa() throws Exception {
//		final String TEST_NAME = "test230DisableBarbossa";
//        TestUtil.displayTestTile(this, TEST_NAME);
//
//        // GIVEN
//        Task task = taskManager.createTaskInstance(this.getClass().getName() + "." + TEST_NAME);
//        OperationResult result = task.getResult();
//        
//        // WHEN
//        TestUtil.displayWhen(TEST_NAME);
//        modifyUserReplace(USER_BARBOSSA_OID, 
//        		new ItemPath(UserType.F_ACTIVATION,  ActivationType.F_ADMINISTRATIVE_STATUS), 
//        		task, result, ActivationStatusType.DISABLED);
//        
//        // THEN
//        TestUtil.displayThen(TEST_NAME);
//        result.computeStatus();
//        TestUtil.assertSuccess(result);
//        
//        PrismObject<UserType> user = getUser(USER_BARBOSSA_OID);
//        assertAdministrativeStatus(user, ActivationStatusType.DISABLED);
//
//        Entry entry = assertLdapAccount(USER_BARBOSSA_USERNAME, USER_BARBOSSA_FULL_NAME);
//        assertAttribute(entry, "loginDisabled", "TRUE");
//        
//        String shadowOid = getSingleLinkOid(user);
//        PrismObject<ShadowType> shadow = getObject(ShadowType.class, shadowOid);
//        assertAdministrativeStatus(shadow, ActivationStatusType.DISABLED);
//	}
//	
//	@Test
//    public void test239EnableBarbossa() throws Exception {
//		final String TEST_NAME = "test239EnableBarbossa";
//        TestUtil.displayTestTile(this, TEST_NAME);
//
//        // GIVEN
//        Task task = taskManager.createTaskInstance(this.getClass().getName() + "." + TEST_NAME);
//        OperationResult result = task.getResult();
//        
//        // WHEN
//        TestUtil.displayWhen(TEST_NAME);
//        modifyUserReplace(USER_BARBOSSA_OID, 
//        		new ItemPath(UserType.F_ACTIVATION,  ActivationType.F_ADMINISTRATIVE_STATUS), 
//        		task, result, ActivationStatusType.ENABLED);
//        
//        // THEN
//        TestUtil.displayThen(TEST_NAME);
//        result.computeStatus();
//        TestUtil.assertSuccess(result);
//        
//        PrismObject<UserType> user = getUser(USER_BARBOSSA_OID);
//        assertAdministrativeStatus(user, ActivationStatusType.ENABLED);
//
//        Entry entry = assertLdapAccount(USER_BARBOSSA_USERNAME, USER_BARBOSSA_FULL_NAME);
//        assertAttribute(entry, "loginDisabled", "FALSE");
//        
//        String shadowOid = getSingleLinkOid(user);
//        PrismObject<ShadowType> shadow = getObject(ShadowType.class, shadowOid);
//        assertAdministrativeStatus(shadow, ActivationStatusType.ENABLED);
//	}
//	
//	/**
//	 * This should create account with a group. And disabled.
//	 */
//	@Test
//    public void test250AssignGuybrushPirates() throws Exception {
//		final String TEST_NAME = "test250AssignGuybrushPirates";
//        TestUtil.displayTestTile(this, TEST_NAME);
//        
//        // GIVEN
//        Task task = taskManager.createTaskInstance(this.getClass().getName() + "." + TEST_NAME);
//        OperationResult result = task.getResult();
//        
//        modifyUserReplace(USER_GUYBRUSH_OID, 
//        		new ItemPath(UserType.F_ACTIVATION,  ActivationType.F_ADMINISTRATIVE_STATUS), 
//        		task, result, ActivationStatusType.DISABLED);
//        
//        // WHEN
//        TestUtil.displayWhen(TEST_NAME);
//        assignRole(USER_GUYBRUSH_OID, ROLE_PIRATES_OID, task, result);
//        
//        // THEN
//        TestUtil.displayThen(TEST_NAME);
//        result.computeStatus();
//        TestUtil.assertSuccess(result);
//
//        Entry entry = assertLdapAccount(USER_GUYBRUSH_USERNAME, USER_GUYBRUSH_FULL_NAME);
//        display("Entry", entry);
//        assertAttribute(entry, "loginDisabled", "TRUE");
//        
//        assertEDirGroupMember(entry, GROUP_PIRATES_NAME);
//        
//        PrismObject<UserType> user = getUser(USER_GUYBRUSH_OID);
//        assertAdministrativeStatus(user, ActivationStatusType.DISABLED);
//        String shadowOid = getSingleLinkOid(user);
//        
//        PrismObject<ShadowType> shadow = getObject(ShadowType.class, shadowOid);
//        IntegrationTestTools.assertAssociation(shadow, getAssociationGroupQName(), groupPiratesOid);
//        assertAdministrativeStatus(shadow, ActivationStatusType.DISABLED);
//	}
//	
//	@Test
//    public void test260EnableGyubrush() throws Exception {
//		final String TEST_NAME = "test260EnableGyubrush";
//        TestUtil.displayTestTile(this, TEST_NAME);
//
//        // GIVEN
//        Task task = taskManager.createTaskInstance(this.getClass().getName() + "." + TEST_NAME);
//        OperationResult result = task.getResult();
//        
//        // WHEN
//        TestUtil.displayWhen(TEST_NAME);
//        modifyUserReplace(USER_GUYBRUSH_OID, 
//        		new ItemPath(UserType.F_ACTIVATION,  ActivationType.F_ADMINISTRATIVE_STATUS), 
//        		task, result, ActivationStatusType.ENABLED);
//        
//        // THEN
//        TestUtil.displayThen(TEST_NAME);
//        result.computeStatus();
//        TestUtil.assertSuccess(result);
//        
//        PrismObject<UserType> user = getUser(USER_GUYBRUSH_OID);
//        assertAdministrativeStatus(user, ActivationStatusType.ENABLED);
//
//        Entry entry = assertLdapAccount(USER_GUYBRUSH_USERNAME, USER_GUYBRUSH_FULL_NAME);
//        assertAttribute(entry, "loginDisabled", "FALSE");
//        
//        String shadowOid = getSingleLinkOid(user);
//        PrismObject<ShadowType> shadow = getObject(ShadowType.class, shadowOid);
//        assertAdministrativeStatus(shadow, ActivationStatusType.ENABLED);
//	}
//
//	// TODO: search for disabled accounts
//	
//	@Test
//    public void test300AssignBarbossaPirates() throws Exception {
//		final String TEST_NAME = "test300AssignBarbossaPirates";
//        TestUtil.displayTestTile(this, TEST_NAME);
//        
//        // GIVEN
//        Task task = taskManager.createTaskInstance(this.getClass().getName() + "." + TEST_NAME);
//        OperationResult result = task.getResult();
//        
//        // WHEN
//        TestUtil.displayWhen(TEST_NAME);
//        assignRole(USER_BARBOSSA_OID, ROLE_PIRATES_OID, task, result);
//        
//        // THEN
//        TestUtil.displayThen(TEST_NAME);
//        result.computeStatus();
//        TestUtil.assertSuccess(result);
//
//        Entry entry = assertLdapAccount(USER_BARBOSSA_USERNAME, USER_BARBOSSA_FULL_NAME);
//        display("Entry", entry);
//        assertAttribute(entry, "title", "Captain");
//        
//        assertEDirGroupMember(entry, GROUP_PIRATES_NAME);
//        
//        PrismObject<UserType> user = getUser(USER_BARBOSSA_OID);
//        String shadowOid = getSingleLinkOid(user);
//        assertEquals("Shadows have moved", accountBarbossaOid, shadowOid);
//        
//        PrismObject<ShadowType> shadow = getObject(ShadowType.class, shadowOid);
//        IntegrationTestTools.assertAssociation(shadow, getAssociationGroupQName(), groupPiratesOid);
//        
//	}
//	
//	@Test
//    public void test390ModifyUserBarbossaRename() throws Exception {
//		final String TEST_NAME = "test390ModifyUserBarbossaRename";
//        TestUtil.displayTestTile(this, TEST_NAME);
//
//        // GIVEN
//        Task task = taskManager.createTaskInstance(this.getClass().getName() + "." + TEST_NAME);
//        OperationResult result = task.getResult();
//        
//        // WHEN
//        TestUtil.displayWhen(TEST_NAME);
//        modifyUserReplace(USER_BARBOSSA_OID, UserType.F_NAME, task, result, PrismTestUtil.createPolyString(USER_CPTBARBOSSA_USERNAME));
//        
//        // THEN
//        TestUtil.displayThen(TEST_NAME);
//        result.computeStatus();
//        TestUtil.assertSuccess(result);
//
//        Entry entry = assertLdapAccount(USER_CPTBARBOSSA_USERNAME, USER_BARBOSSA_FULL_NAME);
//        assertAttribute(entry, "title", "Captain");
//        
//        PrismObject<UserType> user = getUser(USER_BARBOSSA_OID);
//        String shadowOid = getSingleLinkOid(user);
//        assertEquals("Shadows have moved", accountBarbossaOid, shadowOid);
//        PrismObject<ShadowType> shadow = getObject(ShadowType.class, shadowOid);
//        display("Shadow after rename (model)", shadow);
//        
//        PrismObject<ShadowType> repoShadow = repositoryService.getObject(ShadowType.class, shadowOid, null, result);
//        display("Shadow after rename (repo)", repoShadow);
//        
//        assertNoLdapAccount(USER_BARBOSSA_USERNAME);
//	}
//	
//	// TODO: create account with a group membership
//	
//	@Test
//    public void test500AddOrgMeleeIsland() throws Exception {
//		final String TEST_NAME = "test500AddOrgMeleeIsland";
//        TestUtil.displayTestTile(this, TEST_NAME);
//
//        // GIVEN
//        Task task = taskManager.createTaskInstance(this.getClass().getName() + "." + TEST_NAME);
//        OperationResult result = task.getResult();
//        
//        PrismObject<OrgType> org = prismContext.getSchemaRegistry().findObjectDefinitionByCompileTimeClass(OrgType.class).instantiate();
//        OrgType orgType = org.asObjectable();
//        orgType.setName(new PolyStringType(GROUP_MELEE_ISLAND_NAME));
//        AssignmentType metaroleAssignment = new AssignmentType();
//        ObjectReferenceType metaroleRef = new ObjectReferenceType();
//        metaroleRef.setOid(ROLE_META_ORG_OID);
//        metaroleRef.setType(RoleType.COMPLEX_TYPE);
//		metaroleAssignment.setTargetRef(metaroleRef);
//		orgType.getAssignment().add(metaroleAssignment);
//        
//        // WHEN
//        TestUtil.displayWhen(TEST_NAME);
//        addObject(org, task, result);
//        
//        // THEN
//        TestUtil.displayThen(TEST_NAME);
//        result.computeStatus();
//        TestUtil.assertSuccess(result);
//
//        orgMeleeIslandOid = org.getOid();
//        Entry entry = assertLdapGroup(GROUP_MELEE_ISLAND_NAME);
//        
//        org = getObject(OrgType.class, orgMeleeIslandOid);
//        groupMeleeOid = getSingleLinkOid(org);
//        PrismObject<ShadowType> shadow = getShadowModel(groupMeleeOid);
//        display("Shadow (model)", shadow);
//	}
//	
//	@Test
//    public void test510AssignGuybrushMeleeIsland() throws Exception {
//		final String TEST_NAME = "test510AssignGuybrushMeleeIsland";
//        TestUtil.displayTestTile(this, TEST_NAME);
//        
//        // GIVEN
//        Task task = taskManager.createTaskInstance(this.getClass().getName() + "." + TEST_NAME);
//        OperationResult result = task.getResult();
//        
//        // WHEN
//        TestUtil.displayWhen(TEST_NAME);
//        assignOrg(USER_GUYBRUSH_OID, orgMeleeIslandOid, task, result);
//        
//        // THEN
//        TestUtil.displayThen(TEST_NAME);
//        result.computeStatus();
//        TestUtil.assertSuccess(result);
//
//        Entry entry = assertLdapAccount(USER_GUYBRUSH_USERNAME, USER_GUYBRUSH_FULL_NAME);
//        
//        PrismObject<UserType> user = getUser(USER_GUYBRUSH_OID);
//        String shadowOid = getSingleLinkOid(user);
//        PrismObject<ShadowType> shadow = getShadowModel(shadowOid);
//        display("Shadow (model)", shadow);
//        
//        assertEDirGroupMember(entry, GROUP_PIRATES_NAME);
//
//        IntegrationTestTools.assertAssociation(shadow, getAssociationGroupQName(), groupMeleeOid);
//	}
//	
//	// Wait until the lockout of Jack expires, check status
//	@Test
//    public void test800JackLockoutExpires() throws Exception {
//		final String TEST_NAME = "test800JackLockoutExpires";
//        TestUtil.displayTestTile(this, TEST_NAME);
//        
//        // GIVEN
//        Task task = taskManager.createTaskInstance(this.getClass().getName() + "." + TEST_NAME);
//        OperationResult result = task.getResult();
//        
//        long now = System.currentTimeMillis();
//        long lockoutExpires = jackLockoutTimestamp + LOCKOUT_EXPIRATION_SECONDS*1000;
//        if (now < lockoutExpires) {
//        	display("Sleeping for "+(lockoutExpires-now)+"ms (waiting for lockout expiration)");
//        	Thread.sleep(lockoutExpires-now);
//        }
//        now = System.currentTimeMillis();
//        display("Time is now "+now);
//        
//        ObjectQuery query = createUidQuery(ACCOUNT_JACK_UID);
//        
//		rememberConnectorOperationCount();
//		rememberConnectorSimulatedPagingSearchCount();
//		
//        // WHEN
//        TestUtil.displayWhen(TEST_NAME);
//		SearchResultList<PrismObject<ShadowType>> shadows = modelService.searchObjects(ShadowType.class, query, null, task, result);
//        
//		// THEN
//		TestUtil.displayThen(TEST_NAME);
//		result.computeStatus();
//		TestUtil.assertSuccess(result);
//		
//        assertEquals("Unexpected search result: "+shadows, 1, shadows.size());
//        
//        PrismObject<ShadowType> shadow = shadows.get(0);
//        display("Shadow", shadow);
//        assertAccountShadow(shadow, toDn(ACCOUNT_JACK_UID));
//        assertLockout(shadow, LockoutStatusType.NORMAL);
//        
//        assertConnectorOperationIncrement(1);
//        assertConnectorSimulatedPagingSearchIncrement(0);
//        
//        SearchResultMetadata metadata = shadows.getMetadata();
//        if (metadata != null) {
//        	assertFalse(metadata.isPartialResults());
//        }
//	}
//	
//	@Test
//    public void test810SeachLockedAccounts() throws Exception {
//		final String TEST_NAME = "test810SeachLockedAccounts";
//        TestUtil.displayTestTile(this, TEST_NAME);
//        
//        // GIVEN
//        Task task = taskManager.createTaskInstance(this.getClass().getName() + "." + TEST_NAME);
//        OperationResult result = task.getResult();
//        
//        ObjectQuery query = ObjectQueryUtil.createResourceAndObjectClassQuery(getResourceOid(), getAccountObjectClass(), prismContext);
//        ObjectQueryUtil.filterAnd(query.getFilter(), 
//        		EqualFilter.createEqual(new ItemPath(ShadowType.F_ACTIVATION, ActivationType.F_LOCKOUT_STATUS), getShadowDefinition(), LockoutStatusType.LOCKED));
//        
//        SearchResultList<PrismObject<ShadowType>> searchResultList = doSearch(TEST_NAME, query, 0, task, result);
//        
//        assertConnectorOperationIncrement(1);
//        assertConnectorSimulatedPagingSearchIncrement(0);        
//    }
//	
//	@Test
//    public void test820JackLockoutAndUnlock() throws Exception {
//		final String TEST_NAME = "test820JackLockoutAndUnlock";
//        TestUtil.displayTestTile(this, TEST_NAME);
//        
//        // GIVEN
//        Task task = taskManager.createTaskInstance(this.getClass().getName() + "." + TEST_NAME);
//        OperationResult result = task.getResult();
//        
//        makeBadLoginAttempt(ACCOUNT_JACK_UID);
//        makeBadLoginAttempt(ACCOUNT_JACK_UID);
//        makeBadLoginAttempt(ACCOUNT_JACK_UID);
//        makeBadLoginAttempt(ACCOUNT_JACK_UID);
//        
//        jackLockoutTimestamp = System.currentTimeMillis();
//        
//        ObjectQuery query = createUidQuery(ACCOUNT_JACK_UID);
//		
//        // precondition
//		SearchResultList<PrismObject<ShadowType>> shadows = modelService.searchObjects(ShadowType.class, query, null, task, result);
//		result.computeStatus();
//		TestUtil.assertSuccess(result);
//        assertEquals("Unexpected search result: "+shadows, 1, shadows.size());
//        PrismObject<ShadowType> shadowLocked = shadows.get(0);
//        display("Locked shadow", shadowLocked);
//        assertAccountShadow(shadowLocked, toDn(ACCOUNT_JACK_UID));
//        assertLockout(shadowLocked, LockoutStatusType.LOCKED);
//		
//        rememberConnectorOperationCount();
//		rememberConnectorSimulatedPagingSearchCount();
//        
//		// WHEN
//        TestUtil.displayWhen(TEST_NAME);
//        modifyObjectReplaceProperty(ShadowType.class, shadowLocked.getOid(), 
//        		new ItemPath(ShadowType.F_ACTIVATION, ActivationType.F_LOCKOUT_STATUS), task, result,
//        		LockoutStatusType.NORMAL);
//        
//		// THEN
//		TestUtil.displayThen(TEST_NAME);
//		result.computeStatus();
//		TestUtil.assertSuccess(result);
//
//        assertConnectorOperationIncrement(1);
//        assertConnectorSimulatedPagingSearchIncrement(0);
//		
//		PrismObject<ShadowType> shadowAfter = getObject(ShadowType.class, shadowLocked.getOid());
//        display("Shadow after", shadowAfter);
//        assertAccountShadow(shadowAfter, toDn(ACCOUNT_JACK_UID));
//        assertLockout(shadowAfter, LockoutStatusType.NORMAL);
//
//        assertLdapPassword(ACCOUNT_JACK_UID, ACCOUNT_JACK_PASSWORD);
//        
//        SearchResultMetadata metadata = shadows.getMetadata();
//        if (metadata != null) {
//        	assertFalse(metadata.isPartialResults());
//        }
//	}
//	
//	// Let's do this at the very end.
//	// We need to wait after rename, otherwise the delete fail with:
//    // NDS error: previous move in progress (-637)
//    // So ... let's give some time to eDirectory to sort the things out
//	
//	@Test
//    public void test890UnAssignBarbossaPirates() throws Exception {
//		final String TEST_NAME = "test890UnAssignBarbossaPirates";
//        TestUtil.displayTestTile(this, TEST_NAME);
//
//        // TODO: do this on another account. There is a bad interference with rename.
//        
//        // GIVEN
//        Task task = taskManager.createTaskInstance(this.getClass().getName() + "." + TEST_NAME);
//        OperationResult result = task.getResult();
//        
//        // WHEN
//        TestUtil.displayWhen(TEST_NAME);
//        unassignRole(USER_BARBOSSA_OID, ROLE_PIRATES_OID, task, result);
//        
//        // THEN
//        TestUtil.displayThen(TEST_NAME);
//        result.computeStatus();
//        TestUtil.assertSuccess(result);
//
//        Entry entry = assertLdapAccount(USER_CPTBARBOSSA_USERNAME, USER_BARBOSSA_FULL_NAME);
//        display("Entry", entry);
//        assertAttribute(entry, "title", "Captain");
//        
//        assertEDirNoGroupMember(entry, GROUP_PIRATES_NAME);
//        
//        PrismObject<UserType> user = getUser(USER_BARBOSSA_OID);
//        String shadowOid = getSingleLinkOid(user);
//        assertEquals("Shadows have moved", accountBarbossaOid, shadowOid);
//        
//        PrismObject<ShadowType> shadow = getObject(ShadowType.class, shadowOid);
//        IntegrationTestTools.assertNoAssociation(shadow, getAssociationGroupQName(), groupPiratesOid);
//        
//	}
//	
//	@Test
//    public void test899UnAssignAccountBarbossa() throws Exception {
//		final String TEST_NAME = "test899UnAssignAccountBarbossa";
//        TestUtil.displayTestTile(this, TEST_NAME);
//
//        // GIVEN
//        Task task = taskManager.createTaskInstance(this.getClass().getName() + "." + TEST_NAME);
//        OperationResult result = task.getResult();
//        
//        // WHEN
//        TestUtil.displayWhen(TEST_NAME);
//        unassignAccount(USER_BARBOSSA_OID, getResourceOid(), null, task, result);
//        
//        // THEN
//        TestUtil.displayThen(TEST_NAME);
//        result.computeStatus();
//        TestUtil.assertSuccess(result);
//
//        assertNoLdapAccount(USER_BARBOSSA_USERNAME);
//        assertNoLdapAccount(USER_CPTBARBOSSA_USERNAME);
//        
//        PrismObject<UserType> user = getUser(USER_BARBOSSA_OID);
//        assertNoLinkedAccount(user);
//	}
//	
//	// TODO: lock out jack again, explicitly reset the lock, see that he can login
//
//	@Override
//	protected void assertAccountShadow(PrismObject<ShadowType> shadow, String dn) throws SchemaException {
//		super.assertAccountShadow(shadow, dn);
//		ResourceAttribute<String> primaryIdAttr = ShadowUtil.getAttribute(shadow, getPrimaryIdentifierAttributeQName());
//		assertNotNull("No primary identifier ("+getPrimaryIdentifierAttributeQName()+" in "+shadow, primaryIdAttr);
//		String primaryId = primaryIdAttr.getRealValue();
//		assertTrue("Unexpected chars in primary ID: '"+primaryId+"'", primaryId.matches("[a-z0-9]+"));
//	}
//	
//	private void makeBadLoginAttempt(String uid) throws LdapException {
//		LdapNetworkConnection conn = ldapConnect(toDn(uid), "thisIsAwRoNgPASSW0RD");
//		if (conn.isAuthenticated()) {
//			AssertJUnit.fail("Bad authentication went good for "+uid);
//		}
//	}
//	
//	private void assertEDirGroupMember(String accountUid, String groupName) throws LdapException, IOException, CursorException {
//		Entry accountEntry = getLdapAccountByUid(accountUid);
//		assertEDirGroupMember(accountEntry, groupName);
//	}
//	
//	private void assertEDirGroupMember(Entry accountEntry, String groupName) throws LdapException, IOException, CursorException {
//		Entry groupEntry = getLdapGroupByName(groupName);
//		assertAttributeContains(groupEntry, getLdapGroupMemberAttribute(), accountEntry.getDn().toString());
//		assertAttributeContains(groupEntry, ATTRIBUTE_EQUIVALENT_TO_ME_NAME, accountEntry.getDn().toString());
//		assertAttributeContains(accountEntry, ATTRIBUTE_GROUP_MEMBERSHIP_NAME, groupEntry.getDn().toString());
//		assertAttributeContains(accountEntry, ATTRIBUTE_SECURITY_EQUALS_NAME, groupEntry.getDn().toString());
//	}
//	
//	private void assertEDirNoGroupMember(Entry accountEntry, String groupName) throws LdapException, IOException, CursorException {
//		Entry groupEntry = getLdapGroupByName(groupName);
//		assertAttributeNotContains(groupEntry, getLdapGroupMemberAttribute(), accountEntry.getDn().toString());
//		assertAttributeNotContains(groupEntry, ATTRIBUTE_EQUIVALENT_TO_ME_NAME, accountEntry.getDn().toString());
//		assertAttributeNotContains(accountEntry, ATTRIBUTE_GROUP_MEMBERSHIP_NAME, groupEntry.getDn().toString());
//		assertAttributeNotContains(accountEntry, ATTRIBUTE_SECURITY_EQUALS_NAME, groupEntry.getDn().toString());
//	}
}
