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

import static com.evolveum.midpoint.common.InternalsConfig.consistencyChecks;

import com.evolveum.midpoint.common.refinery.PropertyLimitations;
import com.evolveum.midpoint.common.refinery.RefinedAssociationDefinition;
import com.evolveum.midpoint.common.refinery.RefinedObjectClassDefinition;
import com.evolveum.midpoint.common.refinery.RefinedAttributeDefinition;
import com.evolveum.midpoint.model.api.PolicyViolationException;
import com.evolveum.midpoint.model.api.context.SynchronizationPolicyDecision;
import com.evolveum.midpoint.model.common.mapping.Mapping;
import com.evolveum.midpoint.model.common.mapping.PrismValueDeltaSetTripleProducer;
import com.evolveum.midpoint.model.impl.lens.Construction;
import com.evolveum.midpoint.model.impl.lens.ItemValueWithOrigin;
import com.evolveum.midpoint.model.impl.lens.LensContext;
import com.evolveum.midpoint.model.impl.lens.LensFocusContext;
import com.evolveum.midpoint.model.impl.lens.LensProjectionContext;
import com.evolveum.midpoint.model.impl.lens.LensUtil;
import com.evolveum.midpoint.prism.Containerable;
import com.evolveum.midpoint.prism.Item;
import com.evolveum.midpoint.prism.ItemDefinition;
import com.evolveum.midpoint.prism.PrismContainer;
import com.evolveum.midpoint.prism.PrismContainerDefinition;
import com.evolveum.midpoint.prism.PrismContainerValue;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.PrismProperty;
import com.evolveum.midpoint.prism.PrismPropertyDefinition;
import com.evolveum.midpoint.prism.PrismReference;
import com.evolveum.midpoint.prism.PrismValue;
import com.evolveum.midpoint.prism.PrismPropertyValue;
import com.evolveum.midpoint.prism.delta.ChangeType;
import com.evolveum.midpoint.prism.delta.ContainerDelta;
import com.evolveum.midpoint.prism.delta.DeltaSetTriple;
import com.evolveum.midpoint.prism.delta.ItemDelta;
import com.evolveum.midpoint.prism.delta.PrismValueDeltaSetTriple;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.delta.PropertyDelta;
import com.evolveum.midpoint.prism.match.MatchingRuleRegistry;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.provisioning.api.ProvisioningService;
import com.evolveum.midpoint.schema.GetOperationOptions;
import com.evolveum.midpoint.schema.ResourceShadowDiscriminator;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.util.DebugUtil;
import com.evolveum.midpoint.util.MiscUtil;
import com.evolveum.midpoint.util.exception.CommunicationException;
import com.evolveum.midpoint.util.exception.ConfigurationException;
import com.evolveum.midpoint.util.exception.ExpressionEvaluationException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SecurityViolationException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.FocusType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.LayerType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.MappingStrengthType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.OperationResultStatusType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.PropertyAccessType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowAssociationType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowType;

import org.apache.commons.lang.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.xml.namespace.QName;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

/**
 * This processor consolidate delta set triples acquired from account sync context and transforms them to
 * property deltas. It converts mappings to deltas considering exclusions, authoritativeness and strength of individual
 * mappings. It also (somehow indirectly) merges all the mappings together. It considers also property deltas from sync,
 * which already happened.
 *
 * @author Radovan Semancik
 * @author lazyman
 */
@Component
public class ConsolidationProcessor {

    public static final String PROCESS_CONSOLIDATION = ConsolidationProcessor.class.getName() + ".consolidateValues";
    private static final Trace LOGGER = TraceManager.getTrace(ConsolidationProcessor.class);
    
    private PrismContainerDefinition<ShadowAssociationType> associationDefinition;
    
    @Autowired(required=true)
    private ContextLoader contextLoader;
    
    @Autowired(required=true)
	private MatchingRuleRegistry matchingRuleRegistry;

    @Autowired(required=true)
    PrismContext prismContext;
    
    /**
     * Converts delta set triples to a secondary account deltas.
     */
    <F extends FocusType> void consolidateValues(LensContext<F> context, LensProjectionContext accCtx, 
    		OperationResult result) 
    				throws SchemaException, ExpressionEvaluationException, ObjectNotFoundException, CommunicationException,
    				ConfigurationException, SecurityViolationException, PolicyViolationException {
    		//todo filter changes which were already in account sync delta

        //account was deleted, no changes are needed.
        if (wasProjectionDeleted(accCtx)) {
            dropAllProjectionDelta(accCtx);
            return;
        }

        SynchronizationPolicyDecision policyDecision = accCtx.getSynchronizationPolicyDecision();

        if (consistencyChecks) context.checkConsistence();
        if (policyDecision == SynchronizationPolicyDecision.DELETE) {
            // Nothing to do
        } else {
            // This is ADD, KEEP, UNLINK or null. All are in fact the same as KEEP
            consolidateValuesModifyProjection(context, accCtx, result);
            if (consistencyChecks) context.checkConsistence();
        }
        if (consistencyChecks) context.checkConsistence();
    }

    private void dropAllProjectionDelta(LensProjectionContext accContext) {
        accContext.setPrimaryDelta(null);
        accContext.setSecondaryDelta(null);
    }

    private boolean wasProjectionDeleted(LensProjectionContext accContext) {
        ObjectDelta<ShadowType> delta = accContext.getSyncDelta();
        if (delta != null && ChangeType.DELETE.equals(delta.getChangeType())) {
            return true;
        }

        return false;
    }

    private <F extends FocusType> ObjectDelta<ShadowType> consolidateValuesToModifyDelta(LensContext<F> context,
    		LensProjectionContext projCtx, boolean addUnchangedValues, OperationResult result) 
            		throws SchemaException, ExpressionEvaluationException, ObjectNotFoundException, CommunicationException,
            		ConfigurationException, SecurityViolationException, PolicyViolationException {
    	
    	// "Squeeze" all the relevant mappings into a data structure that we can process conveniently. We want to have all the
    	// (meta)data about relevant for a specific attribute in one data structure, not spread over several account constructions.
    	MappingExtractor<PrismPropertyValue<?>,PrismPropertyDefinition<?>,F> attributeExtractor = new MappingExtractor<PrismPropertyValue<?>,PrismPropertyDefinition<?>,F>() {
			@Override
			public Collection<Mapping<PrismPropertyValue<?>,PrismPropertyDefinition<?>>> getMappings(Construction<F> construction) {
				return (Collection)construction.getAttributeMappings();
			}
		};
    	Map<QName, DeltaSetTriple<ItemValueWithOrigin<PrismPropertyValue<?>,PrismPropertyDefinition<?>>>> squeezedAttributes = sqeeze(projCtx, attributeExtractor); 
    	projCtx.setSqueezedAttributes(squeezedAttributes);
    	
    	MappingExtractor<PrismContainerValue<ShadowAssociationType>,PrismContainerDefinition<ShadowAssociationType>,F> associationExtractor = new MappingExtractor<PrismContainerValue<ShadowAssociationType>,PrismContainerDefinition<ShadowAssociationType>,F>() {
			@Override
			public Collection<Mapping<PrismContainerValue<ShadowAssociationType>,PrismContainerDefinition<ShadowAssociationType>>> getMappings(Construction<F> construction) {
				return construction.getAssociationMappings();
			}
		};
    	Map<QName, DeltaSetTriple<ItemValueWithOrigin<PrismContainerValue<ShadowAssociationType>,PrismContainerDefinition<ShadowAssociationType>>>> squeezedAssociations = 
    			sqeeze(projCtx, associationExtractor); 
    	projCtx.setSqueezedAssociations(squeezedAssociations);

        // Association values in squeezed associations do not contain association name attribute.
        // It is hacked-in later for use in this class, but not for other uses (e.g. in ReconciliationProcessor).
        // So, we do it here - once and for all.
        if (!squeezedAssociations.isEmpty()) {
            fillInAssociationNames(squeezedAssociations);
        }
        
        MappingExtractor<PrismPropertyValue<QName>,PrismPropertyDefinition<QName>,F> auxiliaryObjectClassExtractor = new MappingExtractor<PrismPropertyValue<QName>,PrismPropertyDefinition<QName>,F>() {
			@Override
			public Collection<PrismValueDeltaSetTripleProducer<PrismPropertyValue<QName>,PrismPropertyDefinition<QName>>> getMappings(final Construction<F> construction) {
				PrismValueDeltaSetTripleProducer<PrismPropertyValue<QName>,PrismPropertyDefinition<QName>> prod = new PrismValueDeltaSetTripleProducer<PrismPropertyValue<QName>,PrismPropertyDefinition<QName>>() {
					@Override
					public QName getMappingQName() {
						return ShadowType.F_AUXILIARY_OBJECT_CLASS;
					}
					@Override
					public PrismValueDeltaSetTriple<PrismPropertyValue<QName>> getOutputTriple() {
						PrismValueDeltaSetTriple<PrismPropertyValue<QName>> triple = new PrismValueDeltaSetTriple<>();
						if (construction.getAuxiliaryObjectClassDefinitions() != null) {
							for (RefinedObjectClassDefinition auxiliaryObjectClassDefinition: construction.getAuxiliaryObjectClassDefinitions()) {
								triple.addToZeroSet(new PrismPropertyValue<QName>(auxiliaryObjectClassDefinition.getTypeName()));
							}
						}
						return triple;
					}
					@Override
					public MappingStrengthType getStrength() {
						return MappingStrengthType.STRONG;
					}
					@Override
					public PrismValueDeltaSetTripleProducer<PrismPropertyValue<QName>,PrismPropertyDefinition<QName>> clone() {
						return this;
					}
					@Override
					public boolean isExclusive() {
						return false;
					}
					@Override
					public boolean isAuthoritative() {
						return true;
					}
				};
				Collection<PrismValueDeltaSetTripleProducer<PrismPropertyValue<QName>,PrismPropertyDefinition<QName>>> col = new ArrayList<>(1);
				col.add(prod);
				return col;
			}
		};
        Map<QName, DeltaSetTriple<ItemValueWithOrigin<PrismPropertyValue<QName>,PrismPropertyDefinition<QName>>>> squeezedAuxiliaryObjectClasses = 
    			sqeeze(projCtx, auxiliaryObjectClassExtractor); 
    	projCtx.setSqueezedAuxiliaryObjectClasses(squeezedAuxiliaryObjectClasses);
    	
        ResourceShadowDiscriminator discr = projCtx.getResourceShadowDiscriminator();
        ObjectDelta<ShadowType> objectDelta = new ObjectDelta<ShadowType>(ShadowType.class, ChangeType.MODIFY, prismContext);
        objectDelta.setOid(projCtx.getOid());

        RefinedObjectClassDefinition rOcDef = projCtx.getStructuralObjectClassDefinition();
        if (rOcDef == null) {
            LOGGER.error("Definition for account type {} not found in the context, but it should be there, dumping context:\n{}", discr, context.debugDump());
            throw new IllegalStateException("Definition for account type " + discr + " not found in the context, but it should be there");
        }
        
		// Do not automatically load the full projection now. Even if we have weak mapping.
        // That may be a waste of resources if the weak mapping results in no change anyway.
        // Let's be very very lazy about fetching the account from the resource.
 		if (!projCtx.hasFullShadow() && 
 				(hasActiveWeakMapping(squeezedAttributes, projCtx) || hasActiveWeakMapping(squeezedAssociations, projCtx)
 						|| (hasActiveStrongMapping(squeezedAttributes, projCtx) || hasActiveStrongMapping(squeezedAssociations, projCtx)))) {
 			// Full account was not yet loaded. This will cause problems as
 			// the weak mapping may be applied even though it should not be
 			// applied
 			// and also same changes may be discarded because of unavailability
 			// of all
 			// account's attributes.Therefore load the account now, but with
 			// doNotDiscovery options..
 			
 			// We also need to get account if there are strong mappings. Strong mappings
 			// should always be applied. So reading the account now will indirectly
 			// trigger reconciliation which makes sure that the strong mappings are
 			// applied.

 			// By getting accounts from provisioning, there might be a problem with
 	 		// resource availability. We need to know, if the account was read full
 	 		// or we have only the shadow from the repository. If we have only
 	 		// shadow, the weak mappings may applied even if they should not be. 
 			contextLoader.loadFullShadow(context, projCtx, result);
 			if (projCtx.getSynchronizationPolicyDecision() == SynchronizationPolicyDecision.BROKEN) {
 				return null;
 			}
     	}
		
		boolean completeAccount = projCtx.hasFullShadow();
        
        ObjectDelta<ShadowType> existingDelta = projCtx.getDelta();

        // ATTRIBUTES
        // Iterate and process each attribute separately. Now that we have squeezed the data we can process each attribute just
        // with the data in ItemValueWithOrigin triples.
        for (Map.Entry<QName, DeltaSetTriple<ItemValueWithOrigin<PrismPropertyValue<?>,PrismPropertyDefinition<?>>>> entry : squeezedAttributes.entrySet()) {
        	QName attributeName = entry.getKey();
        	DeltaSetTriple<ItemValueWithOrigin<PrismPropertyValue<?>,PrismPropertyDefinition<?>>> triple = entry.getValue();
        	PropertyDelta<?> propDelta = consolidateAttribute(rOcDef, discr, existingDelta, projCtx, 
        			addUnchangedValues, completeAccount, attributeName, (DeltaSetTriple)triple);
        	if (propDelta != null) {
        		objectDelta.addModification(propDelta);
        	}
        }

        // ASSOCIATIONS
        for (Entry<QName, DeltaSetTriple<ItemValueWithOrigin<PrismContainerValue<ShadowAssociationType>,PrismContainerDefinition<ShadowAssociationType>>>> entry : squeezedAssociations.entrySet()) {
        	QName associationName = entry.getKey();
        	DeltaSetTriple<ItemValueWithOrigin<PrismContainerValue<ShadowAssociationType>,PrismContainerDefinition<ShadowAssociationType>>> triple = entry.getValue();
        	ContainerDelta<ShadowAssociationType> containerDelta = consolidateAssociation(rOcDef, discr, existingDelta, projCtx, 
        			addUnchangedValues, completeAccount, associationName, triple);
        	if (containerDelta != null) {
        		objectDelta.addModification(containerDelta);
        	}
        }
        
        // AUXILIARY OBJECT CLASSES
        for (Entry<QName, DeltaSetTriple<ItemValueWithOrigin<PrismPropertyValue<QName>, PrismPropertyDefinition<QName>>>> entry : squeezedAuxiliaryObjectClasses.entrySet()) {
        	DeltaSetTriple<ItemValueWithOrigin<PrismPropertyValue<QName>, PrismPropertyDefinition<QName>>> ivwoTriple = entry.getValue();
        	PropertyDelta<QName> propDelta = (PropertyDelta)new PropertyDelta<>(projCtx.getObjectDefinition().findPropertyDefinition(ShadowType.F_AUXILIARY_OBJECT_CLASS), prismContext);
        	for (ItemValueWithOrigin<PrismPropertyValue<QName>,PrismPropertyDefinition<QName>> ivwo: ivwoTriple.getPlusSet()) {
        		propDelta.addValueToAdd(ivwo.getItemValue());
        	}
        	for (ItemValueWithOrigin<PrismPropertyValue<QName>,PrismPropertyDefinition<QName>> ivwo: ivwoTriple.getMinusSet()) {
        		propDelta.addValueToDelete(ivwo.getItemValue());
        	}
        	if (!propDelta.isEmpty()) {
        		objectDelta.addModification(propDelta);
        	}
        }
        
        return objectDelta;
    }

    private void fillInAssociationNames(Map<QName, DeltaSetTriple<ItemValueWithOrigin<PrismContainerValue<ShadowAssociationType>,PrismContainerDefinition<ShadowAssociationType>>>> squeezedAssociations) throws SchemaException {
        PrismPropertyDefinition<QName> nameDefinition = prismContext.getSchemaRegistry()
                .findContainerDefinitionByCompileTimeClass(ShadowAssociationType.class)
                .findPropertyDefinition(ShadowAssociationType.F_NAME);
        for (Entry<QName, DeltaSetTriple<ItemValueWithOrigin<PrismContainerValue<ShadowAssociationType>,PrismContainerDefinition<ShadowAssociationType>>>> entry : squeezedAssociations.entrySet()) {
            DeltaSetTriple<ItemValueWithOrigin<PrismContainerValue<ShadowAssociationType>,PrismContainerDefinition<ShadowAssociationType>>> deltaSetTriple = entry.getValue();
            for (ItemValueWithOrigin<PrismContainerValue<ShadowAssociationType>,PrismContainerDefinition<ShadowAssociationType>> ivwo : deltaSetTriple.getAllValues()) {
                PrismContainerValue<ShadowAssociationType> value = ivwo.getItemValue();
                if (value != null && value.findProperty(ShadowAssociationType.F_NAME) == null) {  // just for safety
                    PrismProperty<QName> nameProperty = value.createProperty(nameDefinition);
                    nameProperty.setRealValue(entry.getKey());
                }
            }
        }
        LOGGER.trace("Names for squeezed associations filled-in.");
    }

    private <T,V extends PrismValue> PropertyDelta<T> consolidateAttribute(RefinedObjectClassDefinition rOcDef,
			ResourceShadowDiscriminator discr, ObjectDelta<ShadowType> existingDelta, LensProjectionContext projCtx,
			boolean addUnchangedValues, boolean completeShadow, QName itemName,
			DeltaSetTriple<ItemValueWithOrigin<PrismPropertyValue<T>,PrismPropertyDefinition<T>>> triple) throws SchemaException, ExpressionEvaluationException, PolicyViolationException {
    	
    	if (triple == null || triple.isEmpty()) {
    		return null;
    	}
    	
    	RefinedAttributeDefinition<T> attributeDefinition = triple.getAnyValue().getConstruction().findAttributeDefinition(itemName);
    	
    	ItemPath itemPath = new ItemPath(ShadowType.F_ATTRIBUTES, itemName);             
       
        if (attributeDefinition.isIgnored(LayerType.MODEL)) {
        	LOGGER.trace("Skipping processing mappings for attribute {} because it is ignored", itemName);
        	return null;
        }

        // when this code is enabled, attempts to modify unmodifiable attributes
        // seemingly succeed (without actually modifying anything)
//        PropertyLimitations limitations = attributeDefinition.getLimitations(LayerType.MODEL);
//        if (limitations != null) {
//        	PropertyAccessType access = limitations.getAccess();
//        	if (access != null) {
//        		if (projCtx.isAdd() && (access.isAdd() == null || !access.isAdd())) {
//        			LOGGER.trace("Skipping processing mappings for attribute {} because it is non-createable", itemName);
//                	return null;
//        		}
//        		if (projCtx.isModify() && (access.isModify() == null || !access.isModify())) {
//        			LOGGER.trace("Skipping processing mappings for attribute {} because it is non-updateable", itemName);
//                	return null;
//        		}
//        	}
//        }
       
        ValueMatcher<T> valueMatcher = ValueMatcher.createMatcher(attributeDefinition, matchingRuleRegistry); 
       
        return (PropertyDelta<T>) consolidateItem(rOcDef, discr, existingDelta, projCtx, addUnchangedValues, completeShadow, 
        		attributeDefinition.isExlusiveStrong(), itemPath, attributeDefinition, triple, valueMatcher, null, "attribute "+itemName);
    }

    private <V extends PrismValue> ContainerDelta<ShadowAssociationType> consolidateAssociation(RefinedObjectClassDefinition rOcDef,
			ResourceShadowDiscriminator discr, ObjectDelta<ShadowType> existingDelta, LensProjectionContext projCtx,
			boolean addUnchangedValues, boolean completeShadow, QName associationName,
			DeltaSetTriple<ItemValueWithOrigin<PrismContainerValue<ShadowAssociationType>,PrismContainerDefinition<ShadowAssociationType>>> triple) throws SchemaException, ExpressionEvaluationException, PolicyViolationException {
    	
    	ItemPath itemPath = new ItemPath(ShadowType.F_ASSOCIATION);
    	PrismContainerDefinition<ShadowAssociationType> asspcContainerDef = getAssociationDefinition();
    	RefinedAssociationDefinition associationDef = rOcDef.findAssociation(associationName);
       
    	Comparator<PrismContainerValue<ShadowAssociationType>> comparator = new Comparator<PrismContainerValue<ShadowAssociationType>>() {
    		
    		@Override
    		public int compare(PrismContainerValue<ShadowAssociationType> o1,
    				PrismContainerValue<ShadowAssociationType> o2) {
    			
    			if (o1 == null && o2 == null){
    				LOGGER.trace("Comparing {} and {}: 0 (A)", o1, o2);
    				return 0;
    			} 
    			
    			if (o1 == null || o2 == null){
    				LOGGER.trace("Comparing {} and {}: 2 (B)", o1, o2);
    				return 1;
    			}
    			
    			PrismReference ref1 = o1.findReference(ShadowAssociationType.F_SHADOW_REF);
    			PrismReference ref2 = o2.findReference(ShadowAssociationType.F_SHADOW_REF);

				// We do not want to compare references in details. Comparing OIDs suffices.
				// Otherwise we get into problems, as one of the references might be e.g. without type,
				// causing unpredictable behavior (MID-2368)
				String oid1 = ref1 != null ? ref1.getOid() : null;
				String oid2 = ref2 != null ? ref2.getOid() : null;
    			if (ObjectUtils.equals(oid1, oid2)) {
    				LOGGER.trace("Comparing {} and {}: 0 (C)", o1, o2);
    				return 0;
    			}
    			
    			LOGGER.trace("Comparing {} and {}: 1 (D)", o1, o2);
    			return 1;
    		}
		};
    	
		ContainerDelta<ShadowAssociationType> delta = (ContainerDelta<ShadowAssociationType>) consolidateItem(rOcDef, discr, existingDelta,
    			projCtx, addUnchangedValues, completeShadow, associationDef.isExclusiveStrong(), itemPath, 
        		asspcContainerDef, triple, null, comparator, "association "+associationName);
    	
    	if (delta != null) {
	    	setAssociationName(delta.getValuesToAdd(), associationName);
	    	setAssociationName(delta.getValuesToDelete(), associationName);
	    	setAssociationName(delta.getValuesToReplace(), associationName);
    	}
    	
    	return delta;
    }

	private void setAssociationName(Collection<PrismContainerValue<ShadowAssociationType>> values, QName itemName) {
		if (values == null) {
			return;
		}
		for (PrismContainerValue<ShadowAssociationType> val: values) {
			val.asContainerable().setName(itemName);
		}
	}

	private PrismContainerDefinition<ShadowAssociationType> getAssociationDefinition() {
		if (associationDefinition == null) {
			associationDefinition = prismContext.getSchemaRegistry().findObjectDefinitionByCompileTimeClass(ShadowType.class)
					.findContainerDefinition(ShadowType.F_ASSOCIATION);
		}
		return associationDefinition;
	}

	private <V extends PrismValue,D extends ItemDefinition> ItemDelta<V,D> consolidateItem(RefinedObjectClassDefinition rOcDef,
			ResourceShadowDiscriminator discr, ObjectDelta<ShadowType> existingDelta, LensProjectionContext projCtx,
			boolean addUnchangedValues, boolean completeShadow, boolean isExclusiveStrong, 
			ItemPath itemPath, D itemDefinition, 
			DeltaSetTriple<ItemValueWithOrigin<V,D>> triple, ValueMatcher<?> valueMatcher, Comparator<V> comparator, String itemDesc) 
					throws SchemaException, ExpressionEvaluationException, PolicyViolationException {

        boolean forceAddUnchangedValues = false;
        ItemDelta<V,D> existingItemDelta = null;
        if (existingDelta != null) {
        	existingItemDelta = existingDelta.findItemDelta(itemPath);
        }
        if (existingItemDelta != null && existingItemDelta.isReplace()) {
        	// We need to add all values if there is replace delta. Otherwise the zero-set values will be
        	// lost
        	forceAddUnchangedValues = true;
        }
        
        LOGGER.trace("CONSOLIDATE {}\n({}) completeAccount={}, addUnchangedValues={}, forceAddUnchangedValues={}",
        		new Object[]{ itemDesc, discr, completeShadow, addUnchangedValues, forceAddUnchangedValues});
        
        // Use this common utility method to do the computation. It does most of the work.
		ItemDelta<V,D> itemDelta = LensUtil.consolidateTripleToDelta(
				itemPath, (DeltaSetTriple)triple, itemDefinition, existingItemDelta, projCtx.getObjectNew(), 
				valueMatcher, comparator, addUnchangedValues || forceAddUnchangedValues, completeShadow, isExclusiveStrong,
				discr.toHumanReadableString(), completeShadow);
		
		if (LOGGER.isTraceEnabled()) {
			LOGGER.trace("Consolidated delta (before sync filter) for {}:\n{}",discr,itemDelta==null?"null":itemDelta.debugDump());
		}
        
		if (existingItemDelta != null && existingItemDelta.isReplace()) {
			// We cannot filter out any values if there is an replace delta. The replace delta cleans all previous
			// state and all the values needs to be passed on
			LOGGER.trace("Skipping consolidation with sync delta as there was a replace delta on top of that already");
		} else {
			// Also consider a synchronization delta (if it is present). This may filter out some deltas.
			itemDelta = consolidateItemWithSync(projCtx, itemDelta, valueMatcher);
            if (LOGGER.isTraceEnabled()) {
				LOGGER.trace("Consolidated delta (after sync filter) for {}:\n{}",discr,itemDelta==null?"null":itemDelta.debugDump());
			}
		}

        if (itemDelta != null && !itemDelta.isEmpty()) {
        	if (existingItemDelta == null || !existingItemDelta.isReplace()) {
        		// We cannot simplify if there is already a replace delta. This might result in
        		// two replace deltas and therefore some information may be lost
        		itemDelta.simplify();
        	}
        	
        	// Validate the delta. i.e. make sure it conforms to schema (that it does not have more values than allowed, etc.)
        	if (existingItemDelta != null) {
        		// Let's make sure that both the previous delta and this delta makes sense
        		ItemDelta<V,D> mergedDelta = existingItemDelta.clone();
        		mergedDelta.merge(itemDelta);
        		mergedDelta.validate();
        	} else {
        		itemDelta.validate();            		
        	}
        	
        	LensUtil.setDeltaOldValue(projCtx, itemDelta);
        	
        	return itemDelta;
        }
		
        return null;
	}

	private <V extends PrismValue,D extends ItemDefinition> boolean hasActiveWeakMapping(
			Map<QName, DeltaSetTriple<ItemValueWithOrigin<V,D>>> squeezedAttributes, LensProjectionContext accCtx) throws SchemaException {
		for (Map.Entry<QName, DeltaSetTriple<ItemValueWithOrigin<V,D>>> entry : squeezedAttributes.entrySet()) {
			DeltaSetTriple<ItemValueWithOrigin<V,D>> ivwoTriple = entry.getValue();
			boolean hasWeak = false;
			for (ItemValueWithOrigin<V,D> ivwo: ivwoTriple.getAllValues()) {
				PrismValueDeltaSetTripleProducer<V,D> mapping = ivwo.getMapping();
				if (mapping.getStrength() == MappingStrengthType.WEAK) {
					// We only care about mappings that change something. If the weak mapping is not
					// changing anything then it will not be applied in this step anyway. Therefore
					// there is no point in loading the real values just because there is such mapping.
					// Note: we can be sure that we are NOT doing reconciliation. If we do reconciliation
					// then we cannot get here in the first place (the projection is already loaded).
					PrismValueDeltaSetTriple<?> outputTriple = mapping.getOutputTriple();
					if (outputTriple != null && !outputTriple.isEmpty() && !outputTriple.isZeroOnly()) {
						return true;
					}
					hasWeak = true;
				}
			}
			if (hasWeak) {
				// If we have a weak mapping for this attribute and there is also any
				// other mapping with a minus set then we need to get the real current value.
				// The minus value may cause that the result of consolidation is empty value.
				// In that case we should apply the weak mapping. But we will not know this
				// unless we fetch the real values.
				if (ivwoTriple.hasMinusSet()) {
					for (ItemValueWithOrigin<V,D> ivwo: ivwoTriple.getMinusSet()) {
						PrismValueDeltaSetTripleProducer<V, D> mapping = ivwo.getMapping();
						PrismValueDeltaSetTriple<?> outputTriple = mapping.getOutputTriple();
						if (outputTriple != null && !outputTriple.isEmpty()) {
							return true;
						}
					}
				}
				for (ItemValueWithOrigin<V,D> ivwo: ivwoTriple.getNonNegativeValues()) {
					PrismValueDeltaSetTripleProducer<V, D> mapping = ivwo.getMapping();
					PrismValueDeltaSetTriple<?> outputTriple = mapping.getOutputTriple();
					if (outputTriple != null && outputTriple.hasMinusSet()) {
						return true;
					}
				}
				ObjectDelta<ShadowType> projectionDelta = accCtx.getDelta();
				if (projectionDelta != null) {
					PropertyDelta<?> aPrioriAttributeDelta = projectionDelta.findPropertyDelta(new ItemPath(ShadowType.F_ATTRIBUTES, entry.getKey()));
					if (aPrioriAttributeDelta != null && aPrioriAttributeDelta.isDelete()) {
						return true;
					}
					if (aPrioriAttributeDelta != null && aPrioriAttributeDelta.isReplace() && aPrioriAttributeDelta.getValuesToReplace().isEmpty()) {
						return true;
					}
				}
			}
		}
		return false;
	}
	
	private <V extends PrismValue,D extends ItemDefinition> boolean hasActiveStrongMapping(
			Map<QName, DeltaSetTriple<ItemValueWithOrigin<V,D>>> squeezedAttributes, LensProjectionContext accCtx) throws SchemaException {
		for (Map.Entry<QName, DeltaSetTriple<ItemValueWithOrigin<V,D>>> entry : squeezedAttributes.entrySet()) {
			DeltaSetTriple<ItemValueWithOrigin<V,D>> ivwoTriple = entry.getValue();
			for (ItemValueWithOrigin<V,D> ivwo: ivwoTriple.getAllValues()) {
				PrismValueDeltaSetTripleProducer<V,D> mapping = ivwo.getMapping();
				if (mapping.getStrength() == MappingStrengthType.STRONG) {
					// Do not optimize for "nothing changed" case here. We want to make
					// sure that the values of strong mappings are applied even if nothing
					// has changed.
					return true;
				}
			}
		}
		return false;
	}

    private <F extends FocusType> void consolidateValuesModifyProjection(LensContext<F> context, 
    		LensProjectionContext accCtx, OperationResult result) 
    				throws SchemaException, ExpressionEvaluationException, ObjectNotFoundException, 
    				CommunicationException, ConfigurationException, SecurityViolationException, PolicyViolationException {

        boolean addUnchangedValues = false;
        if (accCtx.getSynchronizationPolicyDecision() == SynchronizationPolicyDecision.ADD) {
        	addUnchangedValues = true;
        }
        
		ObjectDelta<ShadowType> modifyDelta = consolidateValuesToModifyDelta(context, accCtx, addUnchangedValues, result);
        if (modifyDelta == null || modifyDelta.isEmpty()) {
        	return;
        }
        ObjectDelta<ShadowType> accountSecondaryDelta = accCtx.getSecondaryDelta();
        if (accountSecondaryDelta != null) {
            accountSecondaryDelta.merge(modifyDelta);
        } else {
            accCtx.setSecondaryDelta(modifyDelta);
        }
    }

    private <V extends PrismValue,D extends ItemDefinition> ItemDelta<V,D> consolidateItemWithSync(LensProjectionContext accCtx, ItemDelta<V,D> delta, 
    		ValueMatcher<?> valueMatcher) {
    	if (delta == null) {
            return null;
        }
    	if (delta instanceof PropertyDelta<?>) {
    		return (ItemDelta<V,D>) consolidatePropertyWithSync(accCtx, (PropertyDelta) delta, valueMatcher);
    	}
    	return delta;
    }
    
    /**
     * This method checks {@link com.evolveum.midpoint.prism.delta.PropertyDelta} created during consolidation with
     * account sync deltas. If changes from property delta are in account sync deltas than they must be removed,
     * because they already had been applied (they came from sync, already happened).
     *
     * @param accCtx current account sync context
     * @param delta  new delta created during consolidation process
     * @return method return updated delta, or null if delta was empty after filtering (removing unnecessary values).
     */
    private <T> PropertyDelta<T> consolidatePropertyWithSync(LensProjectionContext accCtx, PropertyDelta<T> delta, 
    		ValueMatcher<T> valueMatcher) {
        if (delta == null) {
            return null;
        }

        ObjectDelta<ShadowType> syncDelta = accCtx.getSyncDelta();
        if (syncDelta == null) {
            return consolidateWithSyncAbsolute(accCtx, delta, valueMatcher);
        }

        PropertyDelta<T> alreadyDoneDelta = syncDelta.findPropertyDelta(delta.getPath());
        if (alreadyDoneDelta == null) {
            return delta;
        }

        cleanupValues(delta.getValuesToAdd(), alreadyDoneDelta, valueMatcher);
        cleanupValues(delta.getValuesToDelete(), alreadyDoneDelta, valueMatcher);

        if (delta.getValues(Object.class).isEmpty()) {
            return null;
        }

        return delta;
    }

    /**
     * This method consolidate property delta against account absolute state which came from sync (not as delta)
     *
     * @param accCtx
     * @param delta
     * @return method return updated delta, or null if delta was empty after filtering (removing unnecessary values).
     */
    private <T> PropertyDelta<T> consolidateWithSyncAbsolute(LensProjectionContext accCtx, PropertyDelta<T> delta,
    		ValueMatcher<T> valueMatcher) {
        if (delta == null || accCtx.getObjectCurrent() == null) {
            return delta;
        }

        PrismObject<ShadowType> absoluteAccountState = accCtx.getObjectCurrent();
        PrismProperty<T> absoluteProperty = absoluteAccountState.findProperty(delta.getPath());
        if (absoluteProperty == null) {
            return delta;
        }

        cleanupAbsoluteValues(delta.getValuesToAdd(), true, absoluteProperty, valueMatcher);
        cleanupAbsoluteValues(delta.getValuesToDelete(), false, absoluteProperty, valueMatcher);

        if (delta.getValues(Object.class).isEmpty()) {
            return null;
        }

        return delta;
    }

    /**
     * Method removes values from property delta values list (first parameter).
     *
     * @param values   collection with {@link PrismPropertyValue} objects to add or delete (from {@link PropertyDelta}
     * @param adding   if true we removing {@link PrismPropertyValue} from {@link Collection} values parameter if they
     *                 already are in {@link PrismProperty} parameter. Otherwise we're removing {@link PrismPropertyValue}
     *                 from {@link Collection} values parameter if they already are not in {@link PrismProperty} parameter.
     * @param property property with absolute state
     */
    private <T> void cleanupAbsoluteValues(Collection<PrismPropertyValue<T>> values, boolean adding, PrismProperty<T> property,
    		ValueMatcher<T> valueMatcher) {
        if (values == null) {
            return;
        }

        Iterator<PrismPropertyValue<T>> iterator = values.iterator();
        while (iterator.hasNext()) {
            PrismPropertyValue<T> value = iterator.next();
            if (adding && valueMatcher.hasRealValue(property,value)) {
                iterator.remove();
            }

            if (!adding && !valueMatcher.hasRealValue(property,value)) {
                iterator.remove();
            }
        }
    }

    /**
     * Simple util method which checks property values against already done delta from sync. See method
     * {@link ConsolidationProcessor#consolidateWithSync(com.evolveum.midpoint.model.AccountSyncContext,
     * com.evolveum.midpoint.prism.delta.PropertyDelta)}.
     *
     * @param values           collection which has to be filtered
     * @param alreadyDoneDelta already applied delta from sync
     */
    private <T> void cleanupValues(Collection<PrismPropertyValue<T>> values, PropertyDelta<T> alreadyDoneDelta,
    		ValueMatcher<T> valueMatcher) {
        if (values == null) {
            return;
        }

        Iterator<PrismPropertyValue<T>> iterator = values.iterator();
        while (iterator.hasNext()) {
            PrismPropertyValue<T> valueToAdd = iterator.next();
            if (valueMatcher.isRealValueToAdd(alreadyDoneDelta, valueToAdd)) {
                iterator.remove();
            }
        }
    }

	private <V extends PrismValue, D extends ItemDefinition, F extends FocusType> Map<QName, DeltaSetTriple<ItemValueWithOrigin<V,D>>> sqeeze(
			LensProjectionContext accCtx, MappingExtractor<V,D,F> extractor) {
		Map<QName, DeltaSetTriple<ItemValueWithOrigin<V,D>>> squeezedMap 
						= new HashMap<QName, DeltaSetTriple<ItemValueWithOrigin<V,D>>>();
		if (accCtx.getConstructionDeltaSetTriple() != null) {
			sqeezeAttributesFromConstructionTriple(squeezedMap, (PrismValueDeltaSetTriple)accCtx.getConstructionDeltaSetTriple(), extractor);		
		}
		if (accCtx.getOutboundConstruction() != null) {
			// The plus-minus-zero status of outbound account construction is determined by the type of account delta
			if (accCtx.isAdd()) {
				sqeezeAttributesFromConstructionNonminusToPlus(squeezedMap, accCtx.getOutboundConstruction(), extractor);
			} else if (accCtx.isDelete()) {
				sqeezeAttributesFromConstructionNonminusToMinus(squeezedMap, accCtx.getOutboundConstruction(), extractor);
			} else {
				sqeezeAttributesFromConstruction(squeezedMap, accCtx.getOutboundConstruction(), extractor);
			}
		}
		return squeezedMap;
	}

	private <V extends PrismValue, D extends ItemDefinition, F extends FocusType> void sqeezeAttributesFromConstructionTriple(
			Map<QName, DeltaSetTriple<ItemValueWithOrigin<V,D>>> squeezedMap,
			PrismValueDeltaSetTriple<PrismPropertyValue<Construction<F>>> constructionDeltaSetTriple, MappingExtractor<V,D,F> extractor) {
		// Zero account constructions go normally, plus to plus, minus to minus
		sqeezeAttributesFromAccountConstructionSet(squeezedMap, constructionDeltaSetTriple.getZeroSet(), extractor);
		// Plus accounts: zero and plus values go to plus
		sqeezeAttributesFromAccountConstructionSetNonminusToPlus(squeezedMap, constructionDeltaSetTriple.getPlusSet(), extractor);
		// Minus accounts: zero and plus values go to minus
		sqeezeAttributesFromConstructionSetNonminusToMinus(squeezedMap, constructionDeltaSetTriple.getMinusSet(), extractor);
	}

	private <V extends PrismValue, D extends ItemDefinition, F extends FocusType> void sqeezeAttributesFromAccountConstructionSet(
			Map<QName, DeltaSetTriple<ItemValueWithOrigin<V,D>>> squeezedMap,
			Collection<PrismPropertyValue<Construction<F>>> constructionSet, MappingExtractor<V,D,F> extractor) {
		if (constructionSet == null) {
			return;
		}
		for (PrismPropertyValue<Construction<F>> construction: constructionSet) {
			sqeezeAttributesFromConstruction(squeezedMap, construction.getValue(), extractor);
		}
	}
	
	private <V extends PrismValue, D extends ItemDefinition, F extends FocusType> void sqeezeAttributesFromAccountConstructionSetNonminusToPlus(
			Map<QName, DeltaSetTriple<ItemValueWithOrigin<V,D>>> squeezedMap,
			Collection<PrismPropertyValue<Construction<F>>> constructionSet, MappingExtractor<V,D,F> extractor) {
		if (constructionSet == null) {
			return;
		}
		for (PrismPropertyValue<Construction<F>> construction: constructionSet) {
			sqeezeAttributesFromConstructionNonminusToPlus(squeezedMap, construction.getValue(), extractor);
		}
	}
	
	private <V extends PrismValue, D extends ItemDefinition, F extends FocusType> void sqeezeAttributesFromConstructionSetNonminusToMinus(
			Map<QName, DeltaSetTriple<ItemValueWithOrigin<V,D>>> squeezedMap,
			Collection<PrismPropertyValue<Construction<F>>> constructionSet, MappingExtractor<V,D,F> extractor) {
		if (constructionSet == null) {
			return;
		}
		for (PrismPropertyValue<Construction<F>> construction: constructionSet) {
			sqeezeAttributesFromConstructionNonminusToMinus(squeezedMap, construction.getValue(), extractor);
		}
	}

	private <V extends PrismValue, D extends ItemDefinition, F extends FocusType> void sqeezeAttributesFromConstruction(
			Map<QName, DeltaSetTriple<ItemValueWithOrigin<V,D>>> squeezedMap,
			Construction<F> construction, MappingExtractor<V,D,F> extractor) {
		for (PrismValueDeltaSetTripleProducer<V, D> mapping: extractor.getMappings(construction)) {
			PrismValueDeltaSetTriple<V> vcTriple = mapping.getOutputTriple();
			if (vcTriple == null) {
				continue;
			}
			QName name = mapping.getMappingQName();
			DeltaSetTriple<ItemValueWithOrigin<V,D>> squeezeTriple = getSqueezeMapTriple(squeezedMap, name);
			convertSqueezeSet(vcTriple.getZeroSet(), squeezeTriple.getZeroSet(), mapping, construction);
			convertSqueezeSet(vcTriple.getPlusSet(), squeezeTriple.getPlusSet(), mapping, construction);
			convertSqueezeSet(vcTriple.getMinusSet(), squeezeTriple.getMinusSet(), mapping, construction);
		}
	}
	
	private <V extends PrismValue, D extends ItemDefinition, F extends FocusType> void sqeezeAttributesFromConstructionNonminusToPlus(
			Map<QName, DeltaSetTriple<ItemValueWithOrigin<V,D>>> squeezedMap,
			Construction<F> construction, MappingExtractor<V,D,F> extractor) {
		for (PrismValueDeltaSetTripleProducer<V, D> mapping: extractor.getMappings(construction)) {
			PrismValueDeltaSetTriple<V> vcTriple = mapping.getOutputTriple();
			if (vcTriple == null) {
				continue;
			}
			QName name = mapping.getMappingQName();
			DeltaSetTriple<ItemValueWithOrigin<V,D>> squeezeTriple = getSqueezeMapTriple(squeezedMap, name);
			convertSqueezeSet(vcTriple.getZeroSet(), squeezeTriple.getPlusSet(), mapping, construction);
			convertSqueezeSet(vcTriple.getPlusSet(), squeezeTriple.getPlusSet(), mapping, construction);
			// Ignore minus set
		}
	}

	private <V extends PrismValue, D extends ItemDefinition, F extends FocusType> void sqeezeAttributesFromConstructionNonminusToMinus(
			Map<QName, DeltaSetTriple<ItemValueWithOrigin<V,D>>> squeezedMap,
			Construction<F> construction, MappingExtractor<V,D,F> extractor) {
		for (PrismValueDeltaSetTripleProducer<V, D> mapping: extractor.getMappings(construction)) {
			PrismValueDeltaSetTriple<V> vcTriple = mapping.getOutputTriple();
			if (vcTriple == null) {
				continue;
			}
			QName name = mapping.getMappingQName();
			DeltaSetTriple<ItemValueWithOrigin<V,D>> squeezeTriple 
													= getSqueezeMapTriple(squeezedMap, name);
			convertSqueezeSet(vcTriple.getZeroSet(), squeezeTriple.getMinusSet(), mapping, construction);
			convertSqueezeSet(vcTriple.getPlusSet(), squeezeTriple.getMinusSet(), mapping, construction);
		}
	}

	private <V extends PrismValue, D extends ItemDefinition, F extends FocusType> void convertSqueezeSet(Collection<V> fromSet,
			Collection<ItemValueWithOrigin<V,D>> toSet,
			PrismValueDeltaSetTripleProducer<V, D> mapping, Construction<F> construction) {
		if (fromSet != null) {
			for (V from: fromSet) {
				ItemValueWithOrigin<V,D> pvwo = new ItemValueWithOrigin<V,D>(from, mapping, construction);
				toSet.add(pvwo);
			}
		}
	}

	private <V extends PrismValue, D extends ItemDefinition, F extends FocusType> DeltaSetTriple<ItemValueWithOrigin<V,D>> getSqueezeMapTriple(
			Map<QName, DeltaSetTriple<ItemValueWithOrigin<V,D>>> squeezedMap, QName itemName) {
		DeltaSetTriple<ItemValueWithOrigin<V,D>> triple = squeezedMap.get(itemName);
		if (triple == null) {
			triple = new DeltaSetTriple<ItemValueWithOrigin<V,D>>();
			squeezedMap.put(itemName, triple);
		}
		return triple;
	}
	
}
