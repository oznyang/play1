package com.wisorg.maven.play;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class ConfigurationParser {
    public static final String NAME = "application.conf";
    private String playId;
    private File applicationDirectory;
    private File playDirectory;

    public ConfigurationParser(String playId, File applicationDirectory, File playDirectory) {
        this.playId = playId;
        this.applicationDirectory = applicationDirectory;
        this.playDirectory = playDirectory;
    }

    public Properties parse() throws IOException {
        Set<File> confs = new HashSet<File>(1);
        return readOneConfigurationFile(NAME, confs);
    }

    private Properties readOneConfigurationFile(String fileName, Set<File> confs) throws IOException {
        File confDir = new File(applicationDirectory, "conf");
        File configurationFile = new File(confDir, fileName);
        if (!configurationFile.exists()) {
            throw new IOException("Configuration reader - \"" + configurationFile.getName() + "\" file does not exist.");
        }
        if (!configurationFile.isFile()) {
            throw new IOException("Configuration reader - \"" + configurationFile.getName() + "\" is not a file.");
        }
        if (confs.contains(configurationFile)) {
            throw new IOException("Configuration reader - detected recursive @include usage. Have seen the \"" + configurationFile.getName() + "\" file before.");
        }

        Properties propsFromFile = new OrderSafeProperties();
        FileInputStream fis = new FileInputStream(configurationFile);
        try {
            propsFromFile.load(fis);
        } finally {
            fis.close();
        }
        confs.add(configurationFile);

        // OK, check for instance specifics configuration
        Properties newConfiguration = new OrderSafeProperties();
        Pattern pattern = Pattern.compile("^%([a-zA-Z0-9_\\-]+)\\.(.*)$");
        for (Object key : propsFromFile.keySet()) {
            Matcher matcher = pattern.matcher(key + "");
            if (!matcher.matches()) {
                newConfiguration.put(key, propsFromFile.get(key).toString().trim());
            }
        }
        for (Object key : propsFromFile.keySet()) {
            Matcher matcher = pattern.matcher(key + "");
            if (matcher.matches()) {
                String instance = matcher.group(1);
                if (instance.equals(playId)) {
                    newConfiguration.put(matcher.group(2), propsFromFile.get(key).toString().trim());
                }
            }
        }
        propsFromFile = newConfiguration;
        // Resolve ${..}
        pattern = Pattern.compile("\\$\\{([^}]+)}");
        for (Object key : propsFromFile.keySet()) {
            String value = propsFromFile.getProperty(key.toString());
            Matcher matcher = pattern.matcher(value);
            StringBuffer newValue = new StringBuffer(100);
            while (matcher.find()) {
                String jp = matcher.group(1);
                String r;
                if (jp.equals("play.id")) {
                    r = playId != null ? playId : "";
                } else if (jp.equals("application.path")) {
                    r = applicationDirectory.getAbsolutePath();
                } else if (jp.equals("play.path")) {
                    r = playDirectory.getAbsolutePath();
                } else {
                    r = System.getProperty(jp);
                }
                if (r == null) {
                    continue;
                }
                matcher.appendReplacement(newValue, r.replaceAll("\\\\", "\\\\\\\\"));
            }
            matcher.appendTail(newValue);
            propsFromFile.setProperty(key.toString(), newValue.toString());
        }
        // Include
        Map<Object, Object> toInclude = new HashMap<Object, Object>(16);
        for (Object key : propsFromFile.keySet()) {
            if (key.toString().startsWith("@include.")) {
                String filenameToInclude = propsFromFile.getProperty(key.toString());
                toInclude.putAll(readOneConfigurationFile(filenameToInclude, confs));
            }
        }
        propsFromFile.putAll(toInclude);

        return propsFromFile;
    }
}
