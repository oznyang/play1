package com.wisorg.maven.play;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.io.IOException;

/**
 * Create exploded play server.
 * <p/>
 *
 * @author <a href="mailto:oxsean@gmail.com">sean yang</a>
 * @version V1.0, 14-5-25
 */
@Mojo(name = "dist-exploded", requiresDependencyResolution = ResolutionScope.TEST)
public class PlayDistExplodedMojo extends PlayDistMojo {

    @Override
    protected void internalExecute() throws MojoExecutionException, MojoFailureException, IOException {
        assertPlayServer();
        precompile();
        serverExploded();
    }
}
