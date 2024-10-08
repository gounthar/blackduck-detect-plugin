package com.blackduck.integration.jenkins.detect.extensions.global;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import hudson.util.FormValidation;

public class DetectGlobalConfigTest {
    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    @Test
    public void testMissingCredentials() {
        DetectGlobalConfig detectGlobalConfig = new DetectGlobalConfig();
        FormValidation formValidation = detectGlobalConfig.doTestBlackDuckConnection("https://blackduck.domain.com", "123", "30", true);

        assertEquals(FormValidation.Kind.ERROR, formValidation.kind);
        assertTrue(formValidation.getMessage().contains("token"));
        assertTrue(formValidation.getMessage().contains("password"));
        System.out.printf("Message: %s\n", formValidation.getMessage());
    }
}
