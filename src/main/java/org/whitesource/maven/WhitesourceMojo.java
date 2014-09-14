/**
 * Copyright (C) 2011 White Source Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.whitesource.maven;


import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.repository.Authentication;
import org.sonatype.aether.repository.Proxy;
import org.sonatype.aether.repository.RemoteRepository;
import org.whitesource.agent.client.ClientConstants;
import org.whitesource.agent.client.WhitesourceService;

import java.text.MessageFormat;

//import org.eclipse.aether.RepositorySystemSession;
//import org.eclipse.aether.repository.Authentication;
//import org.eclipse.aether.repository.AuthenticationContext;
//import org.eclipse.aether.repository.Proxy;
//import org.eclipse.aether.repository.RemoteRepository;

/**
 * Concrete implementation holding common functionality to all goals in this plugin.
 *
 * @author Edo.Shor
 */
public abstract class WhitesourceMojo extends AbstractMojo {

    /* --- Members --- */

    /**
     * Indicates whether the build will continue even if there are errors.
     */
    @Parameter(defaultValue = "false")
    protected boolean failOnError;

    /**
     * Set this to 'true' to skip the maven execution.
     */
    @Parameter(defaultValue = "false")
    protected boolean skip;

    @Component
    protected MavenSession session;

    @Component
    protected MavenProject mavenProject;

    /**
     * The project dependency resolver to use.
     */
    @Component( hint = "default" )
    protected ProjectDependenciesResolver projectDependenciesResolver;

    /**
     * The current repository/network configuration of Maven.
     */
    @Parameter( defaultValue = "${repositorySystemSession}", readonly = true, required = true )
    protected RepositorySystemSession repoSession;

    protected WhitesourceService service;

    /* --- Abstract methods --- */

    public abstract void doExecute() throws MojoExecutionException, MojoFailureException;

    /* --- Concrete implementation methods --- */

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        final long startTime = System.currentTimeMillis();

        if (skip) {
            info("Skipping update");
        } else {
            try {
                createService();
                doExecute();
            } catch (MojoExecutionException e) {
                handleError(e);
            } catch (RuntimeException e) {
                throw new MojoFailureException("Unexpected error", e);
            } finally {
                if (service != null) {
                    service.shutdown();
                }
            }
        }

        info("Total execution time is " + (System.currentTimeMillis() - startTime) + " [msec]");
    }

    /* --- Protected methods --- */

    protected void createService() {
        String serviceUrl = session.getSystemProperties().getProperty(
                ClientConstants.SERVICE_URL_KEYWORD, ClientConstants.DEFAULT_SERVICE_URL);
        info("Service URL is " + serviceUrl);

        service = new WhitesourceService(Constants.AGENT_TYPE, Constants.AGENT_VERSION, serviceUrl);
        if (service == null) {
            info("Failed to initiate WhiteSource Service");
        } else {
            info("Initiated WhiteSource Service");
        }

        // get proxy configuration from session
        RemoteRepository dummyRepo = new RemoteRepository().setUrl(serviceUrl);
        final Proxy proxy = session.getRepositorySession().getProxySelector().getProxy(dummyRepo);
        if (proxy == null) {
            info("No proxy settings");
        } else {
            String username = null;
            String password = null;
            final Authentication auth = proxy.getAuthentication();
            if (auth != null) {
                username = auth.getUsername();
                password = auth.getPassword();
            }

            String host = proxy.getHost();
            int port = proxy.getPort();
            service.getClient().setProxy(host, port, username, password);

            info(MessageFormat.format("Proxy settings - host:{0}, port:{1}, username:{2}, password:{3}", host, port, username, password));
        }
//        //TODO: uncomment the code below and replace with the above when we need to support maven 3.1.1 (which migrated from Sonatype Aether to Eclipse Aether)
//        RemoteRepository.Builder remoteRepositoryBuilder = new RemoteRepository.Builder(null, null, serviceUrl);
//        RemoteRepository dummyRepo = remoteRepositoryBuilder.build();
//        RepositorySystemSession repositorySystemSession = session.getRepositorySession();
//        final Proxy proxy = repositorySystemSession.getProxySelector().getProxy(dummyRepo);
//        if (proxy != null) {
//            String username = null;
//            String password = null;
//            final Authentication auth = proxy.getAuthentication();
//            if (auth != null) {
//                dummyRepo = remoteRepositoryBuilder.setAuthentication(auth).build();
//                AuthenticationContext authenticationContext = AuthenticationContext.forRepository( repositorySystemSession, dummyRepo );
//                try {
//                    auth.fill(authenticationContext, null, null);
//                    username = authenticationContext.get(AuthenticationContext.USERNAME, String.class);
//                    password = authenticationContext.get(AuthenticationContext.PASSWORD, String.class);
//                }
//                finally {
//                    AuthenticationContext.close(authenticationContext);
//                }
//            }
//            service.getClient().setProxy(proxy.getHost(), proxy.getPort(), username, password);
//        }
    }

    protected void handleError(Exception error) throws MojoFailureException {
        String message = error.getMessage();
        if (failOnError) {
            debug(message, error);
            throw new MojoFailureException(message);
        } else {
            error(message, error);
        }
    }

    protected void debug(CharSequence content) {
        final Log log = getLog();
        if (log != null) {
            log.debug(content);
        }
    }

    protected void debug(CharSequence content, Throwable error) {
        final Log log = getLog();
        if (log != null) {
            log.debug(content, error);
        }
    }

    protected void info(CharSequence content) {
        final Log log = getLog();
        if (log != null) {
            log.info(content);
        }
    }

    protected void warn(CharSequence content, Throwable error) {
        final Log log = getLog();
        if (log != null) {
            log.debug(content, error);
            log.warn(content);
        }
    }

    protected void warn(CharSequence content) {
        final Log log = getLog();
        if (log != null) {
            log.warn(content);
        }
    }

    protected void error(CharSequence content, Throwable error) {
        final Log log = getLog();
        if (log != null) {
            log.debug(content, error);
            log.error(content);
        }
    }

    protected void error(CharSequence content) {
        final Log log = getLog();
        if (log != null) {
            log.error(content);
        }
    }
}
