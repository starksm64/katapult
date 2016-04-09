package org.rhd.katapult.test.services;

import com.gargoylesoftware.htmlunit.ElementNotFoundException;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebResponse;
import com.gargoylesoftware.htmlunit.html.*;
import com.gargoylesoftware.htmlunit.util.NameValuePair;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.importer.ZipImporter;
import org.jboss.shrinkwrap.api.spec.EnterpriseArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.swing.JOptionPane;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;

/**
 * Validation of the GithubResource api endpoint
 * This requires the following environment variables to be configure:
 * GITHUB_USERNAME
 * GITHUB_PASSWORD
 */
@RunWith(Arquillian.class)
public class GithubResourceIT {

    /**
     * Deploy the katapult.ear as built since we only test via the rest endpoints
     * @return
     */
    @Deployment(testable = false)
    public static EnterpriseArchive createDeployment() {
        EnterpriseArchive ear = ShrinkWrap.create(EnterpriseArchive.class, "application-ear.ear")
                .as(ZipImporter.class)
                .importFrom(new File("../ear/target/katapult.ear"))
                .as(EnterpriseArchive.class);
        return ear;
    }

    @ArquillianResource
    private URL deploymentUrl;

    /**
     * Validate that we can fork the jboss-developer/jboss-eap-quickstarts repo
     * @throws IOException
     */
    @Test
    public void should_fork_jboss_eap_quickstarts() throws IOException {
        String forkURL = deploymentUrl.toExternalForm() + "api/github/fork?repo=jboss-developer/jboss-eap-quickstarts";
        final WebClient webClient = new WebClient();

        System.out.printf("Calling for: %s\n", forkURL);
        HtmlPage page = webClient.getPage(forkURL);
        WebResponse response = page.getWebResponse();
        int statusCode = response.getStatusCode();
        String html = page.asXml();
        System.out.printf("Page#1 html: %s\n", html);
        // First need to login user into github
        String username = System.getenv("GITHUB_USERNAME");
        String password = System.getenv("GITHUB_PASSWORD");
        if(username == null || password == null)
            throw new IOException("GITHUB_USERNAME and GITHUB_PASSWORD must be configured for this test");

        HtmlInput loginField = page.getElementById("login_field", false);
        HtmlInput passwordField = page.getElementById("password", false);
        HtmlInput commitBtn = page.getElementByName("commit");
        loginField.setValueAttribute(username);
        passwordField.setValueAttribute(password);
        HtmlPage page2 = commitBtn.click();
        List<NameValuePair> postParams = page2.getWebResponse().getWebRequest().getRequestParameters();
        html = page2.asXml();
        System.out.printf("\nPage#2(submit=%s; status=%d) html: %s\n", postParams, page2.getWebResponse().getStatusCode(), html);

        // See if two-factor auth is enabled
        page2 = checkForTwoFactorAuth(page2);

        // See if we need to authorize our github OAuth app
        try {
            HtmlButton authorizeBtn = page2.getElementByName("authorize");
            HtmlPage nextPage = authorizeBtn.click();
            html = nextPage.asXml();
            System.out.printf("\nPage#3(submit=%s; status=%d) html: %s\n", postParams, nextPage.getWebResponse().getStatusCode(), html);
            page2 = nextPage;
        } catch (ElementNotFoundException e) {
            System.err.printf("No OAuth app authorize found on page, checking for repo fork...\n");
        }

        // Validate that we landed on the users fork of jboss-eap-quickstarts
        String title = page2.getTitleText();
        String expected = username+"/jboss-eap-quickstarts";
        if(title.startsWith(expected)) {
            System.out.printf("Successfully forked jboss-eap-quickstarts\n");
        } else {
            throw new IllegalStateException("Title, expected: "+expected+", actual: "+title);
        }
    }

    private HtmlPage checkForTwoFactorAuth(HtmlPage page2) throws IOException {
        HtmlPage returnPage = page2;
        /* See if two factor auth is enabled by looking for the one time password field. The page does not name the form
        or submit button for the otp code, so we have to find it by locating the form with an action="/sessions/two-factor"
        */
        try {
            List<HtmlForm> forms = page2.getForms();
            HtmlForm theForm = null;
            for (HtmlForm form : forms) {
                String action = form.getActionAttribute();
                if (action.equals("/sessions/two-factor")) {
                    theForm = form;
                    break;
                }
            }
            if (theForm != null) {
                HtmlInput otp = theForm.getInputByName("otp");
                List<HtmlElement> buttons = theForm.getElementsByTagName("button");
                HtmlButton submitBtn = null;
                for (HtmlElement button : buttons) {
                    HtmlButton b = (HtmlButton) button;
                    if (b.getTypeAttribute().equalsIgnoreCase("submit")) {
                        submitBtn = b;
                        break;
                    }
                }
                // Yes, prompt for the otp
                String code = JOptionPane.showInputDialog(
                        null, "Enter your GitHub two-factor one time password/code: ",
                        "two-factor code",
                        JOptionPane.PLAIN_MESSAGE);
                otp.type(code);
                returnPage = submitBtn.click();
            } else {
                System.out.printf("No two-factor form found\n");
            }
        } catch (ElementNotFoundException e) {
            // No, go on to verifying the page is the expected jboss-eap-quickstarts fork
            System.out.printf("No otp field indicating two-factor auth enabled\n");
        }
        return returnPage;
    }
}