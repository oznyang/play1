package play.classloading;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.instrument.ClassDefinition;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.AllPermission;
import java.security.CodeSource;
import java.security.Permissions;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import play.Logger;
import play.Play;
import play.PrecompiledLoader;
import play.classloading.enhancers.SigEnhancer;
import play.classloading.hash.ClassStateHashCreator;
import play.vfs.VirtualFile;
import play.cache.Cache;
import play.classloading.ApplicationClasses.ApplicationClass;
import play.exceptions.UnexpectedException;
import play.libs.IO;

/**
 * The application classLoader.
 * Load the classes from the application Java sources files.
 */
public class ApplicationClassloader extends ClassLoader {


    private final ClassStateHashCreator classStateHashCreator = new ClassStateHashCreator();

    /**
     * A representation of the current state of the ApplicationClassloader.
     * It gets a new value each time the state of the classloader changes.
     */
    public ApplicationClassloaderState currentState = new ApplicationClassloaderState();

    /**
     * This protection domain applies to all loaded classes.
     */
    public ProtectionDomain protectionDomain;

    private final Object lock = new Object();

    public ApplicationClassloader() {
        super(ApplicationClassloader.class.getClassLoader());
        // Clean the existing classes
        for (ApplicationClass applicationClass : Play.classes.all()) {
            applicationClass.uncompile();
        }
        try {
            CodeSource codeSource = new CodeSource(new URL("file:" + Play.applicationPath.getAbsolutePath()), (Certificate[]) null);
            Permissions permissions = new Permissions();
            permissions.add(new AllPermission());
            protectionDomain = new ProtectionDomain(codeSource, permissions);
        } catch (MalformedURLException e) {
            throw new UnexpectedException(e);
        }
    }

    /**
     * You know ...
     */
    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        // Loook up our cache
        Class<?> c = findLoadedClass(name);
        if (c != null) {
            return c;
        }

        synchronized( lock ) {
             // First check if it's an application Class
            Class<?> applicationClass = loadApplicationClass(name);
            if (applicationClass != null) {
                if (resolve) {
                    resolveClass(applicationClass);
                }
                return applicationClass;
            }
        }
        // Delegate tothe classic classloader
        return super.loadClass(name, resolve);
    }

    public Class<?> loadPrecompiledClass(ApplicationClass applicationClass) {
        String name = applicationClass.name;
        File file = applicationClass.classFile.getRealFile();
        try {
            byte[] code = IO.readContent(file);
            Class<?> clazz = findLoadedClass(name);
            if (clazz == null) {
                if (name.endsWith("package-info")) {
                    definePackage(getPackageName(name), null, null, null, null, null, null, null);
                } else {
                    loadPackage(name);
                }
                clazz = defineClass(name, code, 0, code.length, protectionDomain);
            }
            applicationClass.javaClass = clazz;
            applicationClass.enhancedByteCode = applicationClass.javaByteCode = code;
            applicationClass.compiled = true;
            if (!applicationClass.isClass()) {
                applicationClass.javaPackage = applicationClass.javaClass.getPackage();
            }
            return clazz;
        } catch (Exception e) {
            throw new RuntimeException("Load precompiled class file [" + file.getAbsolutePath() + "] for " + name + " error");
        }
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~
    public Class<?> loadApplicationClass(String name) {

        if (ApplicationClass.isClass(name)) {
            Class maybeAlreadyLoaded = findLoadedClass(name);
            if (maybeAlreadyLoaded != null) {
                return maybeAlreadyLoaded;
            }
        }

        if (Play.classes.hasClass(name)) {
            ApplicationClass applicationClass = Play.classes.getApplicationClass(name);
            if (applicationClass.isDefinable()) {
                return applicationClass.javaClass;
            } else {
                VirtualFile classFile = applicationClass.classFile;
                if (classFile != null) {
                    VirtualFile javaFile = applicationClass.javaFile;
                    if (Play.usePrecompiled || javaFile == null || javaFile.lastModified() < classFile.lastModified()) {
                        return loadPrecompiledClass(applicationClass);
                    }
                }
            }
        } else {
            ApplicationClass applicationClass = PrecompiledLoader.loadApplicationClass(name);
            if (applicationClass != null) {
                Play.classes.add(applicationClass);
                return loadPrecompiledClass(applicationClass);
            }
        }

        long start = System.currentTimeMillis();
        ApplicationClass applicationClass = Play.classes.getApplicationClass(name);
        if (applicationClass != null) {
            if (applicationClass.isDefinable()) {
                return applicationClass.javaClass;
            }
            byte[] bc = BytecodeCache.getBytecode(name, applicationClass.javaSource);

            if (Logger.isTraceEnabled()) {
                Logger.trace("Compiling code for %s", name);
            }

            if (!applicationClass.isClass()) {
                definePackage(applicationClass.getPackage(), null, null, null, null, null, null, null);
            } else {
                loadPackage(name);
            }
            if (bc != null) {
                applicationClass.enhancedByteCode = bc;
                applicationClass.javaClass = defineClass(applicationClass.name, applicationClass.enhancedByteCode, 0, applicationClass.enhancedByteCode.length, protectionDomain);
                resolveClass(applicationClass.javaClass);
                if (!applicationClass.isClass()) {
                    applicationClass.javaPackage = applicationClass.javaClass.getPackage();
                }

                if (Logger.isTraceEnabled()) {
                    Logger.trace("%sms to load class %s from cache", System.currentTimeMillis() - start, name);
                }

                return applicationClass.javaClass;
            }
            if (applicationClass.javaByteCode != null || applicationClass.compile() != null) {
                applicationClass.enhance();
                applicationClass.javaClass = defineClass(applicationClass.name, applicationClass.enhancedByteCode, 0, applicationClass.enhancedByteCode.length, protectionDomain);
                BytecodeCache.cacheBytecode(applicationClass.enhancedByteCode, name, applicationClass.javaSource);
                resolveClass(applicationClass.javaClass);
                if (!applicationClass.isClass()) {
                    applicationClass.javaPackage = applicationClass.javaClass.getPackage();
                }

                if (Logger.isTraceEnabled()) {
                    Logger.trace("%sms to load class %s", System.currentTimeMillis() - start, name);
                }

                return applicationClass.javaClass;
            }
            Play.classes.classes.remove(name);
        }
        return null;
    }

    private String getPackageName(String name) {
        int dot = name.lastIndexOf('.');
        return dot > -1 ? name.substring(0, dot) : "";
    }

    private void loadPackage(String className) {
        // find the package class name
        int symbol = className.indexOf("$");
        if (symbol > -1) {
            className = className.substring(0, symbol);
        }
        symbol = className.lastIndexOf(".");
        if (symbol > -1) {
            className = className.substring(0, symbol) + ".package-info";
        } else {
            className = "package-info";
        }
        if (findLoadedClass(className) == null) {
            loadApplicationClass(className);
        }
    }

    /**
     * Search for the byte code of the given class.
     */
    protected byte[] getClassDefinition(String name) {
        if (Play.classes.hasClass(name)) {
            ApplicationClass applicationClass = Play.classes.getApplicationClass(name);
            if (applicationClass.classFile != null) {
                return IO.readContent(applicationClass.classFile.getRealFile());
            }
        }
        name = name.replace(".", "/") + ".class";
        InputStream is = getResourceAsStream(name);
        if (is == null) {
            return null;
        }
        try {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = is.read(buffer, 0, buffer.length)) > 0) {
                os.write(buffer, 0, count);
            }
            return os.toByteArray();
        } catch (Exception e) {
            throw new UnexpectedException(e);
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                throw new UnexpectedException(e);
            }
        }
    }

    /**
     * You know ...
     */
    @Override
    public InputStream getResourceAsStream(String name) {
        for (VirtualFile vf : Play.javaPath) {
            VirtualFile res = vf.child(name);
            if (res != null && res.exists()) {
                return res.inputstream();
            }
        }
        return super.getResourceAsStream(name);
    }

    /**
     * You know ...
     */
    @Override
    public URL getResource(String name) {
        for (VirtualFile vf : Play.javaPath) {
            VirtualFile res = vf.child(name);
            if (res != null && res.exists()) {
                try {
                    return res.getRealFile().toURI().toURL();
                } catch (MalformedURLException ex) {
                    throw new UnexpectedException(ex);
                }
            }
        }
        return super.getResource(name);
    }

    /**
     * You know ...
     */
    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        List<URL> urls = new ArrayList<URL>();
        for (VirtualFile vf : Play.javaPath) {
            VirtualFile res = vf.child(name);
            if (res != null && res.exists()) {
                try {
                    urls.add(res.getRealFile().toURI().toURL());
                } catch (MalformedURLException ex) {
                    throw new UnexpectedException(ex);
                }
            }
        }
        Enumeration<URL> parent = super.getResources(name);
        while (parent.hasMoreElements()) {
            URL next = parent.nextElement();
            if (!urls.contains(next)) {
                urls.add(next);
            }
        }
        final Iterator<URL> it = urls.iterator();
        return new Enumeration<URL>() {

            public boolean hasMoreElements() {
                return it.hasNext();
            }

            public URL nextElement() {
                return it.next();
            }
        };
    }

    /**
     * Detect Java changes
     */
    public void detectChanges() {
        // Now check for file modification
        List<ApplicationClass> modifieds = new ArrayList<ApplicationClass>();
        for (ApplicationClass applicationClass : Play.classes.all()) {
            if (applicationClass.javaFile != null && applicationClass.timestamp < applicationClass.javaFile.lastModified()) {
                if (applicationClass.enhancedByteCode != null && applicationClass.sigChecksum == 0) {
                    try {
                        new SigEnhancer().enhanceThisClass(applicationClass);
                    } catch (Exception ignored) {
                    }
                }
                applicationClass.refresh();
                modifieds.add(applicationClass);
            }
        }
        Set<ApplicationClass> modifiedWithDependencies = new HashSet<ApplicationClass>();
        modifiedWithDependencies.addAll(modifieds);
        if (modifieds.size() > 0) {
            modifiedWithDependencies.addAll(Play.pluginCollection.onClassesChange(modifieds));
        }
        List<ClassDefinition> newDefinitions = new ArrayList<ClassDefinition>();
        boolean dirtySig = false;
        for (ApplicationClass applicationClass : modifiedWithDependencies) {
            if (applicationClass.compile() == null) {
                Play.classes.classes.remove(applicationClass.name);
                currentState = new ApplicationClassloaderState();//show others that we have changed..
            } else {
                int sigChecksum = applicationClass.sigChecksum;
                applicationClass.enhance();
                if (sigChecksum != applicationClass.sigChecksum) {
                    dirtySig = true;
                }
                BytecodeCache.cacheBytecode(applicationClass.enhancedByteCode, applicationClass.name, applicationClass.javaSource);
                newDefinitions.add(new ClassDefinition(applicationClass.javaClass, applicationClass.enhancedByteCode));
                currentState = new ApplicationClassloaderState();//show others that we have changed..
            }
        }
        if (newDefinitions.size() > 0) {
            Cache.clear();
            if (HotswapAgent.enabled) {
                try {
                    HotswapAgent.reload(newDefinitions.toArray(new ClassDefinition[newDefinitions.size()]));
                    for (ClassDefinition cd : newDefinitions) {
                        ClassCache.clean(cd.getDefinitionClass());
                    }
                } catch (Throwable e) {
                    throw new RuntimeException("Need reload");
                }
            } else {
                throw new RuntimeException("Need reload");
            }
        }
        // Check signature (variable name & annotations aware !)
        if (dirtySig) {
            throw new RuntimeException("Signature change !");
        }

        // Now check if there is new classes or removed classes
        int hash = computePathHash();
        if (pathHash == 0) {
            pathHash = hash;
        } else if (hash != pathHash) {
            // Remove class for deleted files !!
            for (ApplicationClass applicationClass : Play.classes.all()) {
                if (applicationClass.javaFile != null && !applicationClass.javaFile.exists()) {
                    Play.classes.classes.remove(applicationClass.name);
                    currentState = new ApplicationClassloaderState();//show others that we have changed..
                }
                if (applicationClass.name.contains("$")) {
                    Play.classes.classes.remove(applicationClass.name);
                    currentState = new ApplicationClassloaderState();//show others that we have changed..
                    // Ok we have to remove all classes from the same file ...
                    VirtualFile vf = applicationClass.javaFile;
                    if (vf == null) {
                        continue;
                    }
                    for (ApplicationClass ac : Play.classes.all()) {
                        if (vf.equals(ac.javaFile)) {
                            Play.classes.classes.remove(ac.name);
                        }
                    }
                }
            }
            throw new RuntimeException("Path has changed");
        }
    }
    /**
     * Used to track change of the application sources path
     */
    int pathHash = 0;

    int computePathHash() {
        return classStateHashCreator.computePathHash(Play.javaPath);
    }

    /**
     * Try to load all .java files found.
     * @return The list of well defined Class
     */
    public List<Class> getAllClasses() {
        if (allClasses == null) {
            allClasses = new ArrayList<Class>();

            PrecompiledLoader.loadClasses();
            if (Play.usePrecompiled) {

                for(ApplicationClass applicationClass:Play.classes.all()){
                    allClasses.add(applicationClass.javaClass);
                }
/*
                List<ApplicationClass> applicationClasses = new ArrayList<ApplicationClass>();
                scanPrecompiled(applicationClasses, "", Play.getVirtualFile("precompiled/java"));
                Play.classes.clear();
                for (ApplicationClass applicationClass : applicationClasses) {
                    Play.classes.add(applicationClass);
                    Class clazz = loadApplicationClass(applicationClass.name);
                    applicationClass.javaClass = clazz;
                    applicationClass.compiled = true;
                    allClasses.add(clazz);
                }
*/

            } else {

                if (!Play.pluginCollection.compileSources()) {

                    List<ApplicationClass> all = new ArrayList<ApplicationClass>();

                    for (VirtualFile virtualFile : Play.javaPath) {
                        all.addAll(getAllClasses(virtualFile));
                    }
                    List<String> classNames = new ArrayList<String>();
                    for (int i = 0; i < all.size(); i++) {
                        ApplicationClass applicationClass = all.get(i);
                        if (applicationClass != null && !applicationClass.compiled && applicationClass.isClass()) {
                            classNames.add(all.get(i).name);
                        }
                    }

                    if (classNames.size() > 0) {
                        Play.classes.compiler.compile(classNames.toArray(new String[classNames.size()]));
                    }

                }

                for (ApplicationClass applicationClass : Play.classes.all()) {
                    Class clazz = loadApplicationClass(applicationClass.name);
                    if (clazz != null) {
                        allClasses.add(clazz);
                    }
                }
/*
                Collections.sort(allClasses, new Comparator<Class>() {

                    public int compare(Class o1, Class o2) {
                        return o1.getName().compareTo(o2.getName());
                    }
                });
*/
            }
        }
        return allClasses;
    }
    List<Class> allClasses = null;

    /**
     * Retrieve all application classes assignable to this class.
     * @param clazz The superclass, or the interface.
     * @return A list of class
     */
    public List<Class> getAssignableClasses(Class clazz) {
        getAllClasses();
        List<Class> results = new ArrayList<Class>();
        for (ApplicationClass c : Play.classes.getAssignableClasses(clazz)) {
            results.add(c.javaClass);
        }
        return results;
    }

    /**
     * Find a class in a case insensitive way
     * @param name The class name.
     * @return a class
     */
    public Class getClassIgnoreCase(String name) {
        getAllClasses();
        for (ApplicationClass c : Play.classes.all()) {
            if (c.name.equalsIgnoreCase(name) || c.name.replace("$", ".").equalsIgnoreCase(name)) {
                if (Play.usePrecompiled) {
                    return c.javaClass;
                }
                return loadApplicationClass(c.name);
            }
        }
        return null;
    }

    /**
     * Retrieve all application classes with a specific annotation.
     * @param clazz The annotation class.
     * @return A list of class
     */
    public List<Class> getAnnotatedClasses(Class<? extends Annotation> clazz) {
        getAllClasses();
        List<Class> results = new ArrayList<Class>();
        for (ApplicationClass c : Play.classes.getAnnotatedClasses(clazz)) {
            results.add(c.javaClass);
        }
        return results;
    }

    public List<Class> getAnnotatedClasses(Class[] clazz) {
        List<Class> results = new ArrayList<Class>();
        for (Class<? extends Annotation> cl : clazz) {
            results.addAll(getAnnotatedClasses(cl));
        }
        return results;
    }

    // ~~~ Intern
    List<ApplicationClass> getAllClasses(String basePackage) {
        List<ApplicationClass> res = new ArrayList<ApplicationClass>();
        for (VirtualFile virtualFile : Play.javaPath) {
            res.addAll(getAllClasses(virtualFile, basePackage));
        }
        return res;
    }

    List<ApplicationClass> getAllClasses(VirtualFile path) {
        return getAllClasses(path, "");
    }

    List<ApplicationClass> getAllClasses(VirtualFile path, String basePackage) {
        if (basePackage.length() > 0 && !basePackage.endsWith(".")) {
            basePackage += ".";
        }
        List<ApplicationClass> res = new ArrayList<ApplicationClass>();
        for (VirtualFile virtualFile : path.list()) {
            scan(res, basePackage, virtualFile);
        }
        return res;
    }

    void scan(List<ApplicationClass> classes, String packageName, VirtualFile current) {
        if (!current.isDirectory()) {
            if (current.getName().endsWith(".java") && !current.getName().startsWith(".")) {
                String classname = packageName + current.getName().substring(0, current.getName().length() - 5);
                if (Play.classes.hasClass(classname)) {
                    ApplicationClass precompiledClass = Play.classes.getApplicationClass(classname);
                    if (precompiledClass.timestamp < current.lastModified()) {
                        Play.classes.remove(precompiledClass);
                    }else {
                        return;
                    }
                }
                classes.add(Play.classes.getApplicationClass(classname));
            }
        } else {
            for (VirtualFile virtualFile : current.list()) {
                scan(classes, packageName + current.getName() + ".", virtualFile);
            }
        }
    }

    void scanPrecompiled(List<ApplicationClass> classes, String packageName, VirtualFile current) {
        if (!current.isDirectory()) {
            if (current.getName().endsWith(".class") && !current.getName().startsWith(".")) {
                String classname = packageName.substring(5) + current.getName().substring(0, current.getName().length() - 6);
                classes.add(new ApplicationClass(classname));
            }
        } else {
            for (VirtualFile virtualFile : current.list()) {
                scanPrecompiled(classes, packageName + current.getName() + ".", virtualFile);
            }
        }
    }

    @Override
    public String toString() {
        return "(play) " + (allClasses == null ? "" : allClasses.toString());
    }

}
