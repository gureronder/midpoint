/*
 * Copyright (c) 2011 Evolveum
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://www.opensource.org/licenses/cddl1 or
 * CDDLv1.0.txt file in the source code distribution.
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 *
 * Portions Copyrighted 2011 [name of copyright owner]
 */
package com.evolveum.midpoint.model.sync.action;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.JAXBElement;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.w3c.dom.Element;

import com.evolveum.midpoint.api.logging.Trace;
import com.evolveum.midpoint.common.jaxb.JAXBUtil;
import com.evolveum.midpoint.common.result.OperationResult;
import com.evolveum.midpoint.common.test.XmlAsserts;
import com.evolveum.midpoint.logging.TraceManager;
import com.evolveum.midpoint.model.sync.SynchronizationException;
import com.evolveum.midpoint.schema.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.DOMUtil;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ObjectChangeAdditionType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.PropertyReferenceListType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ResourceObjectShadowChangeDescriptionType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ResourceObjectShadowType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.SynchronizationSituationType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.UserTemplateType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.UserType;
import com.evolveum.midpoint.xml.schema.SchemaConstants;

/**
 * 
 * @author lazyman
 * 
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "classpath:application-context-model.xml",
		"classpath:application-context-model-unit-test.xml" })
public class AddUserActionTest extends BaseActionTest {

	private static final File TEST_FOLDER = new File("./src/test/resources/sync/action/user");
	private static final Trace LOGGER = TraceManager.getTrace(AddUserActionTest.class);

	@Before
	public void before() {
		Mockito.reset(provisioning, repository);
		before(new AddUserAction());
	}

	/**
	 * This method should test addUser action when user already exists.
	 * Therefore no changes must be made in repository on user, because he
	 * already exists.
	 * 
	 * @throws Exception
	 */
	@Test
	@SuppressWarnings("unchecked")
	public void testUserExists() throws Exception {
		ResourceObjectShadowChangeDescriptionType change = ((JAXBElement<ResourceObjectShadowChangeDescriptionType>) JAXBUtil
				.unmarshal(new File(TEST_FOLDER, "existing-user-change.xml"))).getValue();
		OperationResult result = new OperationResult("Add User Action Test");

		UserType user = ((JAXBElement<UserType>) JAXBUtil
				.unmarshal(new File(TEST_FOLDER, "existing-user.xml"))).getValue();
		final String userOid = user.getOid();
		when(
				repository.getObject(eq(userOid), any(PropertyReferenceListType.class),
						any(OperationResult.class))).thenReturn(user);
		try {
			ObjectChangeAdditionType addition = (ObjectChangeAdditionType) change.getObjectChange();
			action.executeChanges(userOid, change, SynchronizationSituationType.CONFIRMED,
					(ResourceObjectShadowType) addition.getObject(), result);
		} finally {
			LOGGER.debug(result.debugDump());
		}
		verify(repository, times(0)).addObject(any(UserType.class), any(OperationResult.class));
	}

	/**
	 * Test when user template is defined in action, but it's not found.
	 * ObjectNotFoundException from repository should be thrown - and it is
	 * wrapped in SynchronizationException
	 * 
	 * @throws Exception
	 */
	@Test(expected = SynchronizationException.class)
	@SuppressWarnings("unchecked")
	public void templateNotFound() throws Exception {
		setActionParameters();

		ResourceObjectShadowChangeDescriptionType change = ((JAXBElement<ResourceObjectShadowChangeDescriptionType>) JAXBUtil
				.unmarshal(new File(TEST_FOLDER, "existing-user-change.xml"))).getValue();
		OperationResult result = new OperationResult("Add User Action Test");

		String templateOid = "c0c010c0-d34d-b55f-f22d-777666111111";
		when(
				repository.getObject(eq(templateOid), any(PropertyReferenceListType.class),
						any(OperationResult.class))).thenThrow(
				new ObjectNotFoundException("user template not found"));

		try {
			ObjectChangeAdditionType addition = (ObjectChangeAdditionType) change.getObjectChange();
			action.executeChanges(null, change, SynchronizationSituationType.CONFIRMED,
					(ResourceObjectShadowType) addition.getObject(), result);
		} finally {
			LOGGER.debug(result.debugDump());
		}
	}

	private void setActionParameters() {
		List<Object> parameters = new ArrayList<Object>();
		Element element = DOMUtil.getDocument().createElementNS(SchemaConstants.NS_C, "userTemplateRef");
		element.setAttribute("oid", "c0c010c0-d34d-b55f-f22d-777666111111");
		parameters.add(element);
		action.setParameters(parameters);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void correctAddUser() throws Exception {
		setActionParameters();

		ResourceObjectShadowChangeDescriptionType change = ((JAXBElement<ResourceObjectShadowChangeDescriptionType>) JAXBUtil
				.unmarshal(new File(TEST_FOLDER, "existing-user-change.xml"))).getValue();
		OperationResult result = new OperationResult("Add User Action Test");

		UserTemplateType template = ((JAXBElement<UserTemplateType>) JAXBUtil.unmarshal(new File(TEST_FOLDER,
				"user-template.xml"))).getValue();
		when(
				repository.getObject(eq(template.getOid()), any(PropertyReferenceListType.class),
						any(OperationResult.class))).thenReturn(template);

		final String userOid = "2";
		when(repository.addObject(any(UserType.class), any(OperationResult.class))).thenAnswer(
				new Answer<String>() {
					@Override
					public String answer(InvocationOnMock invocation) throws Throwable {
						UserType user = (UserType) invocation.getArguments()[0];
						
						XmlAsserts.assertPatch(new File("new-user.xml"),
								JAXBUtil.marshalWrap(user, SchemaConstants.I_USER_TYPE));

						return userOid;
					}
				});

		try {
			ObjectChangeAdditionType addition = (ObjectChangeAdditionType) change.getObjectChange();
			action.executeChanges(null, change, SynchronizationSituationType.CONFIRMED,
					(ResourceObjectShadowType) addition.getObject(), result);
		} finally {
			LOGGER.debug(result.debugDump());
		}
		verify(repository, times(1)).addObject(any(UserType.class), any(OperationResult.class));
	}
}
