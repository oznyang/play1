package play.templates;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Collections;

import play.Logger;
import play.Play;
import play.PrecompiledLoader;
import play.vfs.VirtualFile;
import play.exceptions.TemplateCompilationException;
import play.exceptions.TemplateNotFoundException;

/**
 * Load templates
 */
public class TemplateLoader {

    private static BaseTemplate NULL = new GroovyTemplate("NULL", "");

    protected static Map<String, BaseTemplate> templates = new ConcurrentHashMap<String, BaseTemplate>();
    /**
     * See getUniqueNumberForTemplateFile() for more info
     */
    private static AtomicLong nextUniqueNumber = new AtomicLong(1000);//we start on 1000
    private static ConcurrentMap<String, String> templateFile2UniqueNumber = new ConcurrentHashMap<String, String>();

    /**
     * All loaded templates is cached in the templates-list using a key.
     * This key is included as part of the classname for the generated class for a specific template.
     * The key is included in the classname to make it possible to resolve the original template-file
     * from the classname, when creating cleanStackTrace
     *
     * This method returns a unique representation of the path which is usable as part of a classname
     *
     * @param path
     * @return
     */
    public static String getUniqueNumberForTemplateFile(String path) {
        //a path cannot be a valid classname so we have to convert it somehow.
        //If we did some encoding on the path, the result would be at least as long as the path.
        //Therefor we assign a unique number to each path the first time we see it, and store it..
        //This way, all seen paths gets a unique number. This number is our UniqueValidClassnamePart..

        String uniqueNumber = templateFile2UniqueNumber.get(path);
        if (uniqueNumber == null) {
            //this is the first time we see this path - must assign a unique number to it.
            uniqueNumber = Long.toString(nextUniqueNumber.getAndIncrement());
            templateFile2UniqueNumber.putIfAbsent(path, uniqueNumber);
        }
        return uniqueNumber;
    }

    /**
     * Load a template from a virtual file
     * @param file A VirtualFile
     * @return The executable template
     */
    public static Template load(VirtualFile file) {
        // Try with plugin
        Template pluginProvided = Play.pluginCollection.loadTemplate(file);
        if (pluginProvided != null) {
            return pluginProvided;
        }

        // Use default engine
        String relativePath = file.relativePath();
        String key = getUniqueNumberForTemplateFile(relativePath);
        BaseTemplate template=templates.get(key);
        if (template == null || template.compiledTemplate == null) {
            template = PrecompiledLoader.loadTemplate(file);
            if (template != null) {
                templates.put(key, template);
                return template;
            }
            if (Play.usePrecompiled) {
                template = new GroovyTemplate(PrecompiledLoader.getPrecompiledTemplateName(relativePath), file);
                try {
                    template.loadPrecompiled();
                    templates.put(key, template);
                    return template;
                } catch(Exception e) {
                    Logger.debug("Precompiled template %s not found, trying to load it dynamically...", relativePath);
                }
            }
            template = new GroovyTemplate(relativePath, file);
            if (template.loadFromCache()) {
                templates.put(key, template);
            } else {
                templates.put(key, template = new GroovyTemplateCompiler().compile(file));
            }
        } else {
            if (Play.mode.isDev() && template.timestamp < file.lastModified()) {
                templates.put(key, template = new GroovyTemplateCompiler().compile(file));
            }
        }
        return template;
    }

    /**
     * Load a template from a String
     * @param key A unique identifier for the template, used for retreiving a cached template
     * @param source The template source
     * @return A Template
     */
    public static BaseTemplate load(String key, String source) {
        if (!templates.containsKey(key) || templates.get(key).compiledTemplate == null) {
            BaseTemplate template = new GroovyTemplate(key, source);
            if (template.loadFromCache()) {
                templates.put(key, template);
            } else {
                templates.put(key, new GroovyTemplateCompiler().compile(template));
            }
        } else {
            BaseTemplate template = new GroovyTemplate(key, source);
            if (Play.mode == Play.Mode.DEV) {
                templates.put(key, new GroovyTemplateCompiler().compile(template));
            }
        }
        if (templates.get(key) == null) {
            throw new TemplateNotFoundException(key);
        }
        return templates.get(key);
    }

    /**
     * Clean the cache for that key
     * Then load a template from a String
     * @param key A unique identifier for the template, used for retreiving a cached template
     * @param source The template source
     * @return A Template
     */
    public static BaseTemplate load(String key, String source, boolean reload) {
        cleanCompiledCache(key);
        return load(key, source);
    }

    /**
     * Load template from a String, but don't cache it
     * @param source The template source
     * @return A Template
     */
    public static BaseTemplate loadString(String source) {
        BaseTemplate template = new GroovyTemplate(source);
        return new GroovyTemplateCompiler().compile(template);
    }

    /**
     * Cleans the cache for all templates
     */
    public static void cleanCompiledCache() {
        templates.clear();
    }

    /**
     * Cleans the specified key from the cache
     * @param key The template key
     */
    public static void cleanCompiledCache(String key) {
        templates.remove(key);
    }

    /**
     * Load a template
     * @param path The path of the template (ex: Application/index.html)
     * @return The executable template
     */
    public static Template load(String path) {
        BaseTemplate template = templates.get(path);
        if (Play.usePrecompiled && template == NULL) {
            throw new TemplateNotFoundException(path);
        } else if (template == null || template.compiledTemplate == null) {
            template = PrecompiledLoader.loadTemplate(path);
            if (template != null) {
                templates.put(path, template);
                return template;
            }
        } else {
            if (Play.mode.isDev() && template.isModified()) {
                templates.put(path, template = new GroovyTemplateCompiler().compile(template.sourceFile));
            }
            return template;
        }

        for (VirtualFile vf : Play.templatesPath) {
            VirtualFile tf = vf.child(path);
            if (tf.exists()) {
                template = (BaseTemplate) TemplateLoader.load(tf);
                templates.put(path, template);
                return template;
            }
        }

        VirtualFile tf = Play.getVirtualFile(path);
        if (tf != null && tf.exists()) {
            template = (BaseTemplate) TemplateLoader.load(tf);
            templates.put(path, template);
            return template;
        }
        if (Play.usePrecompiled) {
            templates.put(path, NULL);
        }
        throw new TemplateNotFoundException(path);
    }

    /**
     * List all found templates
     * @return A list of executable templates
     */
    public static List<Template> getAllTemplate() {
        List<Template> res = new ArrayList<Template>();
        for (VirtualFile virtualFile : Play.templatesPath) {
            if (virtualFile.exists()) {
                scan(res, virtualFile);
            }
        }
        for (VirtualFile root : Play.roots) {
            VirtualFile vf = root.child("conf/routes");
            if (vf != null && vf.exists()) {
                if (PrecompiledLoader.loadTemplate(vf) != null) {
                    continue;
                }
                BaseTemplate template = (BaseTemplate)load(vf);
                if (template != null) {
                    template.compile();
                }
            }
        }
        return res;
    }

    private static void scan(List<Template> templates, VirtualFile current) {
        if (!current.isDirectory() && !current.getName().startsWith(".") && !current.getName().endsWith(".scala.html")) {
            long start = System.currentTimeMillis();
            if (PrecompiledLoader.loadTemplate(current) != null) {
                return;
            }
            BaseTemplate template = (BaseTemplate)load(current);
            if (template != null) {
                try {
                    template.compile();
                    if (Logger.isTraceEnabled()) {
                        Logger.trace("%sms to load %s", System.currentTimeMillis() - start, current.getName());
                    }
                } catch (TemplateCompilationException e) {
                    Logger.error("Template %s does not compile at line %d", e.getTemplate().name, e.getLineNumber());
                    throw e;
                }
                templates.add(template);
            }
        } else if (current.isDirectory() && !current.getName().startsWith(".")) {
            for (VirtualFile virtualFile : current.list()) {
                scan(templates, virtualFile);
            }
        }
    }
}
