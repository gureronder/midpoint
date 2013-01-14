/*
 * Copyright (c) 2013 Evolveum
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
 * Portions Copyrighted 2013 [name of copyright owner]
 */

package com.evolveum.midpoint.web.page.admin.home.component;

import com.evolveum.midpoint.util.MiscUtil;
import com.evolveum.midpoint.web.component.util.LoadableModel;
import com.evolveum.midpoint.web.page.admin.home.dto.PersonalInfoDto;
import com.evolveum.midpoint.web.security.SecurityUtils;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.CredentialsType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.PasswordType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.UserType;
import org.apache.commons.lang.StringUtils;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.AbstractReadOnlyModel;
import org.apache.wicket.model.IModel;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author lazyman
 */
public class PersonalInfoPanel extends Panel {

    private static final String ID_LAST_LOGIN_DATE = "lastLoginDate";
    private static final String ID_LAST_LOGIN_FROM = "lastLoginFrom";
    private static final String ID_LAST_FAIL_DATE = "lastFailDate";
    private static final String ID_LAST_FAIL_FROM = "lastFailFrom";
    private static final String ID_PASSWORD_EXP = "passwordExp";

    private IModel<PersonalInfoDto> model;

    public PersonalInfoPanel(String id) {
        super(id);

        model = new LoadableModel<PersonalInfoDto>(false) {

            @Override
            protected PersonalInfoDto load() {
                return loadPersonalInfo();
            }
        };

        initLayout();
    }

    private PersonalInfoDto loadPersonalInfo() {
        UserType user = SecurityUtils.getPrincipalUser().getUser();
        CredentialsType credentials = user.getCredentials();
        PasswordType password = credentials.getPassword();

        PersonalInfoDto dto = new PersonalInfoDto();
        if (password.getPreviousSuccessfulLogin() != null) {
            dto.setLastLoginDate(MiscUtil.asDate(password.getPreviousSuccessfulLogin().getTimestamp()));
            dto.setLastLoginFrom(password.getPreviousSuccessfulLogin().getFrom());
        }

        if (password.getLastFailedLogin() != null) {
            dto.setLastFailDate(MiscUtil.asDate(password.getLastFailedLogin().getTimestamp()));
            dto.setLastFailFrom(password.getLastFailedLogin().getFrom());
        }
        if (user.getActivation() != null) {
            //todo fix, this is not password expiration date...
            dto.setPasswordExp(MiscUtil.asDate(user.getActivation().getValidTo()));
        }

        return dto;
    }

    private String getSimpleDate(Date date) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, d. MMM yyyy HH:mm:ss");
        return dateFormat.format(date);
    }

    private void initLayout() {
        Label lastLoginDate = new Label(ID_LAST_LOGIN_DATE, new AbstractReadOnlyModel<String>() {

            @Override
            public String getObject() {
                PersonalInfoDto dto = model.getObject();

                return dto.getLastLoginDate() != null ? getSimpleDate(dto.getLastLoginDate()) :
                        PersonalInfoPanel.this.getString("PersonalInfoPanel.never");
            }
        });
        add(lastLoginDate);

        Label lastLoginFrom = new Label(ID_LAST_LOGIN_FROM, new AbstractReadOnlyModel<String>() {

            @Override
            public String getObject() {
                PersonalInfoDto dto = model.getObject();

                return StringUtils.isNotEmpty(dto.getLastLoginFrom()) ? dto.getLastLoginFrom() :
                        PersonalInfoPanel.this.getString("PersonalInfoPanel.undefined");
            }
        });
        add(lastLoginFrom);

        Label lastFailDate = new Label(ID_LAST_FAIL_DATE, new AbstractReadOnlyModel<String>() {

            @Override
            public String getObject() {
                PersonalInfoDto dto = model.getObject();

                return dto.getLastFailDate() != null ? getSimpleDate(dto.getLastFailDate()) :
                        PersonalInfoPanel.this.getString("PersonalInfoPanel.never");
            }
        });
        add(lastFailDate);

        Label lastFailFrom = new Label(ID_LAST_FAIL_FROM, new AbstractReadOnlyModel<String>() {

            @Override
            public String getObject() {
                PersonalInfoDto dto = model.getObject();

                return StringUtils.isNotEmpty(dto.getLastFailFrom()) ? dto.getLastFailFrom() :
                        PersonalInfoPanel.this.getString("PersonalInfoPanel.undefined");
            }
        });
        add(lastFailFrom);

        Label passwordExp = new Label(ID_PASSWORD_EXP, new AbstractReadOnlyModel<String>() {

            @Override
            public String getObject() {
                PersonalInfoDto dto = model.getObject();

                return dto.getPasswordExp() != null ? getSimpleDate(dto.getPasswordExp()) :
                        PersonalInfoPanel.this.getString("PersonalInfoPanel.undefined");
            }
        });
        add(passwordExp);
    }
}