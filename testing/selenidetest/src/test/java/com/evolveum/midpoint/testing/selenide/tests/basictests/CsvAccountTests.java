package com.evolveum.midpoint.testing.selenide.tests.basictests;

import com.evolveum.midpoint.testing.selenide.tests.AbstractSelenideTest;
import org.openqa.selenium.By;
import org.testng.annotations.Test;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.util.HashMap;
import java.util.Map;

import static com.codeborne.selenide.Condition.*;
import static com.codeborne.selenide.Selectors.byAttribute;
import static com.codeborne.selenide.Selectors.byText;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.close;

/**
 * Created by Kate on 24.08.2015.
 */
public class CsvAccountTests extends AbstractSelenideTest {

    public static final String USER_WITH_CSV_ACCOUNT_NAME = "UserWithCsvAccount";
    public static final String CSV_RESOURCE_XML_PATH = "src/test/resources/mp-resources/localhost-csvfile-resource-advanced-sync.xml";
    public static final String CSV_RESOURCE_NAME = "Localhost CSVfile";
    public static final String CSV_FILE_PATH = "target/test-classes/mp-resources/midpoint-flatfile.csv";
    //csv account fields
    public static final String ACCOUNT_FIRST_NAME_FIELD = "accounts:accountList:0:account:body:containers:0:container:properties:1:property:values:0:value:valueContainer:input:input";
    public static final String ACCOUNT_LAST_NAME_FIELD = "accounts:accountList:0:account:body:containers:0:container:properties:2:property:values:0:value:valueContainer:input:input";
    public static final String ACCOUNT_NAME_FIELD = "accounts:accountList:0:account:body:containers:0:container:properties:3:property:values:0:value:valueContainer:input:input";
    public static final String ACCOUNT_PASSWORD2_FIELD = "accounts:accountList:0:account:body:containers:5:container:properties:0:property:values:0:value:valueContainer:input:password2";
    public static final String ACCOUNT_PASSWORD1_FIELD = "accounts:accountList:0:account:body:containers:5:container:properties:0:property:values:0:value:valueContainer:input:password1";
    //csv account values
    public static final String ACCOUNT_FIRST_NAME_VALUE = "AccountFirstName";
    public static final String ACCOUNT_LAST_NAME_VALUE = "AccountLastName";
    public static final String ACCOUNT_NAME_VALUE = "AccountName";
    public static final String ACCOUNT_PASSWORD_VALUE = "password";

    private Map<String, String> accountFieldsMap = new HashMap<>();

    @Test(priority = 0)
    public void test001createCsvAccount() {
        close();
        login();
        //check if welcome message appears after user logged in
        $(byText("welcome to midPoint")).shouldBe(visible);
        //import csv resource localhost-csvfile-resource-advanced-sync
        updateCsvFilePath();
        importObjectFromFile(CSV_RESOURCE_XML_PATH);
        //create test user
        createUser(USER_WITH_CSV_ACCOUNT_NAME, new HashMap<String, String>());
        //open user's Edit page
        openUsersEditPage(USER_WITH_CSV_ACCOUNT_NAME);
        //click on the menu icon in the Accounts section
        $(byAttribute("about", "accountMenu")).find(byAttribute("class", "dropdown-toggle")).shouldBe(visible).click();
        //click on the Add account menu item
        $(By.linkText("Add account")).shouldBe(visible).click();

        //search for resource in resources list in the opened Select resource(s) window
        searchForElement(CSV_RESOURCE_NAME);
        //check if Localhost CSVfile resource was found
        $(byText("Localhost CSVfile")).shouldBe(visible);

        //select check box in the first row for "Localhost CSVfile" resource
        $(byAttribute("about", "resourcePopupTable")).find(By.tagName("tbody")).find(By.tagName("input"))
                .shouldBe(visible).click();
//        $(By.name("resourcePopup:content:table:table:body:rows:3:cells:1:cell:check")).shouldBe(visible).click();
        //click Add resource(s) button
        $(By.linkText("Add resource(s)")).shouldBe(enabled).click();

        //fill in account fields map
        accountFieldsMap.put(ACCOUNT_FIRST_NAME_FIELD, ACCOUNT_FIRST_NAME_VALUE);
        accountFieldsMap.put(ACCOUNT_LAST_NAME_FIELD, ACCOUNT_LAST_NAME_VALUE);
        accountFieldsMap.put(ACCOUNT_NAME_FIELD, ACCOUNT_NAME_VALUE);
        setFieldValues(accountFieldsMap);
        $(byAttribute("about", "password2")).shouldBe(visible).setValue(PASSWORD1_FIELD_VALUE);
        $(byAttribute("about", "password1")).shouldBe(visible).setValue(PASSWORD1_FIELD_VALUE);
        //click Save button
        $(By.linkText("Save")).shouldBe(visible).click();
        //check if Success message appears
        $(byText("Success")).shouldBe(visible);
        //open user's Edit page by account name value
        openUsersEditPage(ACCOUNT_NAME_VALUE);
        //check that account is displayed in the Accounts section
        $(By.linkText(CSV_RESOURCE_NAME)).shouldBe(visible);
        //check that user's attributes were updated by account's attributes
        $(By.name(USER_NAME_FIELD_NAME)).shouldHave(value(ACCOUNT_NAME_VALUE));
        $(By.name(GIVEN_NAME_FIELD_NAME)).shouldHave(value(ACCOUNT_FIRST_NAME_VALUE));
        $(By.name(FAMILY_NAME_FIELD_NAME)).shouldHave(value(ACCOUNT_LAST_NAME_VALUE));
        //check if account was created in the csv file
        //TODO
        System.out.println(isAccountExistInCsvFile(CSV_FILE_PATH, ACCOUNT_NAME_VALUE, ACCOUNT_FIRST_NAME_VALUE, ACCOUNT_LAST_NAME_VALUE, ACCOUNT_PASSWORD_VALUE));
    }

    /**
     * check if account entry was created in the CSV file
     * after account creating in the MidPoint
     * @param csvFilePath
     * @param accountName
     * @param accountFirstName
     * @param accountLastName
     * @param accountPassword
     * @return
     */
    public boolean isAccountExistInCsvFile(String csvFilePath, String accountName,
                                           String accountFirstName, String accountLastName, String accountPassword) {
        BufferedReader br = null;
        String line = "";
        String csvSplitBy = ",";

        try {

            br = new BufferedReader(new FileReader(csvFilePath));
            while ((line = br.readLine()) != null) {

                // use comma as separator
                String[] account = line.split(csvSplitBy);
                if (account[0].equals("\"" + accountName + "\"") && account[1].equals("\"" + accountFirstName + "\"")
                        && account[2].equals("\"" + accountLastName + "\"")) {
                    return true;
                }

            }

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return false;
    }

    /**
     * Update icfccsvfile:filePath tag value, set the correct
     * path to midpoint-flatfile.csv file
     */
    public void updateCsvFilePath() {
        try {
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
            Document doc = docBuilder.parse(CSV_RESOURCE_XML_PATH);

            // Get the CSV filePath element by tag name directly
            Node filePathNode = doc.getElementsByTagName("icfccsvfile:filePath").item(0);
            filePathNode.setTextContent(System.getProperty("user.dir") + "/src/test/resources/mp-resources/midpoint-flatfile.csv");

            // write the content into xml file
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(new File(CSV_RESOURCE_XML_PATH));
            transformer.transform(source, result);
        } catch (ParserConfigurationException pce) {
            pce.printStackTrace();
        } catch (TransformerException tfe) {
            tfe.printStackTrace();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        } catch (SAXException sae) {
            sae.printStackTrace();
        }
    }
}






























































































































































































































































































































































































































































































































































































































































