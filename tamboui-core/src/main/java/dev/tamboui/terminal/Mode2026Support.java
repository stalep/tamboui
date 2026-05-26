/*
 * Copyright TamboUI Contributors
 * SPDX-License-Identifier: MIT
 */
package dev.tamboui.terminal;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Utility class for Mode 2026 (synchronized output) terminal support.
 * <p>
 * Mode 2026 prevents screen tearing during frame rendering. When enabled,
 * the terminal buffers all output between BSU (Begin Synchronized Update)
 * and ESU (End Synchronized Update) sequences, rendering the complete
 * frame atomically when ESU is received.
 * <p>
 * <b>Escape sequences:</b>
 * <ul>
 *   <li>Query (DECRQM): {@code CSI ? 2026 $ p}</li>
 *   <li>Response (DECRPM): {@code CSI ? 2026 ; Ps $ y} where Ps=1 enabled, Ps=2 disabled but recognized</li>
 *   <li>Enable (BSU): {@code CSI ? 2026 h}</li>
 *   <li>Disable (ESU): {@code CSI ? 2026 l}</li>
 * </ul>
 * <p>
 * Terminals known to support Mode 2026: kitty, Ghostty, WezTerm, foot,
 * Contour, iTerm2, mintty.
 *
 * @see <a href="https://gist.github.com/christianparpart/d8a62cc1ab659194571ec44513cb6f04">Synchronized Output Spec</a>
 */
public final class Mode2026Support {

    private static final String CSI = "\033[";
    private static final String QUERY = CSI + "?2026$p";
    private static final String ENABLE = CSI + "?2026h";
    private static final String DISABLE = CSI + "?2026l";

    // Response parsing states
    private static final int STATE_INITIAL = 0;
    private static final int STATE_ESC = 1;
    private static final int STATE_CSI = 2;
    private static final int STATE_QUESTION = 3;
    private static final int STATE_MODE_NUM = 4;
    private static final int STATE_SEMICOLON = 5;
    private static final int STATE_PS_VALUE = 6;
    private static final int STATE_DOLLAR = 7;

    private Mode2026Support() {
        // Utility class
    }

    /**
     * Queries the terminal for Mode 2026 support status.
     * <p>
     * Sends the DECRQM escape sequence and waits for the DECRPM response.
     * If the terminal doesn't respond within the timeout, it is considered
     * as not supporting Mode 2026.
     *
     * @param backend   the backend to use for terminal I/O
     * @param timeoutMs timeout in milliseconds to wait for response
     * @return the status of Mode 2026 support
     * @throws IOException if an I/O error occurs during communication
     */
    public static Mode2026Status query(Backend backend, int timeoutMs) throws IOException {
        // Send the DECRQM query
        backend.writeRaw(QUERY);
        backend.flush();

        // Parse the response
        return parseResponse(backend, timeoutMs);
    }

    /**
     * Writes the BSU (Begin Synchronized Update) sequence to the backend.
     *
     * @param backend the backend to use for terminal I/O
     * @throws IOException if an I/O error occurs
     */
    public static void enable(Backend backend) throws IOException {
        backend.writeRaw(ENABLE);
        // Do not flush — the caller will flush after draw + cursor operations
    }

    /**
     * Writes the ESU (End Synchronized Update) sequence to the backend.
     *
     * @param backend the backend to use for terminal I/O
     * @throws IOException if an I/O error occurs
     */
    public static void disable(Backend backend) throws IOException {
        backend.writeRaw(DISABLE);
        backend.flush();
    }

    /**
     * Parses the DECRPM response from the terminal.
     * <p>
     * Expected response format: {@code ESC [ ? 2026 ; Ps $ y}
     * <ul>
     *   <li>Ps=1 or Ps=3: Mode is enabled</li>
     *   <li>Ps=2 or Ps=4: Mode is recognized but disabled</li>
     *   <li>Ps=0: Mode not recognized</li>
     *   <li>Timeout or malformed response: Not supported</li>
     * </ul>
     */
    private static Mode2026Status parseResponse(Backend backend, int timeoutMs) throws IOException {
        int state = STATE_INITIAL;
        int modeNumber = 0;
        int psValue = 0;
        long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);

        while (System.nanoTime() < deadlineNanos) {
            int remainingTime = (int) TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime());
            if (remainingTime <= 0) {
                break;
            }

            int ch = backend.read(remainingTime);
            if (ch == -1 || ch == -2) {
                // EOF or timeout
                break;
            }

            switch (state) {
                case STATE_INITIAL:
                    if (ch == '\033') {
                        state = STATE_ESC;
                    }
                    break;

                case STATE_ESC:
                    if (ch == '[') {
                        state = STATE_CSI;
                    } else {
                        state = STATE_INITIAL;
                    }
                    break;

                case STATE_CSI:
                    if (ch == '?') {
                        state = STATE_QUESTION;
                        modeNumber = 0;
                    } else {
                        state = STATE_INITIAL;
                    }
                    break;

                case STATE_QUESTION:
                    if (ch >= '0' && ch <= '9') {
                        modeNumber = ch - '0';
                        state = STATE_MODE_NUM;
                    } else {
                        state = STATE_INITIAL;
                    }
                    break;

                case STATE_MODE_NUM:
                    if (ch >= '0' && ch <= '9') {
                        modeNumber = modeNumber * 10 + (ch - '0');
                    } else if (ch == ';') {
                        if (modeNumber == 2026) {
                            state = STATE_SEMICOLON;
                            psValue = 0;
                        } else {
                            state = STATE_INITIAL;
                        }
                    } else {
                        state = STATE_INITIAL;
                    }
                    break;

                case STATE_SEMICOLON:
                    if (ch >= '0' && ch <= '9') {
                        psValue = ch - '0';
                        state = STATE_PS_VALUE;
                    } else {
                        state = STATE_INITIAL;
                    }
                    break;

                case STATE_PS_VALUE:
                    if (ch >= '0' && ch <= '9') {
                        psValue = psValue * 10 + (ch - '0');
                    } else if (ch == '$') {
                        state = STATE_DOLLAR;
                    } else {
                        state = STATE_INITIAL;
                    }
                    break;

                case STATE_DOLLAR:
                    if (ch == 'y') {
                        // Successfully parsed response
                        return mapPsValueToStatus(psValue);
                    }
                    state = STATE_INITIAL;
                    break;

                default:
                    state = STATE_INITIAL;
            }
        }

        // No valid response received
        return Mode2026Status.NOT_SUPPORTED;
    }

    /**
     * Maps the Ps value from DECRPM response to a Mode2026Status.
     *
     * @param psValue the Ps value from the response
     * @return the corresponding status
     */
    private static Mode2026Status mapPsValueToStatus(int psValue) {
        switch (psValue) {
            case 1: // Mode is set (permanently)
            case 3: // Mode is set (temporarily)
                return Mode2026Status.ENABLED;
            case 2: // Mode is reset (permanently)
            case 4: // Mode is reset (temporarily)
                return Mode2026Status.SUPPORTED_DISABLED;
            case 0: // Mode not recognized
            default:
                return Mode2026Status.NOT_SUPPORTED;
        }
    }

    /**
     * Constructs the DECRQM query sequence for Mode 2026.
     *
     * @return the query escape sequence
     */
    public static String querySequence() {
        return QUERY;
    }

    /**
     * Constructs the BSU (enable) sequence for Mode 2026.
     *
     * @return the enable escape sequence
     */
    public static String enableSequence() {
        return ENABLE;
    }

    /**
     * Constructs the ESU (disable) sequence for Mode 2026.
     *
     * @return the disable escape sequence
     */
    public static String disableSequence() {
        return DISABLE;
    }
}
