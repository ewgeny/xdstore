package io.github.xdstorage.annotations;

import io.github.xdstorage.idgeneration.IXdStorageIdGenerator;
import io.github.xdstorage.idgeneration.XdStorageDummyIdGenerator;
import io.github.xdstorage.idgeneration.XdStorageIdGeneratorType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(value = ElementType.FIELD)
@Retention(value = RetentionPolicy.RUNTIME)
public @interface XdStorageObjectFieldProperties {

    long length() default 255;

    XdStorageIdGeneratorType idGeneratorType() default XdStorageIdGeneratorType.DATABASE_GENERATOR;

    Class<? extends IXdStorageIdGenerator> idGeneratorClass() default XdStorageDummyIdGenerator.class;

}
