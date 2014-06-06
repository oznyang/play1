package com.wisorg.maven.play;

import com.google.common.collect.Sets;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.archiver.Archiver;
import org.codehaus.plexus.archiver.manager.NoSuchArchiverException;
import org.codehaus.plexus.archiver.zip.ZipArchiver;
import org.codehaus.plexus.util.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Package play server or module as one zip achive.
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
    protected String distIncludes;

    /**
     * Distribution application resources exclude filter.
     */
    @Parameter(property = "play.distExcludes", defaultValue = "**/.*")
    protected String distExcludes;

    /**
     * Distribution application source exclude filter
     */
    @Parameter(property = "play.distSourceExcludes", defaultValue = "**/*.java,**/*.html,**/.*")
    protected String distSourceExcludes;

    /**
     * Distribution additional directories.
     */
    @Parameter(property = "play.distAdditionalDirectories")
    protected List<Resource> distAdditionalDirectories;


    /**
     * Distribution dependency artifactId exclude filter.
     */
    @Parameter(property = "play.distExcludeArtifactIds", defaultValue = "")
    protected String distExcludeArtifactIds;

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

    /**
     * The directory for the generated exploded directory.
     */
    @Parameter(property = "play.distExplodedDirectory", defaultValue = "${project.build.directory}/${project.build.finalName}", required = true)
    private File distExplodedDirectory;

    /**
     * Specifies whether or not to generated exploded directory.
     */
    @Parameter(property = "distExploded", defaultValue = "false")
    private boolean distExploded;

    @Component
    private MavenProjectHelper projectHelper;

    @Override
    protected void internalExecute() throws MojoExecutionException, MojoFailureException, IOException {
        super.internalExecute();
        try {
            if (!"play".equals(project.getPackaging())) {
                modulePackage();
            } else {
                if (distExploded) {
                    serverExploded();
                }
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
            for (Resource resource : distAdditionalDirectories) {
                File dir = new File(resource.getDirectory());
                zipArchiver.addDirectory(dir, resource.getTargetPath(), getArray(resource.getExcludes()), getArray(resource.getExcludes()));
                getLog().debug("Add Dist dir: " + dir.getAbsolutePath());
            }
        }

        zipArchiver.addDirectory(getPlayHomeDir("resources"), "resources/", null, null);

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

    protected void serverExploded() throws IOException, MojoExecutionException {
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
            for (Resource resource : distAdditionalDirectories) {
                File dir = new File(resource.getDirectory());
                zipArchiver.addDirectory(dir, resource.getTargetPath(), getArray(resource.getExcludes()), getArray(resource.getExcludes()));
                getLog().debug("Add Dist dir: " + dir.getAbsolutePath());
            }
        }

        File destFile = new File(distOutputDirectory, getDistFileName(distClassifier));
        zipArchiver.setDestFile(destFile);
        zipArchiver.createArchive();
        projectHelper.attachArtifact(project, "zip", distClassifier, destFile);
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

    private String[] getArray(List<String> list) {
        if (list == null || list.isEmpty()) {
            return ArrayUtils.EMPTY_STRING_ARRAY;
        }
        return list.toArray(new String[list.size()]);
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
