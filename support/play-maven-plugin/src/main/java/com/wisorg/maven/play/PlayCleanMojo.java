package com.wisorg.maven.play;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;

import java.io.File;
import java.io.IOException;

/**
 * Clean Play temporary directories.
 * <p/>
 * Directories being cleaned
 * - "tmp"
 * - "precompiled"
 * - "db"
 * - "lib"
 * - "logs"
 * - "test-result"
 * <p/>
 *
 * @author <a href="mailto:oznyang@163.com">oznyang</a>
 * @version V1.0, 14-5-6
 */
@Mojo(name = "clean", defaultPhase = LifecyclePhase.CLEAN)
public class PlayCleanMojo extends AbstractMojo {

    /**
     * Should all "cleanable" directories be deleted. If "true", overrides all "cleanXXX" property values.
     */
    @Parameter(property = "play.cleanAll", defaultValue = "true")
    private boolean cleanAll;

    /**
     * Skip cleaning.
     */
    @Parameter(property = "play.cleanSkip", defaultValue = "false")
    private boolean cleanSkip;

    /**
     * Should "precompiled" directory be deleted.
     */
    @Parameter(property = "play.cleanPrecompiled", defaultValue = "false")
    private boolean cleanPrecompiled;

    /**
     * Should "tmp" directory be deleted.
     */
    @Parameter(property = "play.cleanTmp", defaultValue = "true")
    private boolean cleanTmp;

    @Component
    protected MavenProject project;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        String packageType = project.getPackaging();
        if (!"play".equals(packageType) && !"playmodule".equals(packageType)) {
            throw new MojoExecutionException("Not a play project, skip!");
        }

        if (cleanSkip) {
            getLog().info("Cleaning skipped");
            return;
        }
        File baseDir = project.getBasedir();
        if (cleanAll || cleanTmp) {
            deleteDirectory(new File(baseDir, "tmp"));
        }
        if (cleanAll || cleanPrecompiled) {
            deleteDirectory(new File(baseDir, "precompiled"));
        }
        if (cleanAll) {
            deleteDirectory(new File(baseDir, "db"));
            deleteDirectory(new File(baseDir, "lib"));
            deleteDirectory(new File(baseDir, "logs"));
            deleteDirectory(new File(baseDir, "test-result"));
        }
    }

    private void deleteDirectory(File directory) {
        if (directory.exists()) {
            getLog().info(String.format("Deleting directory %s", directory));
            try {
                FileUtils.deleteDirectory(directory);
            } catch (IOException e) {
                getLog().info("Fail to delete directory [" + directory.getAbsolutePath() + "]", e);
            }
        }
    }
}
