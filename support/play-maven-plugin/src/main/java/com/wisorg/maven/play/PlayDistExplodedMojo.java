package com.wisorg.maven.play;

import org.apache.commons.lang.StringUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.plexus.util.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
