package play.classloading;

import play.Play;
import play.mvc.After;
import play.mvc.Before;
import play.mvc.Finally;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * .
 * <p/>
 *
 * @author <a href="mailto:oxsean@gmail.com">sean yang</a>
 * @version V1.0, 14-5-23
 */
public class ClassCache {
    private static Map<String, Method> METHODS = new HashMap<String, Method>();
    private static Map<String, Field> FIELDS = new HashMap<String, Field>();
    private static Map<String, Method> ACTION_METHODS = new HashMap<String, Method>();

    public static Method getMethod(Object obj, String name, Class... argTypes) {
        StringBuilder sb = new StringBuilder(obj.getClass().getName());
        sb.append(".").append(name);
        if (argTypes != null) {
            for (Class clazz : argTypes) {
                sb.append(",").append(clazz.getName());
            }
        }
        String key = sb.toString();
        Method method = METHODS.get(key);
        if (!METHODS.containsKey(key)) {
            try {
                method = obj.getClass().getMethod(name, argTypes);
            } catch (NoSuchMethodException ignored) {
            }
            METHODS.put(key, method);
        }
        return method;
    }

    public static Field getField(Object obj, String name) {
        String key = obj.getClass().getName() + "." + name;
        Field field = FIELDS.get(key);
        if (!FIELDS.containsKey(key)) {
            try {
                field = obj.getClass().getField(name);
                field.setAccessible(true);
            } catch (NoSuchFieldException ignored) {
            }
            FIELDS.put(key, field);
        }
        return field;
    }

    public static Method findActionMethod(String name, Class clazz) {
        String key = clazz.getName() + "." + name;
        Method method = ACTION_METHODS.get(key);
        if (!ACTION_METHODS.containsKey(key)) {
            while (clazz != null) {
                for (Method m : clazz.getDeclaredMethods()) {
                    if (m.getName().equalsIgnoreCase(name) && Modifier.isPublic(m.getModifiers())) {
                        if (!m.isAnnotationPresent(Before.class) && !m.isAnnotationPresent(After.class) && !m.isAnnotationPresent(Finally.class)) {
                            ACTION_METHODS.put(key, m);
                            return m;
                        }
                    }
                }
                clazz = clazz.getSuperclass();
            }
            ACTION_METHODS.put(key, null);
        }
        return method;
    }

    public static synchronized void clean(Class clazz) {
        String name = clazz.getName();
        Iterator<Map.Entry<String, Method>> mit = METHODS.entrySet().iterator();
        while (mit.hasNext()) {
            Map.Entry<String, Method> entry = mit.next();
            if (entry.getKey().startsWith(name)) {
                mit.remove();
            }
        }
        Iterator<Map.Entry<String, Field>> fit = FIELDS.entrySet().iterator();
        while (fit.hasNext()) {
            Map.Entry<String, Field> entry = fit.next();
            if (entry.getKey().startsWith(name)) {
                fit.remove();
            }
        }
        Iterator<Map.Entry<String, Method>> amit = ACTION_METHODS.entrySet().iterator();
        while (amit.hasNext()) {
            Map.Entry<String, Method> entry = amit.next();
            if (entry.getKey().startsWith(name)) {
                amit.remove();
            }
        }
    }

    public static synchronized void cleanAll() {
        METHODS.clear();
        FIELDS.clear();
        ACTION_METHODS.clear();
    }
}
