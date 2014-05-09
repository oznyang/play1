package com.wisorg.maven.play;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.io.File;
import java.io.IOException;

/**
 * Initialize Play Maven project.
 * <p/>
 *
 * @author <a href="mailto:oznyang@163.com">oznyang</a>
 * @version V1.0, 14-5-7
 */
@Mojo(name = "initialize", defaultPhase = LifecyclePhase.INITIALIZE, requiresDependencyResolution = ResolutionScope.COMPILE)
public class PlayInitializeMojo extends AbstractPlayMojo {
    @Override
    @SuppressWarnings("unchecked")
    protected void internalExecute() throws MojoExecutionException, MojoFailureException, IOException {
        File BaseDir = project.getBasedir();
        project.getCompileSourceRoots().remove(new File(BaseDir, "app").getAbsolutePath());
        File src = new File(project.getBasedir(), "src");
        if (src.exists()) {
            project.getCompileSourceRoots().add(src.getAbsolutePath());
        }
    }
}
