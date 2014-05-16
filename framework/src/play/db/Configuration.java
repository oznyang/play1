package play.db;

import java.util.*;

import play.*;
import jregex.Matcher;
import jregex.Pattern;
import play.db.jpa.JPA;

public class Configuration {

     public static Properties convertToMultiDB(Properties p) {
        final Pattern OLD_DB_CONFIG_PATTERN = new jregex.Pattern("^db\\.([^\\.]*)$");
        final Pattern OLD_JPA_CONFIG_PATTERN = new jregex.Pattern("^jpa\\.([^\\.]*)$");
        final Pattern OLD_HIBERNATE_CONFIG_PATTERN = new jregex.Pattern("^hibernate\\.([a-zA-Z.-]*)$");
        
        Properties newProperties = convertPattern(p, OLD_DB_CONFIG_PATTERN, "db.default");
        newProperties = convertPattern(newProperties, OLD_JPA_CONFIG_PATTERN, "jpa.default");  
        newProperties = convertPattern(newProperties, OLD_HIBERNATE_CONFIG_PATTERN, "default.hibernate"); 
        
       return newProperties;
    }
     
     public static Properties convertPattern(Properties p, Pattern pattern, String newFormat) {
         Set<String> propertiesNames = p.stringPropertyNames();
         for (String property : propertiesNames) {
             Matcher m = pattern.matcher(property);
             if (m.matches()) {
                 p.put(newFormat + "." + m.group(1), p.get(property));
             }
             if ("db".equals(property)) {
                 p.put(newFormat, p.get(property));
             }
         }
                  
        return p;
     }

    public static List<String> getDbNames(Properties p) {
        TreeSet<String> dbNames = new TreeSet<String>();
        final Pattern DB_CONFIG_PATTERN = new jregex.Pattern("^db\\.([^\\.]*)\\.([^\\.]*)$");
        for (String property : p.stringPropertyNames()) {
            Matcher m = DB_CONFIG_PATTERN.matcher(property);
            if (m.matches()) {
                dbNames.add(m.group(1));
            }
            // Special case db=...
            if ("db".equals(property)) {
                dbNames.add(JPA.DEFAULT);
            }
        }
        return new ArrayList<String>(dbNames);
    }


    public static Map<String, String> getProperties(String name) {
        Map<String, String> properties = new HashMap<String, String>();
        Properties props = Play.configuration;
        for (String key : props.stringPropertyNames()) {
            if (key.startsWith("db." + name) || key.startsWith(name + ".hibernate") ) {
                properties.put(key, props.getProperty(key));
            }
        } 
        return properties;
    }

    public static Map<String, String> addHibernateProperties(Map<String, String> props, String dbname) {
        Map<String, String> properties = new HashMap<String, String>();
        properties.putAll(props);
        for (String key : props.keySet()) {
            if (key.startsWith(dbname + ".hibernate")) {
                String newKey = key.substring(dbname.length() + 1);
                properties.put(newKey, props.get(key));
            }
        } 
        return properties;
    }

}
