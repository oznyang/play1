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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * .
 * <p/>
 *
 * @author <a href="mailto:oxsean@gmail.com">sean yang</a>
 * @version V1.0, 14-5-7
 */
final class ModuleLoader {
    public static Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)}");
    public static String MODULE_CONF_FIle = "m.conf";
    private static Set<String> LOADED_MODULES = new HashSet<String>();

    public static void loadModules() {
        PrecompiledLoader.addPrecompiledPath(Play.getFile("precompiled"));
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
                    addModule(props, moduleName, new File(resolvePlaceholder(path)));
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

        for (String name : props.stringPropertyNames()) {
            if (LOADED_MODULES.contains(name)) {
                return;
            }
            String path = resolvePlaceholder(props.getProperty(name));
            if (BooleanUtils.toBooleanObject(path) != Boolean.FALSE) {
                addModule(name, new File(path));
            }
        }
    }

    private static String resolvePlaceholder(String value) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(value);
        StringBuffer sb = new StringBuffer(64);
        while (matcher.find()) {
            String jp = matcher.group(1);
            String r;
            if (jp.equals("application.path")) {
                r = Play.applicationPath.getAbsolutePath();
            } else if (jp.equals("play.path")) {
                r = Play.frameworkPath.getAbsolutePath();
            } else if (jp.equals("play.tmp")) {
                r = Play.tmpDir.getAbsolutePath();
            } else {
                r = Play.configuration.getProperty(jp);
                if (r == null) {
                    r = System.getProperty(jp);
                    if (r == null) {
                        r = System.getenv(jp);
                    }
                }
                if (r == null) {
                    continue;
                }
            }
            matcher.appendReplacement(sb, r);
        }
        matcher.appendTail(sb);
        return sb.toString();
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
            Logger.error("Module %s will not be loaded because %s does not exist", name, path.getAbsolutePath());
            return;
        }
        Play.addModule(name, path);
        PrecompiledLoader.addPrecompiledPath(new File(path, "precompiled"));
    }
}
