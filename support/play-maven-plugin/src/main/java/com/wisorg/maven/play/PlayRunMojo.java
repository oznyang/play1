package com.wisorg.maven.play;

import org.apache.commons.lang.StringUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Java;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

/**
 * Run play server in alone jvm instance.
 * <p/>
 *
 * @author <a href="mailto:oznyang@163.com">oznyang</a>
 * @version V1.0, 14-5-6
 */
@Mojo(name = "run", requiresDependencyResolution = ResolutionScope.TEST)
public class PlayRunMojo extends AbstractPlayServerMojo {

    /**
     * Play server http port.
     */
    @Parameter(property = "play.httpPort")
    private String httpPort;

    /**
     * Play server https port.
     */
    @Parameter(property = "play.httpsPort")
    private String httpsPort;

    /**
     * Disable the JPDA port checking and force the jpda.port value
     */
    @Parameter(property = "play.disableCheckJpda", defaultValue = "false")
    private boolean disableCheckJpda;

    /**
     * Play application run mode.
     */
    @Parameter(property = "play.mode")
    private String appMode;

    /**
     * Run in forked Java process.
     */
    @Parameter(property = "play.runFork", defaultValue = "true")
    private boolean runFork;

    /**
     * When in fork mode redirect log to file
     */
    @Parameter(property = "play.redirectLog", defaultValue = "false")
    private boolean redirectLog;

    private String runMode;

    public String getAppMode() {
        return appMode;
    }

    public boolean isRunFork() {
        return runFork;
    }

    @Override
    protected void internalExecute() throws MojoExecutionException, MojoFailureException, IOException {
        assertPlayServer();

        File baseDir = project.getBasedir();
        File confDir = new File(baseDir, "conf");

        File appConfFile = new File(confDir, "application.conf");

        if (!appConfFile.exists() || appConfFile.length() == 0) {
            getLog().info("Empty \"conf/application.conf\" file, skip execution");
            return;
        }

        Properties props = getConfiguration(getPlayId());

        JavaRunnable runner = new JavaRunnable(prepareAntJavaTask(props, isRunFork()));
        Thread t = new Thread(runner, "Play Server runner");
        getLog().info("Launching Play Server in " + runMode.toUpperCase() + " mode");
        t.start();
        try {
            t.join();
        } catch (Exception e) {
            throw new MojoExecutionException("Server error", e);
        }
        if (runner.getException() != null) {
            throw new MojoExecutionException("Server error", runner.getException());
        }
        if (!isRunFork()) {
            while (true) {// wait for Ctrl+C
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    throw new MojoExecutionException("Server error", e);
                }
            }
        }
    }

    protected Java prepareAntJavaTask(Properties props, boolean fork) throws MojoExecutionException {
        File baseDir = project.getBasedir();

        Project antProject = createProject();
        Java javaTask = new Java();
        javaTask.setTaskName("play");
        javaTask.setProject(antProject);
        javaTask.setClassname("play.server.Server");
        javaTask.setClasspath(getProjectClassPath(antProject));
        javaTask.setFailonerror(true);
        javaTask.setFork(fork);

        if (fork && redirectLog) {
            File logFile = getPlayHomeDir("app.log");
            if (!logFile.getParentFile().exists()) {
                logFile.getParentFile().mkdirs();
            }
            javaTask.setOutput(logFile);
            getLog().info(" Output is redirected to [" + logFile.getAbsolutePath() + "]");
        }

        String appMode = getAppMode();
        if (StringUtils.isEmpty(appMode)) {
            appMode = props.getProperty("application.mode", "dev");
        } else {
            addSystemProperty(javaTask, "play.mode", appMode);
        }
        runMode = appMode;

        if (fork) {
            javaTask.setDir(baseDir);
            addJvmArgs(javaTask, props.getProperty("jvm.memory"), getJvmArgs());

            if ("prod".equalsIgnoreCase(appMode)) {
                addJvmArgs(javaTask, "-server");
            }
            // JDK 7 compat
            javaTask.createJvmarg().setValue("-XX:-UseSplitVerifier");
            if (StringUtils.isNotEmpty(httpPort)) {
                addJvmArgs(javaTask, "--http.port=" + httpPort);
            }
            if (StringUtils.isNotEmpty(httpsPort)) {
                addJvmArgs(javaTask, "--https.port=" + httpsPort);
            }
            if ("dev".equalsIgnoreCase(appMode)) {
                if (StringUtils.isEmpty(debugPort)) {
                    debugPort = props.getProperty("jpda.port", "8000");
                }
                int jpdaPort = Integer.parseInt(debugPort);
                if (!disableCheckJpda) {
                    jpdaPort = checkJpda(jpdaPort);
                }
                addJvmArgs(javaTask, "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=" + jpdaPort);
            }

            Artifact frameworkJarArtifact = getFrameworkJarArtifact();
            if (frameworkJarArtifact != null) {
                addJvmArgs(javaTask, "-javaagent:" + frameworkJarArtifact.getFile().getAbsoluteFile());
            }
        } else {
            String jvmArgs = getJvmArgs().trim();
            if (StringUtils.isEmpty(jvmArgs)) {
                String[] args = StringUtils.split(jvmArgs, " ");
                for (String arg : args) {
                    if (arg.startsWith("-D")) {
                        arg = arg.substring(2);
                        int p = arg.indexOf('=');
                        if (p >= 0) {
                            addSystemProperty(javaTask, arg.substring(0, p), arg.substring(p + 1));
                        }
                    }

                }
            }
        }
        addSystemProperty(javaTask, "play.path", getPlayHome().getAbsolutePath());
        addSystemProperty(javaTask, "play.tmp", getPlayHomeDir("tmp").getAbsolutePath());
        addSystemProperty(javaTask, "play.id", getPlayId());
        if (isPlayTest()) {
            addSystemProperty(javaTask, "play.test", "true");
        }
        addSystemProperty(javaTask, "application.path", baseDir.getAbsolutePath());

        getLog().debug("Full cmdLine: " + javaTask.getCommandLine() + "\n");
        getLog().info("CmdLine: " + StringUtils.substringBefore(javaTask.getCommandLine().toString(), "-classpath"));
        return javaTask;
    }
}
