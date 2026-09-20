package org.flib.xdstorage.code;

import org.flib.xdstorage.entities.XdStarSystem;

import java.util.HashMap;
import java.util.Map;

public class SimpleCodeGeneratorTest {

    public static void main(String []args) {
        XdStorageSimpleWrapperClassCodeGenerator generator = new XdStorageSimpleWrapperClassCodeGenerator();

        Map<String, String> codemap = new HashMap<>();
        generator.generate("org.flib.xdstorage.code", XdStarSystem.class, codemap);

        codemap.values().stream().forEach(code -> {
            System.out.println(code);
        });
    }
}
