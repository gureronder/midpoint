package com.evolveum.midpoint.testing.selenide.tests.basictests;

import static com.codeborne.selenide.Condition.*;

import com.codeborne.selenide.SelenideElement;
import com.evolveum.midpoint.testing.selenide.tests.AbstractSelenideTest;
import org.openqa.selenium.By;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.codeborne.selenide.Selectors.byAttribute;
import static com.codeborne.selenide.Selectors.byText;
import static com.codeborne.selenide.Selectors.byValue;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.close;

/**
 * Created by Kate on 22.08.2015.
 */
public class RoleTests extends AbstractSelenideTest {
    //role fields
    public static final String ROLE_NAME_FIELD = "name:textWrapper:text";
    public static final String ROLE_DISPLAY_NAME_FIELD = "displayName:textWrapper:text";
    public static final String ROLE_DESCRIPTION_FIELD = "description:textWrapper:text";
    public static final String ROLE_TYPE_FIELD = "roleType:textWrapper:text";
    public static final String ROLE_IDENTIFIER_FIELD = "identifier:textWrapper:text";
    public static final String ROLE_RISK_LEVEL_FIELD = "riskLevel:textWrapper:text";
    //role values
    public static final String ROLE_NAME_VALUE = "TestRole";
    public static final String ROLE_DISPLAY_NAME_VALUE = "RoleDisplayName";
    public static final String ROLE_DESCRIPTION_VALUE = "RoleDescription";
    public static final String ROLE_TYPE_VALUE = "RoleType";
    public static final String ROLE_IDENTIFIER_VALUE = "RoleIdentifier";
    public static final String ROLE_RISK_LEVEL_VALUE = "RoleRiskLevel";

    private Map<String, String> roleAttributes = new HashMap<>();

    @Test(priority = 0)
    public void test001createRoleTest(){
        close();
        login();
        //click Roles menu
        $(By.partialLinkText("Roles")).shouldBe(visible).click();
        //click New role menu item
        $(By.linkText("New role")).shouldBe(visible).click();
        //set new role attributes
        roleAttributes.put(ROLE_NAME_FIELD, ROLE_NAME_VALUE);
        roleAttributes.put(ROLE_DISPLAY_NAME_FIELD, ROLE_DISPLAY_NAME_VALUE);
        roleAttributes.put(ROLE_DESCRIPTION_FIELD, ROLE_DESCRIPTION_VALUE);
        roleAttributes.put(ROLE_TYPE_FIELD, ROLE_TYPE_VALUE);
        roleAttributes.put(ROLE_IDENTIFIER_FIELD, ROLE_IDENTIFIER_VALUE);
        roleAttributes.put(ROLE_RISK_LEVEL_FIELD, ROLE_RISK_LEVEL_VALUE);
        setFieldValues(roleAttributes);
        //click Save button
        $(By.linkText("Save")).shouldBe(visible).click();
        //check if Success message appears
        $(byText("Success")).shouldBe(visible);
        //search for newly created role
        searchForElement(ROLE_NAME_VALUE);
        //click on the found role
        $(By.linkText(ROLE_NAME_VALUE)).shouldBe(visible).click();
        //check role attributes are filled in with correct values
        checkObjectAttributesValues(roleAttributes);

    }

    @Test(priority = 1, dependsOnMethods = {"test001createRoleTest"})
    public void test002updateRoleTest(){
        //click Roles menu
        $(By.partialLinkText("Roles")).shouldBe(visible).click();
        //click List roles menu item
        $(By.linkText("List roles")).shouldBe(visible).click();
        //search for newly created role
        searchForElement(ROLE_NAME_VALUE);
        //click on the found role
        $(By.linkText(ROLE_NAME_VALUE)).shouldBe(visible).click();
        //update role attributes values
        Set<String> attributeFielldNames = roleAttributes.keySet();
        Map<String, String> roleAttributesUpdated = new HashMap<>();
        for (String attributeFieldName : attributeFielldNames){
            roleAttributesUpdated.put(attributeFieldName, roleAttributes.get(attributeFieldName) + UPDATED_VALUE);
        }
        setFieldValues(roleAttributesUpdated);
        //click Save button
        $(By.linkText("Save")).shouldBe(visible).click();
        //check if Success message appears
        $(byText("Success")).shouldBe(visible);
        //search for newly created role
        searchForElement(ROLE_NAME_VALUE + UPDATED_VALUE);
        //click on the found role
        $(By.linkText(ROLE_NAME_VALUE  + UPDATED_VALUE)).shouldBe(visible).click();
        //check role attributes are filled in with correct values
        checkObjectAttributesValues(roleAttributesUpdated);
    }

    @Test (priority = 2, dependsOnMethods = {"test001createRoleTest"})
    public void test003deleteRoleTest(){
        //click Roles menu
        $(By.partialLinkText("Roles")).shouldBe(visible).click();
        //click List roles menu item
        $(By.linkText("List roles")).shouldBe(visible).click();
        //search for created role
        searchForElement(ROLE_NAME_VALUE + UPDATED_VALUE);
        //select found role checkbox
        $(byAttribute("type", "checkbox")).shouldBe(visible).click();
        //click on the menu icon in the upper right corner of the roles list
        $(By.className("cog")).find(By.className("caret")).shouldBe(visible).click();
        //click Delete menu item
        $(By.linkText("Delete")).shouldBe(visible).click();
        //click Yes button in the Confirm delete window
        $(By.linkText("Yes")).shouldBe(visible).click();
        //check if success message appears after role deletion
        $(byText("The role(s) have been successfully deleted.")).shouldBe(visible);
    }

}
