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

package com.evolveum.midpoint.model.impl.lens.projector;

import com.evolveum.midpoint.common.filter.Filter;
import com.evolveum.midpoint.common.filter.FilterManager;
import com.evolveum.midpoint.common.refinery.PropertyLimitations;
import com.evolveum.midpoint.common.refinery.RefinedObjectClassDefinition;
import com.evolveum.midpoint.common.refinery.RefinedAttributeDefinition;
import com.evolveum.midpoint.model.api.ModelExecuteOptions;
import com.evolveum.midpoint.model.api.context.SynchronizationPolicyDecision;
import com.evolveum.midpoint.model.common.expression.ItemDeltaItem;
import com.evolveum.midpoint.model.common.expression.Source;
import com.evolveum.midpoint.model.common.expression.StringPolicyResolver;
import com.evolveum.midpoint.model.common.mapping.Mapping;
import com.evolveum.midpoint.model.common.mapping.MappingFactory;
import com.evolveum.midpoint.model.impl.lens.LensContext;
import com.evolveum.midpoint.model.impl.lens.LensFocusContext;
import com.evolveum.midpoint.model.impl.lens.LensObjectDeltaOperation;
import com.evolveum.midpoint.model.impl.lens.LensProjectionContext;
import com.evolveum.midpoint.model.impl.lens.LensUtil;
import com.evolveum.midpoint.prism.ItemDefinition;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.PrismObjectDefinition;
import com.evolveum.midpoint.prism.PrismProperty;
import com.evolveum.midpoint.prism.PrismPropertyDefinition;
import com.evolveum.midpoint.prism.PrismValue;
import com.evolveum.midpoint.prism.PrismPropertyValue;
import com.evolveum.midpoint.prism.OriginType;
import com.evolveum.midpoint.prism.crypto.EncryptionException;
import com.evolveum.midpoint.prism.crypto.Protector;
import com.evolveum.midpoint.prism.delta.ChangeType;
import com.evolveum.midpoint.prism.delta.ItemDelta;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.delta.PrismValueDeltaSetTriple;
import com.evolveum.midpoint.prism.delta.PropertyDelta;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.provisioning.api.ProvisioningService;
import com.evolveum.midpoint.schema.ResourceShadowDiscriminator;
import com.evolveum.midpoint.schema.constants.ExpressionConstants;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.PrettyPrinter;
import com.evolveum.midpoint.util.exception.CommunicationException;
import com.evolveum.midpoint.util.exception.ConfigurationException;
import com.evolveum.midpoint.util.exception.ExpressionEvaluationException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SecurityViolationException;
import com.evolveum.midpoint.util.exception.SystemException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import com.evolveum.prism.xml.ns._public.types_3.ProtectedStringType;

import org.apache.commons.lang.mutable.MutableBoolean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Processor that takes changes from accounts and synchronization deltas and updates user attributes if necessary
 * (by creating secondary user object delta {@link ObjectDelta}).
 *
 * @author lazyman
 * @author Radovan Semancik
 */
@Component
public class InboundProcessor {

    public static final String PROCESS_INBOUND_HANDLING = InboundProcessor.class.getName() + ".processInbound";
    private static final Trace LOGGER = TraceManager.getTrace(InboundProcessor.class);

    @Autowired(required = true)
    private PrismContext prismContext;
    
    @Autowired(required = true)
    private FilterManager<Filter> filterManager;
    
    @Autowired(required = true)
    private MappingFactory mappingFactory;
    
    @Autowired(required=true)
    private ContextLoader contextLoader;
    
    @Autowired(required = true)
    private MappingEvaluationHelper mappingEvaluatorHelper;
    
    @Autowired(required = true)
    private Protector protector;

    <O extends ObjectType> void processInbound(LensContext<O> context, XMLGregorianCalendar now, Task task, OperationResult result) throws SchemaException, ExpressionEvaluationException, ObjectNotFoundException, ConfigurationException {
    	LensFocusContext<O> focusContext = context.getFocusContext();
    	if (focusContext == null) {
            LOGGER.trace("Skipping inbound because there is no focus");
    		return;
    	}
    	if (!FocusType.class.isAssignableFrom(focusContext.getObjectTypeClass())) {
    		// We can do this only for focus types.
            LOGGER.trace("Skipping inbound because {} is not focal type", focusContext.getObjectTypeClass());
    		return;
    	}
    	processInboundFocal((LensContext<? extends FocusType>)context, task, now, result);
    }

    <F extends FocusType> void processInboundFocal(LensContext<F> context, Task task, XMLGregorianCalendar now, OperationResult result) throws SchemaException, ExpressionEvaluationException, ObjectNotFoundException, ConfigurationException {
    	LensFocusContext<F> focusContext = context.getFocusContext();
    	if (focusContext == null) {
    		LOGGER.trace("Skipping inbound processing because focus is null");
    		return;
    	}
    	if (focusContext.isDelete()) {
    		LOGGER.trace("Skipping inbound processing because focus is being deleted");
    		return;
    	}

    	OperationResult subResult = result.createSubresult(PROCESS_INBOUND_HANDLING);

        ObjectDelta<F> userSecondaryDelta = focusContext.getProjectionWaveSecondaryDelta();

        if (userSecondaryDelta != null && ChangeType.DELETE.equals(userSecondaryDelta.getChangeType())) {
            //we don't need to do inbound if we are deleting this user
            return;
        }

        try {
            for (LensProjectionContext projectionContext : context.getProjectionContexts()) {
            	if (!projectionContext.isCanProject()){
            		LOGGER.trace("Skipping processing of inbound expressions for projection {}: there is a limit to propagate changes only from resource {}",
							projectionContext.getResourceShadowDiscriminator(), context.getTriggeredResourceOid());
            		continue;
            	}
            	ResourceShadowDiscriminator rat = projectionContext.getResourceShadowDiscriminator();
            	
            	ObjectDelta<ShadowType> aPrioriDelta = getAPrioriDelta(context, projectionContext);
            	
            	if (!projectionContext.isDoReconciliation() && aPrioriDelta == null && !LensUtil.hasDependentContext(context, projectionContext) && !projectionContext.isFullShadow()) {
            		LOGGER.trace("Skipping processing of inbound expressions for projection {}: no reconciliation and no a priori delta and no dependent context", rat);
            		continue;
            	}

                RefinedObjectClassDefinition accountDefinition = projectionContext.getStructuralObjectClassDefinition();
                if (accountDefinition == null) {
                    LOGGER.error("Definition for account type {} not found in the context, but it " +
                            "should be there, dumping context:\n{}", rat, context.debugDump());
                    throw new IllegalStateException("Definition for account type " + rat
                            + " not found in the context, but it should be there");
                }

                processInboundExpressionsForProjection(context, projectionContext, accountDefinition, aPrioriDelta, task, now, result);
            }

        } finally {
            subResult.computeStatus();
        }
    }

    private boolean isDeleteAccountDelta(LensProjectionContext accountContext) throws SchemaException {
        if (accountContext.getSyncDelta() != null && ChangeType.DELETE == accountContext.getSyncDelta().getChangeType()){
            return true;
        }

        if (accountContext.getDelta() != null && ChangeType.DELETE == accountContext.getDelta().getChangeType()){
            return true;
        }
        return false;
    }

    private <F extends FocusType> void processInboundExpressionsForProjection(LensContext<F> context,
    		LensProjectionContext projContext,
            RefinedObjectClassDefinition accountDefinition, ObjectDelta<ShadowType> aPrioriDelta, Task task, XMLGregorianCalendar now, OperationResult result)
    		throws SchemaException, ExpressionEvaluationException, ObjectNotFoundException, ConfigurationException {
    	
        if (aPrioriDelta == null && projContext.getObjectCurrent() == null) {
            LOGGER.trace("Nothing to process in inbound, both a priori delta and current account were null.");
            return;
        }

        PrismObject<ShadowType> accountCurrent = projContext.getObjectCurrent();
        PrismObject<ShadowType> accountNew = projContext.getObjectNew();
        for (QName accountAttributeName : accountDefinition.getNamesOfAttributesWithInboundExpressions()) {
            PropertyDelta<?> accountAttributeDelta = null;
            if (aPrioriDelta != null) {
                accountAttributeDelta = aPrioriDelta.findPropertyDelta(new ItemPath(SchemaConstants.C_ATTRIBUTES), accountAttributeName);
                if (accountAttributeDelta == null && !projContext.isFullShadow()) {
					LOGGER.trace("Skipping inbound for {} in {}: Not a full shadow and account a priori delta exists, but doesn't have change for processed property.",
							accountAttributeName, projContext.getResourceShadowDiscriminator());
					continue;
                }
            }

            RefinedAttributeDefinition attrDef = accountDefinition.getAttributeDefinition(accountAttributeName);
            
            if (attrDef.isIgnored(LayerType.MODEL)) {
            	LOGGER.trace("Skipping inbound for attribute {} in {} because the attribute is ignored", new Object[]{
                		PrettyPrinter.prettyPrint(accountAttributeName), projContext.getResourceShadowDiscriminator()});
            	continue;
            }
            
            List<MappingType> inboundMappingTypes = attrDef.getInboundMappingTypes();
			if (LOGGER.isTraceEnabled()) {
				LOGGER.trace("Processing inbound for {} in {}; ({} mappings)", new Object[]{
						PrettyPrinter.prettyPrint(accountAttributeName), projContext.getResourceShadowDiscriminator(), inboundMappingTypes.size()});
			}

            if (!inboundMappingTypes.isEmpty()) {
            	
            	PropertyLimitations limitations = attrDef.getLimitations(LayerType.MODEL);
            	if (limitations != null) {
            		PropertyAccessType access = limitations.getAccess();
            		if (access != null) {
            			if (access.isRead() == null || !access.isRead()) {
            				LOGGER.warn("Inbound mapping for non-readable attribute {} in {}, skipping", 
            						accountAttributeName, projContext.getHumanReadableName());
            				continue;
            			}
            		}
            	}
            	
	            for (MappingType inboundMappingType : inboundMappingTypes) {
	            	
	            	// There are two processing options:
	            	//
	            	//  * If we have a delta as an input we will proceed in relative mode, applying mappings on the delta.
	            	//    This usually happens when a delta comes from a sync notification or if there is a primary projection delta.
	            	//
	            	//  * if we do NOT have a delta then we will proceed in absolute mode. In that mode we will apply the
	            	//    mappings to the absolute projection state that we got from provisioning. This is a kind of "inbound reconciliation".
					//
					// TODO what if there is a priori delta for a given attribute (e.g. ADD one) and
					// we want to reconcile also the existing attribute value? This probably would not work.

					PrismObject<F> focus;
	            	if (context.getFocusContext().getObjectCurrent() != null){
	            		focus = context.getFocusContext().getObjectCurrent();
	            	} else {
	            		focus = context.getFocusContext().getObjectNew();
	            	}

	                PropertyDelta<?> userPropertyDelta = null;
	                if (aPrioriDelta != null && accountAttributeDelta != null) {
	                    LOGGER.trace("Processing inbound from a priori delta.");
	                    userPropertyDelta = evaluateInboundMapping(context, inboundMappingType, accountAttributeName, null, accountAttributeDelta, 
	                    		focus, accountNew, projContext.getResource(), task, result);
	                } else if (accountCurrent != null) {
	                	if (!projContext.isFullShadow()) {
	                		LOGGER.warn("Attempted to execute inbound expression on account shadow {} WITHOUT full account. Trying to load the account now.", projContext.getOid());      // todo change to trace level eventually
                            try {
                                contextLoader.loadFullShadow(context, projContext, result);
                                if (projContext.getSynchronizationPolicyDecision() == SynchronizationPolicyDecision.BROKEN) {
                                	return;
                                }
                                accountCurrent = projContext.getObjectCurrent(); 
                            } catch (ObjectNotFoundException|SecurityViolationException|CommunicationException|ConfigurationException e) {
                                LOGGER.warn("Couldn't load account with shadow OID {} because of {}, setting context as broken and skipping inbound processing on it", projContext.getOid(), e.getMessage());
                                projContext.setSynchronizationPolicyDecision(SynchronizationPolicyDecision.BROKEN);
                                return;
                            }
                            if (!projContext.isFullShadow()) {
                            	if (projContext.getResourceShadowDiscriminator().getOrder() > 0) {
                            		// higher-order context. It is OK not to load this
                            		LOGGER.trace("Skipped load of higher-order account with shadow OID {} skipping inbound processing on it", projContext.getOid());
                            		return;
                            	}
								// TODO: is it good to mark as broken? what is
								// the resorce is down?? if there is no
								// assignment and the account was added directly
								// it can cause that the account will be
								// unlinked from the user FIXME
                                LOGGER.warn("Couldn't load account with shadow OID {}, setting context as broken and skipping inbound processing on it", projContext.getOid());
                                projContext.setSynchronizationPolicyDecision(SynchronizationPolicyDecision.BROKEN);
                                return;
                            }
                        }
	                    LOGGER.trace("Processing inbound from account sync absolute state (currentAccount).");
	                    PrismProperty<?> oldAccountProperty = accountCurrent.findProperty(new ItemPath(ShadowType.F_ATTRIBUTES, accountAttributeName));
	                    userPropertyDelta = evaluateInboundMapping(context, inboundMappingType, accountAttributeName, oldAccountProperty, null, 
	                    		focus, accountNew, projContext.getResource(), task, result);
	                }
	
	                if (userPropertyDelta != null && !userPropertyDelta.isEmpty()) {
	                    LOGGER.trace("Created delta (from inbound expression) \n{}", new Object[]{userPropertyDelta.debugDump(3)});
	                    context.getFocusContext().swallowToProjectionWaveSecondaryDelta(userPropertyDelta);
	                    context.recomputeFocus();
	                } else {
	                    LOGGER.trace("Created delta (from inbound expression) was null or empty.");
	                }
	            }
            }
        }

		if (isDeleteAccountDelta(projContext)){
//   		 we don't need to do inbound if account was deleted
			return;
		}
        processSpecialPropertyInbound(accountDefinition.getCredentialsInbound(), SchemaConstants.PATH_PASSWORD_VALUE,
        		context.getFocusContext().getObjectNew(), projContext, accountDefinition, context, task, now, result);
        
        processSpecialPropertyInbound(accountDefinition.getActivationBidirectionalMappingType(ActivationType.F_ADMINISTRATIVE_STATUS), SchemaConstants.PATH_ACTIVATION_ADMINISTRATIVE_STATUS,
        		context.getFocusContext().getObjectNew(), projContext, accountDefinition, context, task, now, result);        
        processSpecialPropertyInbound(accountDefinition.getActivationBidirectionalMappingType(ActivationType.F_VALID_FROM), SchemaConstants.PATH_ACTIVATION_VALID_FROM,
        		context.getFocusContext().getObjectNew(), projContext, accountDefinition, context, task, now, result);
        processSpecialPropertyInbound(accountDefinition.getActivationBidirectionalMappingType(ActivationType.F_VALID_TO), SchemaConstants.PATH_ACTIVATION_VALID_TO,
        		context.getFocusContext().getObjectNew(), projContext, accountDefinition, context, task, now, result);
    }

    /**
	 * A priori delta is a delta that was executed in a previous "step". That means it is either delta from a previous
	 * wave or a sync delta (in wave 0).
	 */
	private <F extends ObjectType> ObjectDelta<ShadowType> getAPrioriDelta(LensContext<F> context, 
			LensProjectionContext accountContext) throws SchemaException {
		int wave = context.getProjectionWave();
		if (wave == 0) {
			return accountContext.getSyncDelta();
		}
		if (wave == accountContext.getWave() + 1) {
			// If this resource was processed in a previous wave ....
			// Normally, we take executed delta. However, there are situations (like preview changes - i.e. projector without execution),
			// when there is no executed delta. In that case we take standard primary + secondary delta.
			// TODO is this really correct? Think if the following can happen:
			// - NOT previewing
			// - no executed deltas but
			// - existing primary/secondary delta.
			List<LensObjectDeltaOperation<ShadowType>> executed = accountContext.getExecutedDeltas();
			if (executed != null && !executed.isEmpty()) {
				return executed.get(executed.size()-1).getObjectDelta();
			} else {
				return accountContext.getDelta();
			}
		}
		return null;
	}

	private <F extends ObjectType> boolean checkWeakSkip(Mapping<?,?> inbound, PrismObject<F> newUser) throws SchemaException {
        if (inbound.getStrength() != MappingStrengthType.WEAK) {
        	return false;
        }
        if (newUser == null) {
        	return false;
        }
        PrismProperty<?> property = newUser.findProperty(inbound.getOutputPath());
        if (property != null && !property.isEmpty()) {
            return true;
        }
        return false;
    }
    
    private <A,U, F extends FocusType> PropertyDelta<U> evaluateInboundMapping(final LensContext<F> context, 
    		MappingType inboundMappingType, 
    		QName accountAttributeName, PrismProperty<A> oldAccountProperty, PropertyDelta<A> accountAttributeDelta,
            PrismObject<F> focusNew, PrismObject<ShadowType> account, ResourceType resource, Task task, OperationResult result) throws ExpressionEvaluationException, ObjectNotFoundException, SchemaException, ConfigurationException {

    	if (oldAccountProperty != null && oldAccountProperty.hasRaw()) {
        	throw new SystemException("Property "+oldAccountProperty+" has raw parsing state, such property cannot be used in inbound expressions");
        }
    	
    	Mapping<PrismPropertyValue<U>,PrismPropertyDefinition<U>> mapping = mappingFactory.createMapping(inboundMappingType, 
    			"inbound expression for "+accountAttributeName+" in "+resource);
    	
    	if (!mapping.isApplicableToChannel(context.getChannel())) {
    		return null;
    	}
    	
    	Source<PrismPropertyValue<A>,PrismPropertyDefinition<A>> defaultSource = new Source<>(oldAccountProperty, accountAttributeDelta, null, ExpressionConstants.VAR_INPUT);
    	defaultSource.recompute();
		mapping.setDefaultSource(defaultSource);
		mapping.setTargetContext(LensUtil.getFocusDefinition(context));
    	mapping.addVariableDefinition(ExpressionConstants.VAR_USER, focusNew);
    	mapping.addVariableDefinition(ExpressionConstants.VAR_FOCUS, focusNew);
    	mapping.addVariableDefinition(ExpressionConstants.VAR_ACCOUNT, account);
    	mapping.addVariableDefinition(ExpressionConstants.VAR_SHADOW, account);
    	mapping.addVariableDefinition(ExpressionConstants.VAR_RESOURCE, resource);
		mapping.setStringPolicyResolver(createStringPolicyResolver(context));
		mapping.setOriginType(OriginType.INBOUND);
		mapping.setOriginObject(resource);
    	
    	if (checkWeakSkip(mapping, focusNew)) {
            LOGGER.trace("Skipping because of weak mapping type");
            return null;
        }
        
        ItemPath targetFocusPropertyPath = mapping.getOutputPath();
        if (targetFocusPropertyPath == null || targetFocusPropertyPath.isEmpty()) {
        	throw new ConfigurationException("Empty target path in "+mapping.getContextDescription());
        }
        PrismProperty<U> targetFocusProperty = null;
        if (focusNew != null) {
        	targetFocusProperty = focusNew.findProperty(targetFocusPropertyPath);
        }
        PrismObjectDefinition<F> focusDefinition = context.getFocusContext().getObjectDefinition();
        PrismPropertyDefinition targetPropertyDef = focusDefinition.findPropertyDefinition(targetFocusPropertyPath);
        if (targetPropertyDef == null) {
        	throw new SchemaException("No definition for focus property "+targetFocusPropertyPath+", cannot process inbound expression in "+resource);
        }
        
        PropertyDelta<U> outputUserPropertydelta = new PropertyDelta<U>(targetFocusPropertyPath, targetPropertyDef, prismContext);
    	
        LensUtil.evaluateMapping(mapping, context, task, result);
        
    	
    	PrismValueDeltaSetTriple<PrismPropertyValue<U>> triple = mapping.getOutputTriple();
    	// Meaning of the resulting triple:
    	//   values in PLUS set will be added     (valuesToAdd in delta)
    	//   values in MINUS set will be removed  (valuesToDelete in delta)
    	//   values in ZERO set will be compared with existing values in user property
    	//                  the differences will be added to delta
    	
    	if (LOGGER.isTraceEnabled()) {
    		LOGGER.trace("Inbound value construction for {} returned triple:\n{}", accountAttributeName, triple == null ? "null" : triple.debugDump());
    	}
        
    	if (triple != null) {
    		
	        if (triple.hasPlusSet()) {

				boolean alreadyReplaced = false;

				for (PrismPropertyValue<U> value : triple.getPlusSet()) {

	                if (targetFocusProperty != null && targetFocusProperty.hasRealValue(value)) {
	                    continue;
	                }
	
	                //if property is not multi value replace existing attribute
	                if (targetFocusProperty != null && !targetFocusProperty.getDefinition().isMultiValue() && !targetFocusProperty.isEmpty()) {
	                    Collection<PrismPropertyValue<U>> replace = new ArrayList<PrismPropertyValue<U>>();
	                    replace.add(value.clone());
	                    outputUserPropertydelta.setValuesToReplace(replace);

						if (alreadyReplaced) {
							LOGGER.warn("Multiple values for a single-valued property {}; duplicate value = {}", targetFocusProperty, value);
						} else {
							alreadyReplaced = true;
						}
	                } else {
	                    outputUserPropertydelta.addValueToAdd(value.clone());
	                }
	            }
	        }

	        if (triple.hasMinusSet()) {
	            LOGGER.trace("Checking account sync property delta values to delete");
	            for (PrismPropertyValue<U> value : triple.getMinusSet()) {
	
	                if (targetFocusProperty == null || targetFocusProperty.hasRealValue(value)) {
	                	if (!outputUserPropertydelta.isReplace()) {
	                		// This is not needed if we are going to replace. In fact it might cause an error.
	                		outputUserPropertydelta.addValueToDelete(value);
	                	}
	                }
	            }
	        }
	        
	        if (triple.hasZeroSet()) {
		        PrismProperty<U> sourceProperty = targetPropertyDef.instantiate();
		    	sourceProperty.addAll(PrismValue.cloneCollection(triple.getZeroSet()));
		        if (targetFocusProperty != null) {
		            LOGGER.trace("Simple property comparing user property {} to computed property {} ",
		                    new Object[]{targetFocusProperty, sourceProperty});
		            //simple property comparing if user property exists
		            PropertyDelta<U> diffDelta = targetFocusProperty.diff(sourceProperty);
		            if (diffDelta != null) {
		            	diffDelta.setElementName(ItemPath.getName(targetFocusPropertyPath.last()));
		            	diffDelta.setParentPath(targetFocusPropertyPath.allExceptLast());
		            	outputUserPropertydelta.merge(diffDelta);
		            }
		        } else {
		            if (sourceProperty != null) {	// actually sourceProperty is never null here [med]
		                LOGGER.trace("Adding user property because inbound say so (account doesn't contain that value)");
		                //if user property doesn't exist we have to add it (as delta), because inbound say so
		                outputUserPropertydelta.addValuesToAdd(sourceProperty.getClonedValues());
		            }
		            //we don't have to create delta, because everything is alright
		            LOGGER.trace("We don't have to create delta, everything is alright.");
		        }
	        }
	        
    	} else { // triple == null

			// TODO this is perhaps not always right. If the reason for triple == null is that the inbound was not applied
			// because of condition being false, we perhaps should not reset the user's property.
			// See TestImportRecon.test200ReconcileDummy, where user c0c010c0-d34d-b33f-f00d-11111111c008 has
			// organizationalUnit being zeroed, even if the inbound was not applied because of condition==false.
			// See MID-2441.

   			if (accountAttributeDelta == null && (LensUtil.isSyncChannel(context.getChannel()) || ModelExecuteOptions.isReconcile(context.getOptions()))){
    			// This is the case of "inbound reconciliation" which is quite special. The triple returned null
    			// which means that there was nothing in the input and (unsurprisingly) no change. If the input was empty
    			// then we need to make sure that the output (focus property) is also empty. Otherwise we miss the
    			// re-sets of projection values to empty values and cannot propagate them.
    			
    			if (targetFocusProperty != null && !targetFocusProperty.isEmpty()) {
    				outputUserPropertydelta.setValuesToReplace();
    			}

				// TODO implement something similar also for scripted inbounds (MID-2421)
    		}
    		
    	}

        // if no changes were generated return null
        return outputUserPropertydelta.isEmpty() ? null : outputUserPropertydelta;
    }

	private <F extends ObjectType> StringPolicyResolver createStringPolicyResolver(final LensContext<F> context) {
		StringPolicyResolver stringPolicyResolver = new StringPolicyResolver() {
			private ItemPath outputPath;
			private ItemDefinition outputDefinition;
			@Override
			public void setOutputPath(ItemPath outputPath) {
				this.outputPath = outputPath;
			}
			
			@Override
			public void setOutputDefinition(ItemDefinition outputDefinition) {
				this.outputDefinition = outputDefinition;
			}
			
			@Override
			public StringPolicyType resolve() {
				if (!outputDefinition.getName().equals(PasswordType.F_VALUE)) {
					return null;
				}
				ValuePolicyType passwordPolicy = context.getEffectivePasswordPolicy();
				if (passwordPolicy == null) {
					return null;
				}
				return passwordPolicy.getStringPolicy();
			}
		};
		return stringPolicyResolver;
	}

	private <T> PrismPropertyValue<T> filterValue(PrismPropertyValue<T> propertyValue, List<ValueFilterType> filters) {
        PrismPropertyValue<T> filteredValue = propertyValue.clone();
        filteredValue.setOriginType(OriginType.INBOUND);

        if (filters == null || filters.isEmpty()) {
            return filteredValue;
        }

        for (ValueFilterType filter : filters) {
            Filter filterInstance = filterManager.getFilterInstance(filter.getType(), filter.getAny());
            filterInstance.apply(filteredValue);
        }

        return filteredValue;
    }
	
	/**
     * Processing for special (fixed-schema) properties such as credentials and activation. 
	 * @throws ObjectNotFoundException 
	 * @throws ExpressionEvaluationException 
     */
    private <F extends FocusType> void processSpecialPropertyInbound(ResourceBidirectionalMappingType biMappingType, ItemPath sourcePath,
            PrismObject<F> newUser, LensProjectionContext accContext, 
            RefinedObjectClassDefinition accountDefinition, LensContext<F> context,
            Task task, XMLGregorianCalendar now, OperationResult opResult) throws SchemaException, ExpressionEvaluationException, ObjectNotFoundException {
    	if (biMappingType == null) {
    		return;
    	}
    	processSpecialPropertyInbound(biMappingType.getInbound(), sourcePath, newUser, accContext, accountDefinition, context, task, now, opResult);
    }

//    private void processSpecialPropertyInbound(MappingType inboundMappingType, ItemPath sourcePath,
//            PrismObject<UserType> newUser, LensProjectionContext<ShadowType> accContext, 
//            RefinedObjectClassDefinition accountDefinition, LensContext<UserType,ShadowType> context, 
//            OperationResult opResult) throws SchemaException {
//    	if (inboundMappingType == null) {
//    		return;
//    	}
//    	Collection<MappingType> inboundMappingTypes = new ArrayList<MappingType>(1);
//    	inboundMappingTypes.add(inboundMappingType);
//    	processSpecialPropertyInbound(inboundMappingTypes, sourcePath, newUser, accContext, accountDefinition, context, opResult);
//    }

    /**
     * Processing for special (fixed-schema) properties such as credentials and activation. 
     * @throws ObjectNotFoundException 
     * @throws ExpressionEvaluationException 
     */
    private <F extends FocusType> void processSpecialPropertyInbound(Collection<MappingType> inboundMappingTypes, final ItemPath sourcePath,
            final PrismObject<F> newUser, final LensProjectionContext accContext,
            RefinedObjectClassDefinition accountDefinition, final LensContext<F> context,
            Task task, XMLGregorianCalendar now, OperationResult opResult) throws SchemaException, ExpressionEvaluationException, ObjectNotFoundException {

        if (inboundMappingTypes == null || inboundMappingTypes.isEmpty() || newUser == null || !accContext.isFullShadow()) {
            return;
        }
        
        ObjectDelta<F> userPrimaryDelta = context.getFocusContext().getPrimaryDelta();
        PropertyDelta primaryPropDelta = null;
        if (userPrimaryDelta != null) {
        	primaryPropDelta = userPrimaryDelta.findPropertyDelta(sourcePath);
        	if (primaryPropDelta != null && primaryPropDelta.isReplace()) {
//        		 Replace primary delta overrides any inbound
        		return;
        	}
        }

      ObjectDelta<F> userSecondaryDelta = context.getFocusContext().getProjectionWaveSecondaryDelta();
      if (userSecondaryDelta != null) {
	        PropertyDelta<?> delta = userSecondaryDelta.findPropertyDelta(sourcePath);
	        if (delta != null) {
	            //remove delta if exists, it will be handled by inbound
	            userSecondaryDelta.getModifications().remove(delta);
	        }
      }
        
        MappingInitializer initializer = new MappingInitializer() {
			@Override
			public void initialize(Mapping mapping) throws SchemaException {
				 if (accContext.getObjectNew() == null) {
			            accContext.recompute();
			            if (accContext.getObjectNew() == null) {
			                // Still null? something must be really wrong here.
			                String message = "Recomputing account " + accContext.getResourceShadowDiscriminator()
			                        + " results in null new account. Something must be really broken.";
			                LOGGER.error(message);
			                if (LOGGER.isTraceEnabled()) {
			                    LOGGER.trace("Account context:\n{}", accContext.debugDump());
			                }
			                throw new SystemException(message);
			            }
			        }
			        
			        ObjectDelta<ShadowType> aPrioriShadowDelta = getAPrioriDelta(context, accContext);
			        ItemDelta<PrismPropertyValue<?>,PrismPropertyDefinition<?>> specialAttributeDelta = null;
			        if (aPrioriShadowDelta != null){
			        	specialAttributeDelta = aPrioriShadowDelta.findItemDelta(sourcePath);
			        }
			        ItemDeltaItem<PrismPropertyValue<?>,PrismPropertyDefinition<?>> sourceIdi = accContext.getObjectDeltaObject().findIdi(sourcePath);
			        if (specialAttributeDelta == null){
			        	specialAttributeDelta = sourceIdi.getDelta();
			        }
			        Source<PrismPropertyValue<?>,PrismPropertyDefinition<?>> source = new Source<>(sourceIdi.getItemOld(), specialAttributeDelta, 
			        		sourceIdi.getItemOld(), ExpressionConstants.VAR_INPUT);
					mapping.setDefaultSource(source);
					
			    	mapping.addVariableDefinition(ExpressionConstants.VAR_USER, newUser);
			    	mapping.addVariableDefinition(ExpressionConstants.VAR_FOCUS, newUser);
			    	
			    	PrismObject<ShadowType> accountNew = accContext.getObjectNew();
			    	mapping.addVariableDefinition(ExpressionConstants.VAR_ACCOUNT, accountNew);
			    	mapping.addVariableDefinition(ExpressionConstants.VAR_SHADOW, accountNew);
			    	mapping.addVariableDefinition(ExpressionConstants.VAR_RESOURCE, accContext.getResource());
			    	
			    	mapping.setStringPolicyResolver(createStringPolicyResolver(context));
			    	mapping.setOriginType(OriginType.INBOUND);
			    	mapping.setOriginObject(accContext.getResource());
			}
        };
        
        MappingOutputProcessor<PrismValue> processor = new MappingOutputProcessor<PrismValue>() {
			@Override
			public void process(ItemPath mappingOutputPath, PrismValueDeltaSetTriple<PrismValue> outputTriple)
					throws ExpressionEvaluationException, SchemaException {
		        if (outputTriple == null){
		        	LOGGER.trace("Mapping for property {} evaluated to null. Skipping inboud processing for that property.", sourcePath);
		        	return;
		        }
		        
		        ObjectDelta<F> userSecondaryDelta = context.getFocusContext().getProjectionWaveSecondaryDelta();
		        if (userSecondaryDelta != null) {
			        PropertyDelta<?> delta = userSecondaryDelta.findPropertyDelta(sourcePath);
			        if (delta != null) {
			            //remove delta if exists, it will be handled by inbound
			            userSecondaryDelta.getModifications().remove(delta);
			        }
		        }
		        
		        PrismObjectDefinition<F> focusDefinition = context.getFocusContext().getObjectDefinition();
		        PrismProperty result = focusDefinition.findPropertyDefinition(sourcePath).instantiate();
		    	result.addAll(PrismValue.cloneCollection(outputTriple.getNonNegativeValues()));
		        
		    	PrismProperty targetPropertyNew = newUser.findOrCreateProperty(sourcePath);
		    	PropertyDelta<?> delta;
		    	if (ProtectedStringType.COMPLEX_TYPE.equals(targetPropertyNew.getDefinition().getTypeName())) {
		    		// We have to compare this in a special way. The cipherdata may be different due to a different
		    		// IV, but the value may still be the same
		    		ProtectedStringType resultValue = (ProtectedStringType) result.getRealValue();
		    		ProtectedStringType targetPropertyNewValue = (ProtectedStringType) targetPropertyNew.getRealValue();
		    		try {
						if (protector.compare(resultValue, targetPropertyNewValue)) {
							delta = null;
						} else {
							delta = targetPropertyNew.diff(result);
						}
					} catch (EncryptionException e) {
						throw new SystemException(e.getMessage(), e);
					}
		    	} else {
		    		delta = targetPropertyNew.diff(result);
		    	}
		        LOGGER.trace("targetPropertyNew:\n{}\ndelta:\n{}", targetPropertyNew.debugDump(1), delta==null?"null":delta.debugDump(1));
		        if (delta != null && !delta.isEmpty()) {
		        	delta.setParentPath(sourcePath.allExceptLast());
		        	if (!context.getFocusContext().alreadyHasDelta(delta)){
		        		context.getFocusContext().swallowToProjectionWaveSecondaryDelta(delta);
		        	}
		        }

			}
		};
        
        MappingEvaluatorHelperParams<PrismValue, ItemDefinition, F, F> params = new MappingEvaluatorHelperParams<>();
        params.setMappingTypes(inboundMappingTypes);
        params.setMappingDesc("inbound mapping for " + sourcePath + " in " + accContext.getResource());
        params.setNow(now);
        params.setInitializer(initializer);
		params.setProcessor(processor);
        params.setAPrioriTargetObject(newUser);
        params.setAPrioriTargetDelta(userPrimaryDelta);
        params.setTargetContext(context.getFocusContext());
        params.setDefaultTargetItemPath(sourcePath);
        params.setEvaluateCurrent(true);
        params.setContext(context);
        params.setHasFullTargetObject(true);
		mappingEvaluatorHelper.evaluateMappingSetProjection(params, task, opResult);
        
//        MutableBoolean strongMappingWasUsed = new MutableBoolean();
//        PrismValueDeltaSetTriple<? extends PrismPropertyValue<?>> outputTriple = mappingEvaluatorHelper.evaluateMappingSetProjection(
//                inboundMappingTypes, "inbound mapping for " + sourcePath + " in " + accContext.getResource(), now, initializer, targetPropertyNew, primaryPropDelta, newUser, true, strongMappingWasUsed, context, accContext, task, opResult);
		
    }
    
    private Collection<Mapping> getMappingApplicableToChannel(
			Collection<MappingType> inboundMappingTypes, String description, String channelUri) {
    	Collection<Mapping> inboundMappings = new ArrayList<Mapping>(); 
		for (MappingType inboundMappingType : inboundMappingTypes){
			Mapping<PrismPropertyValue<?>,PrismPropertyDefinition<?>> mapping = mappingFactory.createMapping(inboundMappingType, 
	        		description);
			
			if (mapping.isApplicableToChannel(channelUri)){
				inboundMappings.add(mapping);
			}
		}
		
		return inboundMappings;
	}
}
