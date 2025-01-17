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
package com.evolveum.icf.dummy.connector;

import org.apache.commons.lang.StringUtils;
import org.identityconnectors.framework.spi.operations.*;
import org.identityconnectors.framework.common.exceptions.AlreadyExistsException;
import org.identityconnectors.framework.common.exceptions.ConnectionFailedException;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.ConnectorIOException;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.identityconnectors.framework.common.objects.*;

import static com.evolveum.icf.dummy.connector.Utils.*;

import java.io.FileNotFoundException;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.identityconnectors.common.logging.Log;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.common.security.GuardedString.Accessor;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.ObjectClassInfoBuilder;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.framework.common.objects.SchemaBuilder;
import org.identityconnectors.framework.common.objects.SyncDelta;
import org.identityconnectors.framework.common.objects.SyncDeltaBuilder;
import org.identityconnectors.framework.common.objects.SyncResultsHandler;
import org.identityconnectors.framework.common.objects.SyncToken;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.AndFilter;
import org.identityconnectors.framework.common.objects.filter.AttributeFilter;
import org.identityconnectors.framework.common.objects.filter.ContainsAllValuesFilter;
import org.identityconnectors.framework.common.objects.filter.ContainsFilter;
import org.identityconnectors.framework.common.objects.filter.EndsWithFilter;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.common.objects.filter.FilterTranslator;
import org.identityconnectors.framework.common.objects.filter.GreaterThanFilter;
import org.identityconnectors.framework.common.objects.filter.GreaterThanOrEqualFilter;
import org.identityconnectors.framework.common.objects.filter.LessThanFilter;
import org.identityconnectors.framework.common.objects.filter.LessThanOrEqualFilter;
import org.identityconnectors.framework.common.objects.filter.NotFilter;
import org.identityconnectors.framework.common.objects.filter.OrFilter;
import org.identityconnectors.framework.common.objects.filter.StartsWithFilter;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.Connector;
import org.identityconnectors.framework.spi.ConnectorClass;
import org.identityconnectors.framework.spi.PoolableConnector;
import org.identityconnectors.framework.spi.operations.AuthenticateOp;
import org.identityconnectors.framework.spi.operations.CreateOp;
import org.identityconnectors.framework.spi.operations.DeleteOp;
import org.identityconnectors.framework.spi.operations.ResolveUsernameOp;
import org.identityconnectors.framework.spi.operations.SchemaOp;
import org.identityconnectors.framework.spi.operations.SearchOp;
import org.identityconnectors.framework.spi.operations.SyncOp;
import org.identityconnectors.framework.spi.operations.TestOp;
import org.identityconnectors.framework.spi.operations.UpdateAttributeValuesOp;

import com.evolveum.icf.dummy.resource.DummyAccount;
import com.evolveum.icf.dummy.resource.DummyAttributeDefinition;
import com.evolveum.icf.dummy.resource.DummyDelta;
import com.evolveum.icf.dummy.resource.DummyDeltaType;
import com.evolveum.icf.dummy.resource.DummyGroup;
import com.evolveum.icf.dummy.resource.DummyObject;
import com.evolveum.icf.dummy.resource.DummyObjectClass;
import com.evolveum.icf.dummy.resource.DummyPrivilege;
import com.evolveum.icf.dummy.resource.DummyResource;
import com.evolveum.icf.dummy.resource.DummySyncStyle;
import com.evolveum.icf.dummy.resource.ObjectAlreadyExistsException;
import com.evolveum.icf.dummy.resource.ObjectDoesNotExistException;
import com.evolveum.icf.dummy.resource.SchemaViolationException;

/**
 * Connector for the Dummy Resource.
 * 
 * Dummy resource is a simple Java object that pretends to be a resource. It has accounts and
 * account schema. It has operations to manipulate accounts, execute scripts and so on
 * almost like a real resource. The purpose is to simulate a real resource with a very 
 * little overhead. This connector connects the Dummy resource to ICF.
 * 
 * @see DummyResource
 *
 * @author $author$
 * @version $Revision$ $Date$
 */
@ConnectorClass(displayNameKey = "UI_CONNECTOR_NAME",
configurationClass = DummyConfiguration.class)
public class DummyConnector implements PoolableConnector, AuthenticateOp, ResolveUsernameOp, CreateOp, DeleteOp, SchemaOp,
        ScriptOnConnectorOp, ScriptOnResourceOp, SearchOp<Filter>, SyncOp, TestOp, UpdateAttributeValuesOp {
	
	// We want to see if the ICF framework logging works properly
    private static final Log log = Log.getLog(DummyConnector.class);
    // We also want to see if the libraries that use JUL are logging properly
    private static final java.util.logging.Logger julLogger = java.util.logging.Logger.getLogger(DummyConnector.class.getName());
    
    // Marker used in logging tests
    public static final String LOG_MARKER = "_M_A_R_K_E_R_";
    
    private static final String OBJECTCLASS_ACCOUNT_NAME = "account";
	private static final String OBJECTCLASS_GROUP_NAME = "group";
	private static final String OBJECTCLASS_PRIVILEGE_NAME = "privilege";
	
    /**
     * Place holder for the {@link Configuration} passed into the init() method
     */
    private DummyConfiguration configuration;
    
	private DummyResource resource;

    /**
     * Gets the Configuration context for this connector.
     */
    @Override
    public Configuration getConfiguration() {
        return this.configuration;
    }

    /**
     * Callback method to receive the {@link Configuration}.
     *
     * @see Connector#init(org.identityconnectors.framework.spi.Configuration)
     */
    @Override
    public void init(Configuration configuration) {
        notNullArgument(configuration, "configuration");
        this.configuration = (DummyConfiguration) configuration;
        
        String instanceName = this.configuration.getInstanceId();
        if (instanceName == null || instanceName.isEmpty()) {
        	instanceName = null;
        }
        resource = DummyResource.getInstance(instanceName);
        
        resource.setCaseIgnoreId(this.configuration.getCaseIgnoreId());
        resource.setCaseIgnoreValues(this.configuration.getCaseIgnoreValues());
        resource.setEnforceUniqueName(this.configuration.isEnforceUniqueName());
        resource.setTolerateDuplicateValues(this.configuration.getTolerateDuplicateValues());
        resource.setGenerateDefaultValues(this.configuration.isGenerateDefaultValues());
		resource.setGenerateAccountDescriptionOnCreate(this.configuration.getGenerateAccountDescriptionOnCreate());
		resource.setGenerateAccountDescriptionOnUpdate(this.configuration.getGenerateAccountDescriptionOnUpdate());
		if (this.configuration.getForbiddenNames().length > 0) {
			resource.setForbiddenNames(Arrays.asList(((DummyConfiguration) configuration).getForbiddenNames()));
		} else {
			resource.setForbiddenNames(null);
		}

        resource.setUselessString(this.configuration.getUselessString());
        GuardedString uselessGuardedString = this.configuration.getUselessGuardedString();
        if (uselessGuardedString == null) {
        	resource.setUselessGuardedString(null);
        } else {
        	uselessGuardedString.access(new GuardedString.Accessor() {
    			@Override
    			public void access(char[] chars) {
    				resource.setUselessGuardedString(new String(chars));
    			}
        	});
        }
        
        resource.connect();
        
        log.info("Connected to dummy resource instance {0} ({1} connections open)", resource, resource.getConnectionCount());
    }

    /**
     * Disposes of the {@link CSVFileConnector}'s resources.
     *
     * @see Connector#dispose()
     */
    public void dispose() {
    	resource.disconnect();
    	log.info("Disconnected from dummy resource instance {0} ({1} connections still open)", resource, resource.getConnectionCount());
    }
    
    @Override
	public void checkAlive() {
		// notthig to do. always alive.
	}

    /******************
     * SPI Operations
     *
     * Implement the following operations using the contract and
     * description found in the Javadoc for these methods.
     ******************/
    
    /**
     * {@inheritDoc}
     */    

    /**
     * {@inheritDoc}
     */
    public Uid create(final ObjectClass objectClass, final Set<Attribute> createAttributes, final OperationOptions options) {
        log.info("create::begin attributes {0}", createAttributes);
        validate(objectClass);
        
        DummyObject newObject;
        try {
        	
	        if (ObjectClass.ACCOUNT.is(objectClass.getObjectClassValue())) {
	            // Convert attributes to account
	            DummyAccount newAccount = convertToAccount(createAttributes);
	    			
	            log.ok("Adding dummy account:\n{0}", newAccount.debugDump());
	            
    			resource.addAccount(newAccount);
    			newObject = newAccount;
	
	        } else if (ObjectClass.GROUP.is(objectClass.getObjectClassValue())) {
	            DummyGroup newGroup = convertToGroup(createAttributes);
	    		
	            log.ok("Adding dummy group:\n{0}", newGroup.debugDump());
	            
    			resource.addGroup(newGroup);
    			newObject = newGroup;
	            
	        } else if (objectClass.is(OBJECTCLASS_PRIVILEGE_NAME)) {
	            DummyPrivilege newPriv = convertToPriv(createAttributes);
	
	            log.ok("Adding dummy privilege:\n{0}", newPriv.debugDump());
	            
    			resource.addPrivilege(newPriv);
    			newObject = newPriv;

	        } else {
	        	throw new ConnectorException("Unknown object class "+objectClass);
	        }
	        
        } catch (ObjectAlreadyExistsException e) {
			// Note: let's do the bad thing and add exception loaded by this classloader as inner exception here
			// The framework should deal with it ... somehow
			throw new AlreadyExistsException(e.getMessage(),e);
		} catch (ConnectException e) {
			throw new ConnectionFailedException(e.getMessage(), e);
		} catch (FileNotFoundException e) {
			throw new ConnectorIOException(e.getMessage(), e);
		} catch (SchemaViolationException e) {
			throw new ConnectorException(e);
		}
        
        String id;
        if (configuration.getUidMode().equals(DummyConfiguration.UID_MODE_NAME)) {
        	id = newObject.getName();
        } else if (configuration.getUidMode().equals(DummyConfiguration.UID_MODE_UUID)) {
        	id = newObject.getId();
        } else {
        	throw new IllegalStateException("Unknown UID mode "+configuration.getUidMode());
        }
        Uid uid = new Uid(id);
        
        log.info("create::end");
        return uid;
    }

	/**
     * {@inheritDoc}
     */
    public Uid update(ObjectClass objectClass, Uid uid, Set<Attribute> replaceAttributes, OperationOptions options) {
        log.info("update::begin");
        validate(objectClass);
        
        try {
        	
	        if (ObjectClass.ACCOUNT.is(objectClass.getObjectClassValue())) {
	
		        final DummyAccount account;
		        if (configuration.getUidMode().equals(DummyConfiguration.UID_MODE_NAME)) {
		        	account = resource.getAccountByUsername(uid.getUidValue());
		        } else if (configuration.getUidMode().equals(DummyConfiguration.UID_MODE_UUID)) {
		        	account = resource.getAccountById(uid.getUidValue());
		        } else {
		        	throw new IllegalStateException("Unknown UID mode "+configuration.getUidMode());
		        }
		        if (account == null) {
		        	throw new UnknownUidException("Account with UID "+uid+" does not exist on resource");
		        }

				// we do this before setting attribute values, in case when description itself would be changed
				resource.changeDescriptionIfNeeded(account);
		        
		        for (Attribute attr : replaceAttributes) {
		        	if (attr.is(Name.NAME)) {
		        		String newName = (String)attr.getValue().get(0);
		        		try {
							resource.renameAccount(account.getId(), account.getName(), newName);
						} catch (ObjectDoesNotExistException e) {
							throw new org.identityconnectors.framework.common.exceptions.UnknownUidException(e.getMessage(), e);
						} catch (ObjectAlreadyExistsException e) {
							throw new org.identityconnectors.framework.common.exceptions.AlreadyExistsException(e.getMessage(), e);
						} catch (SchemaViolationException e) {
							throw new org.identityconnectors.framework.common.exceptions.ConnectorException("Schema exception: " + e.getMessage(), e);
						}
						// We need to change the returned uid here (only if the mode is not set to UUID)
						if (!(configuration.getUidMode().equals(DummyConfiguration.UID_MODE_UUID))){
							uid = new Uid(newName);
						}
		        	} else if (attr.is(OperationalAttributes.PASSWORD_NAME)) {
		        		changePassword(account,attr);

		        	} else if (attr.is(OperationalAttributes.ENABLE_NAME)) {
		        		account.setEnabled(getBoolean(attr));

		        	} else if (attr.is(OperationalAttributes.ENABLE_DATE_NAME)) {
		        		account.setValidFrom(getDate(attr));

		        	} else if (attr.is(OperationalAttributes.DISABLE_DATE_NAME)) {
		        		account.setValidTo(getDate(attr));

		        	} else if (attr.is(OperationalAttributes.LOCK_OUT_NAME)) {
		        		account.setLockout(getBoolean(attr));

		        	} else {
			        	String name = attr.getName();
			        	try {
							account.replaceAttributeValues(name, attr.getValue());
						} catch (SchemaViolationException e) {
							// we cannot throw checked exceptions. But this one looks suitable.
							// Note: let's do the bad thing and add exception loaded by this classloader as inner exception here
							// The framework should deal with it ... somehow
							throw new IllegalArgumentException(e.getMessage(),e);
						}
		        	}
		        }
		        
	        } else if (ObjectClass.GROUP.is(objectClass.getObjectClassValue())) {
	        	
	        	final DummyGroup group;
	        	if (configuration.getUidMode().equals(DummyConfiguration.UID_MODE_NAME)) {
	        		group = resource.getGroupByName(uid.getUidValue());
		        } else if (configuration.getUidMode().equals(DummyConfiguration.UID_MODE_UUID)) {
		        	group = resource.getGroupById(uid.getUidValue());
		        } else {
		        	throw new IllegalStateException("Unknown UID mode "+configuration.getUidMode());
		        }
		        if (group == null) {
		        	throw new UnknownUidException("Group with UID "+uid+" does not exist on resource");
		        }
		        
		        for (Attribute attr : replaceAttributes) {
		        	if (attr.is(Name.NAME)) {
		        		String newName = (String)attr.getValue().get(0);
		        		try {
							resource.renameGroup(group.getId(), group.getName(), newName);
						} catch (ObjectDoesNotExistException e) {
							throw new org.identityconnectors.framework.common.exceptions.UnknownUidException(e.getMessage(), e);
						} catch (ObjectAlreadyExistsException e) {
							throw new org.identityconnectors.framework.common.exceptions.AlreadyExistsException(e.getMessage(), e);
						}
		        		// We need to change the returned uid here
		        		uid = new Uid(newName);
		        	} else if (attr.is(OperationalAttributes.PASSWORD_NAME)) {
		        		throw new IllegalArgumentException("Attempt to change password on group");
		        	
		        	} else if (attr.is(OperationalAttributes.ENABLE_NAME)) {
		        		group.setEnabled(getBoolean(attr));
		        		
		        	} else {
			        	String name = attr.getName();
			        	List<Object> values = attr.getValue();
			        	if (attr.is(DummyGroup.ATTR_MEMBERS_NAME) && values != null && configuration.getUpCaseName()) {
			        		List<Object> newValues = new ArrayList<Object>(values.size());
			        		for (Object val: values) {
			        			newValues.add(StringUtils.upperCase((String)val));
			        		}
			        		values = newValues;
			        	}
			        	try {
							group.replaceAttributeValues(name, values);
						} catch (SchemaViolationException e) {
							throw new IllegalArgumentException(e.getMessage(),e);
						}
		        	}
		        }
		        
	        } else if (objectClass.is(OBJECTCLASS_PRIVILEGE_NAME)) {
	        	
	        	final DummyPrivilege priv;
	        	if (configuration.getUidMode().equals(DummyConfiguration.UID_MODE_NAME)) {
	        		priv = resource.getPrivilegeByName(uid.getUidValue());
		        } else if (configuration.getUidMode().equals(DummyConfiguration.UID_MODE_UUID)) {
		        	priv = resource.getPrivilegeById(uid.getUidValue());
		        } else {
		        	throw new IllegalStateException("Unknown UID mode "+configuration.getUidMode());
		        }
		        if (priv == null) {
		        	throw new UnknownUidException("Privilege with UID "+uid+" does not exist on resource");
		        }
		        
		        for (Attribute attr : replaceAttributes) {
		        	if (attr.is(Name.NAME)) {
		        		String newName = (String)attr.getValue().get(0);
		        		try {
							resource.renamePrivilege(priv.getId(), priv.getName(), newName);
						} catch (ObjectDoesNotExistException e) {
							throw new org.identityconnectors.framework.common.exceptions.UnknownUidException(e.getMessage(), e);
						} catch (ObjectAlreadyExistsException e) {
							throw new org.identityconnectors.framework.common.exceptions.AlreadyExistsException(e.getMessage(), e);
						}
		        		// We need to change the returned uid here
		        		uid = new Uid(newName);
		        	} else if (attr.is(OperationalAttributes.PASSWORD_NAME)) {
		        		throw new IllegalArgumentException("Attempt to change password on privilege");
		        	
		        	} else if (attr.is(OperationalAttributes.ENABLE_NAME)) {
		        		throw new IllegalArgumentException("Attempt to change enable on privilege");
		        		
		        	} else {
			        	String name = attr.getName();
			        	try {
							priv.replaceAttributeValues(name, attr.getValue());
						} catch (SchemaViolationException e) {
							throw new IllegalArgumentException(e.getMessage(),e);
						}
		        	}
		        }
		        
	        } else {
	        	throw new ConnectorException("Unknown object class "+objectClass);
	        }
	        
		} catch (ConnectException e) {
	        log.info("update::exception "+e);
			throw new ConnectionFailedException(e.getMessage(), e);
		} catch (FileNotFoundException e) {
			log.info("update::exception "+e);
			throw new ConnectorIOException(e.getMessage(), e);
		}
        
        log.info("update::end");
        return uid;
    }

	/**
     * {@inheritDoc}
     */
    public Uid addAttributeValues(ObjectClass objectClass, Uid uid, Set<Attribute> valuesToAdd, OperationOptions options) {
        log.info("addAttributeValues::begin");
        validate(objectClass);

        try {
        
	        if (ObjectClass.ACCOUNT.is(objectClass.getObjectClassValue())) {
	        
	        	DummyAccount account;
		        if (configuration.getUidMode().equals(DummyConfiguration.UID_MODE_NAME)) {
		        	account = resource.getAccountByUsername(uid.getUidValue());
		        } else if (configuration.getUidMode().equals(DummyConfiguration.UID_MODE_UUID)) {
		        	account = resource.getAccountById(uid.getUidValue());
		        } else {
		        	throw new IllegalStateException("Unknown UID mode "+configuration.getUidMode());
		        }
		        if (account == null) {
		        	throw new UnknownUidException("Account with UID "+uid+" does not exist on resource");
		        }

				// we could change the description here, but don't do that not to collide with ADD operation
				// TODO add the functionality if needed
		        
		        for (Attribute attr : valuesToAdd) {
		        	
		        	if (attr.is(OperationalAttributeInfos.PASSWORD.getName())) {
		        		if (account.getPassword() != null) {
		        			throw new IllegalArgumentException("Attempt to add value for password while password is already set");
		        		}
		        		changePassword(account,attr);
		        		
		        	} else if (attr.is(OperationalAttributes.ENABLE_NAME)) {
		        		throw new IllegalArgumentException("Attempt to add value for enable attribute");
		        		
		        	} else {
			        	String name = attr.getName();
			        	try {
							account.addAttributeValues(name, attr.getValue());
						} catch (SchemaViolationException e) {
							// we cannot throw checked exceptions. But this one looks suitable.
							// Note: let's do the bad thing and add exception loaded by this classloader as inner exception here
							// The framework should deal with it ... somehow
							throw new IllegalArgumentException(e.getMessage(),e);
						}
		        	}
		        }
		        
	        } else if (ObjectClass.GROUP.is(objectClass.getObjectClassValue())) {
	        	
	        	DummyGroup group;
	        	if (configuration.getUidMode().equals(DummyConfiguration.UID_MODE_NAME)) {
	        		group = resource.getGroupByName(uid.getUidValue());
		        } else if (configuration.getUidMode().equals(DummyConfiguration.UID_MODE_UUID)) {
		        	group = resource.getGroupById(uid.getUidValue());
		        } else {
		        	throw new IllegalStateException("Unknown UID mode "+configuration.getUidMode());
		        }
		        if (group == null) {
		        	throw new UnknownUidException("Group with UID "+uid+" does not exist on resource");
		        }
		        
		        for (Attribute attr : valuesToAdd) {
		        	
		        	if (attr.is(OperationalAttributeInfos.PASSWORD.getName())) {
		        		throw new IllegalArgumentException("Attempt to change password on group");
		        		
		        	} else if (attr.is(OperationalAttributes.ENABLE_NAME)) {
		        		throw new IllegalArgumentException("Attempt to add value for enable attribute");
		        		
		        	} else {
			        	String name = attr.getName();
			        	List<Object> values = attr.getValue();
			        	if (attr.is(DummyGroup.ATTR_MEMBERS_NAME) && values != null && configuration.getUpCaseName()) {
			        		List<Object> newValues = new ArrayList<Object>(values.size());
			        		for (Object val: values) {
			        			newValues.add(StringUtils.upperCase((String)val));
			        		}
			        		values = newValues;
			        	}
			        	try {
							group.addAttributeValues(name, values);
						} catch (SchemaViolationException e) {
							// we cannot throw checked exceptions. But this one looks suitable.
							// Note: let's do the bad thing and add exception loaded by this classloader as inner exception here
							// The framework should deal with it ... somehow
							throw new IllegalArgumentException(e.getMessage(),e);
						}
		        	}
		        }
		        
	        } else if (objectClass.is(OBJECTCLASS_PRIVILEGE_NAME)) {
	        	
	        	DummyPrivilege priv;
	        	if (configuration.getUidMode().equals(DummyConfiguration.UID_MODE_NAME)) {
	        		priv = resource.getPrivilegeByName(uid.getUidValue());
		        } else if (configuration.getUidMode().equals(DummyConfiguration.UID_MODE_UUID)) {
		        	priv = resource.getPrivilegeById(uid.getUidValue());
		        } else {
		        	throw new IllegalStateException("Unknown UID mode "+configuration.getUidMode());
		        }
		        if (priv == null) {
		        	throw new UnknownUidException("Privilege with UID "+uid+" does not exist on resource");
		        }
		        
		        for (Attribute attr : valuesToAdd) {
		        	
		        	if (attr.is(OperationalAttributeInfos.PASSWORD.getName())) {
		        		throw new IllegalArgumentException("Attempt to change password on privilege");
		        		
		        	} else if (attr.is(OperationalAttributes.ENABLE_NAME)) {
		        		throw new IllegalArgumentException("Attempt to add value for enable attribute");
		        		
		        	} else {
			        	String name = attr.getName();
			        	try {
							priv.addAttributeValues(name, attr.getValue());
						} catch (SchemaViolationException e) {
							// we cannot throw checked exceptions. But this one looks suitable.
							// Note: let's do the bad thing and add exception loaded by this classloader as inner exception here
							// The framework should deal with it ... somehow
							throw new IllegalArgumentException(e.getMessage(),e);
						}
		        	}
		        }
	        	
	        } else {
	        	throw new ConnectorException("Unknown object class "+objectClass);
	        }
	        
		} catch (ConnectException e) {
	        log.info("addAttributeValues::exception "+e);
			throw new ConnectionFailedException(e.getMessage(), e);
		} catch (FileNotFoundException e) {
			log.info("addAttributeValues::exception "+e);
			throw new ConnectorIOException(e.getMessage(), e);
		}
        
        log.info("addAttributeValues::end");
        return uid;
    }

    /**
     * {@inheritDoc}
     */
    public Uid removeAttributeValues(ObjectClass objectClass, Uid uid, Set<Attribute> valuesToRemove, OperationOptions options) {
        log.info("removeAttributeValues::begin");
        validate(objectClass);

        try {
        
	        if (ObjectClass.ACCOUNT.is(objectClass.getObjectClassValue())) {
	        	
	        	DummyAccount account;
		        if (configuration.getUidMode().equals(DummyConfiguration.UID_MODE_NAME)) {
		        	account = resource.getAccountByUsername(uid.getUidValue());
		        } else if (configuration.getUidMode().equals(DummyConfiguration.UID_MODE_UUID)) {
		        	account = resource.getAccountById(uid.getUidValue());
		        } else {
		        	throw new IllegalStateException("Unknown UID mode "+configuration.getUidMode());
		        }
		        if (account == null) {
		        	throw new UnknownUidException("Account with UID "+uid+" does not exist on resource");
		        }

				// we could change the description here, but don't do that not to collide with REMOVE operation
				// TODO add the functionality if needed

				for (Attribute attr : valuesToRemove) {
		        	if (attr.is(OperationalAttributeInfos.PASSWORD.getName())) {
		        		throw new UnsupportedOperationException("Removing password value is not supported");
		        	} else if (attr.is(OperationalAttributes.ENABLE_NAME)) {
		        		throw new IllegalArgumentException("Attempt to remove value from enable attribute");
		        	} else {
			        	String name = attr.getName();
			        	try {
							account.removeAttributeValues(name, attr.getValue());
						} catch (SchemaViolationException e) {
							// we cannot throw checked exceptions. But this one looks suitable.
							// Note: let's do the bad thing and add exception loaded by this classloader as inner exception here
							// The framework should deal with it ... somehow
							throw new IllegalArgumentException(e.getMessage(),e);
						}
		        	}
		        }
	        
	        } else if (ObjectClass.GROUP.is(objectClass.getObjectClassValue())) {
	        	
	        	DummyGroup group;
	        	if (configuration.getUidMode().equals(DummyConfiguration.UID_MODE_NAME)) {
	        		group = resource.getGroupByName(uid.getUidValue());
		        } else if (configuration.getUidMode().equals(DummyConfiguration.UID_MODE_UUID)) {
		        	group = resource.getGroupById(uid.getUidValue());
		        } else {
		        	throw new IllegalStateException("Unknown UID mode "+configuration.getUidMode());
		        }
		        if (group == null) {
		        	throw new UnknownUidException("Group with UID "+uid+" does not exist on resource");
		        }
		        
		        for (Attribute attr : valuesToRemove) {
		        	if (attr.is(OperationalAttributeInfos.PASSWORD.getName())) {
		        		throw new IllegalArgumentException("Attempt to change password on group");
		        	} else if (attr.is(OperationalAttributes.ENABLE_NAME)) {
		        		throw new IllegalArgumentException("Attempt to remove value from enable attribute");
		        	} else {
			        	String name = attr.getName();
			        	List<Object> values = attr.getValue();
			        	if (attr.is(DummyGroup.ATTR_MEMBERS_NAME) && values != null && configuration.getUpCaseName()) {
			        		List<Object> newValues = new ArrayList<Object>(values.size());
			        		for (Object val: values) {
			        			newValues.add(StringUtils.upperCase((String)val));
			        		}
			        		values = newValues;
			        	}
			        	try {
							group.removeAttributeValues(name, values);
						} catch (SchemaViolationException e) {
							// we cannot throw checked exceptions. But this one looks suitable.
							// Note: let's do the bad thing and add exception loaded by this classloader as inner exception here
							// The framework should deal with it ... somehow
							throw new IllegalArgumentException(e.getMessage(),e);
						}
		        	}
		        }
		        
	        } else if (objectClass.is(OBJECTCLASS_PRIVILEGE_NAME)) {
	        	
	        	DummyPrivilege priv;
	        	if (configuration.getUidMode().equals(DummyConfiguration.UID_MODE_NAME)) {
	        		priv = resource.getPrivilegeByName(uid.getUidValue());
		        } else if (configuration.getUidMode().equals(DummyConfiguration.UID_MODE_UUID)) {
		        	priv = resource.getPrivilegeById(uid.getUidValue());
		        } else {
		        	throw new IllegalStateException("Unknown UID mode "+configuration.getUidMode());
		        }
		        if (priv == null) {
		        	throw new UnknownUidException("Privilege with UID "+uid+" does not exist on resource");
		        }
		        
		        for (Attribute attr : valuesToRemove) {
		        	if (attr.is(OperationalAttributeInfos.PASSWORD.getName())) {
		        		throw new IllegalArgumentException("Attempt to change password on privilege");
		        	} else if (attr.is(OperationalAttributes.ENABLE_NAME)) {
		        		throw new IllegalArgumentException("Attempt to remove value from enable attribute");
		        	} else {
			        	String name = attr.getName();
			        	try {
							priv.removeAttributeValues(name, attr.getValue());
						} catch (SchemaViolationException e) {
							// we cannot throw checked exceptions. But this one looks suitable.
							// Note: let's do the bad thing and add exception loaded by this classloader as inner exception here
							// The framework should deal with it ... somehow
							throw new IllegalArgumentException(e.getMessage(),e);
						}
		        	}
		        }
	        	
	        } else {
	        	throw new ConnectorException("Unknown object class "+objectClass);
	        }
	        
		} catch (ConnectException e) {
	        log.info("removeAttributeValues::exception "+e);
			throw new ConnectionFailedException(e.getMessage(), e);
		} catch (FileNotFoundException e) {
			log.info("removeAttributeValues::exception "+e);
			throw new ConnectorIOException(e.getMessage(), e);
		}

        log.info("removeAttributeValues::end");
        return uid;
    }
    
	/**
     * {@inheritDoc}
     */
    public void delete(final ObjectClass objectClass, final Uid uid, final OperationOptions options) {
        log.info("delete::begin");
        validate(objectClass);
        
        String id = uid.getUidValue();
        
        try {
        	if (ObjectClass.ACCOUNT.is(objectClass.getObjectClassValue())) {
        		if (configuration.getUidMode().equals(DummyConfiguration.UID_MODE_NAME)) {
        			resource.deleteAccountByName(id);
		        } else if (configuration.getUidMode().equals(DummyConfiguration.UID_MODE_UUID)) {
		        	resource.deleteAccountById(id);
		        } else {
		        	throw new IllegalStateException("Unknown UID mode "+configuration.getUidMode());
		        }
        	} else if (ObjectClass.GROUP.is(objectClass.getObjectClassValue())) {
        		if (configuration.getUidMode().equals(DummyConfiguration.UID_MODE_NAME)) {
        			resource.deleteGroupByName(id);
		        } else if (configuration.getUidMode().equals(DummyConfiguration.UID_MODE_UUID)) {
		        	resource.deleteGroupById(id);
		        } else {
		        	throw new IllegalStateException("Unknown UID mode "+configuration.getUidMode());
		        }
        	} else if (objectClass.is(OBJECTCLASS_PRIVILEGE_NAME)) {
        		if (configuration.getUidMode().equals(DummyConfiguration.UID_MODE_NAME)) {
        			resource.deletePrivilegeByName(id);
		        } else if (configuration.getUidMode().equals(DummyConfiguration.UID_MODE_UUID)) {
		        	resource.deletePrivilegeById(id);
		        } else {
		        	throw new IllegalStateException("Unknown UID mode "+configuration.getUidMode());
		        }

        	} else {
        		throw new ConnectorException("Unknown object class "+objectClass);
        	}
			
		} catch (ObjectDoesNotExistException e) {
			// we cannot throw checked exceptions. But this one looks suitable.
			// Note: let's do the bad thing and add exception loaded by this classloader as inner exception here
			// The framework should deal with it ... somehow
			throw new UnknownUidException(e.getMessage(),e);
		} catch (ConnectException e) {
	        log.info("delete::exception "+e);
			throw new ConnectionFailedException(e.getMessage(), e);
		} catch (FileNotFoundException e) {
			log.info("delete::exception "+e);
			throw new ConnectorIOException(e.getMessage(), e);
		}
        
        log.info("delete::end");
    }

    /**
     * {@inheritDoc}
     */
    public Schema schema() {
        log.info("schema::begin");
        
        if (!configuration.getSupportSchema()) {
        	log.info("schema::unsupported operation");
        	throw new UnsupportedOperationException();
        }

        SchemaBuilder builder = new SchemaBuilder(DummyConnector.class);
        
    	builder.defineObjectClass(createAccountObjectClass(configuration.getSupportActivation()));
        builder.defineObjectClass(createGroupObjectClass(configuration.getSupportActivation()));
        builder.defineObjectClass(createPrivilegeObjectClass());

        log.info("schema::end");
        return builder.build();
    }

	private String getAccountObjectClassName() {
		if (configuration.getUseLegacySchema()) {
			return ObjectClass.ACCOUNT_NAME;
		} else {
			return OBJECTCLASS_ACCOUNT_NAME;
		}
	}

	private String getGroupObjectClassName() {
		if (configuration.getUseLegacySchema()) {
			return ObjectClass.GROUP_NAME;
		} else {
			return OBJECTCLASS_GROUP_NAME;
		}
	}
    
    private ObjectClassInfoBuilder createCommonObjectClassBuilder(String typeName, 
    		DummyObjectClass dummyAccountObjectClass, boolean supportsActivation) {
    	ObjectClassInfoBuilder objClassBuilder = new ObjectClassInfoBuilder();
    	if (typeName != null) {
    		objClassBuilder.setType(typeName);
    	}
    	
    	buildAttributes(objClassBuilder, dummyAccountObjectClass);
    	
    	if (supportsActivation) {
    		// __ENABLE__ attribute
    		objClassBuilder.addAttributeInfo(OperationalAttributeInfos.ENABLE);
    		
    		if (configuration.getSupportValidity()) {
            	objClassBuilder.addAttributeInfo(OperationalAttributeInfos.ENABLE_DATE);
            	objClassBuilder.addAttributeInfo(OperationalAttributeInfos.DISABLE_DATE);
            }
    		
    		objClassBuilder.addAttributeInfo(OperationalAttributeInfos.LOCK_OUT);
    	}
        
    	// __NAME__ will be added by default
        return objClassBuilder;
    }
    
	private ObjectClassInfo createAccountObjectClass(boolean supportsActivation) {
		// __ACCOUNT__ objectclass
        
        DummyObjectClass dummyAccountObjectClass;
		try {
			dummyAccountObjectClass = resource.getAccountObjectClass();
		} catch (ConnectException e) {
			throw new ConnectionFailedException(e.getMessage(), e);
		} catch (FileNotFoundException e) {
			throw new ConnectorIOException(e.getMessage(), e);
		} catch (IllegalArgumentException e) {
			throw new ConnectorException(e.getMessage(), e);
		} // DO NOT catch IllegalStateException, let it pass
		
		ObjectClassInfoBuilder objClassBuilder = createCommonObjectClassBuilder(getAccountObjectClassName(), dummyAccountObjectClass, supportsActivation);
        
        // __PASSWORD__ attribute
        objClassBuilder.addAttributeInfo(OperationalAttributeInfos.PASSWORD);
        
        return objClassBuilder.build();
	}
	
	private ObjectClassInfo createGroupObjectClass(boolean supportsActivation) {
		// __GROUP__ objectclass
        ObjectClassInfoBuilder objClassBuilder = createCommonObjectClassBuilder(getGroupObjectClassName(), 
        		resource.getGroupObjectClass(), supportsActivation);
                
        return objClassBuilder.build();
	}
	
	private ObjectClassInfo createPrivilegeObjectClass() {
        ObjectClassInfoBuilder objClassBuilder = createCommonObjectClassBuilder(OBJECTCLASS_PRIVILEGE_NAME,
        		resource.getPrivilegeObjectClass(), false);
        return objClassBuilder.build();
	}

	private void buildAttributes(ObjectClassInfoBuilder icfObjClassBuilder, DummyObjectClass dummyObjectClass) {
		for (DummyAttributeDefinition dummyAttrDef : dummyObjectClass.getAttributeDefinitions()) {
        	AttributeInfoBuilder attrBuilder = new AttributeInfoBuilder(dummyAttrDef.getAttributeName(), dummyAttrDef.getAttributeType());
        	attrBuilder.setMultiValued(dummyAttrDef.isMulti());
        	attrBuilder.setRequired(dummyAttrDef.isRequired());
        	attrBuilder.setReturnedByDefault(dummyAttrDef.isReturnedByDefault());
        	icfObjClassBuilder.addAttributeInfo(attrBuilder.build());
        }
	}

	/**
     * {@inheritDoc}
     */
    public Uid authenticate(final ObjectClass objectClass, final String userName, final GuardedString password, final OperationOptions options) {
        log.info("authenticate::begin");
        Uid uid = null; 
        log.info("authenticate::end");
        return uid;
    }

    /**
     * {@inheritDoc}
     */
    public Uid resolveUsername(final ObjectClass objectClass, final String userName, final OperationOptions options) {
        log.info("resolveUsername::begin");
        Uid uid = null;
        log.info("resolveUsername::end");
        return uid;
    }
    
    /**
     * {@inheritDoc}
     */
    public Object runScriptOnConnector(ScriptContext request, OperationOptions options) {
    	
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    public Object runScriptOnResource(ScriptContext request, OperationOptions options) {
        
        resource.runScript(request.getScriptLanguage(), request.getScriptText(), request.getScriptArguments());
        
        return null;
    }

    /**
     * {@inheritDoc}
     */
    public FilterTranslator<Filter> createFilterTranslator(ObjectClass objectClass, OperationOptions options) {
        log.info("createFilterTranslator::begin");
        validate(objectClass);

        log.info("createFilterTranslator::end");
        return new DummyFilterTranslator() {
        };
    }

    /**
     * {@inheritDoc}
     */
    public void executeQuery(ObjectClass objectClass, Filter query, ResultsHandler handler, OperationOptions options) {
        log.info("executeQuery({0},{1},{2},{3})", objectClass, query, handler, options);
        validate(objectClass);
        notNull(handler, "Results handled object can't be null.");
        
        Collection<String> attributesToGet = getAttrsToGet(options);
        log.ok("attributesToGet={0}", attributesToGet);
        
        try {
	        if (ObjectClass.ACCOUNT.is(objectClass.getObjectClassValue())) {
	        	
		        Collection<DummyAccount> accounts = resource.listAccounts();
		        for (DummyAccount account : accounts) {
		        	ConnectorObject co = convertToConnectorObject(account, attributesToGet);
		        	if (matches(query, co)) {
		        		co = filterOutAttributesToGet(co, attributesToGet);
		        		handler.handle(co);
		        	}
		        }
		        
	        } else if (ObjectClass.GROUP.is(objectClass.getObjectClassValue())) {
	        	
	        	Collection<DummyGroup> groups = resource.listGroups();
		        for (DummyGroup group : groups) {
		        	ConnectorObject co = convertToConnectorObject(group, attributesToGet);
		        	if (matches(query, co)) {
		        		if (attributesToGetHasAttribute(attributesToGet, DummyGroup.ATTR_MEMBERS_NAME)) {
		        			resource.recordGroupMembersReadCount();
		        		}
		        		co = filterOutAttributesToGet(co, attributesToGet);
		        		handler.handle(co);
		        	}
		        }
		        
	        } else if (objectClass.is(OBJECTCLASS_PRIVILEGE_NAME)) {
	        	
	        	Collection<DummyPrivilege> privs = resource.listPrivileges();
		        for (DummyPrivilege priv : privs) {
		        	ConnectorObject co = convertToConnectorObject(priv, attributesToGet);
		        	if (matches(query, co)) {
		        		co = filterOutAttributesToGet(co, attributesToGet);
		        		handler.handle(co);
		        	}
		        }
	        	
	        } else {
	        	throw new ConnectorException("Unknown object class "+objectClass);
	        }
	        
		} catch (ConnectException e) {
	        log.info("executeQuery::exception "+e);
			throw new ConnectionFailedException(e.getMessage(), e);
		} catch (FileNotFoundException e) {
			log.info("executeQuery::exception "+e);
			throw new ConnectorIOException(e.getMessage(), e);
		}
        
        log.info("executeQuery::end");
    }

	private boolean matches(Filter query, ConnectorObject co) {
		if (query == null) {
			return true;
		}
		if (configuration.getCaseIgnoreValues() || configuration.getCaseIgnoreId()) {
			return normalize(query).accept(normalize(co));
		}
		return query.accept(co);
	}

	private ConnectorObject normalize(ConnectorObject co) {
		ConnectorObjectBuilder cob = new ConnectorObjectBuilder();
		if (configuration.getCaseIgnoreId()) {
			cob.setUid(co.getUid().getUidValue().toLowerCase());
			cob.setName(co.getName().getName().toLowerCase());
		} else {
			cob.setUid(co.getUid());
			cob.setName(co.getName());
		}
        cob.setObjectClass(co.getObjectClass());
        for (Attribute attr : co.getAttributes()) {
        	cob.addAttribute(normalize(attr));
        }
        return cob.build();
	}
	
    private Filter normalize(Filter filter) {
        if (filter instanceof ContainsFilter) {
            AttributeFilter afilter = (AttributeFilter) filter;
            return new ContainsFilter(normalize(afilter.getAttribute()));
        } else if (filter instanceof EndsWithFilter) {
            AttributeFilter afilter = (AttributeFilter) filter;
            return new EndsWithFilter(normalize(afilter.getAttribute()));
        } else if (filter instanceof EqualsFilter) {
            AttributeFilter afilter = (AttributeFilter) filter;
            return new EqualsFilter(normalize(afilter.getAttribute()));
        } else if (filter instanceof GreaterThanFilter) {
            AttributeFilter afilter = (AttributeFilter) filter;
            return new GreaterThanFilter(normalize(afilter.getAttribute()));
        } else if (filter instanceof GreaterThanOrEqualFilter) {
            AttributeFilter afilter = (AttributeFilter) filter;
            return new GreaterThanOrEqualFilter(normalize(afilter.getAttribute()));
        } else if (filter instanceof LessThanFilter) {
            AttributeFilter afilter = (AttributeFilter) filter;
            return new LessThanFilter(normalize(afilter.getAttribute()));
        } else if (filter instanceof LessThanOrEqualFilter) {
            AttributeFilter afilter = (AttributeFilter) filter;
            return new LessThanOrEqualFilter(normalize(afilter.getAttribute()));
        } else if (filter instanceof StartsWithFilter) {
            AttributeFilter afilter = (AttributeFilter) filter;
            return new StartsWithFilter(normalize(afilter.getAttribute()));
        } else if (filter instanceof ContainsAllValuesFilter) {
            AttributeFilter afilter = (AttributeFilter) filter;
            return new ContainsAllValuesFilter(normalize(afilter.getAttribute()));
        } else if (filter instanceof NotFilter) {
            NotFilter notFilter = (NotFilter) filter;
            return new NotFilter(normalize(notFilter.getFilter()));
        } else if (filter instanceof AndFilter) {
            AndFilter andFilter = (AndFilter) filter;
            return new AndFilter(normalize(andFilter.getLeft()), normalize(andFilter.getRight()));
        } else if (filter instanceof OrFilter) {
            OrFilter orFilter = (OrFilter) filter;
            return new OrFilter(normalize(orFilter.getLeft()), normalize(orFilter.getRight()));
        } else {
            return filter;
        }
    }
    
    private Attribute normalize(Attribute attr) {
    	if (configuration.getCaseIgnoreValues()) {
        	AttributeBuilder ab = new AttributeBuilder();
        	ab.setName(attr.getName());
        	for (Object value: attr.getValue()) {
        		if (value instanceof String) {
        			ab.addValue(((String)value).toLowerCase());
        		} else {
        			ab.addValue(value);
        		}
        	}
        	return ab.build();
        } else {
        	return attr;
        }
    }

	private ConnectorObject filterOutAttributesToGet(ConnectorObject co, Collection<String> attributesToGet) {
		if (attributesToGet == null) {
			return co;
		}
		ConnectorObjectBuilder cob = new ConnectorObjectBuilder();
        cob.setUid(co.getUid());
        cob.setName(co.getName());
        cob.setObjectClass(co.getObjectClass());
        Set<Attribute> attrs = new HashSet<Attribute>(co.getAttributes().size());
        for (Attribute attr : co.getAttributes()) {
            if (containsAttribute(attributesToGet,attr.getName())) {
            	cob.addAttribute(attr);
            }
        }
        cob.addAttributes(attrs);
        return cob.build();
	}

	private boolean containsAttribute(Collection<String> attrs, String attrName) {
		for (String attr: attrs) {
			if (StringUtils.equalsIgnoreCase(attr, attrName)) {
				return true;
			}
		}
		return false;
	}

	/**
     * {@inheritDoc}
     */
    public void sync(ObjectClass objectClass, SyncToken token, SyncResultsHandler handler, final OperationOptions options) {
        log.info("sync::begin");
        validate(objectClass);
        
        Collection<String> attributesToGet = getAttrsToGet(options);

        try {
	        int syncToken = (Integer)token.getValue();
	        List<DummyDelta> deltas = resource.getDeltasSince(syncToken);
	        for (DummyDelta delta: deltas) {
	        	
	        	Class<? extends DummyObject> deltaObjectClass = delta.getObjectClass();
	        	if (objectClass.is(ObjectClass.ALL_NAME)) {
	        		// take all changes
	        	} else if (objectClass.is(ObjectClass.ACCOUNT_NAME)) {
	        		if (deltaObjectClass != DummyAccount.class) {
	        			log.ok("Skipping delta {0} because of objectclass mismatch", delta);
	        			continue;
	        		}
	        	} else if (objectClass.is(ObjectClass.GROUP_NAME)) {
	        		if (deltaObjectClass != DummyGroup.class) {
	        			log.ok("Skipping delta {0} because of objectclass mismatch", delta);
	        			continue;
	        		}
	        	}
	        	
	        	SyncDeltaBuilder deltaBuilder =  new SyncDeltaBuilder();
	        	if (deltaObjectClass == DummyAccount.class) {
	        		deltaBuilder.setObjectClass(ObjectClass.ACCOUNT);
	        	} else if (deltaObjectClass == DummyGroup.class) {
	        		deltaBuilder.setObjectClass(ObjectClass.GROUP);
	        	} else if (deltaObjectClass == DummyPrivilege.class) {
	        		deltaBuilder.setObjectClass(new ObjectClass(OBJECTCLASS_PRIVILEGE_NAME));
	        	} else {
	        		throw new IllegalArgumentException("Unknown delta objectClass "+deltaObjectClass);
	        	}
	        	
	        	SyncDeltaType deltaType;
	        	if (delta.getType() == DummyDeltaType.ADD || delta.getType() == DummyDeltaType.MODIFY) {
	        		if (resource.getSyncStyle() == DummySyncStyle.DUMB) {
	        			deltaType = SyncDeltaType.CREATE_OR_UPDATE;
	        		} else {
	        			if (delta.getType() == DummyDeltaType.ADD) {
	        				deltaType = SyncDeltaType.CREATE;
	        			} else {
	        				deltaType = SyncDeltaType.UPDATE;
	        			}
	        		}
	        		if (deltaObjectClass == DummyAccount.class) {
		        		DummyAccount account = resource.getAccountById(delta.getObjectId());
		        		if (account == null) {
		        			throw new IllegalStateException("We have delta for account '"+delta.getObjectId()+"' but such account does not exist");
		        		}
		        		ConnectorObject cobject = convertToConnectorObject(account, attributesToGet);
						deltaBuilder.setObject(cobject);
	        		} else if (deltaObjectClass == DummyGroup.class) {
	        			DummyGroup group = resource.getGroupById(delta.getObjectId());
		        		if (group == null) {
		        			throw new IllegalStateException("We have delta for group '"+delta.getObjectId()+"' but such group does not exist");
		        		}
		        		ConnectorObject cobject = convertToConnectorObject(group, attributesToGet);
						deltaBuilder.setObject(cobject);
					} else if (deltaObjectClass == DummyPrivilege.class) {
						DummyPrivilege privilege = resource.getPrivilegeById(delta.getObjectId());
						if (privilege == null) {
							throw new IllegalStateException("We have privilege for group '"+delta.getObjectId()+"' but such privilege does not exist");
						}
						ConnectorObject cobject = convertToConnectorObject(privilege, attributesToGet);
						deltaBuilder.setObject(cobject);
	        		} else {
	        			throw new IllegalArgumentException("Unknown delta objectClass "+deltaObjectClass);
	        		}
	        	} else if (delta.getType() == DummyDeltaType.DELETE) {
	        		deltaType = SyncDeltaType.DELETE;
	        	} else {
	        		throw new IllegalStateException("Unknown delta type "+delta.getType());
	        	}
	        	deltaBuilder.setDeltaType(deltaType);
	        	
	        	deltaBuilder.setToken(new SyncToken(delta.getSyncToken()));
	        	
	        	Uid uid;
	        	if (configuration.getUidMode().equals(DummyConfiguration.UID_MODE_NAME)) {
		        	uid = new Uid(delta.getObjectName());
		        } else if (configuration.getUidMode().equals(DummyConfiguration.UID_MODE_UUID)) {
		        	uid = new Uid(delta.getObjectId());
		        } else {
		        	throw new IllegalStateException("Unknown UID mode "+configuration.getUidMode());
		        }
	        	deltaBuilder.setUid(uid);
	        	
	        	SyncDelta syncDelta = deltaBuilder.build();
	        	log.info("sync::handle {0}",syncDelta);
				handler.handle(syncDelta);
	        }
	        
		} catch (ConnectException e) {
	        log.info("sync::exception "+e);
			throw new ConnectionFailedException(e.getMessage(), e);
		} catch (FileNotFoundException e) {
			log.info("sync::exception "+e);
			throw new ConnectorIOException(e.getMessage(), e);
		}
        
        log.info("sync::end");
    }

	private Collection<String> getAttrsToGet(OperationOptions options) {
        Collection<String> attributesToGet = null;
		if (options != null) {
			String[] attributesToGetArray = options.getAttributesToGet();
			if (attributesToGetArray != null && attributesToGetArray.length != 0) {
				attributesToGet = Arrays.asList(attributesToGetArray);
			}
		}
		return attributesToGet;
	}

	/**
     * {@inheritDoc}
     */
    public SyncToken getLatestSyncToken(ObjectClass objectClass) {
        log.info("getLatestSyncToken::begin");
        validate(objectClass);
        int latestSyncToken = resource.getLatestSyncToken();
        log.info("getLatestSyncToken::end, returning token {0}.", latestSyncToken);
        return new SyncToken(latestSyncToken);
    }

    /**
     * {@inheritDoc}
     */
    public void test() {
        log.info("test::begin");
        log.info("Validating configuration.");
        configuration.validate();
        
        // Produce log messages on all levels. The tests may check if they are really logged.
        log.error(LOG_MARKER + " DummyConnectorIcfError");
        log.info(LOG_MARKER + " DummyConnectorIcfInfo");
        log.warn(LOG_MARKER + " DummyConnectorIcfWarn");
        log.ok(LOG_MARKER + " DummyConnectorIcfOk");
        
        log.info("Dummy Connector JUL logger as seen by the connector: " + julLogger + "; classloader " + julLogger.getClass().getClassLoader());
        
        // Same thing using JUL
        julLogger.severe(LOG_MARKER + " DummyConnectorJULsevere");
		julLogger.warning(LOG_MARKER + " DummyConnectorJULwarning");
		julLogger.info(LOG_MARKER + " DummyConnectorJULinfo");
		julLogger.fine(LOG_MARKER + " DummyConnectorJULfine");
		julLogger.finer(LOG_MARKER + " DummyConnectorJULfiner");
		julLogger.finest(LOG_MARKER + " DummyConnectorJULfinest");
        
        log.info("Test configuration was successful.");
        log.info("test::end");
    }
    
   private ConnectorObjectBuilder createConnectorObjectBuilderCommon(DummyObject dummyObject,
		   DummyObjectClass objectClass, Collection<String> attributesToGet, boolean supportActivation) {
	   ConnectorObjectBuilder builder = new ConnectorObjectBuilder();
	   
	   if (configuration.getUidMode().equals(DummyConfiguration.UID_MODE_NAME)) {
		   builder.setUid(dummyObject.getName());
       } else if (configuration.getUidMode().equals(DummyConfiguration.UID_MODE_UUID)) {
    	   builder.setUid(dummyObject.getId());
       } else {
       		throw new IllegalStateException("Unknown UID mode "+configuration.getUidMode());
       }
	   
		builder.addAttribute(Name.NAME, dummyObject.getName());
		
		for (String name : dummyObject.getAttributeNames()) {
			DummyAttributeDefinition attrDef = objectClass.getAttributeDefinition(name);
			if (attrDef == null) {
				throw new IllegalArgumentException("Unknown account attribute '"+name+"'");
			}
			if (!attrDef.isReturnedByDefault()) {
				if (attributesToGet != null && !attributesToGet.contains(name)) {
					continue;
				}
			}
			// Return all attributes that are returned by default. We will filter them out later.
			Set<Object> values = dummyObject.getAttributeValues(name, Object.class);
			if (configuration.isVaryLetterCase()) {
				name = varyLetterCase(name);
			}
			if (values != null && !values.isEmpty()) {
				builder.addAttribute(name, values);
			}
		}
		
		if (supportActivation) {
			if (attributesToGet == null || attributesToGet.contains(OperationalAttributes.ENABLE_NAME)) {
				builder.addAttribute(OperationalAttributes.ENABLE_NAME, dummyObject.isEnabled());
			}
			
			if (dummyObject.getValidFrom() != null &&
					(attributesToGet == null || attributesToGet.contains(OperationalAttributes.ENABLE_DATE_NAME))) {
				builder.addAttribute(OperationalAttributes.ENABLE_DATE_NAME, convertToLong(dummyObject.getValidFrom()));
			}
			
			if (dummyObject.getValidTo() != null &&
					(attributesToGet == null || attributesToGet.contains(OperationalAttributes.DISABLE_DATE_NAME))) {
				builder.addAttribute(OperationalAttributes.DISABLE_DATE_NAME, convertToLong(dummyObject.getValidTo()));
			}
		}	
		
		return builder;
   }

	private String varyLetterCase(String name) {
		StringBuilder sb = new StringBuilder(name.length());
		for (char c : name.toCharArray()) {
			double a = Math.random();
			if (a < 0.4) {
				c = Character.toLowerCase(c);
			} else if (a > 0.7) {
				c = Character.toUpperCase(c);
			}
			sb.append(c);
		}
		return sb.toString();
	}

	private Long convertToLong(Date date) {
		if (date == null) {
			return null;
		}
		return date.getTime();
	}

	private ConnectorObject convertToConnectorObject(DummyAccount account, Collection<String> attributesToGet) {
		
		DummyObjectClass objectClass;
		try {
			objectClass = resource.getAccountObjectClass();
		} catch (ConnectException e) {
			log.error(e, e.getMessage());
			throw new ConnectionFailedException(e.getMessage(), e);
		} catch (FileNotFoundException e) {
			log.error(e, e.getMessage());
			throw new ConnectorIOException(e.getMessage(), e);
		}
		
		ConnectorObjectBuilder builder = createConnectorObjectBuilderCommon(account, objectClass, attributesToGet, true);
		builder.setObjectClass(ObjectClass.ACCOUNT);
		
		// Password is not returned by default (hardcoded ICF specification)
		if (account.getPassword() != null && configuration.getReadablePassword() && 
				attributesToGet != null && attributesToGet.contains(OperationalAttributes.PASSWORD_NAME)) {
			GuardedString gs = new GuardedString(account.getPassword().toCharArray());
			builder.addAttribute(OperationalAttributes.PASSWORD_NAME,gs);
		}
		
		if (account.isLockout() != null) {
			builder.addAttribute(OperationalAttributes.LOCK_OUT_NAME, account.isLockout());
		}

        return builder.build();
	}
	
	private ConnectorObject convertToConnectorObject(DummyGroup group, Collection<String> attributesToGet) {
		ConnectorObjectBuilder builder = createConnectorObjectBuilderCommon(group, resource.getGroupObjectClass(),
				attributesToGet, true);
		builder.setObjectClass(ObjectClass.GROUP);
        return builder.build();
	}

	private ConnectorObject convertToConnectorObject(DummyPrivilege priv, Collection<String> attributesToGet) {
		ConnectorObjectBuilder builder = createConnectorObjectBuilderCommon(priv, resource.getPrivilegeObjectClass(),
				attributesToGet, false);
		builder.setObjectClass(new ObjectClass(OBJECTCLASS_PRIVILEGE_NAME));
        return builder.build();
	}

	
	private DummyAccount convertToAccount(Set<Attribute> createAttributes) throws ConnectException, FileNotFoundException {
		log.ok("Create attributes: {0}", createAttributes);
		String userName = Utils.getMandatoryStringAttribute(createAttributes, Name.NAME);
		if (configuration.getUpCaseName()) {
			userName = StringUtils.upperCase(userName);
		}
		log.ok("Username {0}", userName);
		final DummyAccount newAccount = new DummyAccount(userName);
		
		Boolean enabled = null;
		for (Attribute attr : createAttributes) {
			if (attr.is(Uid.NAME)) {
				throw new IllegalArgumentException("UID explicitly specified in the account attributes");
				
			} else if (attr.is(Name.NAME)) {
				// Skip, already processed

			} else if (attr.is(OperationalAttributeInfos.PASSWORD.getName())) {
				changePassword(newAccount,attr);
				
			} else if (attr.is(OperationalAttributeInfos.ENABLE.getName())) {
				enabled = getBoolean(attr);
				newAccount.setEnabled(enabled);
				
			} else if (attr.is(OperationalAttributeInfos.ENABLE_DATE.getName())) {
				if (configuration.getSupportValidity()) {
					newAccount.setValidFrom(getDate(attr));
				} else {
					throw new IllegalArgumentException("ENABLE_DATE specified in the account attributes while not supporting it");
				}
				
			} else if (attr.is(OperationalAttributeInfos.DISABLE_DATE.getName())) {
				if (configuration.getSupportValidity()) {
					newAccount.setValidTo(getDate(attr));
				} else {
					throw new IllegalArgumentException("DISABLE_DATE specified in the account attributes while not supporting it");
				}
				
			} else if (attr.is(OperationalAttributeInfos.LOCK_OUT.getName())) {
				Boolean lockout = getBoolean(attr);
				newAccount.setLockout(lockout);
				
			} else {
				String name = attr.getName();
				try {
					newAccount.replaceAttributeValues(name,attr.getValue());
				} catch (SchemaViolationException e) {
					// we cannot throw checked exceptions. But this one looks suitable.
					// Note: let's do the bad thing and add exception loaded by this classloader as inner exception here
					// The framework should deal with it ... somehow
					throw new IllegalArgumentException(e.getMessage(),e);
				}
			}
		}
		
		if (configuration.getRequireExplicitEnable() && enabled == null) {
			throw new IllegalArgumentException("Explicit value for ENABLE attribute was not provided and the connector is set to require it");
		}
		
		return newAccount;
	}
	
	private DummyGroup convertToGroup(Set<Attribute> createAttributes) throws ConnectException, FileNotFoundException {
		String icfName = Utils.getMandatoryStringAttribute(createAttributes,Name.NAME);
		if (configuration.getUpCaseName()) {
			icfName = StringUtils.upperCase(icfName);
		}
		final DummyGroup newGroup = new DummyGroup(icfName);

		Boolean enabled = null;
		for (Attribute attr : createAttributes) {
			if (attr.is(Uid.NAME)) {
				throw new IllegalArgumentException("UID explicitly specified in the group attributes");
				
			} else if (attr.is(Name.NAME)) {
				// Skip, already processed

			} else if (attr.is(OperationalAttributeInfos.PASSWORD.getName())) {
				throw new IllegalArgumentException("Password specified for a group");
				
			} else if (attr.is(OperationalAttributeInfos.ENABLE.getName())) {
				enabled = getBoolean(attr);
				newGroup.setEnabled(enabled);
				
			} else if (attr.is(OperationalAttributeInfos.ENABLE_DATE.getName())) {
				if (configuration.getSupportValidity()) {
					newGroup.setValidFrom(getDate(attr));
				} else {
					throw new IllegalArgumentException("ENABLE_DATE specified in the group attributes while not supporting it");
				}
				
			} else if (attr.is(OperationalAttributeInfos.DISABLE_DATE.getName())) {
				if (configuration.getSupportValidity()) {
					newGroup.setValidTo(getDate(attr));
				} else {
					throw new IllegalArgumentException("DISABLE_DATE specified in the group attributes while not supporting it");
				}
				
			} else {
				String name = attr.getName();
				try {
					newGroup.replaceAttributeValues(name,attr.getValue());
				} catch (SchemaViolationException e) {
					throw new IllegalArgumentException(e.getMessage(),e);
				}
			}
		}

		return newGroup;
	}
	
	private DummyPrivilege convertToPriv(Set<Attribute> createAttributes) throws ConnectException, FileNotFoundException {
		String icfName = Utils.getMandatoryStringAttribute(createAttributes,Name.NAME);
		if (configuration.getUpCaseName()) {
			icfName = StringUtils.upperCase(icfName);
		}
		final DummyPrivilege newPriv = new DummyPrivilege(icfName);

		Boolean enabled = null;
		for (Attribute attr : createAttributes) {
			if (attr.is(Uid.NAME)) {
				throw new IllegalArgumentException("UID explicitly specified in the group attributes");
				
			} else if (attr.is(Name.NAME)) {
				// Skip, already processed

			} else if (attr.is(OperationalAttributeInfos.PASSWORD.getName())) {
				throw new IllegalArgumentException("Password specified for a privilege");
				
			} else if (attr.is(OperationalAttributeInfos.ENABLE.getName())) {
				throw new IllegalArgumentException("Unsupported ENABLE attribute in privilege");
				
			} else {
				String name = attr.getName();
				try {
					newPriv.replaceAttributeValues(name,attr.getValue());
				} catch (SchemaViolationException e) {
					throw new IllegalArgumentException(e.getMessage(),e);
				}
			}
		}

		return newPriv;
	}

	private boolean getBoolean(Attribute attr) {
		if (attr.getValue() == null || attr.getValue().isEmpty()) {
			throw new IllegalArgumentException("Empty "+attr.getName()+" attribute was provided");
		}
		Object object = attr.getValue().get(0);
		if (!(object instanceof Boolean)) {
			throw new IllegalArgumentException("Attribute "+attr.getName()+" was provided as "+object.getClass().getName()+" while expecting boolean");
		}
		return ((Boolean)object).booleanValue();
	}
	
	private Date getDate(Attribute attr) {
		if (attr.getValue() == null || attr.getValue().isEmpty()) {
			throw new IllegalArgumentException("Empty date attribute was provided");
		}
		Object object = attr.getValue().get(0);
		
		if (object == null){
			return null;
		}
		
		if (!(object instanceof Long)) {
			throw new IllegalArgumentException("Date attribute was provided as "+object.getClass().getName()+" while expecting long");
		}
		return new Date(((Long)object).longValue());
	}


	private void changePassword(final DummyAccount account, Attribute attr) {
		if (attr.getValue() == null || attr.getValue().isEmpty()) {
			throw new IllegalArgumentException("Empty password was provided");
		}
		Object passwdObject = attr.getValue().get(0);
		if (!(passwdObject instanceof GuardedString)) {
			throw new IllegalArgumentException("Password was provided as "+passwdObject.getClass().getName()+" while expecting GuardedString");
		}
		((GuardedString)passwdObject).access(new Accessor() {
			@Override
			public void access(char[] passwdChars) {
				account.setPassword(new String(passwdChars));
			}
		});
	}
	
	private boolean attributesToGetHasAttribute(Collection<String> attributesToGet, String attrName) {
		if (attributesToGet == null) {
			return true;
		}
		return attributesToGet.contains(attrName);
	}

}
