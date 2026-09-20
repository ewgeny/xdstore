package org.flib.xdstorage.sqlstorage.search.query;

public class XdStorageSqlValueFormatter {

    public static String formatStringValue(final String value) {
        final StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); ++i) {
            final char ch = value.charAt(i);
            switch (ch) {
                case '\'':
                    builder.append("\\'");
                    break;
                default:
                    builder.append(ch);
            }
        }
        return builder.toString();
    }
}
