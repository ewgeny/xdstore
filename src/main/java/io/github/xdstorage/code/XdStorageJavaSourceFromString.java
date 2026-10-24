package io.github.xdstorage.code;

import javax.tools.SimpleJavaFileObject;
import java.net.URI;

/**
 * In-memory compilation unit: feeds one generated wrapper source string to the system
 * Java compiler as a {@code SOURCE}-kind file object. Engine-internal — not part of
 * the public API.
 */
class XdStorageJavaSourceFromString extends SimpleJavaFileObject {

    /** Fully-qualified class name this compilation unit provides. */
    final String name;

    /** The complete Java source of the class. */
    final String code;

    /**
     * Builds the compilation unit for one generated class.
     *
     * @param name fully-qualified class name of the generated source
     * @param code the complete Java source of the class
     */
    XdStorageJavaSourceFromString(String name, String code) {
        super(URI.create("string:///" + name.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
        this.name = name;
        this.code = code;
    }

    /**
     * Returns the class name of this compilation unit.
     *
     * @return the fully-qualified class name given at construction
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * Returns the generated source text.
     *
     * @param ignoreEncodingErrors ignored — the content is already in memory
     * @return the complete generated source
     */
    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
        return code;
    }
}
