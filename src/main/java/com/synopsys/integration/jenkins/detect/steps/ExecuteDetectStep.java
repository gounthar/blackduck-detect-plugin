/**
 * blackduck-detect
 *
 * Copyright (C) 2019 Black Duck Software, Inc.
 * http://www.blackducksoftware.com/
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.synopsys.integration.jenkins.detect.steps;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.apache.tools.ant.types.Commandline;

import com.synopsys.integration.blackduck.configuration.BlackDuckServerConfigBuilder;
import com.synopsys.integration.exception.IntegrationException;
import com.synopsys.integration.jenkins.detect.JenkinsDetectLogger;
import com.synopsys.integration.jenkins.detect.PluginHelper;
import com.synopsys.integration.jenkins.detect.exception.DetectJenkinsException;
import com.synopsys.integration.jenkins.detect.extensions.global.DetectGlobalConfig;
import com.synopsys.integration.jenkins.detect.steps.remote.DetectRemoteJarRunner;
import com.synopsys.integration.jenkins.detect.steps.remote.DetectRemoteRunner;
import com.synopsys.integration.jenkins.detect.steps.remote.DetectRemoteScriptRunner;
import com.synopsys.integration.jenkins.detect.steps.remote.DetectResponse;
import com.synopsys.integration.jenkins.detect.tools.DummyToolInstallation;
import com.synopsys.integration.jenkins.detect.tools.DummyToolInstaller;
import com.synopsys.integration.polaris.common.configuration.PolarisServerConfig;
import com.synopsys.integration.util.IntEnvironmentVariables;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Util;
import hudson.model.Node;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;

public class ExecuteDetectStep {
    private final Node node;
    private final TaskListener listener;
    private final EnvVars envVars;
    private final FilePath workspace;
    private final Run run;
    private final String javaHome;

    public ExecuteDetectStep(final Node node, final TaskListener listener, final FilePath workspace, final EnvVars envVars, final Run run, final String javaHome) {
        this.node = node;
        this.listener = listener;
        this.envVars = envVars;
        this.workspace = workspace;
        this.run = run;
        this.javaHome = javaHome;
    }

    public void executeDetect(final String detectProperties) {
        final JenkinsDetectLogger logger = new JenkinsDetectLogger(listener);
        final IntEnvironmentVariables variables = new IntEnvironmentVariables();
        variables.putAll(envVars);
        logger.setLogLevel(variables);
        try {
            final String pluginVersion = PluginHelper.getPluginVersion();
            logger.info("Running Detect version: " + pluginVersion);

            final DetectGlobalConfig detectGlobalConfig = PluginHelper.getDetectGlobalConfig();

            final BlackDuckServerConfigBuilder blackDuckServerConfigBuilder = detectGlobalConfig.getBlackDuckServerConfigBuilder();
            final Map<String, String> blackDuckEnvVars = Arrays.stream(BlackDuckServerConfigBuilder.Property.values())
                                                             .collect(Collectors.toMap(BlackDuckServerConfigBuilder.Property::getBlackDuckEnvironmentVariableKey, blackDuckServerConfigBuilder::get));

            envVars.putAll(blackDuckEnvVars);

            final PolarisServerConfig polarisServerConfig = detectGlobalConfig.getPolarisServerConfig();
            polarisServerConfig.populateEnvironmentVariables(envVars::put);

            final String pathToDetectJar = envVars.get("DETECT_JAR", StringUtils.EMPTY);

            final DetectRemoteRunner detectRunner;

            if (StringUtils.isNotBlank(pathToDetectJar)) {
                detectRunner = new DetectRemoteJarRunner(logger, envVars, workspace.getRemote(), getCorrectedParameters(detectProperties), javaHome, pathToDetectJar);
            } else {
                final DummyToolInstaller dummyInstaller = new DummyToolInstaller();
                final String toolsDirectory = dummyInstaller.getToolDir(new DummyToolInstallation(), node).getRemote();
                detectRunner = new DetectRemoteScriptRunner(logger, toolsDirectory, getCorrectedParameters(detectProperties), workspace.getRemote(), envVars);
            }

            final DetectResponse response = node.getChannel().call(detectRunner);

            if (response.getExitCode() > 0) {
                logger.error("Detect failed with exit code: " + response.getExitCode());
                run.setResult(Result.FAILURE);
            } else if (null != response.getException()) {
                final Exception exception = response.getException();
                if (exception instanceof InterruptedException) {
                    run.setResult(Result.ABORTED);
                    Thread.currentThread().interrupt();
                } else {
                    logger.error(exception.getMessage(), exception);
                    run.setResult(Result.UNSTABLE);
                }
            }
        } catch (final IntegrationException e) {
            logger.error(e.getMessage());
            logger.debug(e.getMessage(), e);
            run.setResult(Result.UNSTABLE);
        } catch (final InterruptedException e) {
            logger.error("Detect caller thread was interrupted.", e);
            run.setResult(Result.ABORTED);
            Thread.currentThread().interrupt();
        } catch (final Exception e) {
            logger.error(e.getMessage(), e);
            run.setResult(Result.UNSTABLE);
        }
    }

    private List<String> getCorrectedParameters(final String commandLineParameters) throws DetectJenkinsException {
        final String[] separatedParameters = Commandline.translateCommandline(commandLineParameters);
        final List<String> correctedParameters = new ArrayList<>();
        for (final String parameter : separatedParameters) {
            correctedParameters.add(handleVariableReplacement(envVars, parameter));
        }
        return correctedParameters;
    }

    private String handleVariableReplacement(final Map<String, String> variables, final String value) throws DetectJenkinsException {
        if (value != null) {
            final String newValue = Util.replaceMacro(value, variables);
            if (StringUtils.isNotBlank(newValue) && newValue.contains("$")) {
                throw new DetectJenkinsException("Variable was not properly replaced. Value: " + value + ", Result: " + newValue + ". Make sure the variable has been properly defined.");
            }
            return newValue;
        }
        return null;
    }

}
