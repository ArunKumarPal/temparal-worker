package com.arun.temporal.worker.util;

import com.arun.temporal.worker.configuration.ReportConfiguration;
import com.arun.temporal.worker.model.ReportData;
import com.arun.temporal.worker.model.ReportDetail;
import com.arun.temporal.worker.service.S3Service;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReportUtilTest {

    @Mock
    ReportConfiguration reportConfiguration;

    @Mock
    S3Service s3Service;

    @InjectMocks
    ReportUtil reportUtil;

    @Test
    void generate_report_success() {
        ReportData reportData = new ReportData();
        when(reportConfiguration.getVendorName()).thenReturn("Precisely Software Inc.");
        when(reportConfiguration.getSoftwareName()).thenReturn("Bulk API Geocode");
        when(reportConfiguration.getSoftwareVersion()).thenReturn("1.00.00.0");
        when(reportConfiguration.getDataVintage()).thenReturn("062024");
        ReportDetail reportDetail = new ReportDetail("TESTING INC.", "TEST", "1", "delhi india");
        String report = reportUtil.generateReport(reportData, reportDetail);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yy");
        DateTimeFormatter formatterWithFullYear = DateTimeFormatter.ofPattern("MM/dd/yyyy");
        String currentDate = formatter.format(LocalDate.now());
        String currentDateWithFullYear = formatterWithFullYear.format(LocalDate.now());
        assertEquals(TestData.REPORT_FORM.formatted(currentDateWithFullYear, currentDateWithFullYear, currentDate, currentDate, currentDate, currentDate), report);
    }

    @Test
    void success_parsed_date() {
        LocalDate date = ReportUtil.getParsedDate("062024");
        assertEquals("2024-06-01", date.toString());
    }

    @Test
    void invalid_date_parse() {
        LocalDate date = ReportUtil.getParsedDate("2024");
        assertNotEquals("2024-06-01", date.toString());
        assertEquals(LocalDate.now().withDayOfMonth(1).toString(), date.toString());
    }

    @Test
    void create_left_fixed_length_string_with_big_size_string() {
        String output = ReportUtil.createFixedLengthString("sample", 2, true);
        assertEquals("sa", output);
    }

    @Test
    void create_left_fixed_length_string_with_null_string() {
        String output = ReportUtil.createFixedLengthString(null, 2, true);
        assertEquals("  ", output);
    }

    @Test
    void create_left_fixed_length_string_with_small_size_string() {
        String output = ReportUtil.createFixedLengthString("sample", 7, true);
        assertEquals(" sample", output);
    }

    @Test
    void create_left_fixed_length_string_with_small_size_string_right() {
        String output = ReportUtil.createFixedLengthString("sample", 7, false);
        assertEquals("sample ", output);
    }

    @Test
    void testGenerateAndUploadReport() {
        String bucketName = "test-bucket";
        String key = "test-key";
        ReportData reportData = new ReportData();
        ReportDetail reportDetail = new ReportDetail("TESTING INC.", "TEST", "1", "delhi india");
        when(s3Service.uploadReport(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(true));
        CompletableFuture<Boolean> result = reportUtil.generateAndUploadReport(bucketName, key, reportData, reportDetail);
        assertTrue(result.join());
        verify(s3Service).uploadReport(anyString(), anyString(), anyString());
    }

}
