package org.jenkinsci.confluence.plugins;

/**
 * @author Kohsuke Kawaguchi
 */
public class WikiWriter {
    /**
     * Building string into this.
     */
    public final StringBuilder buf = new StringBuilder();

    WikiWriter append(Object o) {
        buf.append(o);
        return this;
    }

    WikiWriter append(Object... args) {
        for (Object o : args)
            buf.append(o);
        return this;
    }

    WikiWriter href(String text, String url) {
        return append('[',text,'|',url,']');
    }

    WikiWriter h4(String title) {
        return append("h4. ",title,"\n");
    }

    WikiWriter image(String href) {
        return append("!",href,"!");
    }

    WikiWriter print(String fmt, Object... args) {
        buf.append(String.format(fmt,args));
        return this;
    }

    public String toString() {
        return buf.toString();
    }

    public int length() {
        return buf.length();
    }
}
