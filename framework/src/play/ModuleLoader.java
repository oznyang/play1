package play;

import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import play.libs.Files;
import play.libs.IO;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/**
 * .
 * <p/>
 *
 * @author <a href="mailto:oxsean@gmail.com">sean yang</a>
 * @version V1.0, 14-5-7
 */
final class ModuleLoader {
    public static String MODULE_CONF_FIle = "m.conf";
    private static Set<String> LOADED_MODULES = new HashSet<String>();

    public static void loadModules() {
        Properties props;
        try {
            props = IO.readUtf8Properties(new FileInputStream(Play.getFile(MODULE_CONF_FIle)));
        } catch (Exception e) {
            props = new Properties();
        }

        String modulesString = System.getProperty("play.modules");
        if (modulesString != null) {
            for (String s : StringUtils.split(modulesString, ',')) {
                String[] arr = StringUtils.split(s, "->");
                addModule(props, arr[0].trim(), new File(arr[1].trim()));
            }
        }

        File localModules = Play.getFile("modules");
        if (localModules.exists() && localModules.isDirectory()) {
            for (String name : localModules.list()) {
                File module = new File(localModules, name);
                if (module.isFile() && name.toLowerCase().endsWith(".zip")) {
                    String moduleName = StringUtils.substringBefore(name, "-");
                    File to = new File(localModules, moduleName);
                    if (to.exists()) {
                        if (module.lastModified() <= to.lastModified()) {
                            Logger.debug("Module [" + moduleName + "] target dir [" + to.getAbsolutePath() + "] is fresh, skip extract!");
                            continue;
                        }
                    } else {
                        to.mkdirs();
                    }
                    Files.unzip(module, to);
                    to.setLastModified(System.currentTimeMillis());
                }
            }
            for (String name : localModules.list()) {
                File module = new File(localModules, name);
                String moduleName = StringUtils.substringBefore(name, "-");
                if (module.isDirectory()) {
                    addModule(props, moduleName, module);
                } else if (!name.contains(".")) {
                    String path = IO.readContentAsString(module).trim();
                    addModule(props, moduleName, new File(ConfReader.resolvePlaceholder(path)));
                }
            }
        }

        System.setProperty("play.version", Play.version);
        System.setProperty("application.path", Play.applicationPath.getAbsolutePath());

        // Auto add special modules
        if (Play.runingInTestMode()) {
            addModule(props, "_testrunner", new File(Play.frameworkPath, "modules/testrunner"));
        }

        if (Play.mode == Play.Mode.DEV) {
            addModule(props, "_docviewer", new File(Play.frameworkPath, "modules/docviewer"));
        }

        for (Object obj : props.keySet()) {
            String name = obj.toString();
            if (LOADED_MODULES.contains(name)) {
                return;
            }
            String path = ConfReader.resolvePlaceholder(props.getProperty(name));
            if (!path.startsWith("!") && BooleanUtils.toBooleanObject(path) != Boolean.FALSE) {
                File module = new File(path);
                if (!module.isAbsolute()) {
                    module = new File(Play.applicationPath, path);
                }
                addModule(name, module);
            }
        }
        PrecompiledLoader.addPrecompiledPath(Play.getFile("precompiled"));
        if (Play.modules.size() > 0) {
            Logger.info(Play.modules.size() + " module loaded");
        }
    }

    private static void addModule(Properties props, String name, File path) {
        if (BooleanUtils.toBooleanObject(props.getProperty(name)) == Boolean.FALSE) {
            Logger.info("Module %s in [%s] has been disabled", name, path.getAbsoluteFile());
            return;
        }
        addModule(name, path);
        LOADED_MODULES.add(name);
    }

    private static void addModule(String name, File path) {
        if (!path.exists()) {
            Logger.error("!Module %s is unavailable (%s) not exist", name, path.getAbsoluteFile());
            return;
        }
        Play.addModule(name, path);
        PrecompiledLoader.addPrecompiledPath(new File(path, "precompiled"));
    }
}
