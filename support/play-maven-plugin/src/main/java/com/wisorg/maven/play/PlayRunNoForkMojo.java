package com.wisorg.maven.play;

import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * Run play server in current jvm instance.
 * <p/>
 *
 * @author <a href="mailto:oznyang@163.com">oznyang</a>
 * @version V1.0, 14-5-6
 */
@Mojo(name = "run-no-fork", requiresDependencyResolution = ResolutionScope.TEST)
public class PlayRunNoForkMojo extends PlayRunMojo {
    @Override
    public boolean isRunFork() {
        return false;
    }
}
