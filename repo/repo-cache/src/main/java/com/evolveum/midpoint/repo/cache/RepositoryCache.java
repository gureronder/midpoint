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
package com.evolveum.midpoint.repo.cache;

import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.delta.ItemDelta;
import com.evolveum.midpoint.prism.query.ObjectPaging;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.repo.api.RepoAddOptions;
import com.evolveum.midpoint.repo.api.RepositoryService;
import com.evolveum.midpoint.schema.GetOperationOptions;
import com.evolveum.midpoint.schema.RelationalValueSearchType;
import com.evolveum.midpoint.schema.RepositoryDiag;
import com.evolveum.midpoint.schema.ResultHandler;
import com.evolveum.midpoint.schema.SearchResultList;
import com.evolveum.midpoint.schema.SearchResultMetadata;
import com.evolveum.midpoint.schema.SelectorOptions;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.util.exception.ObjectAlreadyExistsException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.FocusType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.TaskType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.UserType;

import org.apache.commons.lang.Validate;

import java.util.Collection;
import java.util.List;

import javax.xml.namespace.QName;

/**
 * Read-through write-through per-session repository cache.
 * 
 * TODO doc
 * TODO logging perf measurements
 *
 * @author Radovan Semancik
 *
 */
public class RepositoryCache implements RepositoryService {

	private static ThreadLocal<Cache> cacheInstance = new ThreadLocal<>();

	private RepositoryService repository;
	
	private static final Trace LOGGER = TraceManager.getTrace(RepositoryCache.class);
	private static final Trace PERFORMANCE_ADVISOR = TraceManager.getPerformanceAdvisorTrace();

	private PrismContext prismContext;

	public RepositoryCache() {
    }
	
    public void setRepository(RepositoryService service, PrismContext prismContext) {
        Validate.notNull(service, "Repository service must not be null.");
		Validate.notNull(prismContext, "Prism context service must not be null.");
        this.repository = service;
		this.prismContext = prismContext;
    }
	
	private static Cache getCache() {
		return cacheInstance.get();
	}
	
	public static void init() {
	}
	
	public static void destroy() {
		Cache.destroy(cacheInstance, LOGGER);
	}
	
	public static void enter() {
		Cache.enter(cacheInstance, Cache.class, LOGGER);
	}
	
	public static void exit() {
		Cache.exit(cacheInstance, LOGGER);
	}

	public static boolean exists() {
		return Cache.exists(cacheInstance);
	}

	public static String debugDump() {
		return Cache.debugDump(cacheInstance);
	}

	@Override
	public <T extends ObjectType> PrismObject<T> getObject(Class<T> type, String oid,
			Collection<SelectorOptions<GetOperationOptions>> options, OperationResult parentResult) throws ObjectNotFoundException, SchemaException {
		if (!isCacheable(type) || !nullOrHarmlessOptions(options)) {
			log("Cache: PASS {} ({})", oid, type.getSimpleName());
			return repository.getObject(type, oid, options, parentResult);
		}
		Cache cache = getCache();
		if (cache == null) {
			log("Cache: NULL {} ({})", oid, type.getSimpleName());
		} else {
			PrismObject<T> object = (PrismObject) cache.getObject(oid);
			if (object != null) {
				// TODO: result?
				log("Cache: HIT {} ({})", oid, type.getSimpleName());
				return object.clone();
			}
			log("Cache: MISS {} ({})", oid, type.getSimpleName());
		}
		PrismObject<T> object = repository.getObject(type, oid, null, parentResult);
		cacheObject(cache, object);
		return object;
	}

	private boolean isCacheable(Class<?> type) {
		if (type.equals(TaskType.class)) {
			return false;
		}
//		if (ShadowType.class.isAssignableFrom(type)) {
//			return false;
//		}
		return true;
	}

	@Override
	public <T extends ObjectType> String addObject(PrismObject<T> object, RepoAddOptions options, OperationResult parentResult)
			throws ObjectAlreadyExistsException, SchemaException {
		String oid = repository.addObject(object, options, parentResult);
		Cache cache = getCache();
		// DON't cache the object here. The object may not have proper "JAXB" form, e.g. some pieces may be
		// DOM element instead of JAXB elements. Not to cache it is safer and the performance loss
		// is acceptable.
		if (cache != null) {
			// Invalidate the cache entry if it happens to be there
			cache.removeObject(oid);
			cache.clearQueryResults(object.getCompileTimeClass());
		}
		return oid;
	}
	
	@Override
	public <T extends ObjectType> SearchResultList<PrismObject<T>> searchObjects(Class<T> type, ObjectQuery query, 
			Collection<SelectorOptions<GetOperationOptions>> options, OperationResult parentResult) throws SchemaException {
		if (!isCacheable(type) || !nullOrHarmlessOptions(options)) {
			log("Cache: PASS ({})", type.getSimpleName());
			return repository.searchObjects(type, query, options, parentResult);
		}
		Cache cache = getCache();
		if (cache == null) {
			log("Cache: NULL ({})", type.getSimpleName());
		} else {
			SearchResultList queryResult = cache.getQueryResult(type, query, prismContext);
			if (queryResult != null) {
				log("Cache: HIT {} ({})", query, type.getSimpleName());
				return queryResult.clone();
			}
			log("Cache: MISS {} ({})", query, type.getSimpleName());
		}

		// Cannot satisfy from cache, pass down to repository
		SearchResultList<PrismObject<T>> objects = repository.searchObjects(type, query, options, parentResult);
		if (cache != null && options == null) {
			for (PrismObject<T> object : objects) {
				cacheObject(cache, object);
			}
			// TODO cloning before storing into cache?
			cache.putQueryResult(type, query, objects, prismContext);
		}
		return objects;
	}
	
	/* (non-Javadoc)
	 * @see com.evolveum.midpoint.repo.api.RepositoryService#searchObjectsIterative(java.lang.Class, com.evolveum.midpoint.prism.query.ObjectQuery, com.evolveum.midpoint.schema.ResultHandler, com.evolveum.midpoint.schema.result.OperationResult)
	 */
	@Override
	public <T extends ObjectType> SearchResultMetadata searchObjectsIterative(Class<T> type, ObjectQuery query,
			final ResultHandler<T> handler, Collection<SelectorOptions<GetOperationOptions>> options, OperationResult parentResult) throws SchemaException {
		// TODO use cached query result if applicable
		log("Cache: PASS searchObjectsIterative ({})", type.getSimpleName());
		final Cache cache = getCache();
		ResultHandler<T> myHandler = new ResultHandler<T>() {
			@Override
			public boolean handle(PrismObject<T> object, OperationResult parentResult) {
				cacheObject(cache, object);
				return handler.handle(object, parentResult);
			}
		};
		return repository.searchObjectsIterative(type, query, myHandler, options, parentResult);
	}
	
	@Override
	public <T extends ObjectType> int countObjects(Class<T> type, ObjectQuery query, OperationResult parentResult)
			throws SchemaException {
		// TODO use cached query result if applicable
		log("Cache: PASS countObjects ({})", type.getSimpleName());
		return repository.countObjects(type, query, parentResult);
	}

	@Override
	public <T extends ObjectType> void modifyObject(Class<T> type, String oid, Collection<? extends ItemDelta> modifications,
			OperationResult parentResult) throws ObjectNotFoundException, SchemaException, ObjectAlreadyExistsException {
		repository.modifyObject(type, oid, modifications, parentResult);
		// this changes the object. We are too lazy to apply changes ourselves, so just invalidate
		// the object in cache
		Cache cache = getCache();
		if (cache != null) {
			cache.removeObject(oid);
			cache.clearQueryResults(type);
		}
	}

	@Override
	public <T extends ObjectType> void deleteObject(Class<T> type, String oid, OperationResult parentResult)
			throws ObjectNotFoundException {
		repository.deleteObject(type, oid, parentResult);
		Cache cache = getCache();
		if (cache != null) {
			cache.removeObject(oid);
			cache.clearQueryResults(type);
		}
	}
	
	@Override
	public <F extends FocusType> PrismObject<F> searchShadowOwner(
			String shadowOid, Collection<SelectorOptions<GetOperationOptions>> options, OperationResult parentResult) throws ObjectNotFoundException {
		// TODO cache the search operation?
		PrismObject<F> ownerObject = repository.searchShadowOwner(shadowOid, options, parentResult);
		if (ownerObject != null && nullOrHarmlessOptions(options)) {
			cacheObject(getCache(), ownerObject);
		}
		return ownerObject;
	}

	private boolean nullOrHarmlessOptions(Collection<SelectorOptions<GetOperationOptions>> options) {
		if (options == null || options.isEmpty()) {
			return true;
		}
		if (options.size() > 1) {
			return false;
		}
		SelectorOptions<GetOperationOptions> selectorOptions = options.iterator().next();
		if (!selectorOptions.isRoot()) {
			return false;
		}
		GetOperationOptions options1 = selectorOptions.getOptions();
		if (options1 == null || options1.equals(new GetOperationOptions()) || options1.equals(GetOperationOptions.createAllowNotFound())) {
			return true;
		}
		return false;
	}

	@Override
	@Deprecated
	public PrismObject<UserType> listAccountShadowOwner(String accountOid, OperationResult parentResult)
			throws ObjectNotFoundException {
		return repository.listAccountShadowOwner(accountOid, parentResult);
	}

	@Override
	public <T extends ShadowType> List<PrismObject<T>> listResourceObjectShadows(String resourceOid,
			Class<T> resourceObjectShadowType, OperationResult parentResult) throws ObjectNotFoundException,
            SchemaException {
		return repository.listResourceObjectShadows(resourceOid, resourceObjectShadowType, parentResult);
	}
	
	/* (non-Javadoc)
	 * @see com.evolveum.midpoint.repo.api.RepositoryService#getVersion(java.lang.Class, java.lang.String, com.evolveum.midpoint.schema.result.OperationResult)
	 */
	@Override
	public <T extends ObjectType> String getVersion(Class<T> type, String oid, OperationResult parentResult)
			throws ObjectNotFoundException, SchemaException {
		if (!isCacheable(type)) {
			log("Cache: PASS {} ({})", oid, type.getSimpleName());
			return repository.getVersion(type, oid, parentResult);
		}
		Cache cache = getCache();
		if (cache == null) {
			log("Cache: NULL {} ({})", oid, type.getSimpleName());
		} else {
			String version = cache.getObjectVersion(oid);
			if (version != null) {
				log("Cache: HIT {} ({})", oid, type.getSimpleName());
				return version;
			}
			log("Cache: MISS {} ({})", oid, type.getSimpleName());
		}
		String version = repository.getVersion(type, oid, parentResult);
		cacheObjectVersion(cache, oid, version);
		return version;
	}

	/* (non-Javadoc)
	 * @see com.evolveum.midpoint.repo.api.RepositoryService#getRepositoryDiag()
	 */
	@Override
	public RepositoryDiag getRepositoryDiag() {
		return repository.getRepositoryDiag();
	}

	/* (non-Javadoc)
	 * @see com.evolveum.midpoint.repo.api.RepositoryService#repositorySelfTest(com.evolveum.midpoint.schema.result.OperationResult)
	 */
	@Override
	public void repositorySelfTest(OperationResult parentResult) {
		repository.repositorySelfTest(parentResult);
	}

    @Override
    public void testOrgClosureConsistency(boolean repairIfNecessary, OperationResult testResult) {
        repository.testOrgClosureConsistency(repairIfNecessary, testResult);
    }

    private <T extends ObjectType> void cacheObject(Cache cache, PrismObject<T> object) {
		if (cache != null) {
			cache.putObject(object.getOid(), (PrismObject<ObjectType>) object.clone());
		}
	}

	private <T extends ObjectType> void cacheObjectVersion(Cache cache, String oid, String version) {
		if (cache != null) {
			cache.putObjectVersion(oid, version);
		}
	}

	@Override
	public boolean isAnySubordinate(String upperOrgOid, Collection<String> lowerObjectOids)
			throws SchemaException {
		return repository.isAnySubordinate(upperOrgOid, lowerObjectOids);
	}

	private void log(String message, Object... params) {
		if (LOGGER.isTraceEnabled()) {
			LOGGER.trace(message, params);
		}
		if (PERFORMANCE_ADVISOR.isTraceEnabled()) {
			PERFORMANCE_ADVISOR.trace(message, params);
		}
	}
}
