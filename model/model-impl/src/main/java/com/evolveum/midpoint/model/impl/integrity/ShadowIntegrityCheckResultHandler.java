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

package com.evolveum.midpoint.model.impl.integrity;

import com.evolveum.midpoint.common.refinery.RefinedAttributeDefinition;
import com.evolveum.midpoint.common.refinery.RefinedResourceSchema;
import com.evolveum.midpoint.model.impl.sync.SynchronizationService;
import com.evolveum.midpoint.model.impl.util.AbstractSearchIterativeResultHandler;
import com.evolveum.midpoint.model.impl.util.Utils;
import com.evolveum.midpoint.prism.Item;
import com.evolveum.midpoint.prism.PrismContainer;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.PrismProperty;
import com.evolveum.midpoint.prism.PrismPropertyValue;
import com.evolveum.midpoint.prism.PrismReferenceValue;
import com.evolveum.midpoint.prism.delta.ItemDelta;
import com.evolveum.midpoint.prism.delta.PropertyDelta;
import com.evolveum.midpoint.prism.delta.ReferenceDelta;
import com.evolveum.midpoint.prism.match.MatchingRule;
import com.evolveum.midpoint.prism.match.MatchingRuleRegistry;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.prism.query.RefFilter;
import com.evolveum.midpoint.provisioning.api.ProvisioningService;
import com.evolveum.midpoint.repo.api.RepositoryService;
import com.evolveum.midpoint.schema.GetOperationOptions;
import com.evolveum.midpoint.schema.SelectorOptions;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.ObjectTypeUtil;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.task.api.TaskManager;
import com.evolveum.midpoint.util.DebugUtil;
import com.evolveum.midpoint.util.exception.CommonException;
import com.evolveum.midpoint.util.exception.CommunicationException;
import com.evolveum.midpoint.util.exception.ConfigurationException;
import com.evolveum.midpoint.util.exception.ExpressionEvaluationException;
import com.evolveum.midpoint.util.exception.ObjectAlreadyExistsException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SecurityViolationException;
import com.evolveum.midpoint.util.exception.SystemException;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.FocusType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.LayerType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectReferenceType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectSynchronizationType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ResourceType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowAttributesType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowKindType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.SynchronizationSituationType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.SystemConfigurationType;
import com.sun.jndi.toolkit.dir.SearchFilter;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author mederly
 */
public class ShadowIntegrityCheckResultHandler extends AbstractSearchIterativeResultHandler<ShadowType> {

    static final Trace LOGGER = TraceManager.getTrace(ShadowIntegrityCheckResultHandler.class);

    private static final String CLASS_DOT = ShadowIntegrityCheckResultHandler.class.getName() + ".";
    private static final String DEFAULT_DUPLICATE_SHADOWS_RESOLVER_CLASS_NAME = DefaultDuplicateShadowsResolver.class.getName();
    public static final String KEY_EXISTS_ON_RESOURCE = CLASS_DOT + "existsOnResource";
    public static final String KEY_OWNERS = CLASS_DOT + "owners";

    private PrismContext prismContext;
    private ProvisioningService provisioningService;
    private MatchingRuleRegistry matchingRuleRegistry;
    private RepositoryService repositoryService;
    private SynchronizationService synchronizationService;

    // derived from task extension diagnose/fix values at instantiation
    private boolean checkIntents;
    private boolean checkUniqueness;
    private boolean checkNormalization;
    private boolean checkFetch;
    private boolean checkOwners;
    private boolean fixIntents;
    private boolean fixUniqueness;
    private boolean fixNormalization;

    private boolean dryRun;

    public static final String INTENTS = "intents";
    public static final String UNIQUENESS = "uniqueness";
    public static final String NORMALIZATION = "normalization";
    public static final String OWNERS = "owners";
    public static final String FETCH = "fetch";
    public static final List<String> KNOWN_KEYS = Arrays.asList(INTENTS, UNIQUENESS, NORMALIZATION, OWNERS, FETCH);

    // resource oid + kind -> ROCD
    // we silently assume that all intents for a given kind share a common attribute definition
    private Map<Pair<String,ShadowKindType>, ObjectTypeContext> contextMap = new HashMap<>();

    private Map<String,PrismObject<ResourceType>> resources = new HashMap<>();

    private PrismObject<SystemConfigurationType> configuration;

    private Statistics statistics = new Statistics();

    DuplicateShadowsResolver duplicateShadowsResolver;
    private Set<String> duplicateShadowsDetected = new HashSet<>();
    private Set<String> duplicateShadowsDeleted = new HashSet<>();

    public ShadowIntegrityCheckResultHandler(Task coordinatorTask, String taskOperationPrefix, String processShortName,
                                             String contextDesc, TaskManager taskManager, PrismContext prismContext,
                                             ProvisioningService provisioningService, MatchingRuleRegistry matchingRuleRegistry,
                                             RepositoryService repositoryService, SynchronizationService synchronizationService,
                                             OperationResult result) {
        super(coordinatorTask, taskOperationPrefix, processShortName, contextDesc, taskManager);
        this.prismContext = prismContext;
        this.provisioningService = provisioningService;
        this.matchingRuleRegistry = matchingRuleRegistry;
        this.repositoryService = repositoryService;
        this.synchronizationService = synchronizationService;
        setStopOnError(false);
        setLogErrors(false);            // we do log errors ourselves

        Integer tasks = getWorkerThreadsCount(coordinatorTask);
        if (tasks != null && tasks != 0) {
            throw new UnsupportedOperationException("Unsupported number of worker threads: " + tasks + ". This task cannot be run with worker threads. Please remove workerThreads extension property or set its value to 0.");
        }

        PrismProperty<String> diagnosePrismProperty = coordinatorTask.getExtensionProperty(SchemaConstants.MODEL_EXTENSION_DIAGNOSE);
        if (diagnosePrismProperty == null || diagnosePrismProperty.isEmpty()) {
            checkIntents = true;
            checkUniqueness = true;
            checkNormalization = true;
            checkOwners = true;
            checkFetch = false;
        } else {
            checkIntents = contains(diagnosePrismProperty, INTENTS);
            checkUniqueness = contains(diagnosePrismProperty, UNIQUENESS);
            checkNormalization = contains(diagnosePrismProperty, NORMALIZATION);
            checkOwners = contains(diagnosePrismProperty, OWNERS);
            checkFetch = contains(diagnosePrismProperty, FETCH);
            checkProperty(diagnosePrismProperty);
        }
        PrismProperty<String> fixPrismProperty = coordinatorTask.getExtensionProperty(SchemaConstants.MODEL_EXTENSION_FIX);
        if (fixPrismProperty == null || fixPrismProperty.isEmpty()) {
            fixIntents = false;
            fixUniqueness = false;
            fixNormalization = false;
        } else {
            fixIntents = contains(fixPrismProperty, INTENTS);
            fixUniqueness = contains(fixPrismProperty, UNIQUENESS);
            fixNormalization = contains(fixPrismProperty, NORMALIZATION);
            checkProperty(fixPrismProperty);
        }

        if (fixIntents) {
            checkIntents = true;
        }
        if (fixUniqueness) {
            checkUniqueness = true;
        }
        if (fixNormalization) {
            checkNormalization = true;
        }

        if (fixUniqueness) {
            PrismProperty<String> duplicateShadowsResolverClass = coordinatorTask.getExtensionProperty(SchemaConstants.MODEL_EXTENSION_DUPLICATE_SHADOWS_RESOLVER);
            String duplicateShadowsResolverClassName;
            if (duplicateShadowsResolverClass != null) {
                duplicateShadowsResolverClassName = duplicateShadowsResolverClass.getRealValue();
            } else {
                duplicateShadowsResolverClassName = DEFAULT_DUPLICATE_SHADOWS_RESOLVER_CLASS_NAME;
            }
            try {
                duplicateShadowsResolver = (DuplicateShadowsResolver) Class.forName(duplicateShadowsResolverClassName).newInstance();
            } catch (InstantiationException | IllegalAccessException | ClassNotFoundException | ClassCastException e) {
                throw new SystemException("Couldn't instantiate duplicate shadows resolver " + duplicateShadowsResolverClassName);
            }
        }

        try {
            configuration = Utils.getSystemConfiguration(repositoryService, result);
        } catch (SchemaException e) {
            throw new SystemException("Couldn't get system configuration", e);
        }

        try {
            dryRun = Utils.isDryRun(coordinatorTask);
        } catch (SchemaException e) {
            throw new SystemException("Couldn't get dryRun flag from task " + coordinatorTask);
        }

        logConfiguration("Shadow integrity check is starting with the configuration:");
    }

    protected void logConfiguration(String state) {
        LOGGER.info("{}\n" +
                        "- normalization  diagnose={},\tfix={}\n" +
                        "- uniqueness     diagnose={},\tfix={}\n" +
                        "- intents        diagnose={},\tfix={}\n" +
                        "- owners         diagnose={}\n" +
                        "- fetch          diagnose={}\n\n" +
                        "dryRun = {}\n",
                state,
                checkNormalization, fixNormalization,
                checkUniqueness, fixUniqueness,
                checkIntents, fixIntents,
                checkOwners,
                checkFetch,
                dryRun);
    }

    private void checkProperty(PrismProperty<String> property) {
        for (PrismPropertyValue<String> value : property.getValues()) {
            if (!KNOWN_KEYS.contains(value.getValue())) {
                throw new IllegalArgumentException("Unknown diagnose/fix keyword: " + value.getValue() + ". Known keys are: " + KNOWN_KEYS);
            }
        }
    }

    private boolean contains(PrismProperty<String> property, String keyword) {
        return property.containsRealValue(new PrismPropertyValue<String>(keyword));
    }

    @Override
    protected boolean handleObject(PrismObject<ShadowType> shadow, Task workerTask, OperationResult parentResult) throws CommonException {
        OperationResult result = parentResult.createMinorSubresult(CLASS_DOT + "handleObject");
        ShadowCheckResult checkResult = new ShadowCheckResult(shadow);
        try {
            checkShadow(checkResult, shadow, workerTask, result);
            for (Exception e : checkResult.getErrors()) {
                result.createSubresult(CLASS_DOT + "handleObject.result").recordPartialError(e.getMessage(), e);
            }
            for (String message : checkResult.getWarnings()) {
                result.createSubresult(CLASS_DOT + "handleObject.result").recordWarning(message);
            }
            if (!checkResult.getErrors().isEmpty()) {
                statistics.incrementShadowsWithErrors();
            } else if (!checkResult.getWarnings().isEmpty()) {
                statistics.incrementShadowsWithWarnings();
            }

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Checking shadow {} (resource {}) finished - errors: {}, warnings: {}",
                        ObjectTypeUtil.toShortString(checkResult.getShadow()),
                        ObjectTypeUtil.toShortString(checkResult.getResource()),
                        checkResult.getErrors().size(), checkResult.getWarnings().size());
            }
        } catch (RuntimeException e) {
            LoggingUtils.logUnexpectedException(LOGGER, "Unexpected error while checking shadow {} integrity", e, ObjectTypeUtil.toShortString(shadow));
            result.recordPartialError("Unexpected error while checking shadow integrity", e);
            statistics.incrementShadowsWithErrors();
        }

        statistics.registerProblemCodeOccurrences(checkResult.getProblemCodes());
        if (checkResult.isFixApplied()) {
            statistics.registerProblemsFixes(checkResult.getFixForProblems());
        }

        result.computeStatusIfUnknown();
        return true;
    }

    private void checkShadow(ShadowCheckResult checkResult, PrismObject<ShadowType> shadow, Task workerTask, OperationResult result) throws SchemaException {
        ShadowType shadowType = shadow.asObjectable();
        ObjectReferenceType resourceRef = shadowType.getResourceRef();
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Checking shadow {} (resource {})", ObjectTypeUtil.toShortString(shadowType), resourceRef!=null?resourceRef.getOid():"(null)");
        }
        statistics.incrementShadows();

        if (resourceRef == null) {
            checkResult.recordError(Statistics.NO_RESOURCE_OID, new SchemaException("No resourceRef"));
            return;
        }
        String resourceOid = resourceRef.getOid();
        if (resourceOid == null) {
            checkResult.recordError(Statistics.NO_RESOURCE_OID, new SchemaException("Null resource OID"));
            return;
        }
        PrismObject<ResourceType> resource = resources.get(resourceOid);
        if (resource == null) {
            statistics.incrementResources();
            try {
                resource = provisioningService.getObject(ResourceType.class, resourceOid, null, workerTask, result);
            } catch (ObjectNotFoundException e) {
                checkResult.recordError(Statistics.CANNOT_GET_RESOURCE, new ObjectNotFoundException("Resource object does not exist: " + e.getMessage(), e));
                return;
            } catch (SchemaException e) {
                checkResult.recordError(Statistics.CANNOT_GET_RESOURCE, new ObjectNotFoundException("Resource object has schema problems: " + e.getMessage(), e));
                return;
            } catch (CommonException|RuntimeException e) {
                checkResult.recordError(Statistics.CANNOT_GET_RESOURCE, new ObjectNotFoundException("Resource object cannot be fetched for some reason: " + e.getMessage(), e));
                return;
            }
            resources.put(resourceOid, resource);
        }
        checkResult.setResource(resource);

        ShadowKindType kind = shadowType.getKind();
        if (kind == null) {
            // TODO or simply assume account?
            checkResult.recordError(Statistics.NO_KIND_SPECIFIED, new SchemaException("No kind specified"));
            return;
        }

        PrismObject<ShadowType> fetchedShadow = null;
        if (checkFetch) {
            fetchedShadow = fetchShadow(checkResult, shadow, resource, workerTask, result);
            if (fetchedShadow != null) {
                shadow.setUserData(KEY_EXISTS_ON_RESOURCE, "true");
            }
        }

        if (checkOwners) {
            List<PrismObject> owners = searchOwners(shadow, result);
            if (owners != null) {
                shadow.setUserData(KEY_OWNERS, owners);
                if (owners.size() > 1) {
                    checkResult.recordError(Statistics.MULTIPLE_OWNERS, new SchemaException("Multiple owners: " + owners));
                }
            }

            if (shadowType.getSynchronizationSituation() == SynchronizationSituationType.LINKED && (owners == null || owners.isEmpty())) {
                checkResult.recordError(Statistics.LINKED_WITH_NO_OWNER, new SchemaException("Linked shadow with no owner"));
            }
            if (shadowType.getSynchronizationSituation() != SynchronizationSituationType.LINKED && owners != null && !owners.isEmpty()) {
                checkResult.recordError(Statistics.NOT_LINKED_WITH_OWNER, new SchemaException("Shadow with an owner but not marked as linked (marked as "
                    + shadowType.getSynchronizationSituation() + ")"));
            }
        }

        String intent = shadowType.getIntent();
        if (checkIntents && (intent == null || intent.isEmpty())) {
            checkResult.recordWarning(Statistics.NO_INTENT_SPECIFIED, "None or empty intent");
        }
        if (fixIntents && (intent == null || intent.isEmpty())) {
            doFixIntent(checkResult, fetchedShadow, shadow, resource, workerTask, result);
        }

        Pair<String,ShadowKindType> key = new ImmutablePair<>(resourceOid, kind);
        ObjectTypeContext context = contextMap.get(key);
        if (context == null) {
            context = new ObjectTypeContext();
            context.setResource(resource);
            RefinedResourceSchema resourceSchema;
            try {
                resourceSchema = RefinedResourceSchema.getRefinedSchema(context.getResource(), LayerType.MODEL, prismContext);
            } catch (SchemaException e) {
                checkResult.recordError(Statistics.CANNOT_GET_REFINED_SCHEMA, new SchemaException("Couldn't derive resource schema: " + e.getMessage(), e));
                return;
            }
            if (resourceSchema == null) {
                checkResult.recordError(Statistics.NO_RESOURCE_REFINED_SCHEMA, new SchemaException("No resource schema"));
                return;
            }
            context.setObjectClassDefinition(resourceSchema.getRefinedDefinition(kind, shadowType));
            if (context.getObjectClassDefinition() == null) {
                // TODO or warning only?
                checkResult.recordError(Statistics.NO_OBJECT_CLASS_REFINED_SCHEMA, new SchemaException("No refined object class definition for kind=" + kind + ", intent=" + intent));
                return;
            }
            contextMap.put(key, context);
        }

        try {
            provisioningService.applyDefinition(shadow, result);
        } catch (SchemaException|ObjectNotFoundException|CommunicationException|ConfigurationException e) {
            checkResult.recordError(Statistics.OTHER_FAILURE, new SystemException("Couldn't apply definition to shadow from repo", e));
            return;
        }

        Set<RefinedAttributeDefinition<?>> identifiers = new HashSet<>();
        identifiers.addAll(context.getObjectClassDefinition().getIdentifiers());
        identifiers.addAll(context.getObjectClassDefinition().getSecondaryIdentifiers());

        PrismContainer<ShadowAttributesType> attributesContainer = shadow.findContainer(ShadowType.F_ATTRIBUTES);
        if (attributesContainer == null) {
            // might happen on unfinished shadows?
            checkResult.recordError(Statistics.OTHER_FAILURE, new SchemaException("No attributes container"));
            return;
        }

        for (RefinedAttributeDefinition<?> identifier : identifiers) {
            PrismProperty property = attributesContainer.getValue().findProperty(identifier.getName());
            if (property == null || property.size() == 0) {
                checkResult.recordWarning(Statistics.OTHER_FAILURE, "No value for identifier " + identifier.getName());
                continue;
            }
            if (property.size() > 1) {
                // we don't expect multi-valued identifiers
                checkResult.recordError(Statistics.OTHER_FAILURE, new SchemaException("Multi-valued identifier " + identifier.getName() + " with values " + property.getValues()));
                continue;
            }
            // size == 1
            String value = (String) property.getValue().getValue();
            if (value == null) {
                checkResult.recordWarning(Statistics.OTHER_FAILURE, "Null value for identifier " + identifier.getName());
                continue;
            }
            if (checkUniqueness) {
                addIdentifierValue(checkResult, context, identifier.getName(), value, shadow);
            }
            if (checkNormalization) {
                doCheckNormalization(checkResult, identifier, value, context);
            }
        }

        if (checkResult.getFixDeltas().size() > 0) {
            try {
                applyFix(checkResult, shadow, workerTask, result);
                checkResult.setFixApplied(true);
            } catch (CommonException e) {
                checkResult.recordError(Statistics.CANNOT_APPLY_FIX, new SystemException("Couldn't apply the shadow fix", e));
                return;
            }
        }
    }

    private List<PrismObject> searchOwners(PrismObject<ShadowType> shadow, OperationResult result) {
        try {
            PrismReferenceValue refValue = new PrismReferenceValue(shadow.getOid(), ShadowType.COMPLEX_TYPE);
            RefFilter ownerFilter = RefFilter.createReferenceEqual(new ItemPath(FocusType.F_LINK_REF), FocusType.class, prismContext, refValue);
            ObjectQuery ownerQuery = ObjectQuery.createObjectQuery(ownerFilter);
            List owners = repositoryService.searchObjects(FocusType.class, ownerQuery, null, result);
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Owners for {}: {}", ObjectTypeUtil.toShortString(shadow), owners);
            }
            return owners;
        } catch (SchemaException e) {
            LoggingUtils.logUnexpectedException(LOGGER, "Couldn't create owners query for shadow {}", e, ObjectTypeUtil.toShortString(shadow));
            return null;
        }
    }

    private PrismObject<ShadowType> fetchShadow(ShadowCheckResult checkResult, PrismObject<ShadowType> shadow, PrismObject<ResourceType> resource, Task task, OperationResult result) {
        try {
            PrismObject<ShadowType> fullShadow = provisioningService.getObject(ShadowType.class, shadow.getOid(),
                    SelectorOptions.createCollection(GetOperationOptions.createDoNotDiscovery()),
                    task, result);
            return fullShadow;
        } catch (ObjectNotFoundException | CommunicationException | SchemaException | ConfigurationException | SecurityViolationException | RuntimeException e) {
            checkResult.recordError(Statistics.CANNOT_FETCH_RESOURCE_OBJECT, new SystemException("The resource object couldn't be fetched", e));
            return null;
        }
    }

    private void doFixIntent(ShadowCheckResult checkResult, PrismObject<ShadowType> fetchedShadow, PrismObject<ShadowType> shadow, PrismObject<ResourceType> resource, Task task, OperationResult result) {
        PrismObject<ShadowType> fullShadow;

        if (!checkFetch) {
            fullShadow = fetchShadow(checkResult, shadow, resource, task, result);
        } else {
            fullShadow = fetchedShadow;
        }
        if (fullShadow == null) {
            checkResult.recordError(Statistics.CANNOT_APPLY_FIX, new SystemException("Cannot fix missing intent, because the resource object couldn't be fetched"));
            return;
        }

        ObjectSynchronizationType synchronizationPolicy;
        try {
            synchronizationPolicy = synchronizationService.determineSynchronizationPolicy(resource.asObjectable(), fullShadow, configuration, task, result);
        } catch (SchemaException|ObjectNotFoundException|ExpressionEvaluationException|RuntimeException e) {
            checkResult.recordError(Statistics.CANNOT_APPLY_FIX, new SystemException("Couldn't prepare fix for missing intent, because the synchronization policy couldn't be determined", e));
            return;
        }
        if (synchronizationPolicy != null) {
            if (synchronizationPolicy.getIntent() != null) {
                PropertyDelta delta = PropertyDelta.createReplaceDelta(fullShadow.getDefinition(), ShadowType.F_INTENT, synchronizationPolicy.getIntent());
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Intent fix delta (not executed now) = \n{}", delta.debugDump());
                }
                checkResult.addFixDelta(delta, Statistics.NO_INTENT_SPECIFIED);
            } else {
                LOGGER.info("Synchronization policy does not contain intent: {}", synchronizationPolicy);
            }
        } else {
            LOGGER.info("Intent couldn't be fixed, because no synchronization policy was found");
        }
    }

    private void applyFix(ShadowCheckResult checkResult, PrismObject<ShadowType> shadow, Task workerTask, OperationResult result) throws CommonException {
        LOGGER.info("Applying shadow fix{}:\n{}", skippedForDryRun(), DebugUtil.debugDump(checkResult.getFixDeltas()));
        if (!dryRun) {
            repositoryService.modifyObject(ShadowType.class, shadow.getOid(), checkResult.getFixDeltas(), result);
        }
    }

    private String skippedForDryRun() {
        if (dryRun) {
            return " (skipped because of dry run)";
        } else {
            return "";
        }
    }

    private void doCheckNormalization(ShadowCheckResult checkResult, RefinedAttributeDefinition<?> identifier, String value, ObjectTypeContext context) throws SchemaException {
        QName matchingRuleQName = identifier.getMatchingRuleQName();
        if (matchingRuleQName == null) {
            return;
        }

        MatchingRule<Object> matchingRule;
        try {
            matchingRule = matchingRuleRegistry.getMatchingRule(matchingRuleQName, identifier.getTypeName());
        } catch (SchemaException e) {
            checkResult.recordError(Statistics.OTHER_FAILURE, new SchemaException("Couldn't retrieve matching rule for identifier " +
                    identifier.getName() + " (rule name = " + matchingRuleQName + ")"));
            return;
        }

        Object normalizedValue = matchingRule.normalize(value);
        if (!(normalizedValue instanceof String)) {
            checkResult.recordError(Statistics.OTHER_FAILURE, new SchemaException("Normalized value is not a string, it's " + normalizedValue.getClass() +
                    " (identifier " + identifier.getName() + ", value " + value));
            return;
        }
        if (value.equals(normalizedValue)) {
            return;     // OK
        }
        String normalizedStringValue = (String) normalizedValue;

        checkResult.recordError(Statistics.NON_NORMALIZED_IDENTIFIER_VALUE,
                new SchemaException("Non-normalized value of identifier " + identifier.getName()
                        + ": " + value + " (normalized form: " + normalizedValue + ")"));

        if (fixNormalization) {
            PropertyDelta delta = identifier.createEmptyDelta(new ItemPath(ShadowType.F_ATTRIBUTES, identifier.getName()));
            delta.setValueToReplace(new PrismPropertyValue<>(normalizedStringValue));
            checkResult.addFixDelta(delta, Statistics.NON_NORMALIZED_IDENTIFIER_VALUE);
        }
    }

    private void addIdentifierValue(ShadowCheckResult checkResult, ObjectTypeContext context, QName identifierName, String identifierValue, PrismObject<ShadowType> shadow) {

        Map<String, List<PrismObject<ShadowType>>> valueMap = context.getIdentifierValueMap().get(identifierName);
        if (valueMap == null) {
            valueMap = new HashMap<>();
            context.getIdentifierValueMap().put(identifierName, valueMap);
        }
        List<PrismObject<ShadowType>> existingShadows = valueMap.get(identifierValue);
        if (existingShadows == null) {
            // all is well
            existingShadows = new ArrayList();
            existingShadows.add(shadow);
            valueMap.put(identifierValue, existingShadows);
        } else {
            // duplicate shadows statistics are collected in a special way
            duplicateShadowsDetected.add(shadow.getOid());
            LOGGER.error("Multiple shadows with the value of identifier attribute {} = {}: existing one(s): {}, duplicate: {}",
                    identifierName, identifierValue, shortDumpList(existingShadows), ObjectTypeUtil.toShortString(shadow.asObjectable()));
            existingShadows.add(shadow);
        }
    }

    private String shortDumpList(List<PrismObject<ShadowType>> list) {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        boolean first = true;
        for (PrismObject<ShadowType> object : list) {
            if (first) {
                first = false;
            } else {
                sb.append(", ");
            }
            sb.append(ObjectTypeUtil.toShortString(object.asObjectable()));
        }
        return sb.toString();
    }

    public Statistics getStatistics() {
        return statistics;
    }

    private String reportOrFixUniqueness(OperationResult result) {

        StringBuilder details = new StringBuilder();
        StringBuilder stat = new StringBuilder();

        for (Map.Entry<Pair<String,ShadowKindType>, ObjectTypeContext> entry : contextMap.entrySet()) {
            String resourceOid = entry.getKey().getLeft();
            ShadowKindType kind = entry.getKey().getRight();
            ObjectTypeContext ctx = entry.getValue();
            PrismObject<ResourceType> resource = resources.get(resourceOid);
            if (resource == null) {
                LOGGER.error("No resource for {}", resourceOid);        // should not happen
                continue;
            }
            for (Map.Entry<QName, Map<String, List<PrismObject<ShadowType>>>> idValEntry : ctx.getIdentifierValueMap().entrySet()) {
                QName identifier = idValEntry.getKey();
                boolean first = true;
                for (Map.Entry<String, List<PrismObject<ShadowType>>> valListEntry : idValEntry.getValue().entrySet()) {
                    List<PrismObject<ShadowType>> shadows = valListEntry.getValue();
                    if (shadows.size() <= 1) {
                        continue;
                    }
                    if (first) {
                        details.append("Duplicates for ").append(ObjectTypeUtil.toShortString(resource));
                        details.append(", kind = ").append(kind);
                        details.append(", identifier = ").append(identifier).append(":\n");
                        first = false;
                    }
                    details.append(" - value: ").append(valListEntry.getKey()).append(", shadows: ").append(shadows.size()).append("\n");
                    List<PrismObject<ShadowType>> shadowsToConsider = new ArrayList<>();
                    for (PrismObject<ShadowType> shadow : shadows) {
                        details.append("   - ").append(ObjectTypeUtil.toShortString(shadow));
                        details.append("; sync situation = ").append(shadow.asObjectable().getSynchronizationSituation()).append("\n");
                        PrismContainer<ShadowAttributesType> attributesContainer = shadow.findContainer(ShadowType.F_ATTRIBUTES);
                        if (attributesContainer != null && !attributesContainer.isEmpty()) {
                            for (Item item : attributesContainer.getValue().getItems()) {
                                details.append("     - ").append(item.getElementName().getLocalPart()).append(" = ");
                                details.append(((PrismProperty) item).getRealValues());
                                details.append("\n");
                            }
                        }
                        if (duplicateShadowsDeleted.contains(shadow.getOid())) {
                            details.append("     (already deleted)\n");
                        } else {
                            shadowsToConsider.add(shadow);
                        }
                    }

                    if (fixUniqueness && shadowsToConsider.size() > 1) {
                        DuplicateShadowsTreatmentInstruction instruction = duplicateShadowsResolver.determineDuplicateShadowsTreatment(shadowsToConsider);
                        deleteShadows(instruction, details, result);
                    }
                }
            }
        }
        stat.append("Duplicate shadows detected: ").append(duplicateShadowsDetected.size());
        if (fixUniqueness) {
            stat.append(", deleted: ").append(duplicateShadowsDeleted.size());
            // TODO report the duplicates that remain
        }

        return stat.toString() + "\n" + details.toString();
    }

    // shadowsToDelete do not contain 'already deleted shadows'
    private void deleteShadows(DuplicateShadowsTreatmentInstruction instruction, StringBuilder sb, OperationResult result) {

        LOGGER.trace("Going to delete shadows:\n{}", instruction);
        if (instruction == null || instruction.getShadowsToDelete() == null) {
            return;
        }
        Collection<PrismObject<ShadowType>> shadowsToDelete = instruction.getShadowsToDelete();
        String shadowOidToReplaceDeleted = instruction.getShadowOidToReplaceDeletedOnes();

        for (PrismObject<ShadowType> shadowToDelete : shadowsToDelete) {
            LOGGER.info("Deleting redundant shadow{} {}", skippedForDryRun(), ObjectTypeUtil.toShortString(shadowToDelete));
            sb.append("   --> deleted redundant shadow").append(skippedForDryRun()).append(" ").append(ObjectTypeUtil.toShortString(shadowToDelete)).append("\n");
            String oid = shadowToDelete.getOid();

            List<PrismObject> owners;
            if (checkOwners) {
                owners = (List) shadowToDelete.getUserData(KEY_OWNERS);
            } else {
                owners = searchOwners(shadowToDelete, result);
            }

            if (!dryRun) {
                try {
                    repositoryService.deleteObject(ShadowType.class, oid, result);
                    duplicateShadowsDeleted.add(oid);
                } catch (ObjectNotFoundException e) {
                    // suspicious, but not a big deal
                    LoggingUtils.logExceptionAsWarning(LOGGER, "Shadow {} couldn't be deleted, because it does not exist anymore", e, ObjectTypeUtil.toShortString(shadowToDelete));
                    continue;
                } catch (RuntimeException e) {
                    LoggingUtils.logUnexpectedException(LOGGER, "Shadow {} couldn't be deleted because of an unexpected exception", e, ObjectTypeUtil.toShortString(shadowToDelete));
                    continue;
                }
            }

            if (owners == null || owners.isEmpty()) {
                continue;
            }

            for (PrismObject owner : owners) {
                List<ItemDelta> modifications = new ArrayList<>(2);
                ReferenceDelta deleteDelta = ReferenceDelta.createModificationDelete(FocusType.F_LINK_REF, owner.getDefinition(),
                        new PrismReferenceValue(oid, ShadowType.COMPLEX_TYPE));
                modifications.add(deleteDelta);
                if (shadowOidToReplaceDeleted != null) {
                    ReferenceDelta addDelta = ReferenceDelta.createModificationAdd(FocusType.F_LINK_REF, owner.getDefinition(),
                            new PrismReferenceValue(shadowOidToReplaceDeleted, ShadowType.COMPLEX_TYPE));
                    modifications.add(addDelta);
                }
                LOGGER.info("Executing modify delta{} for owner {}:\n{}", skippedForDryRun(), ObjectTypeUtil.toShortString(owner), DebugUtil.debugDump(modifications));
                if (!dryRun) {
                    try {
                        repositoryService.modifyObject((Class) owner.getClass(), owner.getOid(), modifications, result);
                    } catch (ObjectNotFoundException | SchemaException | ObjectAlreadyExistsException | RuntimeException e) {
                        LoggingUtils.logUnexpectedException(LOGGER, "Focal object {} (owner of {}) couldn't be updated", e, ObjectTypeUtil.toShortString(owner),
                                ObjectTypeUtil.toShortString(shadowToDelete));
                    }
                }
            }

        }
    }

    @Override
    public void completeProcessing(OperationResult result) {
        super.completeProcessing(result);

        String uniquenessReport = null;
        if (checkUniqueness) {
            uniquenessReport = reportOrFixUniqueness(result);
        }

        logConfiguration("Shadow integrity check finished. It was run with the configuration:");
        LOGGER.info("Results:\n" +
                        "    Shadows processed: {} ({} resources),\n" +
                        "    Shadows with no problems: {}\n" +
                        "    Shadows with warnings: {}\n" +
                        "    Shadows with errors: {}\n" +
                        "    Details:\n{}",
                statistics.getShadows(), statistics.getResources(),
                statistics.getShadows() - statistics.getShadowsWithErrors() - statistics.getShadowsWithWarnings(),
                statistics.getShadowsWithWarnings(), statistics.getShadowsWithErrors(),
                statistics.getDetailsFormatted(dryRun));

        if (uniquenessReport != null) {
            LOGGER.info("Uniqueness report:\n{}", uniquenessReport);
        }
    }

}
