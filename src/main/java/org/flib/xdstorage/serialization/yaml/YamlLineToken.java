package org.flib.xdstorage.serialization.yaml;

public class YamlLineToken {
    public final int level;        // Глубина вложенности (количество '\t')
    public final String key;       // Ключ поля (например, "class", "name", "id")
    public final String value;     // Сырое строковое значение (null, если это имя блока)
    public final boolean isListItem; // Флаг, начинается ли строка с "- "

    public YamlLineToken(int level, String key, String value, boolean isListItem) {
        this.level = level;
        this.key = key;
        this.value = value;
        this.isListItem = isListItem;
    }
}
