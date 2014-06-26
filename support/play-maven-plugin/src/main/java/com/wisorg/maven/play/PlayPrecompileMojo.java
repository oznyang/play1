package com.wisorg.maven.play;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

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

    @Override
    protected void internalExecute() throws MojoExecutionException, MojoFailureException, IOException {
        precompile();
    }
}
