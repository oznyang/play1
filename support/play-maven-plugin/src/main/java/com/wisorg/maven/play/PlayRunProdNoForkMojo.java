package com.wisorg.maven.play;

import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * Run play server prod mode in current jvm instance.
 * <p/>
 *
 * @author <a href="mailto:oznyang@163.com">oznyang</a>
 * @version V1.0, 14-5-6
 */
@Mojo(name = "run-prod-no-fork", requiresDependencyResolution = ResolutionScope.TEST)
public class PlayRunProdNoForkMojo extends PlayRunMojo {
    @Override
    public String getAppMode() {
        return "prod";
    }

    @Override
    public boolean isRunFork() {
        return false;
    }
}
