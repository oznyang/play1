package com.wisorg.maven.play;

/**
 * Resolve modules and extract project dependencies to "lib" and
 * <p/>
 *
 * @author <a href="mailto:oznyang@163.com">oznyang</a>
 * @version V1.0, 14-5-9
 */

import com.google.common.collect.Sets;
import org.apache.commons.io.FileUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.io.File;
import java.io.IOException;
import java.util.Set;

/**
 * Extract project dependencies to "lib" and "modules" directories.
 *
 * @author <a href="mailto:gslowikowski@gmail.com">Grzegorz Slowikowski</a>
 * @since 1.0.0
 */
@Mojo(name = "dep", requiresDependencyResolution = ResolutionScope.TEST)
public class PlayDependenciesMojo extends AbstractPlayMojo {
    private static final Set<String> IGNORE_DEP_JARS = Sets.newHashSet("play");
    protected static final Set<String> IGNORE_JARS = Sets.newHashSet();

    @Override
    protected void internalExecute() throws MojoExecutionException, MojoFailureException, IOException {
        File libDir = new File(project.getBasedir(), "lib");
        FileUtils.forceMkdir(libDir);

        for (Object obj : project.getArtifacts()) {
            Artifact artifact = (Artifact) obj;
            if (!Artifact.SCOPE_PROVIDED.equals(artifact.getScope()) && "jar".equals(artifact.getType()) && !IGNORE_DEP_JARS.contains(artifact.getArtifactId()) && !isExcludeJar(IGNORE_JARS, IGNORE_DEP_JARS, artifact)) {
                File to = new File(libDir, artifact.getFile().getName());
                if (!to.exists() || artifact.getFile().lastModified() > to.lastModified()) {
                    FileUtils.copyFile(artifact.getFile(), to);
                } else {
                    getLog().debug("Lib jar [" + to.getAbsolutePath() + "] is fresh, skip copy!");
                }
            }
        }
    }
}
