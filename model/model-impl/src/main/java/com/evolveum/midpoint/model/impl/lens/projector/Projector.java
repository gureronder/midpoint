/*
 * Copyright (c) 2010-2014 Evolveum
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
import static com.evolveum.midpoint.model.api.ProgressInformation.ActivityType.PROJECTOR;
import static com.evolveum.midpoint.model.api.ProgressInformation.StateType.ENTERING;

import java.util.List;

import javax.xml.datatype.XMLGregorianCalendar;

import com.evolveum.midpoint.model.api.ProgressInformation;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.evolveum.midpoint.common.Clock;
import com.evolveum.midpoint.model.api.PolicyViolationException;
import com.evolveum.midpoint.model.api.context.SynchronizationPolicyDecision;
import com.evolveum.midpoint.model.impl.lens.LensContext;
import com.evolveum.midpoint.model.impl.lens.LensProjectionContext;
import com.evolveum.midpoint.model.impl.lens.LensUtil;
import com.evolveum.midpoint.prism.xml.XmlTypeConverter;
import com.evolveum.midpoint.schema.ResourceShadowDiscriminator;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.exception.CommunicationException;
import com.evolveum.midpoint.util.exception.ConfigurationException;
import com.evolveum.midpoint.util.exception.ExpressionEvaluationException;
import com.evolveum.midpoint.util.exception.ObjectAlreadyExistsException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SecurityViolationException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;

/**
 * Projector recomputes the context. It takes the context with a few basic data as input. It uses all the policies 
 * and mappings to derive all the other data. E.g. a context with only a user (primary) delta. It applies user template,
 * outbound mappings and the inbound mappings and then inbound and outbound mappings of other accounts and so on until
 * all the data are computed. The output is the original context with all the computed delta.
 * 
 * Primary deltas are in the input, secondary deltas are computed in projector. Projector "projects" primary deltas to
 * the secondary deltas of user and accounts.
 * 
 * Projector does NOT execute the deltas. It only recomputes the context. It may read a lot of objects (user, accounts, policies).
 * But it does not change any of them.
 * 
 * @author Radovan Semancik
 *
 */
@Component
public class Projector {
	
	@Autowired(required = true)
	private ContextLoader contextLoader;
	
	@Autowired(required = true)
    private FocusProcessor focusProcessor;

    @Autowired(required = true)
    private AssignmentProcessor assignmentProcessor;
    
    @Autowired(required = true)
    private ProjectionValuesProcessor projectionValuesProcessor;

    @Autowired(required = true)
    private ReconciliationProcessor reconciliationProcessor;

    @Autowired(required = true)
    private CredentialsProcessor credentialsProcessor;

    @Autowired(required = true)
    private ActivationProcessor activationProcessor;
    
    @Autowired(required = true)
    private DependencyProcessor dependencyProcessor;
    
    @Autowired(required = true)
    private Clock clock;
	
	private static final Trace LOGGER = TraceManager.getTrace(Projector.class);

	/**
	 * Runs one projection wave, starting at current execution wave.
	 */
	public <F extends ObjectType> void project(LensContext<F> context, String activityDescription,
											   Task task, OperationResult parentResult)
			throws SchemaException, PolicyViolationException, ExpressionEvaluationException, ObjectNotFoundException,
			ObjectAlreadyExistsException, CommunicationException, ConfigurationException, SecurityViolationException {
		projectInternal(context, activityDescription, true, false, task, parentResult);
	}

	/**
	 * Resumes projection at current projection wave.
	 */
	public <F extends ObjectType> void resume(LensContext<F> context, String activityDescription,
											   Task task, OperationResult parentResult)
			throws SchemaException, PolicyViolationException, ExpressionEvaluationException, ObjectNotFoundException,
			ObjectAlreadyExistsException, CommunicationException, ConfigurationException, SecurityViolationException {

		if (context.getProjectionWave() != context.getExecutionWave()) {
			throw new IllegalStateException("Projector.resume called in illegal wave state: execution wave = " + context.getExecutionWave() +
					", projection wave = " + context.getProjectionWave());
		}
		if (!context.isFresh()) {
			throw new IllegalStateException("Projector.resume called on non-fresh context");
		}

		projectInternal(context, activityDescription, false, false, task, parentResult);
	}

	/**
	 * Executes projector from current execution wave to the last computed wave.
	 * Useful for change preview.
	 */
	public <F extends ObjectType> void projectAllWaves(LensContext<F> context, String activityDescription,
												  Task task, OperationResult parentResult)
			throws SchemaException, PolicyViolationException, ExpressionEvaluationException, ObjectNotFoundException,
			ObjectAlreadyExistsException, CommunicationException, ConfigurationException, SecurityViolationException {
		projectInternal(context, activityDescription, true, true, task, parentResult);
	}

	private <F extends ObjectType> void projectInternal(LensContext<F> context, String activityDescription,
            boolean fromStart, boolean allWaves, Task task, OperationResult parentResult)
			throws SchemaException, PolicyViolationException, ExpressionEvaluationException, ObjectNotFoundException,
			ObjectAlreadyExistsException, CommunicationException, ConfigurationException, SecurityViolationException {

        context.checkAbortRequested();

		if (context.getDebugListener() != null) {
			context.getDebugListener().beforeProjection(context);
		}

		// Read the time at the beginning so all processors have the same notion of "now"
		// this provides nicer unified timestamp that can be used in equality checks in tests and also for
		// troubleshooting
		XMLGregorianCalendar now = clock.currentTimeXMLGregorianCalendar();

		String traceTitle = fromStart ? "projector start" : "projector resume";
		LensUtil.traceContext(LOGGER, activityDescription, traceTitle, false, context, false);

		if (consistencyChecks) context.checkConsistence();

		if (fromStart) {
			context.normalize();
			context.resetProjectionWave();
		}
		
		OperationResult result = parentResult.createSubresult(Projector.class.getName() + ".project");
		result.addParam("fromStart", fromStart);
		result.addContext("projectionWave", context.getProjectionWave());
		result.addContext("executionWave", context.getExecutionWave());
		
		// Projector is using a set of "processors" to do parts of its work. The processors will be called in sequence
		// in the following code.
		
		try {

            context.reportProgress(new ProgressInformation(PROJECTOR, ENTERING));

			if (fromStart) {
				contextLoader.load(context, activityDescription, task, result);
				// Set the "fresh" mark now so following consistency check will be stricter
				context.setFresh(true);
				if (consistencyChecks) context.checkConsistence();
			}
	        // For now let's pretend to do just one wave. The maxWaves number will be corrected in the
			// first wave when dependencies are sorted out for the first time.
	        int maxWaves = context.getExecutionWave() + 1;
	                
	        // Start the waves ....
	        LOGGER.trace("WAVE: Starting the waves.");

			boolean firstWave = true;

			while ((allWaves && context.getProjectionWave() < maxWaves) ||
					(!allWaves && context.getProjectionWave() <= context.getExecutionWave())) {

				boolean inFirstWave = firstWave;
				firstWave = false;					// in order to not forget to reset it ;)
	        	
                context.checkAbortRequested();

	        	LOGGER.trace("WAVE {} (maxWaves={}, executionWave={})", new Object[]{
	        			context.getProjectionWave(), maxWaves, context.getExecutionWave()});
	        	
	        	//just make sure everything is loaded and set as needed
				dependencyProcessor.preprocessDependencies(context);
	        	
	        	// Process the focus-related aspects of the context. That means inbound, focus activation,
	        	// object template and assignments.
		        focusProcessor.processFocus(context, activityDescription, now, task, result);
		        context.recomputeFocus();
		        if (consistencyChecks) context.checkConsistence();
		        
		        // Process activation of all resources, regardless of the waves. This is needed to properly
		        // sort projections to waves as deprovisioning will reverse the dependencies. And we know whether
		        // a projection is provisioned or deprovisioned only after the activation is processed.
		        if (fromStart && inFirstWave) {
		        	LOGGER.trace("Processing activation for all contexts");
			        for (LensProjectionContext projectionContext: context.getProjectionContexts()) {
			        	if (projectionContext.getSynchronizationPolicyDecision() == SynchronizationPolicyDecision.BROKEN ||
			        			projectionContext.getSynchronizationPolicyDecision() == SynchronizationPolicyDecision.IGNORE) {
							continue;
			        	}
			        	activationProcessor.processActivation(context, projectionContext, now, task, result);
			        	projectionContext.recompute();
			        }
					assignmentProcessor.removeIgnoredContexts(context);		// TODO move implementation of this method elsewhere; but it has to be invoked here, as activationProcessor sets the IGNORE flag
		        }
		        LensUtil.traceContext(LOGGER, activityDescription, "projection activation of all resources", true, context, true);
		
		        dependencyProcessor.sortProjectionsToWaves(context);
		        maxWaves = dependencyProcessor.computeMaxWaves(context);
		        LOGGER.trace("Continuing wave {}, maxWaves={}", context.getProjectionWave(), maxWaves);

		        LensUtil.traceContext(LOGGER, activityDescription,"focus processing", false, context, false);
		        if (consistencyChecks) context.checkConsistence();
		        LensUtil.checkContextSanity(context, "focus processing", result);
		
		        // Focus-related processing is over. Now we will process projections in a loop.
		        for (LensProjectionContext projectionContext: context.getProjectionContexts()) {

                    context.checkAbortRequested();

		        	if (projectionContext.getSynchronizationPolicyDecision() == SynchronizationPolicyDecision.BROKEN ||
		        			projectionContext.getSynchronizationPolicyDecision() == SynchronizationPolicyDecision.IGNORE) {
						continue;
		        	}
		        	String projectionDesc = getProjectionDesc(projectionContext);
		        	
		        	if (projectionContext.getWave() != context.getProjectionWave()) {
		        		// Let's skip accounts that do not belong into this wave.
		        		continue;
		        	}
		        	
		        	LOGGER.trace("WAVE {} PROJECTION {}", context.getProjectionWave(), projectionDesc);

		        	// Some projections may not be loaded at this point, e.g. high-order dependency projections
		        	contextLoader.makeSureProjectionIsLoaded(context, projectionContext, result);
		        	
		        	if (consistencyChecks) context.checkConsistence();
		        	
		        	if (!dependencyProcessor.checkDependencies(context, projectionContext, result)) {
		        		continue;
		        	}
		        	
		        	// TODO: decide if we need to continue
		        	
		        	// This is a "composite" processor. it contains several more processor invocations inside
		        	projectionValuesProcessor.process(context, projectionContext, activityDescription, task, result);
		        	
		        	if (consistencyChecks) context.checkConsistence();
		        	
		        	projectionContext.recompute();
		        	//SynchronizerUtil.traceContext("values", context, false);
		        	if (consistencyChecks) context.checkConsistence();
		        	
		        	credentialsProcessor.processProjectionCredentials(context, projectionContext, now, task, result);
		        	
		        	//SynchronizerUtil.traceContext("credentials", context, false);
		        	if (consistencyChecks) context.checkConsistence();
			        
		        	projectionContext.recompute();
		        	LensUtil.traceContext(LOGGER, activityDescription, "projection values and credentials of "+projectionDesc, false, context, true);
			        if (consistencyChecks) context.checkConsistence();
			
			        reconciliationProcessor.processReconciliation(context, projectionContext, result);
			        projectionContext.recompute();
			        LensUtil.traceContext(LOGGER, activityDescription, "projection reconciliation of "+projectionDesc, false, context, false);
			        if (consistencyChecks) context.checkConsistence();
			        
			        
		        }
		        
		        // if there exists some conflicting projection contexts, add them to the context so they will be recomputed in the next wave..
		        addConflictingContexts(context);
		        
		        if (consistencyChecks) context.checkConsistence();
		        
		        context.incrementProjectionWave();
	        }
	        
	        LOGGER.trace("WAVE: Stopping the waves. There was {} waves", context.getProjectionWave());
	        
	        // We can do this only when computation of all the waves is finished. Before that we do not know
	        // activation of every account and therefore cannot decide what is OK and what is not
	        dependencyProcessor.checkDependenciesFinal(context, result);
	        
	        if (consistencyChecks) context.checkConsistence();
	        
	        computeResultStatus(now, result);
	        
		} catch (SchemaException e) {
			recordFatalError(e, now, result);
			throw e;
		} catch (PolicyViolationException e) {
			recordFatalError(e, now, result);
			throw e;
		} catch (ExpressionEvaluationException e) {
			recordFatalError(e, now, result);
			throw e;
		} catch (ObjectNotFoundException e) {
			recordFatalError(e, now, result);
			throw e;
		} catch (ObjectAlreadyExistsException e) {
			recordFatalError(e, now, result);
			throw e;
		} catch (CommunicationException e) {
			recordFatalError(e, now, result);
			throw e;
		} catch (ConfigurationException e) {
			recordFatalError(e, now, result);
			throw e;
		} catch (SecurityViolationException e) {
			recordFatalError(e, now, result);
			throw e;
		} catch (RuntimeException e) {
			recordFatalError(e, now, result);
			// This should not normally happen unless there is something really bad or there is a bug.
			// Make sure that it is logged.
			LOGGER.error("Runtime error in projector: {}", e.getMessage(), e);
			throw e;
		} finally {
			if (context.getDebugListener() != null) {
				context.getDebugListener().afterProjection(context);
			}
            context.reportProgress(new ProgressInformation(PROJECTOR, result));
        }
		
	}

	private String getProjectionDesc(LensProjectionContext projectionContext) {
		if (projectionContext.getResource() != null) {
    		return projectionContext.getResource() + "("+projectionContext.getResourceShadowDiscriminator().getIntent()+")";		        		
    	} else {
    		ResourceShadowDiscriminator discr = projectionContext.getResourceShadowDiscriminator();
    		if (discr != null) {
    			return projectionContext.getResourceShadowDiscriminator().toString();
    		} else {
    			return "(UNKNOWN)";
    		}
    	}
	}

	// TODO projectionValuesProcessor is a singleton so its conflictingContexts property is de-facto a global variable!
	private <F extends ObjectType> void addConflictingContexts(LensContext<F> context) {
		List<LensProjectionContext> conflictingContexts = projectionValuesProcessor.getConflictingContexts();
		if (conflictingContexts != null || !conflictingContexts.isEmpty()){
			for (LensProjectionContext conflictingContext : conflictingContexts){
				LOGGER.trace("Adding conflicting projection context {}", conflictingContext.getHumanReadableName());
				context.addProjectionContext(conflictingContext);
			}
		
			projectionValuesProcessor.getConflictingContexts().clear();
		}
		
	}

	private void recordFatalError(Exception e, XMLGregorianCalendar projectoStartTimestampCal, OperationResult result) {
		result.recordFatalError(e);
		result.cleanupResult(e);
		if (LOGGER.isDebugEnabled()) {
			long projectoStartTimestamp = XmlTypeConverter.toMillis(projectoStartTimestampCal);
	    	long projectorEndTimestamp = clock.currentTimeMillis();
			LOGGER.debug("Projector failed: {}. Etime: {} ms", e.getMessage(), (projectorEndTimestamp - projectoStartTimestamp));
		}
	}
	
	private void computeResultStatus(XMLGregorianCalendar projectoStartTimestampCal, OperationResult result) {
        result.computeStatus();
        result.cleanupResult();
        if (LOGGER.isDebugEnabled()) {
        	long projectoStartTimestamp = XmlTypeConverter.toMillis(projectoStartTimestampCal);
        	long projectorEndTimestamp = clock.currentTimeMillis();
        	LOGGER.trace("Projector finished ({}). Etime: {} ms", result.getStatus(), (projectorEndTimestamp - projectoStartTimestamp));
        }
	}

}
