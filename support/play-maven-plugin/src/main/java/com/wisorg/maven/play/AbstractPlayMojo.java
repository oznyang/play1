package com.wisorg.maven.play;

import com.google.common.collect.Sets;
import org.apache.commons.io.IOUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.util.FileUtils;
import org.sonatype.plexus.build.incremental.BuildContext;

import java.io.*;
import java.util.Properties;
import java.util.Set;

/**
 * .
 * <p/>
 *
 * @author <a href="mailto:oznyang@163.com">oznyang</a>
 * @version V1.0, 14-5-6
 */
public abstract class AbstractPlayMojo extends AbstractMojo {
    protected static final Set<String> IGNORE_MODULES = Sets.newHashSet("testrunner", "docviewer", "play", "play-documentation");

    @Component
    protected MavenProject project;

    @Component
    protected ArchiverManager archiverManager;

    @Component
    private BuildContext buildContext;

    public void execute() throws MojoExecutionException, MojoFailureException {
        assertPlay();
        try {
            initFramework();
            initModules();
            internalExecute();
            if (project.getArtifact().getFile() == null) {
                project.getArtifact().setFile(project.getBasedir());
            }
        } catch (IOException e) {
            throw new MojoExecutionException("IO error", e);
        }
    }

    private void initModules() throws MojoExecutionException, IOException {
        Properties props = new OrderSafeProperties();
        File moduleStatusFile = new File(project.getBasedir(), "m.conf");
        if (moduleStatusFile.exists()) {
            props.load(new FileInputStream(moduleStatusFile));
        }
        boolean changed = false;
        for (Object obj : project.getArtifacts()) {
            Artifact artifact = (Artifact) obj;
            if (!Artifact.SCOPE_PROVIDED.equals(artifact.getScope()) && "zip".equals(artifact.getType())) {
                String artifactId = artifact.getArtifactId();
                if (IGNORE_MODULES.contains(artifactId)) {
                    continue;
                }
                int pos = artifactId.lastIndexOf("-");
                String moduleName = pos == -1 ? artifactId : artifactId.substring(pos + 1);
                File to = getPlayHomeDir("modules/" + moduleName);
                extractArtifact(artifact, to, false);
                if (!props.containsKey(moduleName)) {
                    props.setProperty(moduleName, to.getAbsolutePath());
                    changed = true;
                }
            }
        }
        if (changed) {
            Writer writer = null;
            try {
                writer = new OutputStreamWriter(new FileOutputStream(moduleStatusFile), "UTF-8");
                props.store(writer, "Modules Configuration, set value as false or prefix path with ! to disable module.");
                buildContext.refresh(moduleStatusFile);
            } finally {
                IOUtils.closeQuietly(writer);
            }
        }
    }

    private void initFramework() throws MojoExecutionException, IOException {
        for (Object obj : project.getArtifacts()) {
            Artifact artifact = (Artifact) obj;
            if ("com.wisorg.playframework".equals(artifact.getGroupId()) && "zip".endsWith(artifact.getType())) {
                if ("play".equals(artifact.getArtifactId()) && "resource".equals(artifact.getClassifier())) {
                    extractArtifact(artifact, getPlayHome(), false);
                } else if ("play-documentation".equals(artifact.getArtifactId())) {
                    extractArtifact(artifact, getPlayHomeDir("documentation"), false);
                } else if ("testrunner".equals(artifact.getArtifactId())) {
                    extractArtifact(artifact, getPlayHomeDir("modules/testrunner"), false);
                } else if ("docviewer".equals(artifact.getArtifactId())) {
                    extractArtifact(artifact, getPlayHomeDir("modules/docviewer"), false);
                }
            }
        }
    }

    private void extractArtifact(Artifact artifact, File dir, boolean forceClean) throws MojoExecutionException, IOException {
        if (dir.exists()) {
            if (forceClean) {
                FileUtils.cleanDirectory(dir);
            } else if (artifact.getFile().lastModified() <= dir.lastModified()) {
                getLog().debug("Extract target dir [" + dir.getAbsolutePath() + "] is fresh, skip extract!");
                return;
            }
        } else {
            FileUtils.forceMkdir(dir);
        }
        try {
            UnArchiver unArchiver = archiverManager.getUnArchiver(artifact.getType());
            unArchiver.setSourceFile(artifact.getFile());
            unArchiver.setDestDirectory(dir);
            unArchiver.setOverwrite(true);
            unArchiver.extract();
        } catch (Exception e) {
            throw new MojoExecutionException("extract error", e);
        }
        dir.setLastModified(System.currentTimeMillis());
        buildContext.refresh(dir);
        getLog().debug("Extract artifact [" + artifact.getFile().getAbsolutePath() + "] to [" + dir + "]");
    }

    protected File getPlayHome() {
        return new File(project.getBuild().getDirectory(), "play");
    }

    protected File getPlayHomeDir(String name) {
        return new File(getPlayHome(), name);
    }

    protected void assertPlay() throws MojoExecutionException {
        String packageType = project.getPackaging();
        if (!"play".equals(packageType) && !"playmodule".equals(packageType)) {
            throw new MojoExecutionException("Not a play project, skip!");
        }
    }

    protected void assertPlayServer() throws MojoExecutionException {
        if (!"play".equals(project.getPackaging())) {
            throw new MojoExecutionException("Not a play server, skip!");
        }
    }

    protected void assertPlayModule() throws MojoExecutionException {
        if (!"playmodule".equals(project.getPackaging())) {
            throw new MojoExecutionException("Not a play module, skip!");
        }
    }

    protected void deleteDirectory(File directory) {
        if (directory.exists()) {
            getLog().info("Deleting directory [" + directory + "]");
            try {
                FileUtils.deleteDirectory(directory);
            } catch (IOException e) {
                getLog().info("Fail to delete directory [" + directory.getAbsolutePath() + "]", e);
            }
        }
    }

    protected static boolean isExcludeJar(Set<String> excludeJars, Set<String> excludeIds, Artifact artifact) {
        if (excludeJars.contains(artifact.getArtifactId())) {
            return true;
        }
        if (!excludeIds.isEmpty()) {
            for (String id : artifact.getDependencyTrail()) {
                int start = id.indexOf(':') + 1;
                String parentId = id.substring(start, id.indexOf(':', start));
                if (excludeIds.contains(parentId)) {
                    return true;
                }
            }
        }
        return false;
    }

    protected abstract void internalExecute() throws MojoExecutionException, MojoFailureException, IOException;
}
