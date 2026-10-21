package io.github.xdstorage.temp;

import io.github.xdstorage.code.XdStorageObservableWrapperClassCodeGenerator;
import io.github.xdstorage.utils.XdStorageClassInfo;
import io.github.xdstorage.utils.XdStorageObjectField;
import io.github.xdstorage.utils.XdStorageObjectUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SpecificTest {

    public static void main(final String[] args) {
        final Entity entity = new Entity();
        entity.setName("Name");

        final EntityWrapper wrapper = new EntityWrapper(entity);

        entity.setName("NewName");

        System.out.println("############################");
        final XdStorageClassInfo clWrapperInfo = XdStorageObjectUtils.getClassInfo(wrapper.getClass());
        for (final XdStorageObjectField field : clWrapperInfo.getFields().values()) {
            System.out.println(field.getName() + ": " + field.get(wrapper));
        }

        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(entity.getClass());
        for (final XdStorageObjectField field : clInfo.getFields().values()) {
            System.out.println("############################");
            System.out.println("wrapper " + field.getName() + ": " + field.get(wrapper));
            System.out.println("object " + field.getName() + ": " + field.get(entity));
            System.out.println("### " + field.getName() + " is set for wrapper");
            field.set(wrapper, field.getName());
            System.out.println("wrapper " + field.getName() + ": " + field.get(wrapper));
            System.out.println("object " + field.getName() + ": " + field.get(entity));
        }

        Map<String, String> code = new HashMap<>();
        new XdStorageObservableWrapperClassCodeGenerator().generate("test.pack", Entity.class, code);
        code.get("");

        Set<String> strings = new HashSet<>();
        for (int i = 0; i < 10; ++i) {
            strings.add("string" + i);
        }

    }
}

