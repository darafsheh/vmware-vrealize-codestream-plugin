package com.vmware.vcac.code.stream.jenkins.plugin.model;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;

import org.apache.commons.lang.StringUtils;

/**
 * Created by rsaraf on 4/22/2015.
 */
public class PluginParam implements Serializable {

    private String serverUrl;
    private String tenant;
    private String pipelineName;
    private String credentialsId;
    private boolean waitExec;
    private List<PipelineParam> pipelineParams;

    public PluginParam(String serverUrl, String tenant, String pipelineName, String credentialsId, boolean waitExec, List<PipelineParam> pipelineParams) {
        this.serverUrl = serverUrl;
        this.tenant = tenant;
        this.pipelineName = pipelineName;
        this.credentialsId = credentialsId;
        this.waitExec = waitExec;
        this.pipelineParams = pipelineParams;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public String getTenant() {
        return tenant;
    }

    public String getPipelineName() {
        return pipelineName;
    }
    
    public String getCredentialsId() {
    	return credentialsId;
    }


    public List<PipelineParam> getPipelineParams() {
        return pipelineParams;
    }

    public boolean isWaitExec() {
        return waitExec;
    }


    public Boolean validate() throws IOException {
        if (StringUtils.isBlank(this.getServerUrl())) {
            throw new IOException("CodeStream server url cannot be empty");
        }

//        if (StringUtils.isBlank(this.getUserName())) {
//            throw new IOException("CodeStream server username cannot be empty");
//        }
//
//        if (StringUtils.isBlank(this.getPassword())) {
//            throw new IOException("CodeStream server password cannot be empty");
//        }

        if (StringUtils.isBlank(this.getTenant())) {
            throw new IOException("CodeStream tenant cannot be empty");
        }

        if (StringUtils.isBlank(this.getPipelineName())) {
            throw new IOException("CodeStream pipeline name cannot be empty");
        }

        return true;
    }
}
