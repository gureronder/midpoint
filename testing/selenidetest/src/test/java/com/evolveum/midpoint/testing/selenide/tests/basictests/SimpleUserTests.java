package com.evolveum.midpoint.testing.selenide.tests.basictests;

import com.evolveum.midpoint.testing.selenide.tests.AbstractSelenideTest;
import org.openqa.selenium.By;
import org.springframework.stereotype.Component;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

import static com.codeborne.selenide.Condition.*;
import static com.codeborne.selenide.Selectors.byAttribute;
import static com.codeborne.selenide.Selectors.byText;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.close;

/**
 * Created by Kate on 14.08.2015.
 */
@Component
public class SimpleUserTests extends AbstractSelenideTest {
    //test data
    public static final String UPDATED_STRING = "_updated";
    public static final String SIMPLE_USER_NAME = "SimpleUserName";
    public static final String NOT_UPDATED_USER_NAME = "NotUpdatedUserName";
    public static final String EXISTING_USER_NAME = "ExistingUserName";
    public static final String ALL_FIELDS_USER_NAME = "AllFieldsUserName";
    public static final String USER_LIST_XML_FILEPATH = "src/test/resources/user/user-list.xml";


    /**
     * Create user test with Name field only
     */
    @Test (priority = 0)
    public void test001createUserWithUserNameOnlyTest() {
        close();
        login();

        //check if welcome message appears after user logged in
        $(byText("welcome to midPoint")).shouldBe(visible);

        //create user with filled user name only
        createUser(SIMPLE_USER_NAME, new HashMap<String, String>());

        //check if Success message appears after user saving
        $(byText("Success")).shouldBe(visible);

        //search for the created in users list
        searchForElement(SIMPLE_USER_NAME);
        $(By.linkText(SIMPLE_USER_NAME)).shouldBe(visible).click();
    }

   /**
     * Create user test with all fields filled in
     */
    @Test (priority = 1)
    public void test002createUserWithAllFieldsTest() {
        //create user with filled user name only
        Map<String, String> userAttributesMap = getAllUserAttributesMap();
        createUser(ALL_FIELDS_USER_NAME, userAttributesMap);

        //check if Success message appears after user saving
        $(byText("Success")).shouldBe(visible);

        //search for the created user in users list
        searchForElement(ALL_FIELDS_USER_NAME);
        //check if all fields are saved with values
        $(By.linkText(ALL_FIELDS_USER_NAME)).shouldBe(visible).click();
        checkObjectAttributesValues(userAttributesMap);
    }

    /**
     * Updating user fields
     */
    @Test(dependsOnMethods = {"test001createUserWithUserNameOnlyTest"}, priority = 3)
    public void test003editUserTest() {
        //open user's Edit page
        openUsersEditPage(SIMPLE_USER_NAME);

        //click on the menu icon in the User details section
        $(byText("User details")).parent().parent().find(byAttribute("about", "dropdownMenu"))
                .shouldBe(visible).click();
        //click on Show empty fields menu item
        $(By.linkText("Show empty fields")).shouldBe(visible).click();

        Map<String, String> userFieldsMap = new HashMap<>();
        userFieldsMap.put(USER_NAME_FIELD_NAME, SIMPLE_USER_NAME + UPDATED_STRING);
        userFieldsMap.put(DESCRIPTION_FIELD_NAME, DESCRIPTION_FIELD_VALUE + UPDATED_STRING);
        userFieldsMap.put(FULL_NAME_FIELD_NAME, FULL_NAME_FIELD_VALUE + UPDATED_STRING);
        userFieldsMap.put(GIVEN_NAME_FIELD_NAME, GIVEN_NAME_FIELD_VALUE + UPDATED_STRING);
        userFieldsMap.put(FAMILY_NAME_FIELD_NAME, FAMILY_NAME_FIELD_VALUE + UPDATED_STRING);
        userFieldsMap.put(NICKNAME_FIELD_NAME, NICKNAME_FIELD_VALUE + UPDATED_STRING);
        //update user details
        setFieldValues(userFieldsMap);
        //click Save button
        $(By.linkText("Save")).shouldBe(visible).click();

        //check if Success message appears after user saving
        $(byText("Success")).shouldBe(visible);

        //search for user in users list
        searchForElement(SIMPLE_USER_NAME + UPDATED_STRING);

        //check if updated values are displayed in the users list
        $(byText(GIVEN_NAME_FIELD_VALUE + UPDATED_STRING)).shouldBe(visible);
        $(byText(FAMILY_NAME_FIELD_VALUE + UPDATED_STRING)).shouldBe(visible);
        $(byText(FULL_NAME_FIELD_VALUE + UPDATED_STRING)).shouldBe(visible);

        //click on the user link
        $(By.linkText(SIMPLE_USER_NAME + UPDATED_STRING)).shouldBe(visible).click();

        //check if updated values are displayed on the user's Edit page
        checkObjectAttributesValues(userFieldsMap);
    }

    /**
     * Cancelling of user update
     */
    @Test(dependsOnMethods = {"test001createUserWithUserNameOnlyTest"}, priority = 2)
    public void test004cancelUserUpdateTest() {
        //create user
        createUser(NOT_UPDATED_USER_NAME, new HashMap<String, String>());
        //open user's Edit page
        openUsersEditPage(NOT_UPDATED_USER_NAME);
        //click on the menu icon in the User details section
        $(byText("User details")).parent().parent().find(byAttribute("about", "dropdownMenu"))
                .shouldBe(visible).click();
        //click on Show empty fields menu item
        $(By.linkText("Show empty fields")).shouldBe(visible).click();

        //update Name field
        $(By.name("userForm:body:containers:0:container:properties:0:property:values:0:value:valueContainer:input:input"))
                .shouldBe(visible).clear();
        $(By.name("userForm:body:containers:0:container:properties:0:property:values:0:value:valueContainer:input:input"))
                .shouldBe(visible).setValue(NOT_UPDATED_USER_NAME + "_updated");

        //click Back button
        $(By.linkText("Back")).shouldBe(visible).click();

        //search for user in users list
        searchForElement(NOT_UPDATED_USER_NAME);

        //check if user name wasn't updated
        $(byText(NOT_UPDATED_USER_NAME)).shouldBe(visible);
    }

    /**
     * Attempt to create user with all empty fields
     */
    @Test(alwaysRun = true, priority = 4)
    public void test005createUserWithEmptyFieldsTest() {
        close();
        login();
        //create user with all empty fields
        createUser("", new HashMap<String, String>());

        //check if error message appears after user saving
        $(byAttribute("title", "Partial error")).shouldHave(text("No name in new object"));
    }

    /**
     * Attempt to create user with existing name
     */
    @Test(alwaysRun = true, priority = 5)
    public void test006createUserWithExistingNameTest() {
        //create user
        createUser(EXISTING_USER_NAME, new HashMap<String, String>());
        //check if Success message appears after user saving
        $(byText("Success")).shouldBe(visible);
        //try to create user with the same name
        createUser(EXISTING_USER_NAME, new HashMap<String, String>());
        //check if error message appears
        $(byAttribute("title", "Partial error")).find(By.tagName("span")).shouldHave(text("Error processing focus"));
    }

    @Test(alwaysRun = true, priority = 6)
    public void test007deleteUserTest() {
        //open Users -> List users
        openListUsersPage();

        //search for user in users list
        searchForElement(SIMPLE_USER_NAME + UPDATED_STRING);

        //select checkbox next to the found user
        $(byAttribute("about", "table")).find(By.tagName("tbody")).find(By.tagName("input")).shouldBe(visible).click();

        //click on the menu icon in the upper right corner of the users list
        $(byAttribute("about", "dropdownMenu")).shouldBe(visible).click();
        //click on Delete menu item
        $(By.linkText("Delete")).shouldBe(visible).click();

        //click on Yes button in the opened "Confirm delete" window
        $(By.linkText("Yes")).shouldBe(visible).click();

        //check if operation success message appears
        $(byText("Success")).shouldBe(visible);
        //search for user in users list
        searchForElement(SIMPLE_USER_NAME + UPDATED_STRING);
        //check the user was not found during user search
        $(byText("No matching result found.")).shouldBe(visible);
    }

    @Test (priority = 7)
    public void test008pagingThroughUsersList(){
        //import 100 users from xml file
        importObjectFromFile(USER_LIST_XML_FILEPATH);
        //open user's list page
        openListUsersPage();
        //check if administrator user is present on the first list user page
        $(By.linkText(ADMIN_LOGIN)).shouldBe(visible);
        //click on the second number page
        $(By.linkText("2")).shouldBe(visible).click();
        //check that administrator user isn't present on the second list user page
        $(By.linkText(ADMIN_LOGIN)).shouldNotBe(visible);
        //check paging message shows the correct page numbers
        $(By.name("table:table:bottomToolbars:toolbars:2:td:pageSize:popButton")).parent().shouldHave(text("Displaying 11 to 20"));
        //click on the next  page button
        $(By.linkText(">")).shouldBe(visible).click();
        //check paging message shows the correct page numbers
        $(By.name("table:table:bottomToolbars:toolbars:2:td:pageSize:popButton")).parent().shouldHave(text("Displaying 21 to 30"));
        //click on the previous page button
        $(By.linkText("<")).shouldBe(visible).click();
        //check paging message shows the correct page numbers
        $(By.name("table:table:bottomToolbars:toolbars:2:td:pageSize:popButton")).parent().shouldHave(text("Displaying 11 to 20"));

    }


}
