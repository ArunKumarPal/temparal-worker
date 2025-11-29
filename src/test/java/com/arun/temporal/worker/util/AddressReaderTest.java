package com.arun.temporal.worker.util;

import com.arun.temporal.worker.model.InputRequest;
import com.arun.temporal.worker.exception.BulkProcessorException;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AddressReaderTest {

    @Test
    void should_pass_when_all_mandatory_headers_present() {
        String[] headers = new String[]{"input1", "input2", "input3"};
        assertDoesNotThrow(() -> AddressReader.validateHeaders(headers));
    }

    @Test
    void should_fail_when_all_mandatory_headers_not_present() {
        String[] headers = new String[]{"input1", "input2", "input3"};
        assertThrows(BulkProcessorException.class, () -> AddressReader.validateHeaders(headers));
    }

    @Test
    void should_pass_when_extra_headers_present() {
        String[] headers = new String[]{"input1", "input2", "input3", "abc",};
        assertDoesNotThrow(() -> AddressReader.validateHeaders(headers));
    }

    @Test
    void should_pass_when_empty_headers_present() {
        String[] headers = new String[]{"input1", "input2", "input3", "", "abc",};
        assertDoesNotThrow(() -> AddressReader.validateHeaders(headers));
    }

    @Test
    void should_convert_when_all_headers_and_values_same() {
        String[] headers = new String[]{"addressid", "address", "country"};
        String[] values = new String[]{"123", "kbc road", "usa"};
        AtomicInteger totalRecords = new AtomicInteger(0);
        InputRequest request = AddressReader.convertToRequest(headers, String.join(",", values), ",", totalRecords);
        assertEquals(values[0], request.getInput1());
        assertEquals(values[1], request.getInput2());
        assertEquals(values[2], request.getInput3());
        assertEquals(1, totalRecords.get());
    }
}
