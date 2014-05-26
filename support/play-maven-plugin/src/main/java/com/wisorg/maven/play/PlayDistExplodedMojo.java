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

    @Parameter(property = "play.distExplodedDirectory", defaultValue = "${project.build.directory}/${project.build.finalName}", required = true)
    private File distExplodedDirectory;

    @Override
    protected void internalExecute() throws MojoExecutionException, MojoFailureException, IOException {
        assertPlayServer();
        precompile();
        File baseDir = project.getBasedir();
        getLog().debug("Dist includes: " + distIncludes);
        getLog().debug("Dist excludes: " + distExcludes);
        getLog().debug("Dist sourceExcludes: " + distSourceExcludes);

        copyDirectory(baseDir, distExplodedDirectory, StringUtils.deleteWhitespace(distIncludes), StringUtils.deleteWhitespace(distExcludes));
        copyDirectory(new File(baseDir, "app"), new File(distExplodedDirectory, "app"), null, StringUtils.deleteWhitespace(distSourceExcludes));

        if (distAdditionalDirectories != null) {
            for (Resource resource : distAdditionalDirectories) {
                File dir = getFile(baseDir, resource.getDirectory());
                copyDirectory(dir, getFile(distExplodedDirectory, StringUtils.defaultIfEmpty(resource.getTargetPath(), resource.getDirectory())), StringUtils.join(resource.getIncludes(), ","), StringUtils.join(resource.getExcludes(), ","));
                getLog().debug("Add Dist dir: " + dir.getAbsolutePath());
            }
        }

        FileUtils.copyDirectoryStructure(getPlayHomeDir("resources"), new File(distExplodedDirectory, "resources"));

        Set<String> excludeIds = new HashSet<String>();
        if (distExcludeArtifactIds != null) {
            for (String s : StringUtils.split(StringUtils.deleteWhitespace(distExcludeArtifactIds), ",")) {
                excludeIds.add(s);
                int i = s.lastIndexOf("/*");
                if (i > -1) {
                    excludeIds.add(s.substring(0, i));
                }
            }
        }

        for (Object obj : project.getArtifacts()) {
            Artifact artifact = (Artifact) obj;
            if (!Artifact.SCOPE_PROVIDED.equals(artifact.getScope())) {
                if ("zip".equals(artifact.getType())) {
                    String artifactId = artifact.getArtifactId();
                    if (IGNORE_MODULES.contains(artifactId)) {
                        continue;
                    }
                    FileUtils.copyFileIfModified(artifact.getFile(), new File(distExplodedDirectory, "modules/" + artifact.getFile().getName()));
                    getLog().debug("Add module [" + artifact.getFile().getAbsolutePath() + "]");
                } else if ("jar".equals(artifact.getType()) && !isExcludeJar(IGNORE_JARS, excludeIds, artifact)) {
                    FileUtils.copyFileIfModified(artifact.getFile(), new File(distExplodedDirectory, "lib/" + artifact.getFile().getName()));
                    getLog().debug("Add jar [" + artifact.getFile().getAbsolutePath() + "]");
                }
            }
        }
    }

    private static void copyDirectory(File sourceDirectory, File destinationDirectory, String includes, String excludes) throws IOException {
        if (!sourceDirectory.exists()) {
            return;
        }
        List<File> files = FileUtils.getFiles(sourceDirectory, includes, excludes);
        String sourcePath = sourceDirectory.getAbsolutePath();
        for (File file : files) {
            FileUtils.copyFileIfModified(file, new File(destinationDirectory, StringUtils.substringAfter(file.getAbsolutePath(), sourcePath)));
        }
    }

    private static File getFile(File parent, String child) {
        return StringUtils.isEmpty(child) ? parent : new File(parent, child);
    }
}
