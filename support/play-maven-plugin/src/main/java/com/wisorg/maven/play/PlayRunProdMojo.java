package com.wisorg.maven.play;

import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * Run play server prod mode in alone jvm instance.
 * <p/>
 *
 * @author <a href="mailto:oznyang@163.com">oznyang</a>
 * @version V1.0, 14-5-6
 */
@Mojo(name = "run-prod", requiresDependencyResolution = ResolutionScope.TEST)
public class PlayRunProdMojo extends PlayRunMojo {
    @Override
    public String getAppMode() {
        return "prod";
    }
}
