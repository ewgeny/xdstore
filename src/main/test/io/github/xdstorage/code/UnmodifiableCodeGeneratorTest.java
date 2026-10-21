package io.github.xdstorage.code;

import io.github.xdstorage.entities.XdPlanet;

import java.util.HashMap;
import java.util.Map;

public class UnmodifiableCodeGeneratorTest {

    public static void main(String []args) {
        XdStorageUnmodifiableWrapperClassCodeGenerator generator = new XdStorageUnmodifiableWrapperClassCodeGenerator();

        Map<String, String> codemap = new HashMap<>();
        generator.generate("io.github.xdstorage.code", XdPlanet.class, codemap);

        codemap.values().stream().forEach(code -> {
            System.out.println(code);
        });
    }
}
