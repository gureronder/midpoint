/*
 * Copyright (c) 2010-2015 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.evolveum.midpoint.model.intest;

import com.evolveum.icf.dummy.resource.DummyAccount;
import com.evolveum.icf.dummy.resource.DummyResource;
import com.evolveum.icf.dummy.resource.DummySyncStyle;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.util.PrismAsserts;
import com.evolveum.midpoint.prism.util.PrismTestUtil;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.MiscSchemaUtil;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.test.DummyResourceContoller;
import com.evolveum.midpoint.test.util.TestUtil;
import com.evolveum.midpoint.util.exception.CommunicationException;
import com.evolveum.midpoint.util.exception.ConfigurationException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SecurityViolationException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AssignmentPolicyEnforcementType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectTemplateType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ResourceType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.SynchronizationSituationType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.UserType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ContextConfiguration;
import org.testng.annotations.Test;

import javax.xml.datatype.XMLGregorianCalendar;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collection;

import static com.evolveum.midpoint.test.IntegrationTestTools.display;
import static com.evolveum.midpoint.test.IntegrationTestTools.getAttributeValue;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;

/**
 * Tests for MID-2436 (volatile attributes).
 *
 * @author mederly
 *
 */
@ContextConfiguration(locations = {"classpath:ctx-model-intest-test-main.xml"})
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
public class TestVolatility extends AbstractInitializedModelIntegrationTest {
	
	public static final File TEST_DIR = new File("src/test/resources/volatility");

    protected static final File RESOURCE_DUMMY_HR_FILE = new File(TEST_DIR, "resource-dummy-hr.xml");
    protected static final String RESOURCE_DUMMY_HR_OID = "10000000-0000-0000-0000-00000000f004";
    protected static final String RESOURCE_DUMMY_HR_NAME = "hr";

    protected static final File RESOURCE_DUMMY_VOLATILE_FILE = new File(TEST_DIR, "resource-dummy-volatile.xml");
    protected static final String RESOURCE_DUMMY_VOLATILE_OID = "10000000-0000-0000-0000-00000000f104";
    protected static final String RESOURCE_DUMMY_VOLATILE_NAME = "volatile";

    protected static final String ACCOUNT_MANCOMB_DUMMY_USERNAME = "mancomb";
    protected static final String ACCOUNT_GUYBRUSH_DUMMY_USERNAME = "guybrush";     //Guybrush Threepwood
    protected static final String ACCOUNT_LARGO_DUMMY_USERNAME = "largo";

    protected static final String TASK_LIVE_SYNC_DUMMY_HR_FILENAME = TEST_DIR + "/task-dummy-hr-livesync.xml";
    protected static final String TASK_LIVE_SYNC_DUMMY_HR_OID = "10000000-0000-0000-5555-55550000f004";

    protected static final File USER_TEMPLATE_FILE = new File(TEST_DIR, "user-template-import-hr.xml");
    protected static final File USER_LARGO_WITH_ASSIGNMENT_FILE = new File(TEST_DIR, "user-largo-with-assignment.xml");

    protected PrismObject<ResourceType> resourceDummyHr;
    protected DummyResourceContoller dummyResourceCtlHr;
    protected DummyResource dummyResourceHr;

    protected PrismObject<ResourceType> resourceDummyVolatile;
    protected DummyResourceContoller dummyResourceCtlVolatile;
    protected DummyResource dummyResourceVolatile;

    @Override
	public void initSystem(Task initTask, OperationResult initResult) throws Exception {
		super.initSystem(initTask, initResult);
        assumeAssignmentPolicy(AssignmentPolicyEnforcementType.FULL);

        repoAddObjectFromFile(USER_TEMPLATE_FILE, ObjectTemplateType.class, initResult);

        dummyResourceCtlHr = DummyResourceContoller.create(RESOURCE_DUMMY_HR_NAME, null);
        dummyResourceHr = dummyResourceCtlHr.getDummyResource();
        dummyResourceHr.setSyncStyle(DummySyncStyle.SMART);
        dummyResourceHr.populateWithDefaultSchema();
        resourceDummyHr = importAndGetObjectFromFile(ResourceType.class, RESOURCE_DUMMY_HR_FILE, RESOURCE_DUMMY_HR_OID, initTask, initResult);
        dummyResourceCtlHr.setResource(resourceDummyHr);

        dummyResourceCtlVolatile = DummyResourceContoller.create(RESOURCE_DUMMY_VOLATILE_NAME, null);
        dummyResourceVolatile = dummyResourceCtlVolatile.getDummyResource();
        dummyResourceVolatile.setSyncStyle(DummySyncStyle.SMART);
        dummyResourceVolatile.populateWithDefaultSchema();
        resourceDummyVolatile = importAndGetObjectFromFile(ResourceType.class, RESOURCE_DUMMY_VOLATILE_FILE, RESOURCE_DUMMY_VOLATILE_OID, initTask, initResult);
        dummyResourceCtlVolatile.setResource(resourceDummyVolatile);
    }

    @Test
    public void test100ImportLiveSyncTaskDummyHr() throws Exception {
        final String TEST_NAME = "test100ImportLiveSyncTaskDummyHr";
        TestUtil.displayTestTile(this, TEST_NAME);

        // GIVEN
        Task task = createTask(TestVolatility.class.getName() + "." + TEST_NAME);
        OperationResult result = task.getResult();

        /// WHEN
        TestUtil.displayWhen(TEST_NAME);
        importSyncTask();

        // THEN
        TestUtil.displayThen(TEST_NAME);

        waitForSyncTaskStart();
    }

    @Test
    public void test110AddDummyHrAccountMancomb() throws Exception {
        final String TEST_NAME = "test110AddDummyHrAccountMancomb";
        TestUtil.displayTestTile(this, TEST_NAME);

        // GIVEN
        Task task = createTask(TestVolatility.class.getName() + "." + TEST_NAME);
        OperationResult result = task.getResult();

        // Preconditions
        //assertUsers(5);

        DummyAccount account = new DummyAccount(ACCOUNT_MANCOMB_DUMMY_USERNAME);
        account.setEnabled(true);
        account.addAttributeValues(DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_FULLNAME_NAME, "Mancomb Seepgood");

        /// WHEN
        TestUtil.displayWhen(TEST_NAME);

        dummyResourceHr.addAccount(account);

        waitForSyncTaskNextRun();

        // THEN
        TestUtil.displayThen(TEST_NAME);

        PrismObject<ShadowType> accountMancombHr = findAccountByUsername(ACCOUNT_MANCOMB_DUMMY_USERNAME, resourceDummyHr);
        display("Account mancomb on HR", accountMancombHr);
        assertNotNull("No mancomb HR account shadow", accountMancombHr);
        assertEquals("Wrong resourceRef in mancomb HR account", RESOURCE_DUMMY_HR_OID,
                accountMancombHr.asObjectable().getResourceRef().getOid());
        assertShadowOperationalData(accountMancombHr, SynchronizationSituationType.LINKED, null);

        PrismObject<ShadowType> accountMancombVolatileTarget = findAccountByUsername(ACCOUNT_MANCOMB_DUMMY_USERNAME, resourceDummyVolatile);
        display("Account mancomb on target", accountMancombVolatileTarget);
        assertNotNull("No mancomb target account shadow", accountMancombVolatileTarget);
        assertEquals("Wrong resourceRef in mancomb target account", RESOURCE_DUMMY_VOLATILE_OID,
                accountMancombVolatileTarget.asObjectable().getResourceRef().getOid());
        assertShadowOperationalData(accountMancombVolatileTarget, SynchronizationSituationType.LINKED, null);

        PrismObject<UserType> userMancomb = findUserByUsername(ACCOUNT_MANCOMB_DUMMY_USERNAME);
        display("User mancomb", userMancomb);
        assertNotNull("User mancomb was not created", userMancomb);
        assertLinks(userMancomb, 2);

        assertLinked(userMancomb, accountMancombHr);
        assertLinked(userMancomb, accountMancombVolatileTarget);

        String descriptionOnResource = getAttributeValue(accountMancombVolatileTarget.asObjectable(),
                DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_DESCRIPTION_QNAME);
        String descriptionOfUser = userMancomb.asObjectable().getDescription();
        String expectedDescription = "Description of " + ACCOUNT_MANCOMB_DUMMY_USERNAME;

        assertEquals("Wrong description on resource account", expectedDescription, descriptionOnResource);
        assertEquals("Wrong description in user record", expectedDescription, descriptionOfUser);

//        assertUsers(6);

        // notifications
        notificationManager.setDisabled(true);
    }

    @Test
    public void test120UpdateDummyHrAccountMancomb() throws Exception {
        final String TEST_NAME = "test120UpdateDummyHrAccountMancomb";
        TestUtil.displayTestTile(this, TEST_NAME);

        // GIVEN
        Task task = createTask(TestVolatility.class.getName() + "." + TEST_NAME);
        OperationResult result = task.getResult();

        /// WHEN
        TestUtil.displayWhen(TEST_NAME);
        DummyAccount account = dummyResourceHr.getAccountByUsername(ACCOUNT_MANCOMB_DUMMY_USERNAME);
        account.replaceAttributeValue(DummyAccount.ATTR_FULLNAME_NAME, "Sir Mancomb Seepgood");

        display("Dummy HR resource", dummyResourceHr.debugDump());

        // Make sure we have steady state
        waitForSyncTaskNextRun();

        // THEN
        TestUtil.displayThen(TEST_NAME);

        PrismObject<ShadowType> accountMancombHr = findAccountByUsername(ACCOUNT_MANCOMB_DUMMY_USERNAME, resourceDummyHr);
        display("Account mancomb on HR", accountMancombHr);
        assertNotNull("No mancomb HR account shadow", accountMancombHr);
        assertEquals("Wrong resourceRef in mancomb HR account", RESOURCE_DUMMY_HR_OID,
                accountMancombHr.asObjectable().getResourceRef().getOid());
        assertEquals("Wrong name in mancomb HR account", "Sir Mancomb Seepgood",
                getAttributeValue(accountMancombHr.asObjectable(),
                        DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_FULLNAME_QNAME));
        assertShadowOperationalData(accountMancombHr, SynchronizationSituationType.LINKED, null);

        PrismObject<ShadowType> accountMancombVolatileTarget = findAccountByUsername(ACCOUNT_MANCOMB_DUMMY_USERNAME, resourceDummyVolatile);
        display("Account mancomb on target", accountMancombVolatileTarget);
        assertNotNull("No mancomb target account shadow", accountMancombVolatileTarget);
        assertEquals("Wrong resourceRef in mancomb target account", RESOURCE_DUMMY_VOLATILE_OID,
                accountMancombVolatileTarget.asObjectable().getResourceRef().getOid());
        assertEquals("Wrong name in mancomb target account", "Sir Mancomb Seepgood",
                getAttributeValue(accountMancombHr.asObjectable(),
                        DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_FULLNAME_QNAME));
        assertShadowOperationalData(accountMancombVolatileTarget, SynchronizationSituationType.LINKED, null);

        PrismObject<UserType> userMancomb = findUserByUsername(ACCOUNT_MANCOMB_DUMMY_USERNAME);
        display("User mancomb", userMancomb);
        assertNotNull("User mancomb is not there", userMancomb);
        assertLinks(userMancomb, 2);
        assertEquals("Wrong name in mancomb user", "Sir Mancomb Seepgood",
                userMancomb.asObjectable().getFullName().getOrig());

        assertLinked(userMancomb, accountMancombHr);
        assertLinked(userMancomb, accountMancombVolatileTarget);

        String descriptionOnResource = getAttributeValue(accountMancombVolatileTarget.asObjectable(),
                DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_DESCRIPTION_QNAME);
        String descriptionOfUser = userMancomb.asObjectable().getDescription();
        String expectedDescription = "Updated description of " + ACCOUNT_MANCOMB_DUMMY_USERNAME;

        assertEquals("Wrong description on resource account", expectedDescription, descriptionOnResource);
        assertEquals("Wrong description in user record", expectedDescription, descriptionOfUser);

        // notifications
        notificationManager.setDisabled(true);
    }

    @Test
    public void test200ModifyGuybrushAssignAccount() throws Exception {
        final String TEST_NAME = "test200ModifyGuybrushAssignAccount";
        TestUtil.displayTestTile(this, TEST_NAME);

        // GIVEN
        Task task = createTask(TestVolatility.class.getName() + "." + TEST_NAME);
        OperationResult result = task.getResult();

        // Preconditions
        //assertUsers(5);

        TestUtil.displayWhen(TEST_NAME);

        Collection<ObjectDelta<? extends ObjectType>> deltas = new ArrayList<ObjectDelta<? extends ObjectType>>();
        ObjectDelta<UserType> accountAssignmentUserDelta = createAccountAssignmentUserDelta(USER_GUYBRUSH_OID, RESOURCE_DUMMY_VOLATILE_OID, null, true);
        deltas.add(accountAssignmentUserDelta);

        // WHEN
        modelService.executeChanges(deltas, null, task, result);

        // THEN
        TestUtil.displayThen(TEST_NAME);

        PrismObject<UserType> userGuybrush = findUserByUsername(ACCOUNT_GUYBRUSH_DUMMY_USERNAME);
        display("User guybrush", userGuybrush);
        assertNotNull("User guybrush is not there", userGuybrush);
        assertLinks(userGuybrush, 1);

        PrismObject<ShadowType> accountGuybrushVolatileTarget = findAccountByUsername(ACCOUNT_GUYBRUSH_DUMMY_USERNAME, resourceDummyVolatile);
        display("Account guybrush on target", accountGuybrushVolatileTarget);
        assertNotNull("No guybrush target account shadow", accountGuybrushVolatileTarget);
        assertEquals("Wrong resourceRef in guybrush target account", RESOURCE_DUMMY_VOLATILE_OID,
                accountGuybrushVolatileTarget.asObjectable().getResourceRef().getOid());
        assertShadowOperationalData(accountGuybrushVolatileTarget, SynchronizationSituationType.LINKED, null);

        assertLinked(userGuybrush, accountGuybrushVolatileTarget);

        String descriptionOnResource = getAttributeValue(accountGuybrushVolatileTarget.asObjectable(),
                DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_DESCRIPTION_QNAME);
        String descriptionOfUser = userGuybrush.asObjectable().getDescription();
        String expectedDescription = "Description of " + ACCOUNT_GUYBRUSH_DUMMY_USERNAME;

        assertEquals("Wrong description on resource account", expectedDescription, descriptionOnResource);
        assertEquals("Wrong description in user record", expectedDescription, descriptionOfUser);

//        assertUsers(6);

        // notifications
        notificationManager.setDisabled(true);
    }

    @Test
    public void test300AddLargo() throws Exception {
        final String TEST_NAME = "test300AddLargo";
        TestUtil.displayTestTile(this, TEST_NAME);

        // GIVEN
        Task task = createTask(TestVolatility.class.getName() + "." + TEST_NAME);
        OperationResult result = task.getResult();

        // Preconditions
        //assertUsers(5);

        TestUtil.displayWhen(TEST_NAME);
        PrismObject<UserType> user = PrismTestUtil.parseObject(USER_LARGO_WITH_ASSIGNMENT_FILE);
        ObjectDelta<UserType> userDelta = ObjectDelta.createAddDelta(user);
        Collection<ObjectDelta<? extends ObjectType>> deltas = MiscSchemaUtil.createCollection(userDelta);

        // WHEN
        modelService.executeChanges(deltas, null, task, result);

        // THEN
        TestUtil.displayThen(TEST_NAME);

        PrismObject<UserType> userLargo = findUserByUsername(ACCOUNT_LARGO_DUMMY_USERNAME);
        display("User largo", userLargo);
        assertNotNull("User largo is not there", userLargo);
        assertLinks(userLargo, 1);

        PrismObject<ShadowType> accountLargoVolatileTarget = findAccountByUsername(ACCOUNT_LARGO_DUMMY_USERNAME, resourceDummyVolatile);
        display("Account largo on target", accountLargoVolatileTarget);
        assertNotNull("No largo target account shadow", accountLargoVolatileTarget);
        assertEquals("Wrong resourceRef in largo target account", RESOURCE_DUMMY_VOLATILE_OID,
                accountLargoVolatileTarget.asObjectable().getResourceRef().getOid());
        assertShadowOperationalData(accountLargoVolatileTarget, SynchronizationSituationType.LINKED, null);

        assertLinked(userLargo, accountLargoVolatileTarget);

        String descriptionOnResource = getAttributeValue(accountLargoVolatileTarget.asObjectable(),
                DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_DESCRIPTION_QNAME);
        String descriptionOfUser = userLargo.asObjectable().getDescription();
        String expectedDescription = "Description of " + ACCOUNT_LARGO_DUMMY_USERNAME;

        assertEquals("Wrong description on resource account", expectedDescription, descriptionOnResource);
        assertEquals("Wrong description in user record", expectedDescription, descriptionOfUser);

//        assertUsers(6);

        // notifications
        notificationManager.setDisabled(true);
    }

    protected void importSyncTask() throws FileNotFoundException {
        importObjectFromFile(TASK_LIVE_SYNC_DUMMY_HR_FILENAME);
    }

    protected void waitForSyncTaskStart() throws Exception {
        waitForTaskStart(TASK_LIVE_SYNC_DUMMY_HR_OID, false, 10000);
    }

    protected void waitForSyncTaskNextRun() throws Exception {
        waitForTaskNextRunAssertSuccess(TASK_LIVE_SYNC_DUMMY_HR_OID, false, 10000);
    }

	private void assertAccount(PrismObject<UserType> userJack, String name, String expectedFullName, String shipAttributeName, String expectedShip,
			boolean expectedEnabled, DummyResourceContoller resourceCtl, Task task) throws ObjectNotFoundException, SchemaException, SecurityViolationException, CommunicationException, ConfigurationException {
		// ship inbound mapping is used, it is strong 
        String accountOid = getSingleLinkOid(userJack);
        
		// Check shadow
        PrismObject<ShadowType> accountShadow = repositoryService.getObject(ShadowType.class, accountOid, null, task.getResult());
        display("Repo shadow", accountShadow);
        assertAccountShadowRepo(accountShadow, accountOid, name, resourceCtl.getResource().asObjectable());
        
        // Check account
        // All the changes should be reflected to the account
        PrismObject<ShadowType> accountModel = modelService.getObject(ShadowType.class, accountOid, null, task, task.getResult());
        display("Model shadow", accountModel);
        assertAccountShadowModel(accountModel, accountOid, name, resourceCtl.getResource().asObjectable());
        PrismAsserts.assertPropertyValue(accountModel, 
        		resourceCtl.getAttributePath(DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_FULLNAME_NAME),
        		expectedFullName);
        if (shipAttributeName != null) {
	        if (expectedShip == null) {
	        	PrismAsserts.assertNoItem(accountModel, 
	            		resourceCtl.getAttributePath(shipAttributeName));        	
	        } else {
	        	PrismAsserts.assertPropertyValue(accountModel, 
	        		resourceCtl.getAttributePath(shipAttributeName),
	        		expectedShip);
	        }
        }
        
        // Check account in dummy resource
        assertDummyAccount(resourceCtl.getName(), name, expectedFullName, expectedEnabled);
	}
	
}
