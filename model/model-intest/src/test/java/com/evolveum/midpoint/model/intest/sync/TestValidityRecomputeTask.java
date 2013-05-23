/*
 * Copyright (c) 2013 Evolveum
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
package com.evolveum.midpoint.model.intest.sync;

import static org.testng.AssertJUnit.assertNotNull;
import static com.evolveum.midpoint.test.IntegrationTestTools.displayTestTile;
import static com.evolveum.midpoint.test.IntegrationTestTools.displayThen;
import static com.evolveum.midpoint.test.IntegrationTestTools.displayWhen;
import static com.evolveum.midpoint.test.IntegrationTestTools.display;

import javax.xml.datatype.XMLGregorianCalendar;

import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ContextConfiguration;
import org.testng.annotations.Test;

import com.evolveum.midpoint.model.intest.AbstractInitializedModelIntegrationTest;
import com.evolveum.midpoint.model.sync.SynchronizationConstants;
import com.evolveum.midpoint.prism.PrismContainer;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.PrismProperty;
import com.evolveum.midpoint.prism.PrismPropertyValue;
import com.evolveum.midpoint.prism.xml.XmlTypeConverter;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.test.IntegrationTestTools;
import com.evolveum.midpoint.util.exception.CommunicationException;
import com.evolveum.midpoint.util.exception.ConfigurationException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SecurityViolationException;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ActivationStatusType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.TaskType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.TimeIntervalStatusType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.UserType;

/**
 * @author Radovan Semancik
 *
 */
@ContextConfiguration(locations = {"classpath:ctx-model-intest-test-main.xml"})
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
public class TestValidityRecomputeTask extends AbstractInitializedModelIntegrationTest {

	private static final XMLGregorianCalendar LONG_LONG_TIME_AGO = XmlTypeConverter.createXMLGregorianCalendar(1111, 1, 1, 12, 00, 00);

	@Test
    public void test100ImportScannerTask() throws Exception {
		final String TEST_NAME = "test100ImportScannerTask";
        displayTestTile(this, TEST_NAME);

        // GIVEN
        Task task = createTask(AbstractSynchronizationStoryTest.class.getName() + "." + TEST_NAME);
        OperationResult result = task.getResult();
        
        // Pretend that the user was added a long time ago
        clock.override(LONG_LONG_TIME_AGO);
        addObject(USER_HERMAN_FILE);
        // Make sure that it is effectivelly disabled
        PrismObject<UserType> userHermanBefore = getUser(USER_HERMAN_OID);
        assertEffectiveActivation(userHermanBefore, ActivationStatusType.DISABLED);
        assertValidityStatus(userHermanBefore, TimeIntervalStatusType.BEFORE);
        clock.resetOverride();
        
        XMLGregorianCalendar startCal = clock.currentTimeXMLGregorianCalendar();
        
		/// WHEN
        displayWhen(TEST_NAME);
        importObjectFromFile(TASK_VALIDITY_SCANNER_FILENAME);
		
        waitForTaskStart(TASK_VALIDITY_SCANNER_OID, false);
        waitForTaskFinish(TASK_VALIDITY_SCANNER_OID, true);
        
        // THEN
        displayThen(TEST_NAME);
        XMLGregorianCalendar endCal = clock.currentTimeXMLGregorianCalendar();
        assertLastRecomputeTimestamp(TASK_VALIDITY_SCANNER_OID, startCal, endCal);
        
        PrismObject<UserType> userHermanAfter = getUser(USER_HERMAN_OID);
        assertEffectiveActivation(userHermanAfter, ActivationStatusType.ENABLED);
        assertValidityStatus(userHermanAfter, TimeIntervalStatusType.IN);
	}
	
	@Test
    public void test110HermanGoesInvalid() throws Exception {
		final String TEST_NAME = "test110HermanGoesInvalid";
        displayTestTile(this, TEST_NAME);

        // GIVEN
        Task task = createTask(AbstractSynchronizationStoryTest.class.getName() + "." + TEST_NAME);
        OperationResult result = task.getResult();
        
        XMLGregorianCalendar startCal = clock.currentTimeXMLGregorianCalendar();
        
        PrismObject<UserType> userHermanBefore = getUser(USER_HERMAN_OID);
        XMLGregorianCalendar validTo = userHermanBefore.asObjectable().getActivation().getValidTo();
        assertEffectiveActivation(userHermanBefore, ActivationStatusType.ENABLED);
        assertValidityStatus(userHermanBefore, TimeIntervalStatusType.IN);
        
        // Let's move the clock tiny bit after herman's validTo 
        validTo.add(XmlTypeConverter.createDuration(100));
        clock.override(validTo);
        
		/// WHEN
        displayWhen(TEST_NAME);
        waitForTaskNextRun(TASK_VALIDITY_SCANNER_OID, true);
		
        // THEN
        displayThen(TEST_NAME);
        
        // THEN
        XMLGregorianCalendar endCal = clock.currentTimeXMLGregorianCalendar();

        PrismObject<UserType> userHermanAfter = getUser(USER_HERMAN_OID);
        assertEffectiveActivation(userHermanAfter, ActivationStatusType.DISABLED);
        assertValidityStatus(userHermanAfter, TimeIntervalStatusType.AFTER);

        assertLastRecomputeTimestamp(TASK_VALIDITY_SCANNER_OID, startCal, endCal);
	}

	private void assertLastRecomputeTimestamp(String taskOid, XMLGregorianCalendar startCal, XMLGregorianCalendar endCal) throws ObjectNotFoundException, SchemaException, SecurityViolationException, CommunicationException, ConfigurationException {
		PrismObject<TaskType> task = getTask(taskOid);
		display("Task", task);
        PrismContainer<?> taskExtension = task.getExtension();
        assertNotNull("No task extension", taskExtension);
        PrismProperty<XMLGregorianCalendar> lastRecomputeTimestampProp = taskExtension.findProperty(SynchronizationConstants.LAST_RECOMPUTE_TIMESTAMP_PROPERTY_NAME);
        assertNotNull("no lastRecomputeTimestamp property", lastRecomputeTimestampProp);
        XMLGregorianCalendar lastRecomputeTimestamp = lastRecomputeTimestampProp.getRealValue();
        assertNotNull("null lastRecomputeTimestamp", lastRecomputeTimestamp);
        IntegrationTestTools.assertBetween("lastRecomputeTimestamp", startCal, endCal, lastRecomputeTimestamp);
	}
}