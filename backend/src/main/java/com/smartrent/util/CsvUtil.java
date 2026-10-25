package com.smartrent.util;

import java.util.List;
import java.util.stream.Collectors;

/** Minimal RFC 4180 CSV writer helpers, with protection against spreadsheet formula injection. */
public final class CsvUtil {

    private CsvUtil() {
    }

    public static String escape(String value) {
        if (value == null) {
            return "";
        }
        String v = value;
        if (!v.isEmpty() && "=+@\t\r".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;   // neutralize formulas such as =HYPERLINK(...) when opened in Excel
        }
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    public static String line(List<String> cells) {
        return cells.stream().map(CsvUtil::escape).collect(Collectors.joining(","));
    }
}
