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

package com.evolveum.midpoint.web.security;

import com.evolveum.midpoint.security.api.MidPointPrincipal;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;

import com.evolveum.midpoint.web.application.AuthorizationAction;
import com.evolveum.midpoint.web.application.PageDescriptor;
import com.evolveum.midpoint.web.component.menu.top.MenuItem;
import com.evolveum.midpoint.web.util.WebMiscUtil;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.List;

/**
 * @author lazyman
 */
public class SecurityUtils {

    private static final Trace LOGGER = TraceManager.getTrace(SecurityUtils.class);

    public static MidPointPrincipal getPrincipalUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        return getPrincipalUser(authentication);
    }

    public static MidPointPrincipal getPrincipalUser(Authentication authentication) {
        if (authentication == null) {
            LOGGER.debug("Authentication not available in security current context holder.");
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof MidPointPrincipal)) {
            LOGGER.debug("Principal user in security context holder is {} but not type of {}",
                    new Object[]{principal, MidPointPrincipal.class.getName()});
            return null;
        }

        return (MidPointPrincipal) principal;
    }

    public static boolean isMenuAuthorized(MenuItem item) {
        Class clazz = item.getPage();
        if (clazz == null) {
            return false;
        }

        PageDescriptor descriptor = (PageDescriptor) clazz.getAnnotation(PageDescriptor.class);
        if (descriptor == null ){
            return false;
        }


        AuthorizationAction[] actions = descriptor.action();
        List<String> list = new ArrayList<>();
        if (actions != null) {
            for (AuthorizationAction action : actions) {
                list.add(action.actionUri());
            }
        }

        return WebMiscUtil.isAuthorized(list.toArray(new String[list.size()]));
    }
}
