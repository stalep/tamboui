/*
 * Copyright TamboUI Contributors
 * SPDX-License-Identifier: MIT
 */
package dev.tamboui.terminal;

/**
 * Represents the status of Mode 2026 (synchronized output) support in a terminal.
 * <p>
 * Mode 2026 prevents screen tearing during frame rendering by buffering all output
 * between BSU (Begin Synchronized Update) and ESU (End Synchronized Update) sequences.
 * The terminal only renders the buffered content when it receives the ESU sequence,
 * ensuring atomic frame updates.
 * <p>
 * Terminals known to support Mode 2026: kitty, Ghostty, WezTerm, foot, Contour,
 * iTerm2, mintty.
 */
public enum Mode2026Status {

    /**
     * Terminal doesn't recognize Mode 2026.
     * This typically means the terminal is older or doesn't support
     * the DECRQM query for mode 2026.
     */
    NOT_SUPPORTED,

    /**
     * Terminal recognizes Mode 2026 but has it disabled (Ps=2 or Ps=4 in DECRPM response).
     * This means the terminal supports synchronized output and it can be enabled.
     */
    SUPPORTED_DISABLED,

    /**
     * Mode 2026 is currently enabled (Ps=1 or Ps=3 in DECRPM response).
     */
    ENABLED;

    /**
     * Returns whether the terminal supports Mode 2026.
     *
     * @return true if the terminal recognizes Mode 2026 (either enabled or disabled),
     *         false if the terminal doesn't support it at all
     */
    public boolean isSupported() {
        return this != NOT_SUPPORTED;
    }
}
