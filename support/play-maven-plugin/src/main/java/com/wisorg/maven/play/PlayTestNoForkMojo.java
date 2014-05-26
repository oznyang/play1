package com.wisorg.maven.play;

import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * Run play server test mode in alone jvm instance.
 * <p/>
 *
 * @author <a href="mailto:oznyang@163.com">oznyang</a>
 * @version V1.0, 14-5-6
 */
@Mojo(name = "test-no-fork", requiresDependencyResolution = ResolutionScope.TEST)
public class PlayTestNoForkMojo extends PlayRunMojo {
    @Override
    public boolean isPlayTest() {
        return true;
    }

    @Override
    public boolean isRunFork() {
        return false;
    }
}
