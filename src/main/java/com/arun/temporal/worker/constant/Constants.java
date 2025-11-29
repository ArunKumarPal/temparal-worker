package com.arun.temporal.worker.constant;

import java.util.Collection;
import java.util.List;

public interface Constants {
    Collection<String> MANDATORY_INPUT_HEADERS = List.of("input1", "input2", "input3");
    String DOUBLE_QUOTES = "\"";
    String EMPTY_STRING = "";
    String PIPE_SEPARATOR = "|";
    String TAB_SEPARATOR = "\t";
    String NAME_ID_SEPARATOR = "::";
    String BULK_API = "bulk-api";
    String BULK_RESULT = "bulk-result";
    String REPORT_HEADER = "with_report";
    int MAX_ACTIVITY_RUN_TIME_OUT = 40;

    static String getOutputCsvHeaders(String separator) {
        return String.join(separator,
                "name",
                "email",
                "address",
                "input1",
                "input2",
                "input3",
                "error"
        );
    }

    String REPORT_NAME = "report.txt";
}
