package com.wisorg.maven.play;

import com.google.common.collect.Sets;
import org.apache.commons.lang.StringUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.archiver.Archiver;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.archiver.manager.NoSuchArchiverException;
import org.codehaus.plexus.archiver.zip.ZipArchiver;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Package Play server or module as one zip achive.
 * <p/>
 *
 * @author <a href="mailto:oznyang@163.com">oznyang</a>
 * @version V1.0, 14-5-6
 */
@Mojo(name = "dist", requiresDependencyResolution = ResolutionScope.TEST)
public class PlayDistMojo extends PlayPrecompileMojo {
    protected static final Set<String> IGNORE_JARS = Sets.newHashSet("docviewer", "testrunner");

    /**
     * Distribution application resources include filter
     */
    @Parameter(property = "play.distIncludes", defaultValue = "precompiled/,conf/,public/,documentation/,bin/")
    private String distIncludes;

    /**
     * Distribution application resources exclude filter.
     */
    @Parameter(property = "play.distExcludes", defaultValue = "")
    private String distExcludes;

    /**
     * Distribution application source exclude filter
     */
    @Parameter(property = "play.distSourceExcludes", defaultValue = "**/*.java,**/*.html")
    private String distSourceExcludes;

    /**
     * Distribution additional directories.
     */
    @Parameter(property = "play.distAdditionalDirectories")
    private File[] distAdditionalDirectories;

    /**
     * Distribution dependency artifactId exclude filter.
     */
    @Parameter(property = "play.distExcludeArtifactIds", defaultValue = "")
    private String distExcludeArtifactIds;

    /**
     * The directory for the generated distribution file.
     */
    @Parameter(property = "play.distOutputDirectory", defaultValue = "${project.build.directory}", required = true)
    private String distOutputDirectory;

    /**
     * The name of the generated distribution file.
     */
    @Parameter(property = "play.distArchiveName", defaultValue = "${project.build.finalName}", required = true)
    private String distArchiveName;

    /**
     * Classifier to add to the generated distribution file.
     */
    @Parameter(property = "play.distClassifier", defaultValue = "")
    private String distClassifier;

    /**
     * Specifies whether or not to create the source distribution to the project
     */
    @Parameter(property = "distAttachSource", defaultValue = "true")
    private boolean distAttachSource;

    /**
     * Classifier to add to the source distribution file.
     */
    @Parameter(property = "play.distSourceClassifier", defaultValue = "sources")
    private String distSourceClassifier;

    @Component
    private MavenProjectHelper projectHelper;

    @Override
    protected void internalExecute() throws MojoExecutionException, MojoFailureException, IOException {
        super.internalExecute();
        try {
            if (!"play".equals(project.getPackaging())) {
                modulePackage();
            } else {
                serverPackage();
            }
            if (distAttachSource) {
                sourcePackage();
            }
        } catch (NoSuchArchiverException e) {
            throw new MojoExecutionException("Dist error", e);
        }
    }

    private void serverPackage() throws NoSuchArchiverException, IOException, MojoExecutionException {
        ZipArchiver zipArchiver = (ZipArchiver) archiverManager.getArchiver("zip");
        zipArchiver.setDuplicateBehavior(Archiver.DUPLICATES_SKIP);
        zipArchiver.setIncludeEmptyDirs(false);
        File baseDir = project.getBasedir();
        getLog().debug("Dist includes: " + distIncludes);
        getLog().debug("Dist excludes: " + distExcludes);
        getLog().debug("Dist sourceExcludes: " + distSourceExcludes);

        String[] includes = null;
        if (StringUtils.isNotEmpty(distIncludes)) {
            includes = StringUtils.split(distIncludes, ",");
        }
        String[] excludes = null;
        if (StringUtils.isNotEmpty(distExcludes)) {
            excludes = StringUtils.split(distExcludes, ",");
        }
        zipArchiver.addDirectory(baseDir, includes, excludes);

        String[] sourceExcludes = null;
        if (StringUtils.isNotEmpty(distSourceExcludes)) {
            sourceExcludes = StringUtils.split(distSourceExcludes, ",");
        }
        zipArchiver.addDirectory(new File(baseDir, "app"), "app/", null, sourceExcludes);
        if (distAdditionalDirectories != null) {
            for (File dir : distAdditionalDirectories) {
                zipArchiver.addDirectory(dir);
                getLog().debug("Add Dist dir: " + dir.getAbsolutePath());
            }
        }

        Set<String> excludeIds = new HashSet<String>();
        if (distExcludeArtifactIds != null) {
            for (String s : StringUtils.split(distExcludeArtifactIds, ",")) {
                excludeIds.add(s.trim());
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
                    zipArchiver.addFile(artifact.getFile(), "modules/" + artifact.getFile().getName());
                    getLog().debug("Add module [" + artifact.getFile().getAbsolutePath() + "]");
                } else if ("jar".equals(artifact.getType()) && !isExcludeJar(IGNORE_JARS, excludeIds, artifact)) {
                    zipArchiver.addFile(artifact.getFile(), "lib/" + artifact.getFile().getName());
                    getLog().debug("Add jar [" + artifact.getFile().getAbsolutePath() + "]");
                }
            }
        }

        File destFile = new File(distOutputDirectory, getDistFileName(distClassifier));
        zipArchiver.setDestFile(destFile);
        zipArchiver.createArchive();
        projectHelper.attachArtifact(project, "zip", distClassifier, destFile);
    }

    private void modulePackage() throws NoSuchArchiverException, IOException {
        ZipArchiver zipArchiver = (ZipArchiver) archiverManager.getArchiver("zip");
        zipArchiver.setDuplicateBehavior(Archiver.DUPLICATES_SKIP);
        zipArchiver.setIncludeEmptyDirs(false);
        File baseDir = project.getBasedir();
        getLog().debug("Dist includes: " + distIncludes);
        getLog().debug("Dist excludes: " + distExcludes);
        getLog().debug("Dist sourceExcludes: " + distSourceExcludes);

        String[] includes = null;
        if (StringUtils.isNotEmpty(distIncludes)) {
            includes = StringUtils.split(distIncludes, ",");
        }
        String[] excludes = null;
        if (StringUtils.isNotEmpty(distExcludes)) {
            excludes = StringUtils.split(distExcludes, ",");
        }
        zipArchiver.addDirectory(baseDir, includes, excludes);

        String[] sourceExcludes = null;
        if (StringUtils.isNotEmpty(distSourceExcludes)) {
            sourceExcludes = StringUtils.split(distSourceExcludes, ",");
        }
        zipArchiver.addDirectory(new File(baseDir, "app"), "app/", null, sourceExcludes);
        if (distAdditionalDirectories != null) {
            for (File dir : distAdditionalDirectories) {
                zipArchiver.addDirectory(dir);
                getLog().debug("Add Dist dir: " + dir.getAbsolutePath());
            }
        }

        File destFile = new File(distOutputDirectory, getDistFileName(distClassifier));
        zipArchiver.setDestFile(destFile);
        zipArchiver.createArchive();
        projectHelper.attachArtifact(project, "zip", distClassifier, destFile);
    }

    public static String cleanToBeTokenizedString(String str) {
        String ret = "";
        if (!StringUtils.isEmpty(str)) {
            ret = str.trim().replaceAll("[\\s]*,[\\s]*", ",");
        }
        return ret;
    }

    private void sourcePackage() throws NoSuchArchiverException, IOException {
        ZipArchiver zipArchiver = (ZipArchiver) archiverManager.getArchiver("zip");
        zipArchiver.setDuplicateBehavior(Archiver.DUPLICATES_SKIP);
        zipArchiver.setIncludeEmptyDirs(false);
        File baseDir = project.getBasedir();
        for (String s : new String[]{"src", "app", "conf"}) {
            File dir = new File(baseDir, s);
            if (dir.exists()) {
                zipArchiver.addDirectory(dir, null, new String[]{"**/.*"});
            }
        }

        File destFile = new File(distOutputDirectory, getDistFileName(distSourceClassifier));
        zipArchiver.setDestFile(destFile);
        zipArchiver.createArchive();
        projectHelper.attachArtifact(project, "zip", distSourceClassifier, destFile);
    }

    public String getDistFileName(String classifier) {
        StringBuilder sb = new StringBuilder();
        sb.append(distArchiveName);
        if (StringUtils.isNotEmpty(classifier)) {
            if (!classifier.startsWith("-")) {
                sb.append('-');
            }
            sb.append(classifier);
        }
        sb.append(".zip");
        return sb.toString();
    }
}
