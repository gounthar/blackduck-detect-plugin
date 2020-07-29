/**
 * blackduck-detect
 *
 * Copyright (c) 2020 Synopsys, Inc.
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
package com.synopsys.integration.jenkins.detect.services;

import org.apache.commons.lang3.StringUtils;

import com.synopsys.integration.jenkins.detect.DetectJenkinsEnvironmentVariable;
import com.synopsys.integration.jenkins.extensions.JenkinsIntLogger;
import com.synopsys.integration.jenkins.wrapper.JenkinsProxyHelper;
import com.synopsys.integration.rest.proxy.ProxyInfo;
import com.synopsys.integration.util.IntEnvironmentVariables;
import com.synopsys.integration.util.OperatingSystemType;

public class DetectWorkspaceService {
    private final JenkinsIntLogger logger;
    private final String remoteTempWorkspacePath;
    private final JenkinsProxyHelper jenkinsProxyHelper;

    public DetectWorkspaceService(JenkinsIntLogger logger, JenkinsProxyHelper jenkinsProxyHelper, String remoteTempWorkspacePath) {
        this.logger = logger;
        this.jenkinsProxyHelper = jenkinsProxyHelper;
        this.remoteTempWorkspacePath = remoteTempWorkspacePath;
    }

    public DetectExecutionManager determineExecutionManager(IntEnvironmentVariables intEnvironmentVariables, OperatingSystemType operatingSystemType, String remoteJdkHome) {
        DetectExecutionManager detectExecutionManager;
        String detectJarPath = intEnvironmentVariables.getValue(DetectJenkinsEnvironmentVariable.USER_PROVIDED_JAR_PATH.stringValue());

        if (StringUtils.isNotBlank(detectJarPath)) {
            detectExecutionManager = new DetectJarManager(logger, remoteJdkHome, intEnvironmentVariables.getVariables(), detectJarPath);
        } else {
            detectExecutionManager = createScriptManager(operatingSystemType);
        }

        return detectExecutionManager;
    }

    private DetectScriptManager createScriptManager(OperatingSystemType operatingSystemType) {
        String scriptUrl;
        if (operatingSystemType == OperatingSystemType.WINDOWS) {
            scriptUrl = DetectScriptManager.LATEST_POWERSHELL_SCRIPT_URL;
        } else {
            scriptUrl = DetectScriptManager.LATEST_SHELL_SCRIPT_URL;
        }

        ProxyInfo proxyInfo;
        try {
            proxyInfo = jenkinsProxyHelper.getProxyInfo(scriptUrl);
        } catch (IllegalArgumentException e) {
            logger.warn("Synopsys Detect for Jenkins could not resolve proxy info from Jenkins because: " + e.getMessage());
            logger.warn("Continuing without proxy...");
            logger.trace("Stack trace:", e);
            proxyInfo = ProxyInfo.NO_PROXY_INFO;
        }

        return new DetectScriptManager(logger, scriptUrl, proxyInfo, remoteTempWorkspacePath);
    }

}
