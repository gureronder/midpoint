/*
 * Copyright (c) 2012 Evolveum
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
 * Portions Copyrighted 2012 [name of copyright owner]
 */

package com.evolveum.midpoint.provisioning.api;

import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.SchemaDebugUtil;
import com.evolveum.midpoint.schema.util.ObjectTypeUtil;
import com.evolveum.midpoint.util.DebugDumpable;
import com.evolveum.midpoint.util.DebugUtil;
import com.evolveum.midpoint.util.Dumpable;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ResourceObjectShadowType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ResourceType;

/**
 * Describes a failure to apply a change to a specific resource object.
 *
 * @author Radovan Semancik
 */
public class ResourceOperationDescription implements Dumpable, DebugDumpable {

    private ObjectDelta<? extends ResourceObjectShadowType> objectDelta;
    private PrismObject<? extends ResourceObjectShadowType> currentShadow;
    private String sourceChannel;
    private PrismObject<ResourceType> resource;
    private OperationResult result;
    private boolean asynchronous = false;
    private int attemptNumber = 0;

    /**
     * The operation that was about to execute and that has failed.
     */
    public ObjectDelta<? extends ResourceObjectShadowType> getObjectDelta() {
        return objectDelta;
    }

    public void setObjectDelta(ObjectDelta<? extends ResourceObjectShadowType> objectDelta) {
        this.objectDelta = objectDelta;
    }

    /**
     * Shadow describing the object that was the target of the operation. It may a "temporary" shadow that
     * is not yet bound to a specific resource object (e.g. in case of add operation).
     */
    public PrismObject<? extends ResourceObjectShadowType> getCurrentShadow() {
        return currentShadow;
    }

    public void setCurrentShadow(PrismObject<? extends ResourceObjectShadowType> currentShadow) {
        this.currentShadow = currentShadow;
    }

    public String getSourceChannel() {
        return sourceChannel;
    }

    public void setSourceChannel(String sourceChannel) {
        this.sourceChannel = sourceChannel;
    }

    public PrismObject<ResourceType> getResource() {
        return resource;
    }

    public void setResource(PrismObject<ResourceType> resource) {
        this.resource = resource;
    }
    
    /**
     * Result of the failed operation.
     */
    public OperationResult getResult() {
		return result;
	}

	public void setResult(OperationResult result) {
		this.result = result;
	}

	/**
	 * True if the operation is asynchronous. I.e. true if the operation
	 * cannot provide direct return value and therefore the invocation of
	 * the listener is the only way how to pass operation return value to
	 * the upper layers.
	 * 
	 * This may be useful e.g. for decided whether log the message and what
	 * log level to use (it can be assumed that the error gets logged at least
	 * once for synchronous operations, but this may be the only chance to 
	 * properly log it for asynchronous operations).
	 */
	public boolean isAsynchronous() {
		return asynchronous;
	}

	public void setAsynchronous(boolean asynchronous) {
		this.asynchronous = asynchronous;
	}

	public int getAttemptNumber() {
		return attemptNumber;
	}

	public void setAttemptNumber(int attemptNumber) {
		this.attemptNumber = attemptNumber;
	}

	public void checkConsistence() {
    	if (resource == null) {
    		throw new IllegalArgumentException("No resource in "+this.getClass().getSimpleName());
    	}
    	resource.checkConsistence();
    	//FIXME: have not to be set always
//    	if (sourceChannel == null) {
//    		throw new IllegalArgumentException("No sourceChannel in "+this.getClass().getSimpleName());
//    	}
    	if (objectDelta == null && currentShadow == null) {
    		throw new IllegalArgumentException("Either objectDelta or currentShadow must be set in "+this.getClass().getSimpleName());
    	}
    	if (objectDelta != null && !objectDelta.isAdd() && objectDelta.getOid() == null) {
    		throw new IllegalArgumentException("Delta OID not set in "+this.getClass().getSimpleName());
    	}
    	if (objectDelta != null) {
    		objectDelta.checkConsistence();
    	}
    	//shadow does not have oid set, for example the shadow should be added, but is wasn't because of some error
    	if (currentShadow != null && currentShadow.getOid() == null && objectDelta != null && !objectDelta.isAdd()) {
    		throw new IllegalArgumentException("Current shadow OID not set in "+this.getClass().getSimpleName());
    	}
    	if (currentShadow != null) {
    		currentShadow.checkConsistence();
    	}
    }

	@Override
	public String toString() {
		return "ResourceOperationDescription(objectDelta=" + objectDelta + ", currentShadow="
				+ SchemaDebugUtil.prettyPrint(currentShadow) + ", sourceChannel=" + sourceChannel
				+ ", resource=" + resource + 
				(asynchronous ? ", ASYNC" : "") +
				(attemptNumber != 0 ? ", attemptNumber="+attemptNumber : "") +
				", result=" + result + ")";
	}
    
    @Override
    public String dump() {
    	return debugDump(0);
    }

	/* (non-Javadoc)
	 * @see com.evolveum.midpoint.util.DebugDumpable#debugDump()
	 */
	@Override
	public String debugDump() {
		return debugDump(0);
	}

	/* (non-Javadoc)
	 * @see com.evolveum.midpoint.util.DebugDumpable#debugDump(int)
	 */
	@Override
	public String debugDump(int indent) {
		StringBuilder sb = new StringBuilder();
		SchemaDebugUtil.indentDebugDump(sb, indent);
		sb.append("ResourceObjectShadowFailureDescription(");
		sb.append(sourceChannel);
		sb.append(")\n");
		
		SchemaDebugUtil.indentDebugDump(sb, indent+1);
		sb.append("resource:");
		if (resource == null) {
			sb.append(" null");
		} else {
			sb.append(resource);
		}
		
		sb.append("\n");
		SchemaDebugUtil.indentDebugDump(sb, indent+1);
		sb.append("objectDelta:");
		if (objectDelta == null) {
			sb.append(" null");
		} else {
			sb.append(objectDelta.debugDump(indent+2));
		}
		
		sb.append("\n");
		SchemaDebugUtil.indentDebugDump(sb, indent+1);
		sb.append("currentShadow:");
		if (currentShadow == null) {
			sb.append(" null\n");
		} else {
			sb.append("\n");
			sb.append(currentShadow.debugDump(indent+2));
		}
		
		sb.append("\n");
		DebugUtil.debugDumpLabel(sb, "Asynchronous", indent+1);
		sb.append(asynchronous);

		sb.append("\n");
		DebugUtil.debugDumpLabel(sb, "Attempt number", indent+1);
		sb.append(attemptNumber);

		sb.append("\n");
		SchemaDebugUtil.indentDebugDump(sb, indent+1);		
		sb.append("result:");
		if (result == null) {
			sb.append(" null\n");
		} else {
			sb.append("\n");
			sb.append(result.debugDump(indent+2));
		}

		return sb.toString();
	}

}