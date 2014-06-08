package play.vfs;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channel;
import java.security.AccessControlException;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import play.Play;
import play.exceptions.UnexpectedException;
import play.libs.IO;

/**
 * The VFS used by Play!
 */
public class VirtualFile {

    File realFile;

    VirtualFile(File file) {
        this.realFile = file;
    }

    public String getName() {
        return realFile.getName();
    }

    public boolean isDirectory() {
        return realFile.isDirectory();
    }

    public String relativePath() {
        String path = realFile.getAbsolutePath();
        String relPath = RELATIVE_CACHE.get(path);
        if (relPath != null) {
            return relPath;
        }
        for (int i = 1, len = Play.roots.size(); i < len; i++) {
            VirtualFile vf = Play.roots.get(i);
            relPath = getRelPath(vf.getRealFile(), path);
            if (relPath != null) {
                relPath = "{module:" + vf.getName() + "}" + relPath;
                break;
            }
        }
        if (relPath == null && !Play.frameworkPath.equals(Play.applicationPath)) {
            String tmpPath = getRelPath(Play.frameworkPath, path);
            if (tmpPath != null) {
                relPath = "{play}" + tmpPath;
            }
        }
        if (relPath == null) {
            relPath = getRelPath(Play.applicationPath, path);
        }
        if (relPath == null) {
            relPath = "{?}" + path;
        }
        RELATIVE_CACHE.put(path, relPath);
        return relPath;
    }

    private String getRelPath(File file, String subFile) {
        String path = file.getAbsolutePath() + File.separator;
        int pos = subFile.indexOf(path);
        if (pos > -1) {
            return StringUtils.replace(subFile.substring(path.length() - 1), "\\", "/");
        }
        return null;
    }

    public List<VirtualFile> list() {
        List<VirtualFile> res = new ArrayList<VirtualFile>();
        if (exists()) {
            for (File aChildren : realFile.listFiles()) {
                res.add(new VirtualFile(aChildren));
            }
        }
        return res;
    }

    public boolean exists() {
        try {
            return realFile != null && realFile.exists();
        } catch (AccessControlException e) {
            return false;
        }
    }

    public InputStream inputstream() {
        try {
            return new FileInputStream(realFile);
        } catch (Exception e) {
            throw new UnexpectedException(e);
        }
    }

    public OutputStream outputstream() {
        try {
            return new FileOutputStream(realFile);
        } catch (Exception e) {
            throw new UnexpectedException(e);
        }
    }

    public Long lastModified() {
        if (realFile != null) {
            return realFile.lastModified();
        }
        return 0L;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof VirtualFile) {
            VirtualFile vf = (VirtualFile) other;
            if (realFile != null && vf.realFile != null) {
                return realFile.equals(vf.realFile);
            }
        }
        return super.equals(other);
    }

    @Override
    public int hashCode() {
        if (realFile != null) {
            return realFile.hashCode();
        }
        return super.hashCode();
    }

    public long length() {
        return realFile.length();
    }

    public VirtualFile child(String name) {
        return new VirtualFile(new File(realFile, name));
    }

    public Channel channel() {
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(realFile);
            return fis.getChannel();
        } catch (FileNotFoundException e) {
            return null;
        } finally {
            IOUtils.closeQuietly(fis);
        }
    }

    public static VirtualFile open(String file) {
        return open(new File(file));
    }

    public static VirtualFile open(File file) {
        return new VirtualFile(file);
    }

    public String contentAsString() {
        try {
            return IO.readContentAsString(inputstream());
        } catch (Exception e) {
            throw new UnexpectedException(e);
        }
    }

    public File getRealFile() {
        return realFile;
    }

    public String getAbsolutePath(){
        return realFile.getAbsolutePath();
    }

    public void write(CharSequence string) {
        try {
            IO.writeContent(string, outputstream());
        } catch (Exception e) {
            throw new UnexpectedException(e);
        }
    }

    public byte[] content() {
        byte[] buffer = new byte[(int) length()];
        InputStream is=null;
        try {
            is = inputstream();
            is.read(buffer);
            is.close();
            return buffer;
        } catch (Exception e) {
            throw new UnexpectedException(e);
        } finally {
            IOUtils.closeQuietly(is);
        }
    }

    @Override
    public String toString() {
        return getName();
    }

    public static VirtualFile search(Collection<VirtualFile> roots, String path) {
        String absFile = ABSOLUTE_CACHE.get(path);
        if (absFile != null) {
            return VirtualFile.open(absFile);
        }
        for (VirtualFile file : roots) {
            VirtualFile child = file.child(path);
            if (child.exists()) {
                ABSOLUTE_CACHE.put(path, child.getAbsolutePath());
                return child;
            }
        }
        return null;
    }

    public static VirtualFile fromRelativePath(String relativePath) {
        Pattern pattern = Pattern.compile("^(\\{(.+?)\\})?(.*)$");
        Matcher matcher = pattern.matcher(relativePath);

        if(matcher.matches()) {
            String path = matcher.group(3);
            String module = matcher.group(2);
            if(module == null || module.equals("?") || module.equals("")) {
                return new VirtualFile(Play.applicationPath).child(path);
            } else {
                if(module.equals("play")) {
                    return new VirtualFile(Play.frameworkPath).child(path);
                }
                if(module.startsWith("module:")){
                    module = module.substring("module:".length());
                    VirtualFile vf = Play.modules.get(module);
                    if (vf != null) {
                        return vf.child(path);
                    }
                }
            }
        }

        return null;
    }

    private static final Map<String, String> RELATIVE_CACHE = new ConcurrentHashMap<String, String>();
    private static final Map<String, String> ABSOLUTE_CACHE = new ConcurrentHashMap<String, String>();

    private static String put(Map<String, String> map, String key, String value) {
        map.put(key, value);
        return value;
    }

    public static void cleanCache(){
        RELATIVE_CACHE.clear();
        ABSOLUTE_CACHE.clear();
    }
}