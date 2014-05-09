package com.wisorg.maven.play;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.io.File;
import java.io.IOException;

/**
 * Invoke Play precompilation.
 * <p/>
 *
 * @author <a href="mailto:oznyang@163.com">oznyang</a>
 * @version V1.0, 14-5-6
 */
@Mojo(name = "precompile", requiresDependencyResolution = ResolutionScope.TEST)
public class PlayPrecompileMojo extends AbstractPlayServerMojo {

    /**
     * Before precompile clean the target directory.
     */
    @Parameter(property = "play.precompileClean", defaultValue = "false")
    private boolean precompileClean;

    @Override
    protected void internalExecute() throws MojoExecutionException, MojoFailureException, IOException {
        if (precompileClean) {
            deleteDirectory(new File(project.getBasedir(), "precompiled"));
        }
        precompile();
    }
}
