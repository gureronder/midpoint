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
package com.evolveum.midpoint.model.common.expression;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.xml.bind.JAXBException;
import javax.xml.namespace.QName;

import com.evolveum.midpoint.prism.query.ExpressionWrapper;
import com.evolveum.midpoint.security.api.MidPointPrincipal;
import com.evolveum.midpoint.security.api.SecurityEnforcer;
import com.evolveum.midpoint.util.exception.SecurityViolationException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.UserType;
import com.evolveum.prism.xml.ns._public.types_3.ProtectedStringType;

import org.springframework.expression.ExpressionException;
import org.w3c.dom.Element;

import com.evolveum.midpoint.model.api.expr.MidpointFunctions;
import com.evolveum.midpoint.model.common.expression.functions.BasicExpressionFunctions;
import com.evolveum.midpoint.model.common.expression.functions.BasicExpressionFunctionsXPath;
import com.evolveum.midpoint.model.common.expression.functions.FunctionLibrary;
import com.evolveum.midpoint.model.common.expression.functions.LogExpressionFunctions;
import com.evolveum.midpoint.prism.Containerable;
import com.evolveum.midpoint.prism.Item;
import com.evolveum.midpoint.prism.ItemDefinition;
import com.evolveum.midpoint.prism.Objectable;
import com.evolveum.midpoint.prism.PrismConstants;
import com.evolveum.midpoint.prism.PrismContainer;
import com.evolveum.midpoint.prism.PrismContainerDefinition;
import com.evolveum.midpoint.prism.PrismContainerValue;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.PrismObjectDefinition;
import com.evolveum.midpoint.prism.PrismPropertyDefinition;
import com.evolveum.midpoint.prism.PrismPropertyValue;
import com.evolveum.midpoint.prism.PrismValue;
import com.evolveum.midpoint.prism.Recomputable;
import com.evolveum.midpoint.prism.Structured;
import com.evolveum.midpoint.prism.Visitable;
import com.evolveum.midpoint.prism.Visitor;
import com.evolveum.midpoint.prism.crypto.EncryptionException;
import com.evolveum.midpoint.prism.crypto.Protector;
import com.evolveum.midpoint.prism.delta.ItemDelta;
import com.evolveum.midpoint.prism.delta.PlusMinusZero;
import com.evolveum.midpoint.prism.delta.PrismValueDeltaSetTriple;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.path.ItemPathSegment;
import com.evolveum.midpoint.prism.path.NameItemPathSegment;
import com.evolveum.midpoint.prism.polystring.PolyString;
import com.evolveum.midpoint.prism.query.EqualFilter;
import com.evolveum.midpoint.prism.query.InOidFilter;
import com.evolveum.midpoint.prism.query.LogicalFilter;
import com.evolveum.midpoint.prism.query.NoneFilter;
import com.evolveum.midpoint.prism.query.ObjectFilter;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.prism.query.PropertyValueFilter;
import com.evolveum.midpoint.prism.query.RefFilter;
import com.evolveum.midpoint.prism.query.ValueFilter;
import com.evolveum.midpoint.prism.util.JavaTypeConverter;
import com.evolveum.midpoint.prism.util.PrismUtil;
import com.evolveum.midpoint.prism.xml.XsdTypeMapper;
import com.evolveum.midpoint.schema.constants.ExpressionConstants;
import com.evolveum.midpoint.schema.constants.MidPointConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.ObjectResolver;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.DOMUtil;
import com.evolveum.midpoint.util.PrettyPrinter;
import com.evolveum.midpoint.util.exception.ExpressionEvaluationException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SystemException;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ExpressionType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.MappingType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectReferenceType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ResourceType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowType;

/**
 * @author semancik
 *
 */
public class ExpressionUtil {
	
	private static final Trace LOGGER = TraceManager.getTrace(ExpressionUtil.class);
	
    public static <V extends PrismValue> PrismValueDeltaSetTriple<V> toOutputTriple(PrismValueDeltaSetTriple<V> resultTriple, 
    		ItemDefinition outputDefinition, final ItemPath residualPath, final Protector protector, final PrismContext prismContext) {
    	final Class<?> resultTripleValueClass = resultTriple.getRealValueClass();
    	if (resultTripleValueClass == null) {
    		// triple is empty. type does not matter.
    		return resultTriple;
    	}
    	Class<?> expectedJavaType = XsdTypeMapper.toJavaType(outputDefinition.getTypeName());
    	if (expectedJavaType == null) {
    		expectedJavaType = prismContext.getSchemaRegistry().getCompileTimeClass(outputDefinition.getTypeName());
    	}
    	if (resultTripleValueClass == expectedJavaType) {
    		return resultTriple;
    	}
    	final Class<?> finalExpectedJavaType = expectedJavaType;
    	
    	PrismValueDeltaSetTriple<V> clonedTriple = resultTriple.clone();
    	clonedTriple.accept(new Visitor() {
			@Override
			public void visit(Visitable visitable) {
				if (visitable instanceof PrismPropertyValue<?>) {
					PrismPropertyValue<Object> pval = (PrismPropertyValue<Object>)visitable;
					Object realVal = pval.getValue();
					if (realVal != null) {
						if (Structured.class.isAssignableFrom(resultTripleValueClass)) {
							if (residualPath != null && !residualPath.isEmpty()) {
								realVal = ((Structured)realVal).resolve(residualPath);
							}
						}
						if (finalExpectedJavaType != null) {
							Object convertedVal = convertValue(finalExpectedJavaType, realVal, protector, prismContext);
							pval.setValue(convertedVal);
						}
					}
				}
			}
		});
    	return clonedTriple;
    }
    
    /**
     * Slightly more powerful version of "convert" as compared to  JavaTypeConverter.
     * This version can also encrypt/decrypt and also handles polystrings. 
     */
    public static <I,O> O convertValue(Class<O> finalExpectedJavaType, I inputVal, 
    		Protector protector, PrismContext prismContext) {
    	if (inputVal == null) {
    		return null;
    	}
    	if (finalExpectedJavaType.isInstance(inputVal)) {
    		return (O) inputVal;
    	}
    	
    	Object intermediateVal = inputVal;
		if (finalExpectedJavaType == ProtectedStringType.class) {
			String valueToEncrypt;
			if (inputVal instanceof String) {
				valueToEncrypt = (String)inputVal;
			} else {
				valueToEncrypt = JavaTypeConverter.convert(String.class, inputVal);
			}
			try {
				intermediateVal = protector.encryptString(valueToEncrypt);
			} catch (EncryptionException e) {
				throw new SystemException(e.getMessage(),e);
			}
		} else {
		
			if (inputVal instanceof ProtectedStringType) {
				String decryptedString;
				try {
					intermediateVal = protector.decryptString((ProtectedStringType)inputVal);
				} catch (EncryptionException e) {
					throw new SystemException(e.getMessage(),e);
				}
				
			}
		}
		
		O convertedVal = JavaTypeConverter.convert(finalExpectedJavaType, intermediateVal);
		
		PrismUtil.recomputeRealValue(convertedVal, prismContext);

		return convertedVal;
    }

	public static Object resolvePath(ItemPath path, ExpressionVariables variables, Object defaultContext, 
			ObjectResolver objectResolver, String shortDesc, OperationResult result) throws SchemaException, ObjectNotFoundException {
		
		Object root = defaultContext;
		ItemPath relativePath = path;
		ItemPathSegment first = path.first();
		String varDesc = "default context";
		if (first instanceof NameItemPathSegment && ((NameItemPathSegment)first).isVariable()) {
			QName varName = ((NameItemPathSegment)first).getName();
			varDesc = "variable "+PrettyPrinter.prettyPrint(varName);
			relativePath = path.rest();
			if (variables.containsKey(varName)) {
				root = variables.get(varName);
			} else {
				throw new SchemaException("No variable with name "+varName+" in "+shortDesc);
			}
		}
		if (root == null) {
			return null;
		}
		if (relativePath.isEmpty()) {
			return root;
		}
		
		if (root instanceof ObjectReferenceType) {
			root = resolveReference((ObjectReferenceType)root, objectResolver, varDesc, shortDesc, result);
		}

        if (root instanceof Objectable) {
            return (((Objectable) root).asPrismObject()).find(relativePath);
        } if (root instanceof PrismObject<?>) {
			return ((PrismObject<?>)root).find(relativePath);
		} else if (root instanceof PrismContainer<?>) {
			return ((PrismContainer<?>)root).find(relativePath);
		} else if (root instanceof PrismContainerValue<?>) {
			return ((PrismContainerValue<?>)root).find(relativePath);
		} else if (root instanceof Item<?,?>) {
			// Except for container (which is handled above)
			throw new SchemaException("Cannot apply path "+relativePath+" to "+root+" in "+shortDesc);
		} else if (root instanceof ObjectDeltaObject<?>) {
			return ((ObjectDeltaObject<?>)root).findIdi(relativePath);
		} else if (root instanceof ItemDeltaItem<?,?>) {
			return ((ItemDeltaItem<?,?>)root).findIdi(relativePath);
		} else {
			throw new IllegalArgumentException("Unexpected root "+root+" (relative path:"+relativePath+") in "+shortDesc);
		}
	}
	
	private static PrismObject<?> resolveReference(ObjectReferenceType ref, ObjectResolver objectResolver, String varDesc, String contextDescription, 
			OperationResult result) throws SchemaException, ObjectNotFoundException {
		if (ref.getOid() == null) {
    		throw new SchemaException("Null OID in reference in variable "+varDesc+" in "+contextDescription);
    	} else {
	    	try {
	    		
				ObjectType objectType = objectResolver.resolve(ref, ObjectType.class, null, contextDescription, result);
				if (objectType == null) {
					throw new IllegalArgumentException("Resolve returned null for "+ref+" in "+contextDescription);
				}
				return objectType.asPrismObject();
				
			} catch (ObjectNotFoundException e) {
				throw new ObjectNotFoundException("Object not found during variable "+varDesc+" resolution in "+contextDescription+": "+e.getMessage(),e, ref.getOid());
			} catch (SchemaException e) {
				throw new SchemaException("Schema error during variable "+varDesc+" resolution in "+contextDescription+": "+e.getMessage(), e);
			}
    	}
    }

	public static <ID extends ItemDefinition> ID resolveDefinitionPath(ItemPath path, ExpressionVariables variables,
			PrismObjectDefinition<?> defaultContext, String shortDesc) throws SchemaException {
		while (path!=null && !path.isEmpty() && !(path.first() instanceof NameItemPathSegment)) {
			path = path.rest();
		}
		Object root = defaultContext;
		ItemPath relativePath = path;
		NameItemPathSegment first = (NameItemPathSegment)path.first();
		if (first.isVariable()) {
			relativePath = path.rest();
			QName varName = first.getName();
			if (variables.containsKey(varName)) {
				Object varValue = variables.get(varName);
				if (varValue instanceof ItemDeltaItem<?,?>) {
					root = ((ItemDeltaItem<?,?>)varValue).getDefinition();
				} else if (varValue instanceof Item<?,?>) {
					root = ((Item<?,?>)varValue).getDefinition();
				} else if (varValue instanceof Objectable) {
					root = ((Objectable)varValue).asPrismObject().getDefinition();
				} else if (varValue instanceof ItemDefinition) {
					root = varValue;
				} else {
					throw new IllegalStateException("Unexpected content of variable "+varName+": "+varValue+" ("+varValue.getClass()+")");
				}
				if (root == null) {
					throw new IllegalStateException("Null definition in content of variable "+varName+": "+varValue);
				}
			} else {
				throw new SchemaException("No variable with name "+varName+" in "+shortDesc);
			}
		}
		if (root == null) {
			return null;
		}
		if (relativePath.isEmpty()) {
			return (ID) root;
		}
		ItemDefinition result = null;
		if (root instanceof PrismObjectDefinition<?>) {
			return ((PrismObjectDefinition<?>)root).findItemDefinition(relativePath);
		} else if (root instanceof PrismContainerDefinition<?>) {
			return ((PrismContainerDefinition<?>)root).findItemDefinition(relativePath);
		} else if (root instanceof ItemDefinition) {
			// Except for container (which is handled above)
			throw new SchemaException("Cannot apply path "+relativePath+" to "+root+" in "+shortDesc);
		} else {
			throw new IllegalArgumentException("Unexpected root "+root+" in "+shortDesc);
		}
	}

	public static <IV extends PrismValue, ID extends ItemDefinition> ItemDeltaItem<IV,ID> toItemDeltaItem(Object object, ObjectResolver objectResolver,
			String string, OperationResult result) {
		if (object == null) {
			return null;
		}
		
		if (object instanceof ItemDeltaItem<?,?>) {
			return (ItemDeltaItem<IV,ID>) object;
		}
		
        if (object instanceof PrismObject<?>) {
        	return (ItemDeltaItem<IV,ID>) new ObjectDeltaObject((PrismObject<?>)object, null, (PrismObject<?>)object);
        } else if (object instanceof Item<?,?>) {
        	return new ItemDeltaItem<IV,ID>((Item<IV,ID>)object, null, (Item<IV,ID>)object);
        } else if (object instanceof ItemDelta<?,?>) {
        	return new ItemDeltaItem<IV,ID>(null, (ItemDelta<IV,ID>)object, null);
        } else {
        	throw new IllegalArgumentException("Unexpected object "+object+" "+object.getClass());
        }
        
	}

	public static FunctionLibrary createBasicFunctionLibrary(PrismContext prismContext, Protector protector) {
		FunctionLibrary lib = new FunctionLibrary();
		lib.setVariableName(MidPointConstants.FUNCTION_LIBRARY_BASIC_VARIABLE_NAME);
		lib.setNamespace(MidPointConstants.NS_FUNC_BASIC);
		BasicExpressionFunctions func = new BasicExpressionFunctions(prismContext, protector);
		lib.setGenericFunctions(func);
		BasicExpressionFunctionsXPath funcXPath = new BasicExpressionFunctionsXPath(func);
		lib.setXmlFunctions(funcXPath);
		return lib;
	}
	
	public static FunctionLibrary createLogFunctionLibrary(PrismContext prismContext) {
		FunctionLibrary lib = new FunctionLibrary();
		lib.setVariableName(MidPointConstants.FUNCTION_LIBRARY_LOG_VARIABLE_NAME);
		lib.setNamespace(MidPointConstants.NS_FUNC_LOG);
		LogExpressionFunctions func = new LogExpressionFunctions(prismContext);
		lib.setGenericFunctions(func);
		return lib;
	}
	
	public static ObjectQuery evaluateQueryExpressions(ObjectQuery origQuery, ExpressionVariables variables, 
			ExpressionFactory expressionFactory, PrismContext prismContext,
			String shortDesc, Task task, OperationResult result) throws SchemaException, ObjectNotFoundException, ExpressionEvaluationException {
		if (origQuery == null) {
			return null;
		}
		ObjectQuery query = origQuery.clone();
		ObjectFilter evaluatedFilter = evaluateFilterExpressionsInternal(query.getFilter(), variables, expressionFactory, prismContext, shortDesc, task, result);
		query.setFilter(evaluatedFilter);
		return query;
	}
	
	public static ObjectFilter evaluateFilterExpressions(ObjectFilter origFilter, ExpressionVariables variables, 
			ExpressionFactory expressionFactory, PrismContext prismContext,
			String shortDesc, Task task, OperationResult result) throws SchemaException, ObjectNotFoundException, ExpressionEvaluationException {
		if (origFilter == null) {
			return null;
		}
		
		return evaluateFilterExpressionsInternal(origFilter, variables, expressionFactory, prismContext, shortDesc, task, result);
	}
		
	private static ObjectFilter evaluateFilterExpressionsInternal(ObjectFilter filter, ExpressionVariables variables, 
			ExpressionFactory expressionFactory, PrismContext prismContext,
			String shortDesc, Task task, OperationResult result) throws SchemaException, ObjectNotFoundException, ExpressionEvaluationException {
		if (filter == null) {
			return null;
		}
		
		if (filter instanceof InOidFilter){
			ExpressionWrapper expressionWrapper = ((InOidFilter) filter).getExpression();
			if (expressionWrapper == null || expressionWrapper.getExpression() == null) {
                LOGGER.warn("No valueExpression in filter in {}", shortDesc);
                return NoneFilter.createNone();
			}
			
			if (!(expressionWrapper.getExpression() instanceof ExpressionType)) {
                throw new SchemaException("Unexpected expression type " + expressionWrapper.getExpression().getClass() + " in filter in " + shortDesc);
            }

            ExpressionType valueExpression = (ExpressionType) expressionWrapper.getExpression();

            try {
//            	PrismPropertyDefinition outputDefinition = new PrismPropertyDefinition(ExpressionConstants.OUTPUT_ELMENT_NAME, 
//    					DOMUtil.XSD_STRING, prismContext);
//            	outputDefinition.setMaxOccurs(-1);
            	Collection<String> expressionResult = evaluateStringExpression(variables, prismContext, valueExpression, expressionFactory, shortDesc, task, result);
                 
            	if (expressionResult == null || expressionResult.isEmpty()) {
                    LOGGER.debug("Result of search filter expression was null or empty. Expression: {}",
                            valueExpression);
                    return NoneFilter.createNone();
                }
                // TODO: log more context
                LOGGER.trace("Search filter expression in the rule for {} evaluated to {}.", new Object[] {
                        shortDesc, expressionResult });
                
                InOidFilter evaluatedFilter = (InOidFilter) filter.clone();
//                if (evaluatedFilter instanceof EqualFilter) {
                    evaluatedFilter.setOids(expressionResult);
                    evaluatedFilter.setExpression(null);
//                }
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Transformed filter to:\n{}", evaluatedFilter.debugDump());
                }
                return evaluatedFilter;
		} catch (Exception ex){
			throw new ExpressionEvaluationException(ex);
		}
		}else
		if (filter instanceof LogicalFilter) {
			List<ObjectFilter> conditions = ((LogicalFilter) filter).getConditions();
			LogicalFilter evaluatedFilter = ((LogicalFilter)filter).cloneEmpty();
			for (ObjectFilter condition : conditions) {
				ObjectFilter evaluatedSubFilter = evaluateFilterExpressionsInternal(condition, variables, expressionFactory, prismContext, shortDesc, task, result);
				evaluatedFilter.addCondition(evaluatedSubFilter);
			}
			return evaluatedFilter;
			
		} else if (filter instanceof PropertyValueFilter) {
            PropertyValueFilter pvfilter = (PropertyValueFilter) filter;

            if (pvfilter.getValues() != null && !pvfilter.getValues().isEmpty()) {
                // We have value. Nothing to evaluate.
                return pvfilter.clone();
            }

            ExpressionWrapper expressionWrapper = pvfilter.getExpression();
            if (expressionWrapper == null || expressionWrapper.getExpression() == null) {
                LOGGER.warn("No valueExpression in filter in {}", shortDesc);
                return NoneFilter.createNone();
            }
            if (!(expressionWrapper.getExpression() instanceof ExpressionType)) {
                throw new SchemaException("Unexpected expression type " + expressionWrapper.getExpression().getClass() + " in filter in " + shortDesc);
            }

            ExpressionType valueExpression = (ExpressionType) expressionWrapper.getExpression();

            try {
                PrismValue expressionResult = evaluateExpression(variables, prismContext,
                        valueExpression, filter, expressionFactory, shortDesc, task, result);

                if (expressionResult == null || expressionResult.isEmpty()) {
                    LOGGER.debug("Result of search filter expression was null or empty. Expression: {}",
                            valueExpression);
                    return NoneFilter.createNone();
                }
                // TODO: log more context
                LOGGER.trace("Search filter expression in the rule for {} evaluated to {}.", new Object[] {
                        shortDesc, expressionResult });
                
                PropertyValueFilter evaluatedFilter = (PropertyValueFilter) pvfilter.clone();
//                if (evaluatedFilter instanceof EqualFilter) {
//                    ((EqualFilter) evaluatedFilter).setValue(expressionResult);
//                    evaluatedFilter.setExpression(null);
//                } else if (evaluatedFilter instanceof RefFilter) {
                	evaluatedFilter.setValue(expressionResult);
                	evaluatedFilter.setExpression(null);
//                }
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Transformed filter to:\n{}", evaluatedFilter.debugDump());
                }
                return evaluatedFilter;
                
            } catch (RuntimeException ex) {
                LoggingUtils.logException(LOGGER, "Couldn't evaluate expression " + valueExpression + ".", ex);
                throw new SystemException("Couldn't evaluate expression" + valueExpression + ": "
                        + ex.getMessage(), ex);

            } catch (SchemaException ex) {
                LoggingUtils.logException(LOGGER, "Couldn't evaluate expression " + valueExpression + ".", ex);
                throw new SchemaException("Couldn't evaluate expression" + valueExpression + ": "
                        + ex.getMessage(), ex);
            } catch (ObjectNotFoundException ex) {
                LoggingUtils.logException(LOGGER, "Couldn't evaluate expression " + valueExpression + ".", ex);
                throw new ObjectNotFoundException("Couldn't evaluate expression" + valueExpression + ": "
                        + ex.getMessage(), ex);
            } catch (ExpressionEvaluationException ex) {
                LoggingUtils.logException(LOGGER, "Couldn't evaluate expression " + valueExpression + ".", ex);
                throw new ExpressionEvaluationException("Couldn't evaluate expression" + valueExpression + ": "
                        + ex.getMessage(), ex);
            }

        } else {
            throw new IllegalStateException("Unsupported filter type: " + filter.getClass());
        }
		
	}

	private static <V extends PrismValue> V  evaluateExpression(ExpressionVariables variables, PrismContext prismContext,
			ExpressionType expressionType, ObjectFilter filter, ExpressionFactory expressionFactory, 
			String shortDesc, Task task, OperationResult parentResult) throws SchemaException, ObjectNotFoundException, ExpressionEvaluationException {
		
		//TODO rafactor after new query engine is implemented
		ItemDefinition outputDefinition = null;
		if (filter instanceof ValueFilter){
			outputDefinition = ((ValueFilter)filter).getDefinition();
		}
		
		if (outputDefinition == null){
			outputDefinition =  new PrismPropertyDefinition(ExpressionConstants.OUTPUT_ELMENT_NAME, 
					DOMUtil.XSD_STRING, prismContext);
		}
		
		return evaluateExpression(variables, outputDefinition, expressionType, expressionFactory, shortDesc, task, parentResult);
		
		
//		String expressionResult = expressionHandler.evaluateExpression(currentShadow, valueExpression,
//				shortDesc, result);
   	}
	
	public static <V extends PrismValue,D extends ItemDefinition> V evaluateExpression(ExpressionVariables variables,
			D outputDefinition, ExpressionType expressionType,
			ExpressionFactory expressionFactory,
			String shortDesc, Task task, OperationResult parentResult) throws SchemaException, ExpressionEvaluationException, ObjectNotFoundException {
		
		Expression<V,D> expression = expressionFactory.makeExpression(expressionType,
				outputDefinition, shortDesc, parentResult);

		ExpressionEvaluationContext params = new ExpressionEvaluationContext(null, variables, shortDesc, task, parentResult);
		PrismValueDeltaSetTriple<V> outputTriple = expression.evaluate(params);
		
		LOGGER.trace("Result of the expression evaluation: {}", outputTriple);
		
		if (outputTriple == null) {
			return null;
		}
		Collection<V> nonNegativeValues = outputTriple.getNonNegativeValues();
		if (nonNegativeValues == null || nonNegativeValues.isEmpty()) {
			return null;
		}
        if (nonNegativeValues.size() > 1) {
        	throw new ExpressionEvaluationException("Expression returned more than one value ("+nonNegativeValues.size()+") in "+shortDesc);
        }

        return nonNegativeValues.iterator().next();
	}
	
	private static Collection<String> evaluateStringExpression(ExpressionVariables variables, PrismContext prismContext,
			ExpressionType expressionType,
			ExpressionFactory expressionFactory,
			String shortDesc, Task task, OperationResult parentResult) throws SchemaException, ExpressionEvaluationException, ObjectNotFoundException {
		
		PrismPropertyDefinition<String> outputDefinition = new PrismPropertyDefinition(ExpressionConstants.OUTPUT_ELMENT_NAME, 
				DOMUtil.XSD_STRING, prismContext);
    	outputDefinition.setMaxOccurs(-1);
		Expression<PrismPropertyValue<String>,PrismPropertyDefinition<String>> expression = expressionFactory.makeExpression(expressionType,
				outputDefinition, shortDesc, parentResult);

		ExpressionEvaluationContext params = new ExpressionEvaluationContext(null, variables, shortDesc, task, parentResult);
		PrismValueDeltaSetTriple<PrismPropertyValue<String>> outputTriple = expression.evaluate(params);
		
		LOGGER.trace("Result of the expression evaluation: {}", outputTriple);
		
		if (outputTriple == null) {
			return null;
		}
		Collection<PrismPropertyValue<String>> nonNegativeValues = outputTriple.getNonNegativeValues();
		if (nonNegativeValues == null || nonNegativeValues.isEmpty()) {
			return null;
		}
        
        return PrismValue.getRealValuesOfCollection((Collection) nonNegativeValues);
//        return nonNegativeValues.iterator().next();
	}
	
	public static PrismPropertyValue<Boolean> evaluateCondition(ExpressionVariables variables, ExpressionType expressionType,
			ExpressionFactory expressionFactory,
			String shortDesc, Task task, OperationResult parentResult) throws SchemaException, ExpressionEvaluationException, ObjectNotFoundException{
		ItemDefinition outputDefinition = new PrismPropertyDefinition<Boolean>(ExpressionConstants.OUTPUT_ELMENT_NAME, 
				DOMUtil.XSD_BOOLEAN, expressionFactory.getPrismContext());
		return (PrismPropertyValue<Boolean>) evaluateExpression(variables, outputDefinition, expressionType, expressionFactory, shortDesc, task, parentResult);
	}
	
	public static Map<QName, Object> compileVariablesAndSources(ExpressionEvaluationContext params) {
		Map<QName, Object> variablesAndSources = new HashMap<QName, Object>();
        
        if (params.getVariables() != null) {
	        for (Entry<QName, Object> entry: params.getVariables().entrySet()) {
	        	variablesAndSources.put(entry.getKey(), entry.getValue());
	        }
        }
	        
        if (params.getSources() != null) {
	        for (Source<?,?> source: params.getSources()) {
	        	variablesAndSources.put(source.getName(), source);
	        }
        }
        
        return variablesAndSources;
	}

	public static boolean hasExplicitTarget(List<MappingType> mappingTypes) {
		for (MappingType mappingType: mappingTypes) {
			if (hasExplicitTarget(mappingType)) {
				return true;
			}
		}
		return false;
	}

	private static boolean hasExplicitTarget(MappingType mappingType) {
		return mappingType.getTarget() != null;
	}
	
	public static boolean computeConditionResult(Collection<PrismPropertyValue<Boolean>> booleanPropertyValues) {
		if (booleanPropertyValues == null || booleanPropertyValues.isEmpty()) {
			// No value means false
			return false;
		}
		boolean hasFalse = false;
		for (PrismPropertyValue<Boolean> pval: booleanPropertyValues) {
			Boolean value = pval.getValue();
			if (Boolean.TRUE.equals(value)) {
				return true;
			}
			if (Boolean.FALSE.equals(value)) {
				hasFalse = true;
			}
		}
		if (hasFalse) {
			return false;
		}
		// No value or all values null. Return default.
		return true;
	}

	public static PlusMinusZero computeConditionResultMode(boolean condOld, boolean condNew) {
		if (condOld && condNew) {
			return PlusMinusZero.ZERO;
		}
		if (!condOld && !condNew) {
			return null;
		}
		if (condOld && !condNew) {
			return PlusMinusZero.MINUS;
		}
		if (!condOld && condNew) {
			return PlusMinusZero.PLUS;
		}
		throw new IllegalStateException("notreached");
	}

    public static void addActorVariable(ExpressionVariables scriptVariables, SecurityEnforcer securityEnforcer) {
        // There can already be a value, because for mappings, we create the variable before parsing sources.
        // For other scripts we do it just before the execution, to catch all possible places where scripts can be executed.

        UserType oldActor = (UserType) scriptVariables.get(ExpressionConstants.VAR_ACTOR);
        if (oldActor != null) {
            return;
        }

        UserType actor = null;
        try {
            if (securityEnforcer != null) {
                MidPointPrincipal principal = securityEnforcer.getPrincipal();
                if (principal != null) {
                    actor = principal.getUser();
                }
            }
            if (actor == null) {
                LOGGER.error("Couldn't get principal information - the 'actor' variable is set to null");
            }
        } catch (SecurityViolationException e) {
            LoggingUtils.logUnexpectedException(LOGGER, "Couldn't get principal information - the 'actor' variable is set to null", e);
        }
        scriptVariables.addVariableDefinition(ExpressionConstants.VAR_ACTOR, actor);
    }

}
