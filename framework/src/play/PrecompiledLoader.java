package play;

import org.apache.commons.lang.StringUtils;
import play.classloading.ApplicationClasses;
import play.templates.BaseTemplate;
import play.templates.GroovyTemplate;
import play.vfs.VirtualFile;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * .
 * <p/>
 *
 * @author <a href="mailto:oznyang@163.com">oznyang</a>
 * @version V1.0, 14-5-8
 */
public final class PrecompiledLoader {
    private static List<VirtualFile> precompiledPath = new ArrayList<VirtualFile>();
    private static final String PRECOMPILE_JAVA_PATH = "precompiled" + File.separator + "java";

    public static void addPrecompiledPath(File path) {
        if (path.exists()) {
            precompiledPath.add(VirtualFile.open(new File(path, "java")));
        }
    }

    public static ApplicationClasses.ApplicationClass loadApplicationClass(String name) {
        if (name.startsWith("play.") || name.startsWith("java.")) {
            return null;
        }
        for (VirtualFile path : precompiledPath) {
            VirtualFile file = path.child(StringUtils.replace(name, ".", "/") + ".class");
            if (file.exists()) {
                VirtualFile javaFile = null;
                if (Play.mode.isDev()) {
                    javaFile = getJavaFile(file.getRealFile().getAbsolutePath(), name);
                    if (javaFile != null && javaFile.lastModified() > file.lastModified()) {
                        return null;
                    }
                }
                ApplicationClasses.ApplicationClass applicationClass = new ApplicationClasses.ApplicationClass();
                applicationClass.name = name;
                applicationClass.classFile = file;
                applicationClass.javaFile = javaFile;
                return applicationClass;
            }
        }
        return null;
    }

    public static void loadClasses() {
        List<ApplicationClasses.ApplicationClass> applicationClasses = new ArrayList<ApplicationClasses.ApplicationClass>(64);
        for (VirtualFile path : precompiledPath) {
            scanPrecompiled(applicationClasses, "", path);
        }
        for (ApplicationClasses.ApplicationClass applicationClass : applicationClasses) {
            if (!Play.classes.hasClass(applicationClass.name)) {
                Play.classes.add(applicationClass);
            }
        }
    }

    private static void scanPrecompiled(List<ApplicationClasses.ApplicationClass> classes, String packageName, VirtualFile current) {
        if (!current.isDirectory()) {
            if (current.getName().endsWith(".class") && !current.getName().startsWith(".")) {
                String classname = packageName.substring(5) + current.getName().substring(0, current.getName().length() - 6);
                ApplicationClasses.ApplicationClass applicationClass = new ApplicationClasses.ApplicationClass();
                applicationClass.name = classname;
                applicationClass.timestamp = current.lastModified();
                applicationClass.classFile = current;
                if (Play.mode.isDev()) {
                    applicationClass.javaFile = getJavaFile(current.getRealFile().getAbsolutePath(), classname);
                }
                classes.add(applicationClass);
            }
        } else {
            for (VirtualFile virtualFile : current.list()) {
                scanPrecompiled(classes, packageName + current.getName() + ".", virtualFile);
            }
        }
    }

    private static VirtualFile getJavaFile(String path, String classname) {
        path = StringUtils.replace(path, PRECOMPILE_JAVA_PATH, "app");
        File file = new File(StringUtils.substringBeforeLast(path, ".") + ".java");
        if (file.exists()) {
            return VirtualFile.open(file);
        } else {
            for (VirtualFile jp : Play.javaPath) {
                VirtualFile javaFile = jp.child(classname);
                if (javaFile.exists()) {
                    return javaFile;
                }
            }
        }
        return null;
    }

    public static long getTemplateLastModified(VirtualFile vf) {
        String name = StringUtils.substringAfter(Play.applicationPath.getAbsolutePath(), vf.getRealFile().getAbsolutePath());
        name = StringUtils.replace(name, ":", "_");
        File file = Play.getFile("precompiled/templates/" + name);
        if (file.exists()) {
            return file.lastModified();
        }
        return 0;
    }

    public static BaseTemplate loadTemplate(VirtualFile file) {
        String path = file.getRealFile().getAbsolutePath();
        for (VirtualFile vf : Play.roots) {
            String rootPath = vf.getRealFile().getAbsolutePath();
            if (path.contains(rootPath)) {
                String relativePath = StringUtils.replace(path.substring(rootPath.length()), ":", "_");
                File precompiledFile = new File(vf.getRealFile(), "precompiled/templates/" + relativePath);
                if (precompiledFile.exists() && precompiledFile.lastModified() >= file.lastModified()) {
                    BaseTemplate template = new GroovyTemplate(relativePath, file.exists() ? file.contentAsString() : "");
                    template.loadPrecompiled(precompiledFile);
                    return template;
                }
            }
        }
        return null;
    }

    public static BaseTemplate loadTemplate(String path) {
        path = StringUtils.replace(path, ":", "_");
        for (VirtualFile vf : Play.roots) {
            File precompiledFile = new File(vf.getRealFile(), "precompiled/templates/app/views/" + path);
            if (precompiledFile.exists()) {
                File sourceFile = new File(vf.getRealFile(), path);
                if (sourceFile.exists() && sourceFile.lastModified() > precompiledFile.lastModified()) {
                    return null;
                }
                BaseTemplate template = new GroovyTemplate(path, sourceFile.exists() ? VirtualFile.open(sourceFile).contentAsString() : "");
                template.loadPrecompiled(precompiledFile);
                return template;
            }
        }
        return null;
    }
}
