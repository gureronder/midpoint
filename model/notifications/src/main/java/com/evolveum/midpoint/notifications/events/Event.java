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

package com.evolveum.midpoint.notifications.events;

import com.evolveum.midpoint.notifications.NotificationsUtil;
import com.evolveum.midpoint.notifications.SimpleObjectRef;
import com.evolveum.midpoint.prism.delta.ChangeType;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.*;

import javax.xml.namespace.QName;
import java.util.Map;

/**
 * @author mederly
 */
public abstract class Event {

    private String id;                              // randomly generated event ID
    private SimpleObjectRef requester;              // who requested this operation (null if unknown)

    // about who is this operation (null if unknown);
    // - for model notifications, this is the focus, (usually a user but may be e.g. role or other kind of object)
    // - for account notifications, this is the account owner,
    // - for workflow notifications, this is the workflow process instance object

    private SimpleObjectRef requestee;

    public Event() {
        id = System.currentTimeMillis() + ":" + (long) (Math.random() * 100000000);
    }

    public String getId() {
        return id;
    }

    @Override
    public String toString() {
        return "Event{" +
                "id=" + id +
                ",requester=" + requester +
                ",requestee=" + requestee +
                '}';
    }

    abstract public boolean isStatusType(EventStatusType eventStatusType);
    abstract public boolean isOperationType(EventOperationType eventOperationType);
    abstract public boolean isCategoryType(EventCategoryType eventCategoryType);

    public boolean isAccountRelated() {
        return isCategoryType(EventCategoryType.ACCOUNT_EVENT);
    }

    public boolean isUserRelated() {
        return isCategoryType(EventCategoryType.USER_EVENT);
    }

    public boolean isWorkItemRelated() {
        return isCategoryType(EventCategoryType.WORK_ITEM_EVENT);
    }

    public boolean isWorkflowProcessRelated() {
        return isCategoryType(EventCategoryType.WORKFLOW_PROCESS_EVENT);
    }

    public boolean isWorkflowRelated() {
        return isCategoryType(EventCategoryType.WORKFLOW_EVENT);
    }

    public boolean isAdd() {
        return isOperationType(EventOperationType.ADD);
    }

    public boolean isModify() {
        return isOperationType(EventOperationType.MODIFY);
    }

    public boolean isDelete() {
        return isOperationType(EventOperationType.DELETE);
    }

    public boolean isSuccess() {
        return isStatusType(EventStatusType.SUCCESS);
    }

    public boolean isFailure() {
        return isStatusType(EventStatusType.FAILURE);
    }

    public boolean isInProgress() {
        return isStatusType(EventStatusType.IN_PROGRESS);
    }

    // requester

    public SimpleObjectRef getRequester() {
        return requester;
    }

    public String getRequesterOid() {
        return requester.getOid();
    }

    public void setRequester(UserType requester) {
        this.requester = new SimpleObjectRef(requester);
    }

    public void setRequesterOid(String requesterOid) {
        this.requester = new SimpleObjectRef(requesterOid);
    }

    // requestee

    public SimpleObjectRef getRequestee() {
        return requestee;
    }

    public String getRequesteeOid() {
        return requestee.getOid();
    }

    public void setRequestee(ObjectType requestee) {
        this.requestee = new SimpleObjectRef(requestee);
    }

    public void setRequesteeOid(String requesteeOid) {
        this.requestee = new SimpleObjectRef(requesteeOid);
    }

    boolean changeTypeMatchesOperationType(ChangeType changeType, EventOperationType eventOperationType) {
        switch (eventOperationType) {
            case ADD: return changeType == ChangeType.ADD;
            case MODIFY: return changeType == ChangeType.MODIFY;
            case DELETE: return changeType == ChangeType.DELETE;
            default: throw new IllegalStateException("Unexpected EventOperationType: " + eventOperationType);
        }
    }

    public void createExpressionVariables(Map<QName, Object> variables, NotificationsUtil notificationsUtil, OperationResult result) {
        variables.put(SchemaConstants.C_EVENT, this);
        variables.put(SchemaConstants.C_REQUESTER, notificationsUtil.getObjectType(requester, result));
        variables.put(SchemaConstants.C_REQUESTEE, notificationsUtil.getObjectType(requestee, result));
    }
}
