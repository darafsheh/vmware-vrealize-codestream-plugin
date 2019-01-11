package com.vmware.vcac.code.stream.jenkins.plugin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.List;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vmware.vcac.code.stream.jenkins.plugin.model.PipelineParam;
import com.vmware.vcac.code.stream.jenkins.plugin.model.PluginParam;
import com.vmware.vcac.code.stream.jenkins.plugin.util.ReleasePipelineExecutionInfoParser;

import hudson.model.Item;
import hudson.security.ACL;

import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;

/**
 * Created by rsaraf on 3/23/2015.
 */
public class CodeStreamClient {
    private String token;
    private String FETCH_TOKEN = "";
    private String CHECK_EXEC_STATUS = "";
    private String FETCH_PIPELINE = "";
    private String EXECUTE_PIPELINE = "";
    private String TOKEN_JSON = "{\"username\": \"%s\", \"password\": \"%s\", \"tenant\": \"%s\"}";
    private PluginParam params;
	public static final List<DomainRequirement> NO_REQUIREMENTS = Collections.<DomainRequirement> emptyList();

    public CodeStreamClient(PluginParam params) throws IOException {
        this.params = params;
        this.FETCH_TOKEN = params.getServerUrl() + "/identity/api/tokens";
        String codeStreamApiUrl = params.getServerUrl() + "/release-management-service/api/release-pipelines/";
        this.FETCH_PIPELINE = codeStreamApiUrl + "?name=%s";
        this.EXECUTE_PIPELINE = codeStreamApiUrl + "%s/executions";
        this.CHECK_EXEC_STATUS = codeStreamApiUrl + "%s/executions/%s";
        this.token = populateToken();
    }

	private StandardUsernamePasswordCredentials lookupCredentialsById(final String credentialId) {
		final List<StandardUsernamePasswordCredentials> all = CredentialsProvider.lookupCredentials(
				StandardUsernamePasswordCredentials.class, (Item) null, ACL.SYSTEM, NO_REQUIREMENTS);

		return CredentialsMatchers.firstOrNull(all, CredentialsMatchers.withId(credentialId));
	}

    private String populateToken() throws IOException {
    	StandardUsernamePasswordCredentials credentials = lookupCredentialsById(params.getCredentialsId());
        String tokenPayload = String.format(TOKEN_JSON, credentials.getUsername(), credentials.getPassword(), params.getTenant());
        HttpResponse httpResponse = this.post(FETCH_TOKEN, tokenPayload);
        String responseAsJson = this.getResponseAsJsonString(httpResponse);
        JsonObject stringJsonAsObject = getJsonObject(responseAsJson);
        JsonElement idElement = stringJsonAsObject.get("id");
        if (idElement == null) {
            handleError(stringJsonAsObject);
        } else {
            token = idElement.getAsString();
        }
        return token;
    }

    public JsonObject fetchPipeline(String pipelineName) throws IOException {
        JsonObject response = null;
        String url = String.format(FETCH_PIPELINE, getEncodedString(pipelineName));
        HttpResponse pipelineResponse = get(url);
        String responseAsJson = this.getResponseAsJsonString(pipelineResponse);
        JsonObject stringJsonAsObject = getJsonObject(responseAsJson);
        JsonElement contentElement = stringJsonAsObject.get("content");
        if (contentElement == null) {
            handleError(stringJsonAsObject);
        } else {
            JsonArray contents = contentElement.getAsJsonArray();
            if (contents.size() == 1) {
                response = contents.get(0).getAsJsonObject();
            } else {
                if (contents.size() > 1) {
                    throw new IOException("More than one pipeline with name " + pipelineName + " found");
                } else if (contents.size() < 1) {
                    throw new IOException("Pipeline with name " + pipelineName + " not found");
                }
            }
        }
        return response;
    }

    private String getEncodedString(String pipelineName) throws UnsupportedEncodingException {
        return URLEncoder.encode(pipelineName, "UTF-8");
    }

    public JsonObject executePipeline(String pipelineId, List<PipelineParam> pipelineParams) throws IOException {
        JsonObject response = null;
        String url = String.format(EXECUTE_PIPELINE, pipelineId);
        Gson gson = new Gson();
        String pipelineParamsArray = gson.toJson(pipelineParams);

        String payload = String.format("{\"description\": \"%s\", \"pipelineParams\": %s}", "Executed from jenkins", pipelineParamsArray);
        HttpResponse httpResponse = this.post(url, payload);
        String responseAsJson = this.getResponseAsJsonString(httpResponse);
        response = getJsonObject(responseAsJson);
        return response;
    }


    public ReleasePipelineExecutionInfoParser getPipelineExecutionResponse(String pipelineId, String pipelineExecId) throws IOException {
        String url = String.format(CHECK_EXEC_STATUS, pipelineId, pipelineExecId);
        HttpResponse httpResponse = this.get(url);
        String responseAsJson = this.getResponseAsJsonString(httpResponse);
        return new ReleasePipelineExecutionInfoParser(responseAsJson);
    }

    private JsonObject getJsonObject(String responseAsJson) {
        JsonElement execResponseParse = new JsonParser().parse(responseAsJson);
        return execResponseParse.getAsJsonObject();
    }

    public HttpResponse get(String URL) throws IOException {
        CloseableHttpClient httpClient = null;
        try {
            httpClient = getHttpClient();
            HttpGet request = new HttpGet(URL);
            request.setHeader("accept", "application/json; charset=utf-8");
            if (StringUtils.isNotBlank(token)) {
                String authorization = "Bearer " + token;
                request.setHeader("Authorization", authorization);
            }
            return httpClient.execute(request);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (KeyStoreException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        }
        return null;
    }

    private CloseableHttpClient getHttpClient() throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        SSLContextBuilder builder = new SSLContextBuilder();
        builder.loadTrustMaterial(null, new TrustSelfSignedStrategy());
        SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(
                builder.build());
        return HttpClients.custom().setSSLSocketFactory(
                sslsf).build();
    }


    private HttpResponse post(String URL, String payload) throws IOException {
        CloseableHttpClient httpClient = null;
        try {
            httpClient = getHttpClient();
            HttpPost postRequest = new HttpPost(URL);
            StringEntity input = new StringEntity(payload);
            input.setContentType("application/json");
            postRequest.setEntity(input);
            postRequest.setHeader("Content-Type", "application/json");
            postRequest.setHeader("accept", "application/json; charset=utf-8");
            if (StringUtils.isNotBlank(token)) {
                String authorization = "Bearer " + token;
                postRequest.setHeader("Authorization", authorization);
            }
            return httpClient.execute(postRequest);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (KeyStoreException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        }
        return null;
    }

    private String getResponseAsJsonString(HttpResponse response) throws IOException {
        BufferedReader br = new BufferedReader(
                new InputStreamReader((response.getEntity().getContent())));

        StringBuilder output = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            output.append(line);
        }
        return output.toString();
    }

    public void handleError(JsonObject asJsonObject) throws IOException {
        JsonElement errorElement = asJsonObject.get("errors");
        if (errorElement != null) {
            JsonObject errorElJsonObj = errorElement.getAsJsonArray().get(0).getAsJsonObject();
            JsonElement messageEle = errorElJsonObj.get("systemMessage");
            if (messageEle == null) {
                messageEle = errorElJsonObj.get("message");
            }
            String systemErrorMessage = messageEle.toString();
            throw new IOException(systemErrorMessage);
        }
    }

}

