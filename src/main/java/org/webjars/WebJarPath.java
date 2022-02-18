package org.webjars;

public class WebJarPath {

    private final String prefix;

    private final boolean comma;

    public WebJarPath(String prefix, boolean comma) {
        this.prefix = prefix;
        this.comma = comma;
    }

    public String getPrefix() {
        return prefix;
    }

    public boolean isComma() {
        return comma;
    }
}
