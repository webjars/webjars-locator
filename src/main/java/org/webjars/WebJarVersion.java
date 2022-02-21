package org.webjars;

public class WebJarVersion {

    private final String webJarId;

    private final String webJarVersion;

    public WebJarVersion(String webJarId, String webJarVersion) {
        this.webJarId = webJarId;
        this.webJarVersion = webJarVersion;
    }

    public String getWebJarId() {
        return webJarId;
    }

    public String getWebJarVersion() {
        return webJarVersion;
    }

}
