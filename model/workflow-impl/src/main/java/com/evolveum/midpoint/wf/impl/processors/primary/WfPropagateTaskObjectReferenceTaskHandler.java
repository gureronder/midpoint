/*
 * Copyright (c) 2010-2013 Evolveum
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

package com.evolveum.midpoint.wf.impl.processors.primary;

/**
 * @author mederly
 */

import com.evolveum.midpoint.model.api.context.ModelContext;
import com.evolveum.midpoint.model.impl.lens.LensContext;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.*;
import com.evolveum.midpoint.util.JAXBUtil;
import com.evolveum.midpoint.util.exception.*;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.wf.impl.jobs.Job;
import com.evolveum.midpoint.wf.impl.jobs.JobController;
import com.evolveum.midpoint.wf.impl.jobs.WfTaskUtil;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectReferenceType;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.xml.namespace.QName;

import java.util.List;

/**
 * This handler propagates object OID of newly added object to dependent tasks.
 * (Quite a hack for now.)
 *
 * @author mederly
 */

@Component
public class WfPropagateTaskObjectReferenceTaskHandler implements TaskHandler {

    // should be available only within the context of primary change processor
    static final String HANDLER_URI = "http://midpoint.evolveum.com/xml/ns/public/workflow/propagate-task-object-reference/handler-3";

    private static final Trace LOGGER = TraceManager.getTrace(WfPropagateTaskObjectReferenceTaskHandler.class);

    //region Spring beans and initialization
    @Autowired
    private TaskManager taskManager;

    @Autowired
    private JobController jobController;

    @Autowired
    private WfTaskUtil wfTaskUtil;

    @PostConstruct
    public void init() {
        LOGGER.trace("Registering with taskManager as a handler for " + HANDLER_URI);
        taskManager.registerHandler(HANDLER_URI, this);
    }
    //endregion

    //region Body
    @Override
    public TaskRunResult run(Task task) {

        TaskRunResult.TaskRunResultStatus status = TaskRunResult.TaskRunResultStatus.FINISHED;

        OperationResult result = task.getResult().createSubresult(WfPropagateTaskObjectReferenceTaskHandler.class + ".run");

        Job job;
        try {
            job = jobController.recreateJob(task);
        } catch (SchemaException e) {
            return reportException("Couldn't create a job from task " + task, task, result, e);
        } catch (ObjectNotFoundException e) {
            return reportException("Couldn't create a job from task " + task, task, result, e);
        }

        LOGGER.trace("WfPropagateTaskObjectReferenceTaskHandler starting... job = {}", job);

        ModelContext modelContext;
        try {
            modelContext = job.retrieveModelContext(result);
            if (modelContext == null) {
                throw new IllegalStateException("There's no model context in the task; job = " + job);
            }
        } catch (SchemaException e) {
            return reportException("Couldn't retrieve model context from job " + job, task, result, e);
        } catch (ObjectNotFoundException e) {
            return reportException("Couldn't retrieve model context from job " + job, task, result, e);
        } catch (CommunicationException e) {
            return reportException("Couldn't retrieve model context from job " + job, task, result, TaskRunResult.TaskRunResultStatus.TEMPORARY_ERROR, e);
        } catch (ConfigurationException e) {
            return reportException("Couldn't retrieve model context from job " + job, task, result, e);
        }

        String oid = ((LensContext) modelContext).getFocusContext().getOid();
        if (oid == null) {
            LOGGER.warn("No object OID in job " + job);
        } else {

            Class typeClass = ((LensContext) modelContext).getFocusContext().getObjectTypeClass();
            QName type = typeClass != null ? JAXBUtil.getTypeQName(typeClass) : null;
            if (type == null) {
                LOGGER.warn("Unknown type of object " + oid + " in task " + task);
            } else {

                ObjectReferenceType objectReferenceType = new ObjectReferenceType();
                objectReferenceType.setType(type);
                objectReferenceType.setOid(oid);

                if (task.getObjectRef() == null) {
                    task.setObjectRef(objectReferenceType);
                } else {
                    LOGGER.warn("object reference in task " + task + " is already set, although it shouldn't be");
                }

                List<Job> dependents;
                try {
                    dependents = job.listDependents(result);
                    dependents.add(job.getParentJob(result));
                } catch (SchemaException e) {
                    return reportException("Couldn't get dependents from job " + job, task, result, e);
                } catch (ObjectNotFoundException e) {
                    return reportException("Couldn't get dependents from job " + job, task, result, e);
                }

                for (Job dependent : dependents) {
                    if (dependent.getTask().getObjectRef() == null) {
                        try {
                            dependent.getTask().setObjectRefImmediate(objectReferenceType, result);
                        } catch (ObjectNotFoundException e) {
                            // note we DO NOT return, because we want to set all references we can
                            reportException("Couldn't set object reference on job " + dependent, task, result, e);
                        } catch (SchemaException e) {
                            reportException("Couldn't set object reference on job " + dependent, task, result, e);
                        } catch (ObjectAlreadyExistsException e) {
                            reportException("Couldn't set object reference on job " + dependent, task, result, e);
                        }
                    } else {
                        LOGGER.warn("object reference in job " + dependent + " is already set, although it shouldn't be");
                    }
                }
            }
        }

        result.computeStatusIfUnknown();

        TaskRunResult runResult = new TaskRunResult();
        runResult.setRunResultStatus(status);
        runResult.setOperationResult(task.getResult());
        return runResult;
    }

    private TaskRunResult reportException(String message, Task task, OperationResult result, Throwable cause) {
        return reportException(message, task, result, TaskRunResult.TaskRunResultStatus.PERMANENT_ERROR, cause);
    }

    private TaskRunResult reportException(String message, Task task, OperationResult result, TaskRunResult.TaskRunResultStatus status, Throwable cause) {

        LoggingUtils.logException(LOGGER, message, cause);
        result.recordFatalError(message, cause);

        TaskRunResult runResult = new TaskRunResult();
        runResult.setRunResultStatus(status);
        runResult.setOperationResult(task.getResult());
        return runResult;
    }
    //endregion

    //region Other task handler stuff
    @Override
    public Long heartbeat(Task task) {
        return null;		// null - as *not* to record progress (which would overwrite operationResult!)
    }

    @Override
    public void refreshStatus(Task task) {
    }

    @Override
    public String getCategoryName(Task task) {
        return TaskCategory.WORKFLOW;
    }

    @Override
    public List<String> getCategoryNames() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }
    //endregion

}
