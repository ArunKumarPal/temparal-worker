package com.arun.temporal.worker.util;

import com.arun.temporal.worker.model.activity.FileChunk;
import com.arun.temporal.worker.model.activity.FileChunkListResponse;
import com.arun.temporal.worker.model.activity.FileHeaderAndTerminator;
import com.arun.temporal.worker.model.activity.FileMetadata;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class FileChunkReader {

    private static final String TAB_SEPARATOR = "\t";
    private FileChunkReader(){
    }


    public static FileMetadata getFileMetadata(int sampleLines, long fileSize, String delimiter, InputStream responseStream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(responseStream, StandardCharsets.UTF_8))) {
            FileHeaderAndTerminator lineTerminatorSizeAndHeader = detectLineTerminatorSize(responseStream);
            int lineTerminatorSize = lineTerminatorSizeAndHeader.terminatorSize();
            int avgValue = (int) Math.ceil(reader.lines()
                    .limit(sampleLines)
                    .mapToInt(line -> line.getBytes(StandardCharsets.UTF_8).length + lineTerminatorSize)
                    .average().orElse(0));
            return new FileMetadata(lineTerminatorSize, avgValue, fileSize, fetchCsvHeaders(lineTerminatorSizeAndHeader.header(), delimiter));
        }
    }

    public static FileChunkListResponse getFileChunkList(long chunkSize, int totalLines, FileMetadata fileMetadata) {
        int avgLineSize = fileMetadata.avgLineSize();
        int newLineSize = fileMetadata.lineTerminatorSize();
        long totalBytes = fileMetadata.fileSize();
        chunkSize = Math.max((long) avgLineSize * totalLines, chunkSize);
        List<FileChunk> chunks = new ArrayList<>();
        long startPosition = newLineSize;
        int chunkId = 1;
        while (startPosition < totalBytes) {
            long endPosition = Math.min(startPosition + chunkSize, totalBytes);
            FileChunk chunk = new FileChunk(
                    chunkId++,
                    startPosition,
                    endPosition,
                    fileMetadata
            );
            chunks.add(chunk);
            startPosition = endPosition + 1;
        }
        return new FileChunkListResponse(chunks);
    }

    public static Flux<String> readDataFromInputStream(InputStream s3ChunkStream, AtomicLong currentPosition, long endOffset) {
        return Flux.create(sink -> {
            try (BufferedInputStream bis = new BufferedInputStream(s3ChunkStream, 1024 * 1024)) {
                processStreamLines(bis, currentPosition, sink, endOffset);
                sink.complete();
            } catch (IOException e) {
                sink.error(e);
            }
        });
    }

    private static String[] fetchCsvHeaders(String headerLine, String delimiter) {
        String splitter = TAB_SEPARATOR.equals(delimiter) ? delimiter : "\\".concat(delimiter);
        return headerLine.trim().toLowerCase().split(splitter);
    }

    private static void processStreamLines(BufferedInputStream bis, AtomicLong currentPosition, FluxSink<String> sink, long endOffset) throws IOException {
        ByteArrayOutputStream lineBuffer = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = bis.read(buffer)) != -1) {
            for (int i = 0; i < read; i++) {
                byte b = buffer[i];
                lineBuffer.write(b);
                if (b == '\n') {
                    emitLine(lineBuffer, currentPosition, sink);
                    lineBuffer.reset();
                    if((currentPosition.get() - 1) >= endOffset){
                        sink.complete();
                        return;
                    }
                }
            }
        }
        if (lineBuffer.size() > 0) {
            emitLine(lineBuffer, currentPosition, sink);
        }
    }

    private static void emitLine(ByteArrayOutputStream lineBuffer, AtomicLong currentPosition, FluxSink<String> sink) {
        byte[] lineBytes = lineBuffer.toByteArray();
        int end = lineBytes.length;
        if (end >= 1 && lineBytes[end - 1] == '\n') end--;
        if (end >= 1 && lineBytes[end - 1] == '\r') end--;
        String line = new String(lineBytes, 0, end, StandardCharsets.UTF_8);
        currentPosition.addAndGet(lineBytes.length);
        sink.next(line);
    }

    public static FileHeaderAndTerminator detectLineTerminatorSize(InputStream input) throws IOException {
        int prev = -1;
        int curr;
        StringBuilder header = new StringBuilder();
        int lineTerminatorSize = 0;
        while ((curr = input.read()) != -1) {
            if (curr == '\n') {
                lineTerminatorSize = (prev == '\r') ? 2 : 1;
                break;
            } else if (curr != '\r') {
                header.append((char) curr);
            }
            prev = curr;
        }
        return new FileHeaderAndTerminator(header.toString(), lineTerminatorSize);
    }
}
