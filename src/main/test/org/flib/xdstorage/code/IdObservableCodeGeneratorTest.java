package org.flib.xdstorage.code;

import org.flib.xdstorage.entities.XdSatellite;
import org.flib.xdstorage.entities.XdStarSystem;

import java.util.HashMap;
import java.util.Map;

public class IdObservableCodeGeneratorTest {

    public static void main(String []args) {
        XdStorageObservableWrapperClassCodeGenerator generator = new XdStorageObservableWrapperClassCodeGenerator();

        Map<String, String> codemap = new HashMap<>();
        generator.generate("org.flib.xdstorage.code", XdSatellite.class, codemap);

        codemap.values().stream().forEach(code -> {
            System.out.println(code);
        });
    }
}