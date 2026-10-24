package io.github.xdstorage.utils;

import io.github.xdstorage.exceptions.XdStorageRuntimeException;
import java.util.regex.Pattern;

/**
 * Security utilities of the xdstorage engine: validation and escaping guards against injection
 * through user-controlled identifiers. Stateless and thread-safe (the whitelist is a compiled
 * constant {@link Pattern}). Engine-internal — not part of the public API.
 */
public class XdStorageSecurityUtils {

    // Разрешаем только латиницу, цифры, подчеркивания и точки (для схем)
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[a-zA-Z0-9_\\.]+$");

    /**
     * Валидирует и экранирует SQL-идентификатор (имя таблицы, колонки или индекса).
     * Выбрасывает XdStorageRuntimeException в случае обнаружения подозрительных символов.
     *
     * @param identifier the raw identifier to validate ({@code null} passes through unchanged)
     * @return the identifier safely double-quoted for PostgreSQL
     * @throws XdStorageRuntimeException when the identifier contains characters outside the
     *         {@code [a-zA-Z0-9_.]} whitelist (injection attempt)
     */
    public static String sanitizeIdentifier(final String identifier) {
        if (identifier == null) {
            return null;
        }

        // 1. Проверяем по белому списку регулярного выражения
        if (!IDENTIFIER_PATTERN.matcher(identifier).matches()) {
            throw new XdStorageRuntimeException(
                    "Критическая ошибка безопасности: обнаружен недопустимый SQL-идентификатор '" + identifier + "'"
            );
        }

        // 2. Возвращаем безопасно экранированный в двойные кавычки токен для PostgreSQL
        return "\"" + identifier + "\"";
    }

    /** Intentionally public to mirror the implicit constructor's access level. */
    public XdStorageSecurityUtils() {
    }
}
