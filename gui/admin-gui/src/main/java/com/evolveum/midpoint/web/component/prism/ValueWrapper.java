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

package com.evolveum.midpoint.web.component.prism;

import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismPropertyValue;
import com.evolveum.midpoint.prism.PrismReferenceValue;
import com.evolveum.midpoint.prism.PrismValue;
import com.evolveum.midpoint.prism.polystring.PolyString;
import com.evolveum.midpoint.prism.util.CloneUtil;
import com.evolveum.midpoint.prism.util.PrismUtil;
import com.evolveum.midpoint.util.DisplayableValue;
import com.evolveum.prism.xml.ns._public.types_3.ProtectedStringType;

import org.apache.commons.lang.Validate;
import org.apache.cxf.common.util.PrimitiveUtils;

import java.io.Serializable;

/**
 * @author lazyman
 */
public class ValueWrapper<T> implements Serializable {

    private ItemWrapper item;
    private PrismValue value;
    private PrismValue oldValue;
//    private PrismPropertyValue<T> value;
//    private PrismPropertyValue<T> oldValue;
    private ValueStatus status;

    public ValueWrapper(ItemWrapper property, PrismValue value) {
        this(property, value, ValueStatus.NOT_CHANGED);
    }

    public ValueWrapper(ItemWrapper property, PrismValue value, ValueStatus status) {
        this(property, value, null, status);
    }

    public ValueWrapper(ItemWrapper property, PrismValue value, PrismValue oldValue,
            ValueStatus status) {
        Validate.notNull(property, "Property wrapper must not be null.");
        Validate.notNull(value, "Property value must not be null.");

        this.item = property;
        this.status = status;
        
		if (value != null) {
			if (value instanceof PrismPropertyValue) {

				T val = ((PrismPropertyValue<T>) value).getValue();
				if (val instanceof PolyString) {
					PolyString poly = (PolyString) val;
					this.value = new PrismPropertyValue(new PolyString(poly.getOrig(), poly.getNorm()),
							value.getOriginType(), value.getOriginObject());
				} else if (val instanceof ProtectedStringType) {
					this.value = value.clone();
					// prevents
					// "Attempt to encrypt protected data that are already encrypted"
					// when applying resulting delta
					((ProtectedStringType) (((PrismPropertyValue) this.value).getValue()))
							.setEncryptedData(null);
				} else {
					this.value = value.clone();
				}
			} else {
				this.value = value.clone();
			}
		}
        
        if (oldValue == null && value instanceof PrismPropertyValue) {
            T val = ((PrismPropertyValue<T>) this.value).getValue();
            if (val instanceof PolyString) {
                PolyString poly = (PolyString)val;
                val = (T) new PolyString(poly.getOrig(), poly.getNorm());
            }
            oldValue = new PrismPropertyValue<T>(CloneUtil.clone(val), this.value.getOriginType(), this.value.getOriginObject());
        }
        
        this.oldValue = oldValue;
    }

    public ItemWrapper getItem() {
        return item;
    }

    public ValueStatus getStatus() {
        return status;
    }

    public PrismValue getValue() {
        return value;
    }

    public PrismValue getOldValue() {
        return oldValue;
    }

    public void setStatus(ValueStatus status) {
        this.status = status;
    }

    public void normalize(PrismContext prismContext) {
		if (value instanceof PrismPropertyValue) {
			PrismPropertyValue ppVal = (PrismPropertyValue) value;
			if (ppVal.getValue() instanceof PolyString) {
				PolyString poly = (PolyString) ppVal.getValue();
				if (poly.getOrig() == null) {
					ppVal.setValue((T) new PolyString(""));
				}
				if (prismContext != null){
					PrismUtil.recomputePrismPropertyValue(ppVal, prismContext);
				}
				
			} else if (ppVal.getValue() instanceof DisplayableValue) {
				DisplayableValue displayableValue = (DisplayableValue) ppVal.getValue();
				ppVal.setValue((T) displayableValue.getValue());
			}
		}
    }

    public boolean hasValueChanged() {
        return oldValue != null ? !oldValue.equals(value) : value != null;
    }

    public boolean isReadonly() {
        return item.isReadonly();
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("value: ");
        builder.append(value);
        builder.append(", old value: ");
        builder.append(oldValue);
        builder.append(", status: ");
        builder.append(status);

        return builder.toString();
    }
}
