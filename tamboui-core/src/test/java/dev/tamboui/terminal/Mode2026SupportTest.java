/*
 * Copyright TamboUI Contributors
 * SPDX-License-Identifier: MIT
 */
package dev.tamboui.terminal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.Queue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.tamboui.buffer.DiffResult;
import dev.tamboui.layout.Position;
import dev.tamboui.layout.Size;

import static org.assertj.core.api.Assertions.assertThat;

class Mode2026SupportTest {

    @Nested
    @DisplayName("Mode2026Status")
    class Mode2026StatusTests {

        @Test
        @DisplayName("NOT_SUPPORTED is not supported")
        void notSupportedIsNotSupported() {
            assertThat(Mode2026Status.NOT_SUPPORTED.isSupported()).isFalse();
        }

        @Test
        @DisplayName("SUPPORTED_DISABLED is supported")
        void supportedDisabledIsSupported() {
            assertThat(Mode2026Status.SUPPORTED_DISABLED.isSupported()).isTrue();
        }

        @Test
        @DisplayName("ENABLED is supported")
        void enabledIsSupported() {
            assertThat(Mode2026Status.ENABLED.isSupported()).isTrue();
        }
    }

    @Nested
    @DisplayName("Escape sequences")
    class EscapeSequenceTests {

        @Test
        @DisplayName("query sequence is CSI ? 2026 $ p")
        void querySequence() {
            assertThat(Mode2026Support.querySequence()).isEqualTo("\033[?2026$p");
        }

        @Test
        @DisplayName("enable (BSU) sequence is CSI ? 2026 h")
        void enableSequence() {
            assertThat(Mode2026Support.enableSequence()).isEqualTo("\033[?2026h");
        }

        @Test
        @DisplayName("disable (ESU) sequence is CSI ? 2026 l")
        void disableSequence() {
            assertThat(Mode2026Support.disableSequence()).isEqualTo("\033[?2026l");
        }
    }

    @Nested
    @DisplayName("enable and disable operations")
    class EnableDisableTests {

        private MockBackend backend;
        private ByteArrayOutputStream output;

        @BeforeEach
        void setUp() {
            output = new ByteArrayOutputStream();
            backend = new MockBackend(output);
        }

        @Test
        @DisplayName("enable (BSU) sends CSI ? 2026 h without flushing")
        void enableSendsCorrectSequenceWithoutFlush() throws IOException {
            Mode2026Support.enable(backend);
            assertThat(output.toString(StandardCharsets.UTF_8.name())).isEqualTo("\033[?2026h");
            // BSU should not flush — the caller will flush after draw + cursor operations
            assertThat(backend.flushCount).isEqualTo(0);
        }

        @Test
        @DisplayName("disable (ESU) sends CSI ? 2026 l and flushes")
        void disableSendsCorrectSequence() throws IOException {
            Mode2026Support.disable(backend);
            assertThat(output.toString(StandardCharsets.UTF_8.name())).isEqualTo("\033[?2026l");
            assertThat(backend.flushCount).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Response parsing")
    class ResponseParsingTests {

        private MockBackend backend;
        private ByteArrayOutputStream output;

        @BeforeEach
        void setUp() {
            output = new ByteArrayOutputStream();
            backend = new MockBackend(output);
        }

        @Test
        @DisplayName("valid enabled response (Ps=1) returns ENABLED")
        void validEnabledResponsePs1() throws IOException {
            backend.setResponseBytes("\033[?2026;1$y");
            Mode2026Status status = Mode2026Support.query(backend, 100);
            assertThat(status).isEqualTo(Mode2026Status.ENABLED);
        }

        @Test
        @DisplayName("valid enabled response (Ps=3) returns ENABLED")
        void validEnabledResponsePs3() throws IOException {
            backend.setResponseBytes("\033[?2026;3$y");
            Mode2026Status status = Mode2026Support.query(backend, 100);
            assertThat(status).isEqualTo(Mode2026Status.ENABLED);
        }

        @Test
        @DisplayName("valid disabled response (Ps=2) returns SUPPORTED_DISABLED")
        void validDisabledResponsePs2() throws IOException {
            backend.setResponseBytes("\033[?2026;2$y");
            Mode2026Status status = Mode2026Support.query(backend, 100);
            assertThat(status).isEqualTo(Mode2026Status.SUPPORTED_DISABLED);
        }

        @Test
        @DisplayName("valid disabled response (Ps=4) returns SUPPORTED_DISABLED")
        void validDisabledResponsePs4() throws IOException {
            backend.setResponseBytes("\033[?2026;4$y");
            Mode2026Status status = Mode2026Support.query(backend, 100);
            assertThat(status).isEqualTo(Mode2026Status.SUPPORTED_DISABLED);
        }

        @Test
        @DisplayName("unrecognized mode response (Ps=0) returns NOT_SUPPORTED")
        void unrecognizedModeResponsePs0() throws IOException {
            backend.setResponseBytes("\033[?2026;0$y");
            Mode2026Status status = Mode2026Support.query(backend, 100);
            assertThat(status).isEqualTo(Mode2026Status.NOT_SUPPORTED);
        }

        @Test
        @DisplayName("timeout returns NOT_SUPPORTED")
        void timeoutReturnsNotSupported() throws IOException {
            // No response - backend returns -2 (timeout)
            Mode2026Status status = Mode2026Support.query(backend, 50);
            assertThat(status).isEqualTo(Mode2026Status.NOT_SUPPORTED);
        }

        @Test
        @DisplayName("malformed response returns NOT_SUPPORTED")
        void malformedResponseReturnsNotSupported() throws IOException {
            backend.setResponseBytes("\033[?2026;1$x");
            Mode2026Status status = Mode2026Support.query(backend, 100);
            assertThat(status).isEqualTo(Mode2026Status.NOT_SUPPORTED);
        }

        @Test
        @DisplayName("partial response before timeout returns NOT_SUPPORTED")
        void partialResponseBeforeTimeout() throws IOException {
            backend.setResponseBytes("\033[?2026;");
            Mode2026Status status = Mode2026Support.query(backend, 50);
            assertThat(status).isEqualTo(Mode2026Status.NOT_SUPPORTED);
        }

        @Test
        @DisplayName("response for different mode number is ignored")
        void differentModeNumberIgnored() throws IOException {
            // Response for mode 2027 instead of 2026
            backend.setResponseBytes("\033[?2027;1$y");
            Mode2026Status status = Mode2026Support.query(backend, 50);
            assertThat(status).isEqualTo(Mode2026Status.NOT_SUPPORTED);
        }

        @Test
        @DisplayName("response with multi-digit Ps value")
        void multiDigitPsValue() throws IOException {
            backend.setResponseBytes("\033[?2026;12$y");
            Mode2026Status status = Mode2026Support.query(backend, 100);
            assertThat(status).isEqualTo(Mode2026Status.NOT_SUPPORTED);
        }

        @Test
        @DisplayName("noise before valid response is handled")
        void noiseBeforeValidResponse() throws IOException {
            backend.setResponseBytes("abc\033[?2026;1$y");
            Mode2026Status status = Mode2026Support.query(backend, 100);
            assertThat(status).isEqualTo(Mode2026Status.ENABLED);
        }

        @Test
        @DisplayName("query sends correct sequence")
        void querySendsCorrectSequence() throws IOException {
            backend.setResponseBytes("\033[?2026;1$y");
            Mode2026Support.query(backend, 100);
            assertThat(output.toString(StandardCharsets.UTF_8.name())).isEqualTo("\033[?2026$p");
        }
    }

    /**
     * Mock Backend implementation for testing Mode2026Support.
     */
    private static class MockBackend implements Backend {
        private final ByteArrayOutputStream output;
        private final Queue<Integer> responseQueue = new LinkedList<>();
        int flushCount = 0;

        MockBackend(ByteArrayOutputStream output) {
            this.output = output;
        }

        void setResponseBytes(String response) {
            for (char c : response.toCharArray()) {
                responseQueue.add((int) c);
            }
        }

        @Override
        public void writeRaw(byte[] data) throws IOException {
            output.write(data);
        }

        @Override
        public void flush() throws IOException {
            flushCount++;
        }

        @Override
        public int read(int timeoutMs) throws IOException {
            if (responseQueue.isEmpty()) {
                return -2; // timeout
            }
            return responseQueue.poll();
        }

        @Override
        public void draw(DiffResult diff) throws IOException {
        }

        @Override
        public void clear() throws IOException {
        }

        @Override
        public Size size() throws IOException {
            return new Size(80, 24);
        }

        @Override
        public void showCursor() throws IOException {
        }

        @Override
        public void hideCursor() throws IOException {
        }

        @Override
        public Position getCursorPosition() throws IOException {
            return Position.ORIGIN;
        }

        @Override
        public void setCursorPosition(Position position) throws IOException {
        }

        @Override
        public void enterAlternateScreen() throws IOException {
        }

        @Override
        public void leaveAlternateScreen() throws IOException {
        }

        @Override
        public void enableRawMode() throws IOException {
        }

        @Override
        public void disableRawMode() throws IOException {
        }

        @Override
        public void onResize(Runnable handler) {
        }

        @Override
        public int peek(int timeoutMs) throws IOException {
            return responseQueue.isEmpty() ? -2 : responseQueue.peek();
        }

        @Override
        public void close() throws IOException {
        }
    }
}
