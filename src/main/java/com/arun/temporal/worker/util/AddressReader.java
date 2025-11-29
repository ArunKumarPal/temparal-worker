package com.arun.temporal.worker.util;

import com.arun.temporal.worker.model.*;
import com.arun.temporal.worker.exception.BulkProcessorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.arun.temporal.worker.constant.Constants.*;

public class AddressReader {

    private AddressReader() {
    }

    private static final Logger logger = LoggerFactory.getLogger(AddressReader.class);

    public static void validateHeaders(String[] headers) {
        if (!new HashSet<>(Arrays.asList(headers)).containsAll(MANDATORY_INPUT_HEADERS)) {
            logger.info("Mandatory Headers are missing headers: {}", Arrays.asList(headers));
            throw new BulkProcessorException("The file is not UTF-8 encoded");
        }
    }

    public static InputRequest convertToRequest(String[] header, String line, String delimiter, AtomicInteger totalRecordCount) {
        totalRecordCount.incrementAndGet();
        String[] values;
        if (hasBalancedQuotes(line)) {
            values = line.split(delimiter.concat("(?=(?:(?:[^\"]*\"){2})*[^\"]*$)"), -1);
            if (values.length != header.length) {
                values = line.split(delimiter, -1);
            }
        } else {
            values = line.split(delimiter, -1);
        }
        Map<String, String> fieldMap = new HashMap<>();
        for (int i = 0; i < header.length && i < values.length; i++) {
            fieldMap.put(header[i], cleanValue(values[i]));
        }
        return new InputRequest(fieldMap.get("input1"),fieldMap.get("input2"),fieldMap.get("input2"));
    }

    private static String cleanValue(String value) {
        if (value == null || value.isEmpty()) return "";
        value = value.replace("\"", " ").trim();
        if (value.startsWith("'")) value = value.substring(1);
        if (value.endsWith("'")) value = value.substring(0, value.length() - 1);
        return value.trim();
    }

    private static boolean hasBalancedQuotes(String line) {
        long count = line.chars().filter(ch -> ch == '"').count();
        return count % 2 == 0;
    }

    public static String getRowValueForOutputCsv(ResponseWithInputRequest response, String delimiter, ReportData reportData) {
        StringBuilder csvString = new StringBuilder();
        csvString
                .append(Optional.ofNullable(response).map(Response::getName).orElse(EMPTY_STRING)).append(delimiter)
                .append(Optional.ofNullable(response).map(Response::getEmail).orElse(EMPTY_STRING)).append(delimiter)
                .append(Optional.ofNullable(response).map(Response::getPhone).map(r -> wrapIfContainsDelimiter(r, delimiter)).orElse(EMPTY_STRING)).append(delimiter)
                .append(Optional.ofNullable(response).map(Response::getAddress).map(r -> wrapIfContainsDelimiter(r, delimiter)).orElse(EMPTY_STRING)).append(delimiter)
                .append(Optional.ofNullable(response).map(ResponseWithInputRequest::getInputRequest).map(InputRequest::getInput1).orElse(EMPTY_STRING)).append(delimiter)
                .append(Optional.ofNullable(response).map(ResponseWithInputRequest::getInputRequest).map(InputRequest::getInput2).orElse(EMPTY_STRING)).append(delimiter)
                .append(Optional.ofNullable(response).map(ResponseWithInputRequest::getInputRequest).map(InputRequest::getInput3).orElse(EMPTY_STRING)).append(delimiter)
                .append(Optional.ofNullable(response).map(Response::getError).orElse(EMPTY_STRING));
        addReportData(response, reportData);
        return csvString.toString();
    }


    private static void addReportData(ResponseWithInputRequest response, ReportData reportData) {
        reportData.addRecs();
    }

    private static String wrapWithQuotes(String value) {
        return value.isEmpty() ? EMPTY_STRING : DOUBLE_QUOTES + value + DOUBLE_QUOTES;
    }

    private static String wrapIfContainsDelimiter(String value, String delimiter) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return value.contains(delimiter) ? wrapWithQuotes(value) : value;
    }
}
