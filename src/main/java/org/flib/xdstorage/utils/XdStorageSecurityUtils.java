package org.flib.xdstorage.utils;

import org.flib.xdstorage.exceptions.XdStorageRuntimeException;
import java.util.regex.Pattern;

/**
 * Утилиты обеспечения безопасности и защиты от инъекций СУБД xdstorage.
 */
public class XdStorageSecurityUtils {

    // Разрешаем только латиницу, цифры, подчеркивания и точки (для схем)
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[a-zA-Z0-9_\\.]+$");

    /**
     * Валидирует и экранирует SQL-идентификатор (имя таблицы, колонки или индекса).
     * Выбрасывает XdStorageRuntimeException в случае обнаружения подозрительных символов.
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
}
