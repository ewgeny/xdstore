package org.flib.xdstorage.serialization.yaml;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Юнит-тест: Проверка конечного автомата YAML-эмиттера")
public class XdStorageYamlEmitterTest {

    @Test
    @DisplayName("Проверка геометрии отступов: тест каскадной вложенности и одинарных кавычек")
    public void testEmitter_CascadeBlocks_ShouldGenerateCorrectTabulations() throws Exception {
        StringWriter stringWriter = new StringWriter();
        XdStorageYamlEmitter emitter = new XdStorageYamlEmitter(stringWriter);

        emitter.openBlock("objects");
        emitter.openBlock("- object");

        emitter.writeKey("class");
        emitter.writeScalar("'org.flib.xdstorage.entities.XdGalaxy'");

        emitter.writeKey("id");
        emitter.writeScalar("'galaxy-001'");

        emitter.openBlock("hole");
        emitter.openBlock("object");
        emitter.writeKey("class");
        emitter.writeScalar("'org.flib.xdstorage.entities.XdBlackHole'");
        emitter.closeBlock();
        emitter.closeBlock();

        emitter.closeBlock();
        emitter.closeBlock();

        // ПРАВКА АССЕРТА: Ожидаем идеальный стандартный YAML на табуляциях с одинарными кавычками
        String expectedYaml =
                "objects:\r\n" +
                        "\t- object:\r\n" +
                        "\t\tclass: 'org.flib.xdstorage.entities.XdGalaxy'\r\n" +
                        "\t\tid: 'galaxy-001'\r\n" +
                        "\t\thole:\r\n" +
                        "\t\t\tobject:\r\n" +
                        "\t\t\t\tclass: 'org.flib.xdstorage.entities.XdBlackHole'\r\n";

        assertEquals(expectedYaml, stringWriter.toString(), "🚨 Конечный автомат нарушил контракт одинарных кавычек!");
    }

    @Test
    @DisplayName("Fail-Safe защита: вызовы closeBlock не должны уводить автомат в отрицательные отступы")
    public void testEmitter_NegativeLevelGuard_ShouldStayAtZero() throws Exception {
        StringWriter stringWriter = new StringWriter();
        XdStorageYamlEmitter emitter = new XdStorageYamlEmitter(stringWriter);

        emitter.closeBlock();
        emitter.closeBlock();

        emitter.writeKey("rootKey");
        emitter.writeScalar("100");

        assertEquals("rootKey: 100\r\n", stringWriter.toString());
    }
}
