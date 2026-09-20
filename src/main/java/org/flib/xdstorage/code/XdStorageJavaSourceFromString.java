package org.flib.xdstorage.code;

import javax.tools.SimpleJavaFileObject;
import java.net.URI;

class XdStorageJavaSourceFromString extends SimpleJavaFileObject {

    final String name;

    final String code;

    XdStorageJavaSourceFromString(String name, String code) {
        super(URI.create("string:///" + name.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
        this.name = name;
        this.code = code;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
        return code;
    }
}
