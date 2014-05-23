package play.vfs;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channel;
import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
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
        String relPath;
        for (int i = 1, len = Play.roots.size(); i < len; i++) {
            VirtualFile vf = Play.roots.get(i);
            relPath = getRelPath(vf.getRealFile(), path);
            if (relPath != null) {
                return "{module:" + vf.getName() + "}" + relPath;
            }
        }
        relPath = getRelPath(Play.applicationPath, path);
        if (relPath != null) {
            return relPath;
        }
        relPath = getRelPath(Play.frameworkPath, path);
        if (relPath != null) {
            return "{play}" + relPath;
        }
        return "{?}" + path;
    }

    private String getRelPath(File file, String subFile) {
        String path = file.getAbsolutePath() + File.separator;
        int pos = subFile.indexOf(path);
        if (pos > -1) {
            return subFile.substring(path.length() - 1);
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
        for (VirtualFile file : roots) {
            VirtualFile child = file.child(path);
            if (child.exists()) {
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
                    for(Entry<String, VirtualFile> entry : Play.modules.entrySet()) {
                        if(entry.getKey().equals(module))
                            return entry.getValue().child(path);
                    }
                }
            }
        }

        return null;
    }
}