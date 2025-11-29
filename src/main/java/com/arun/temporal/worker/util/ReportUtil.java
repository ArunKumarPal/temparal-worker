package com.arun.temporal.worker.util;

import com.arun.temporal.worker.configuration.ReportConfiguration;
import com.arun.temporal.worker.model.ReportData;
import com.arun.temporal.worker.model.ReportDetail;
import com.arun.temporal.worker.model.ResponseWithInputRequest;
import com.arun.temporal.worker.service.S3Service;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;

import static com.arun.temporal.worker.constant.ReportForm.REPORT_FORM;
import static com.arun.temporal.worker.constant.Constants.*;


@Singleton
public class ReportUtil {

    private static final Logger logger = LoggerFactory.getLogger(ReportUtil.class);
    private final ReportConfiguration reportConfiguration;
    private final S3Service s3Service;
    public static final ReentrantLock lock = new ReentrantLock();

    public ReportUtil(ReportConfiguration reportConfiguration, S3Service s3Service) {
        this.reportConfiguration = reportConfiguration;
        this.s3Service = s3Service;
    }

    public static void addCASSData(ResponseWithInputRequest responseWithInputRequest, ReportData cassData) {
        cassData.addRecs();
    }

    public static LocalDate getParsedDate(String date) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("ddMMyyyy");
        try {
            return LocalDate.parse("01" + date, formatter);
        } catch (DateTimeParseException e) {
            return LocalDate.now().withDayOfMonth(1);
        }
    }

    public CompletableFuture<Boolean> generateAndUploadReport(String bucketName, String key, ReportData reportData, ReportDetail reportDetail) {
        String cassForm = generateReport(reportData, reportDetail);
        return s3Service.uploadReport(bucketName, key, cassForm);
    }

    public String generateReport(ReportData reportData, ReportDetail reportDetail) {
        DateTimeFormatter formatterWithFullYear = DateTimeFormatter.ofPattern("MM/dd/yyyy");
        String currentDateWithFullYear = formatterWithFullYear.format(LocalDate.now());
        List<String> mailingAddress = splitString(Optional.ofNullable(reportDetail).map(ReportDetail::getAddress).orElse(EMPTY_STRING), 36);
        List<String> listProcessorName = splitString(Optional.ofNullable(reportDetail).map(ReportDetail::getName).orElse(EMPTY_STRING), 24);
        return String.format(REPORT_FORM, createFixedLengthString(reportConfiguration.getVendorName(), 24, false),
                createFixedLengthString(reportConfiguration.getSoftwareName(), 24, false),
                createFixedLengthString(reportConfiguration.getSoftwareVersion(), 24, false),
                createFixedLengthString(getAddressLine(listProcessorName, 0), 24, false),
                createFixedLengthString(getAddressLine(listProcessorName, 1), 24, false),
                createFixedLengthString(getAddressLine(listProcessorName, 2), 24, false),
                currentDateWithFullYear,
                createFixedLengthString(Optional.ofNullable(reportDetail).map(ReportDetail::getEmail).orElse(EMPTY_STRING), 23, false),
                createFixedLengthString(Optional.ofNullable(reportDetail).map(ReportDetail::getPhoneNumber).orElse(EMPTY_STRING), 2, false),
                createFixedLengthString(reportData.getRecords().toString(), 16, false),
                createFixedLengthString(getAddressLine(mailingAddress, 0), 36, false),
                createFixedLengthString(getAddressLine(mailingAddress, 1), 36, false),
                createFixedLengthString(getAddressLine(mailingAddress, 2), 36, false),
                createFixedLengthString(getAddressLine(mailingAddress, 3), 36, false),
                createFixedLengthString(getAddressLine(mailingAddress, 4), 36, false),
                createFixedLengthString(getAddressLine(mailingAddress, 5), 36, false)
        );
    }

    public static String createFixedLengthString(String input, int length, boolean left) {
        if (input == null) {
            input = EMPTY_STRING;
        }
        if (input.length() >= length) {
            return input.substring(0, length);
        }
        return left ? StringUtils.leftPad(input, length) : StringUtils.rightPad(input, length);
    }

    public static List<String> splitString(String input, int n) {
        List<String> result = new ArrayList<>();
        int length = input.length();
        int start = 0;

        while (start < length) {
            int end = Math.min(start + n, length);
            while (end < length && input.charAt(end) != ' ' && end > start) {
                end--;
            }
            if (end == start) {
                end = Math.min(start + n, length);
            }
            result.add(input.substring(start, end).trim());
            start = end;
            while (start < length && input.charAt(start) == ' ') {
                start++;
            }
        }
        return result;
    }

    public static String getAddressLine(List<String> mailingAddress, int index) {
        return mailingAddress.size() > index ? mailingAddress.get(index) : EMPTY_STRING;
    }

}
