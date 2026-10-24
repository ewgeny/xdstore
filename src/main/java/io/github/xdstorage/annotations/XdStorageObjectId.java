package io.github.xdstorage.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the entity's primary-key field — every stored class MUST carry exactly one.
 * The field needs a getter and a setter, and its type must be constructible from a
 * {@link String} (a constructor taking one {@code String} argument) unless it is a
 * simple type ({@code String}, {@code Long}/{@code long}, {@code Integer}/{@code int},
 * {@code UUID}).
 *
 * <p>Who assigns the id is chosen on the same field via
 * {@link XdStorageObjectFieldProperties}: with the default
 * {@code DATABASE_GENERATOR} a {@code null} id is generated inside the
 * {@code save} call and written back onto the same instance.
 */
@Target(value = ElementType.FIELD)
@Retention(value = RetentionPolicy.RUNTIME)
public @interface XdStorageObjectId {

}
