package play;

import play.libs.IO;
import play.utils.OrderSafeProperties;
import play.vfs.VirtualFile;

import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * .
 * <p/>
 *
 * @author <a href="mailto:oxsean@gmail.com">sean yang</a>
 * @version V1.0, 14-5-17
 */
public class ConfReader {
    public static Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)}");
    public static Pattern PROPS_PATTERN = Pattern.compile("^%([a-zA-Z0-9_\\-]+)\\.(.*)$");

    public static String resolvePlaceholder(String value) {
        return resolvePlaceholder(value, null);
    }

    public static String resolvePlaceholder(String value, Properties props) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(value);
        StringBuffer sb = new StringBuffer(64);
        boolean matched = false;
        while (matcher.find()) {
            matched = true;
            String jp = matcher.group(1);
            String r = null;
            String def = null;
            int i = jp.indexOf(':');
            if (i > 0) {
                def = jp.substring(i + 1);
                jp = jp.substring(0, i);
            }
            if (jp.equals("application.path")) {
                r = Play.applicationPath.getAbsolutePath();
            } else if (jp.equals("play.path")) {
                r = Play.frameworkPath.getAbsolutePath();
            } else if (jp.equals("play.tmp")) {
                r = Play.tmpDir.getAbsolutePath();
            } else {
                if (props != null) {
                    r = props.getProperty(jp);
                }
                if (r == null && Play.configuration != null) {
                    r = Play.configuration.getProperty(jp);
                }
                if (r == null) {
                    r = System.getProperty(jp);
                    if (r == null) {
                        r = System.getenv(jp);
                    }
                }
                if (r == null && def != null) {
                    r = def;
                }
                if (r == null) {
                    continue;
                }
            }
            matcher.appendReplacement(sb, r);
        }
        if (!matched) {
            return value;
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    public static Properties read(VirtualFile conf) {
        return read(conf, new HashSet<VirtualFile>());
    }

    public static Properties read(InputStream is) {
        return read(is, new HashSet<VirtualFile>());
    }

    public static Properties read(VirtualFile conf, Set<VirtualFile> confs) {
        if (confs != null && confs.contains(conf)) {
            throw new RuntimeException("Detected recursive @include usage. Have seen the file [" + conf.getRealFile().getAbsolutePath() + "] before");
        }
        return read(conf.inputstream(), confs);
    }

    public static Properties read(InputStream is, Set<VirtualFile> confs) {
        Properties propsFromFile = IO.readUtf8Properties(is);
        Properties newConfiguration = new OrderSafeProperties();
        for (String key : list(propsFromFile.keySet())) {
            Matcher matcher = PROPS_PATTERN.matcher(key);
            if (!matcher.matches()) {
                newConfiguration.put(key, propsFromFile.get(key).toString().trim());
            }
        }
        for (String key : list(propsFromFile.keySet())) {
            Matcher matcher = PROPS_PATTERN.matcher(key);
            if (matcher.matches()) {
                String instance = matcher.group(1);
                if (instance.equals(Play.id)) {
                    newConfiguration.put(matcher.group(2), propsFromFile.get(key).toString().trim());
                }
            }
        }
        propsFromFile = newConfiguration;
        // Resolve ${..}
        for (String key : propsFromFile.stringPropertyNames()) {
            propsFromFile.put(key, resolvePlaceholder(propsFromFile.get(key).toString(), propsFromFile));
        }
        // Include
        Map<Object, Object> toInclude = new HashMap<Object, Object>(16);
        for (String key : list(propsFromFile.keySet())) {
            if (key.startsWith("@include.")) {
                try {
                    toInclude.putAll(read(Play.getVirtualFile(propsFromFile.get(key).toString()), confs));
                } catch (Exception ex) {
                    Logger.warn("Missing include: %s", key);
                }
            }
        }
        propsFromFile.putAll(toInclude);

        return propsFromFile;
    }

    @SuppressWarnings("unchecked")
    private static Collection<String> list(Set set) {
        return (Collection) set;
    }
}
