package com.wisorg.maven.play;

import org.apache.commons.lang.StringUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.tools.ant.BuildLogger;
import org.apache.tools.ant.NoBannerLogger;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Java;
import org.apache.tools.ant.types.Environment;
import org.apache.tools.ant.types.Path;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.Properties;
import java.util.Set;

/**
 * .
 * <p/>
 *
 * @author <a href="mailto:oxsean@gmail.com">sean yang</a>
 * @version V1.0, 14-5-6
 */
public abstract class AbstractPlayServerMojo extends AbstractPlayMojo {

    /**
     * Additional JVM arguments passed to Play server's JVM
     */
    @Parameter(property = "play.jvmArgs", defaultValue = "-Xms128m -Xmx1024m -XX:MaxPermSize=256m")
    private String jvmArgs;

    /**
     * Play id (profile) used when starting server without tests.
     */
    @Parameter(property = "play.id", defaultValue = "")
    private String playId;

    /**
     * Play id (profile) used when running server with tests.
     */
    @Parameter(property = "play.testId", defaultValue = "test")
    private String playTestId;

    /**
     * Run server with test profile.
     */
    @Parameter(property = "play.runWithTests", defaultValue = "false")
    private boolean runWithTests;

    public String getJvmArgs() {
        return jvmArgs;
    }

    public String getPlayId() {
        return StringUtils.defaultString(isRunWithTests() ? playTestId : playId);
    }

    public boolean isRunWithTests() {
        return runWithTests;
    }

    protected Properties getConfiguration(String playId) throws IOException {
        ConfigurationParser configParser = new ConfigurationParser(playId, project.getBasedir(), getPlayHome());
        return configParser.parse();
    }

    protected void addJvmArgs(Java javaTask, String arg) {
        javaTask.createJvmarg().setValue(arg);
        getLog().debug("  Adding jvmarg '" + arg + "'");
    }

    protected void addSystemProperty(Java javaTask, String name, String value) {
        Environment.Variable var = new Environment.Variable();
        var.setKey(name);
        var.setValue(value);
        javaTask.addSysproperty(var);
        getLog().debug("  Adding sysProp '" + var.getContent() + "'");
    }

    protected void addSystemProperty(Java javaTask, String name, File file) {
        Environment.Variable var = new Environment.Variable();
        var.setKey(name);
        var.setFile(file);
        javaTask.addSysproperty(var);
        getLog().debug("  Adding sysProp '" + var.getContent() + "'");
    }

    protected int checkJpda(int confJpdaPort) {
        int result = confJpdaPort;
        try {
            ServerSocket serverSocket = new ServerSocket(confJpdaPort);
            serverSocket.close();
        } catch (IOException e) {
            getLog().info("JPDA port " + confJpdaPort + " is already used. Will try to use any free port for debugging");
            result = 0;
        }
        return result;
    }

    protected Project createProject() {
        Project project = new Project();
        BuildLogger logger = new NoBannerLogger();
        logger.setMessageOutputLevel(Project.MSG_INFO);
        logger.setOutputPrintStream(System.out);
        logger.setErrorPrintStream(System.err);
        project.addBuildListener(logger);
        project.init();
        project.getBaseDir();
        return project;
    }

    protected Path getProjectClassPath(Project antProject) throws MojoExecutionException {
        Path classPath = new Path(antProject);
        classPath.createPathElement().setLocation(new File(project.getBasedir(), "conf"));
        classPath.createPathElement().setLocation(new File(project.getBuild().getOutputDirectory()));
        Set classPathArtifacts = project.getArtifacts();
        for (Object obj : classPathArtifacts) {
            Artifact artifact = (Artifact) obj;
            if (artifact.getArtifactHandler().isAddedToClasspath()) {
                getLog().debug(String.format("CP: %s:%s:%s:%s (%s)", artifact.getGroupId(), artifact.getArtifactId(), artifact.getType(), artifact.getVersion(), artifact.getScope()));
                classPath.createPathElement().setLocation(artifact.getFile());
            }
        }
        return classPath;
    }

    protected Artifact getFrameworkJarArtifact() {
        for (Object obj : project.getArtifacts()) {
            Artifact artifact = (Artifact) obj;
            if ("com.wisorg.playframework".equals(artifact.getGroupId()) && "play".equals(artifact.getArtifactId()) && "jar".equals(artifact.getType())) {
                return artifact;
            }
        }
        return null;
    }

    protected void addJvmArgs(Java javaTask, String jvmMemory, String jvmArgs) {
        boolean memoryInProps = false;
        if (StringUtils.isNotBlank(jvmMemory)) {
            String[] args = StringUtils.split(jvmMemory.trim(), " ");
            for (String arg : args) {
                if (arg.startsWith("-Xm")) {
                    memoryInProps = true;
                }
                addJvmArgs(javaTask, arg);
            }
        }
        if (StringUtils.isNotBlank(jvmArgs)) {
            String[] args = StringUtils.split(jvmArgs.trim(), " ");
            for (String arg : args) {
                if (memoryInProps && arg.startsWith("-Xm")) {
                    continue;
                }
                addJvmArgs(javaTask, arg);
            }
        }
    }

    protected void precompile() throws MojoExecutionException, MojoFailureException, IOException {
        File baseDir = project.getBasedir();
        String playId = getPlayId();
        Properties props;
        try {
            props = getConfiguration(playId);
        } catch (IOException e) {
            props = new Properties();
        }
        Project antProject = createProject();
        final Java javaTask = new Java();
        javaTask.setTaskName("play");
        javaTask.setProject(antProject);
        javaTask.setClassname("play.server.Server");
        javaTask.setClasspath(getProjectClassPath(antProject));
        javaTask.setFailonerror(true);
        javaTask.setFork(true);
        javaTask.setDir(baseDir);
        addJvmArgs(javaTask, props.getProperty("jvm.memory"), getJvmArgs());
        // JDK 7 compat
        javaTask.createJvmarg().setValue("-XX:-UseSplitVerifier");
        addSystemProperty(javaTask, "play.path", getPlayHome().getAbsolutePath());
        addSystemProperty(javaTask, "play.id", playId);
        addSystemProperty(javaTask, "play.tmp", getPlayHomeDir("tmp").getAbsolutePath());
        addSystemProperty(javaTask, "application.path", baseDir.getAbsolutePath());
        addSystemProperty(javaTask, "precompile", "yes");

        JavaRunnable runner = new JavaRunnable(javaTask);
        Thread t = new Thread(runner, "Play precompilation runner");
        getLog().info("Starting Play Precompile");
        t.start();
        try {
            t.join();
        } catch (Exception e) {
            throw new MojoExecutionException("Precompile error", e);
        }
        if (runner.getException() != null) {
            throw new MojoExecutionException("Precompile error", runner.getException());
        }
    }

    protected static class JavaRunnable implements Runnable {
        private Java java;
        private Exception exception;

        public JavaRunnable(Java java) {
            this.java = java;
        }

        public Exception getException() {
            return exception;
        }

        public void run() {
            try {
                java.execute();
            } catch (Exception e) {
                this.exception = e;
            }
        }
    }
}
