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

package com.evolveum.midpoint.web.page.login;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.rmi.server.UID;

import javax.imageio.ImageIO;

import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.security.api.AuthorizationConstants;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.web.application.AuthorizationAction;
import com.evolveum.midpoint.web.application.PageDescriptor;
import com.evolveum.midpoint.web.component.menu.top.LocalePanel;
import com.evolveum.midpoint.web.component.menu.top.TopMenuBar;
import com.evolveum.midpoint.web.page.PageBase;
import com.evolveum.midpoint.web.page.admin.home.PageDashboard;
import com.evolveum.midpoint.web.page.forgetpassword.PageForgetPassword;
import com.evolveum.midpoint.web.security.MidPointAuthWebSession;
import com.evolveum.midpoint.xml.ns._public.common.common_3.CredentialsPolicyType;
import com.octo.captcha.service.multitype.GenericManageableCaptchaService;
import com.octo.captcha.service.*;

import org.apache.wicket.Session;
import org.apache.wicket.markup.ComponentTag;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.PasswordTextField;
import org.apache.wicket.markup.html.form.RequiredTextField;
import org.apache.wicket.markup.html.image.Image;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.request.resource.DynamicImageResource;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.apache.wicket.validation.IValidatable;
import org.apache.wicket.validation.validator.AbstractValidator;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author mserbak
 */
@PageDescriptor(url = "/login")
public class PageLogin extends PageBase {

	PageBase page = getPageBase();
	private static final String ID_LOGIN_FORM = "loginForm";

	private static final String ID_USERNAME = "username";
	private static final String ID_PASSWORD = "password";

	protected static final String OPERATION_LOAD_RESET_PASSWORD_POLICY = "LOAD PASSWORD RESET POLICY";
	@SpringBean(name = "captchaService")
	private static CaptchaServiceSingleton captchaService;
	private String challengeId = null;
	private String challengeResponse;

	// private static final ImageCaptchaService captchaService = new
	// DefaultManageableImageCaptchaService();

	public PageLogin() {
		TopMenuBar menuBar = getTopMenuBar();
		menuBar.addOrReplace(new LocalePanel(TopMenuBar.ID_RIGHT_PANEL));

		Form form = new Form(ID_LOGIN_FORM) {

			
			@Override
			protected void onSubmit() {
				MidPointAuthWebSession session = MidPointAuthWebSession
						.getSession();

				RequiredTextField<String> username = (RequiredTextField) get(ID_USERNAME);
				PasswordTextField password = (PasswordTextField) get(ID_PASSWORD);
				 info(getLocalizer().getString("captcha.validation.succeeded",
				          this));
				if (session.authenticate(username.getModelObject(),
						password.getModelObject())) {
					setResponsePage(PageDashboard.class);

				}
			}

			
		};

		OperationResult parentResult = new OperationResult(
				OPERATION_LOAD_RESET_PASSWORD_POLICY);

		try {
			CredentialsPolicyType creds = getModelInteractionService()
					.getCredentialsPolicy(null, parentResult);
			BookmarkablePageLink<String> link = new BookmarkablePageLink<String>(
					"forgetpassword", PageForgetPassword.class);
			boolean linkIsVisible = false;
			if (creds != null) {
				if (creds.getSecurityQuestions().getQuestionNumber() != null) {
					linkIsVisible = true;

				}

			}
			link.setVisible(linkIsVisible);
			form.add(link);
		} catch (ObjectNotFoundException | SchemaException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		

		DynamicImageResource res = new DynamicImageResource() {
			
		
			
			@Override
			protected byte[] getImageData(Attributes attributes) {
				ByteArrayOutputStream os = new ByteArrayOutputStream();
				challengeId = new UID().toString();
				BufferedImage challenge = captchaService.getService().
						getImageChallengeForID(challengeId, Session.get().getLocale());
				
				try {
					
		            ImageIO.write(challenge, "jpeg", os);
		            return os.toByteArray();
		          } catch (Exception e) {
		            throw new RuntimeException(e);
		          }
				
			}
			
		};

		form.add(new RequiredTextField(ID_USERNAME, new Model<String>()));
		form.add(new PasswordTextField(ID_PASSWORD, new Model<String>()));
		form.add(new Image("captchaImage",res));
		form.add(new RequiredTextField("response", new PropertyModel(this,
		          "challengeResponse")) {
			@Override
			  protected final void onComponentTag(final ComponentTag tag) {
		          super.onComponentTag(tag);
		          tag.put("value", "");
		        }
		}.add(new AbstractValidator() {

			@Override
			protected void onValidate(IValidatable validatable) {
				if (!captchaService.getService().validateResponseForID(challengeId,
			              validatable.getValue())) {
			            error(validatable);
			          }				
				}
			 @Override
		        protected String resourceKey() {
		          return "captcha.validation.failed";
		        }
		}));
			
		form.add(new FeedbackPanel("feedback"));

		add(form);
	}
	public String getChallengeResponse() {
	      return challengeResponse;
	    }

	    public void setChallengeResponse(String challengeResponse) {
	      this.challengeResponse = challengeResponse;
	    }

	/*
	 * @Override protected IModel<String> createPageTitleModel() { return new
	 * Model<>(""); }
	 */

	public PageBase getPageBase() {
		return (PageBase) getPage();
	}
}
