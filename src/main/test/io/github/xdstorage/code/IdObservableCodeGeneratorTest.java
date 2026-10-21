package io.github.xdstorage.code;

import io.github.xdstorage.entities.XdSatellite;
import io.github.xdstorage.entities.XdStarSystem;

import java.util.HashMap;
import java.util.Map;

public class IdObservableCodeGeneratorTest {

    public static void main(String []args) {
        XdStorageObservableWrapperClassCodeGenerator generator = new XdStorageObservableWrapperClassCodeGenerator();

        Map<String, String> codemap = new HashMap<>();
        generator.generate("io.github.xdstorage.code", XdSatellite.class, codemap);

        codemap.values().stream().forEach(code -> {
            System.out.println(code);
        });
    }
}