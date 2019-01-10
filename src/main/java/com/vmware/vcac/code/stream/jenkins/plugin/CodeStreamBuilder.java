package com.vmware.vcac.code.stream.jenkins.plugin;

import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.vmware.vcac.code.stream.jenkins.plugin.model.PipelineParam;
import com.vmware.vcac.code.stream.jenkins.plugin.model.PluginParam;
import com.vmware.vcac.code.stream.jenkins.plugin.util.EnvVariableResolver;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.EnvironmentContributingAction;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import static java.util.Arrays.asList;
import hudson.util.ListBoxModel;


import static hudson.Util.fixEmptyAndTrim;

/**
 * Sample {@link Builder}.
 * <p/>
 * <p/>
 * When the user configures the project and enables this builder,
 * {@link DescriptorImpl#newInstance(StaplerRequest)} is invoked
 * and a new {@link CodeStreamBuilder} is created. The created
 * instance is persisted to the project configuration XML by using
 * XStream, so this allows you to use instance fields (like {@link })
 * to remember the configuration.
 * <p/>
 * <p/>
 * When a build is performed, the {@link #perform(AbstractBuild, Launcher, BuildListener)}
 * method will be invoked.
 *
 * @author Rishi Saraf
 */
public class CodeStreamBuilder extends Builder implements Serializable {

    private String serverUrl;
    private String userName;
    private String password;
    private String tenant;
    private String pipelineName;
    private String state;
    private boolean waitExec;
    private List<PipelineParam> pipelineParams;


    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public CodeStreamBuilder(String serverUrl, String userName, String password, String tenant, String pipelineName, String state, boolean waitExec, List<PipelineParam> pipelineParams) {
        this.serverUrl = fixEmptyAndTrim(serverUrl);
        this.userName = fixEmptyAndTrim(userName);
        this.password = fixEmptyAndTrim(password);
        this.tenant = fixEmptyAndTrim(tenant);
        this.pipelineName = fixEmptyAndTrim(pipelineName);
        this.state = fixEmptyAndTrim(pipelineName);
        this.waitExec = waitExec;
        this.pipelineParams = pipelineParams;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public String getUserName() {
        return userName;
    }

    public String getPassword() {
        return password;
    }

    public String getTenant() {
        return tenant;
    }

    public String getPipelineName() {
        return pipelineName;
    }
    
    public String getStatee() {
        return state;
    }


    public List<PipelineParam> getPipelineParams() {
        return pipelineParams;
    }

    public boolean isWaitExec() {
        return waitExec;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        PrintStream logger = listener.getLogger();
        EnvVariableResolver helper = new EnvVariableResolver(build, listener);
        PluginParam param = new PluginParam(helper.replaceBuildParamWithValue(serverUrl), helper.replaceBuildParamWithValue(userName),
                helper.replaceBuildParamWithValue(password), helper.replaceBuildParamWithValue(tenant), helper.replaceBuildParamWithValue(pipelineName), waitExec, helper.replaceBuildParamWithValue(pipelineParams));
        logger.println("Starting CodeStream pipeline execution of pipeline : " + param.getPipelineName());
        param.validate();
        CodeStreamPipelineCallable callable = new CodeStreamPipelineCallable(param);
        Map<String, String> envVariables = launcher.getChannel().call(callable);
        CodeStreamEnvAction action = new CodeStreamEnvAction();
        action.addAll(envVariables);
        build.addAction(action);
        return true;
    }


    /**
     * We'll use this from the <tt>config.jelly</tt>.
     */


    // Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    /**
     * Descriptor for {@link CodeStreamBuilder}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     * <p/>
     * <p/>
     * See <tt>src/main/resources/hudson/plugins/hello_world/HelloWorldBuilder/*.jelly</tt>
     * for the actual HTML fragment for the configuration screen.
     */
    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        private static final Logger log;

        static {
            log = Logger.getLogger(DescriptorImpl.class.getName());
        }


        /**
         * In order to load the persisted global configuration, you have to
         * call load() in the constructor.
         */
        public DescriptorImpl() {
            load();
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return "Execute CodeStream Pipeline";
        }


        public FormValidation doCheckServerUrl(
                @QueryParameter final String value) {

            String url = Util.fixEmptyAndTrim(value);
            if (url == null)
                return FormValidation.error("Please enter CodeStream server URL.");

            if (url.indexOf('$') >= 0)
                // set by variable, can't validate
                return FormValidation.ok();

            try {
                new URL(value).toURI();
            } catch (MalformedURLException e) {
                return FormValidation.error("This is not a valid URI");
            } catch (URISyntaxException e) {
                return FormValidation.error("This is not a valid URI");
            }

            return FormValidation.ok();
        }

        public FormValidation doCheckUserName(
                @QueryParameter final String value) {

            String url = Util.fixEmptyAndTrim(value);
            if (url == null)
                return FormValidation.error("Please enter user name.");

            if (url.indexOf('$') >= 0)
                // set by variable, can't validate
                return FormValidation.ok();

            return FormValidation.ok();
        }

        public FormValidation doCheckPassword(
                @QueryParameter final String value) {

            String url = Util.fixEmptyAndTrim(value);
            if (url == null)
                return FormValidation.error("Please enter password.");

            if (url.indexOf('$') >= 0)
                // set by variable, can't validate
                return FormValidation.error("Environment variable cannot be used in password.");

            return FormValidation.ok();
        }

        public FormValidation doCheckTenant(
                @QueryParameter final String value) {

            String url = Util.fixEmptyAndTrim(value);
            if (url == null)
                return FormValidation.error("Please enter tenant.");

            if (url.indexOf('$') >= 0)
                // set by variable, can't validate
                return FormValidation.ok();

            return FormValidation.ok();
        }

        public FormValidation doCheckPipelineName(
                @QueryParameter final String value) {

            String url = Util.fixEmptyAndTrim(value);
            if (url == null)
                return FormValidation.error("Please enter pipeline name.");

            if (url.indexOf('$') >= 0)
                // set by variable, can't validate
                return FormValidation.ok();

            return FormValidation.ok();
        }
        public ListBoxModel doFillStateItems(@QueryParameter String userName) {
            ListBoxModel m = new ListBoxModel();
            for (String s : asList("A","B","C"))
                m.add(String.format("State %s in %s", s, userName),
                        userName+':'+s);
            return m;
        }

    }

    public static class CodeStreamEnvAction implements EnvironmentContributingAction {
        private transient Map<String, String> data = new HashMap<String, String>();

        private void add(String key, String val) {
            if (data == null) return;
            data.put(key, val);
        }

        private void addAll(Map<String, String> map) {
            data.putAll(map);
        }

        @Override
        public void buildEnvVars(AbstractBuild<?, ?> build, EnvVars env) {
            if (data != null) env.putAll(data);
        }

        @Override
        public String getIconFileName() {
            return null;
        }

        @Override
        public String getDisplayName() {
            return null;
        }

        @Override
        public String getUrlName() {
            return null;
        }

        public Map<String, String> getData() {
            return data;
        }
    }
}

