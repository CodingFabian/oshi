/**
 * Oshi (https://github.com/dblock/oshi)
 *
 * Copyright (c) 2010 - 2016 The Oshi Project Team
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Maintainers:
 * dblock[at]dblock[dot]org
 * widdis[at]gmail[dot]com
 * enrico.bianchi[at]gmail[dot]com
 *
 * Contributors:
 * https://github.com/dblock/oshi/graphs/contributors
 */
package oshi.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * File reading methods
 *
 * @author widdis[at]gmail[dot]com
 */
public class FileUtil {
    private static final Logger LOG = LoggerFactory.getLogger(FileUtil.class);

    private FileUtil() {
    }

    /**
     * Read an entire file at one time. Intended primarily for Linux /proc
     * filesystem to avoid recalculating file contents on iterative reads.
     *
     * @param filename
     *            The file to read
     *
     * @return A list of Strings representing each line of the file, or an empty
     *         list if the file could not be read or is empty
     */
    public static List<String> readFile(String filename) {
        return readFile(filename, true);
    }

    /**
     * Read an entire file at one time. Intended primarily for Linux /proc
     * filesystem to avoid recalculating file contents on iterative reads.
     *
     * @param filename
     *            The file to read
     * @param reportError
     *            Whether to log errors reading the file
     *
     * @return A list of Strings representing each line of the file, or an empty
     *         list if the file could not be read or is empty
     */
    public static List<String> readFile(String filename, boolean reportError) {
        if (new File(filename).exists()) {
            LOG.debug("Reading file {}", filename);
            try {
                return Files.readAllLines(Paths.get(filename), StandardCharsets.UTF_8);
            } catch (IOException e) {
                if (reportError) {
                    LOG.error("Error reading file {}. {}", filename, e);
                }
            }
        } else if (reportError) {
            LOG.warn("File not found: {}", filename);
        }
        return new ArrayList<>();
    }

    /**
     * Read the first line of a file. Intended primarily for Linux /proc
     * filesystem to avoid recalculating file contents on iterative reads.
     *
     * @param filename
     *            The file to read
     *
     * @return A String representing the first line of the file, or null if the
     *         file could not be read or is empty
     */
    public static String readFirstLineOfFile(String filename) {
        return readFirstLineOfFile(filename, true);
    }

    /**
     * Read the first line of a file. Intended primarily for Linux /proc
     * filesystem to avoid recalculating file contents on iterative reads.
     *
     * @param filename
     *            The file to read
     * @param reportError
     *            Whether to log errors reading the file
     * 
     * @return A String representing the first line of the file, or null if the
     *         file could not be read or is empty
     */
    public static String readFirstLineOfFile(String filename, boolean reportError) {
        if (new File(filename).exists()) {
            LOG.debug("Reading file {}", filename);
            try {
                try (BufferedReader reader = Files.newBufferedReader(Paths.get(filename), StandardCharsets.UTF_8)) {
                    return reader.readLine();
                }
            } catch (IOException e) {
                if (reportError) {
                    LOG.error("Error reading file {}. {}", filename, e);
                }
            }
        } else if (reportError) {
            LOG.warn("File not found: {}", filename);
        }
        return null;
    }

    
    /**
     * Read a file and return the long value contained therein. Intended
     * primarily for Linux /sys filesystem
     *
     * @param filename
     *            The file to read
     * @return The value contained in the file, if any; otherwise zero
     */
    public static long getLongFromFile(String filename) {
        LOG.debug("Reading file {}", filename);
        String read = FileUtil.readFirstLineOfFile(filename, false);
        if (read != null) {
            LOG.trace("Read {}", read);
            return ParseUtil.parseLongOrDefault(read, 0L);
        }
        return 0L;
    }

    /**
     * Read a file and return the int value contained therein. Intended
     * primarily for Linux /sys filesystem
     *
     * @param filename
     *            The file to read
     * @return The value contained in the file, if any; otherwise zero
     */
    public static int getIntFromFile(String filename) {
        LOG.debug("Reading file {}", filename);
        try {
            String read = FileUtil.readFirstLineOfFile(filename, false);
            if (read != null) {
                LOG.trace("Read {}", read);
                return Integer.parseInt(read);
            }
        } catch (NumberFormatException ex) {
            LOG.debug("Unable to read value from {}. {}", filename, ex);
        }
        return 0;
    }

    /**
     * Read a file and return the String value contained therein. Intended
     * primarily for Linux /sys filesystem
     *
     * @param filename
     *            The file to read
     * @return The value contained in the file, if any; otherwise empty string
     */
    public static String getStringFromFile(String filename) {
        LOG.debug("Reading file {}", filename);
        String read = FileUtil.readFirstLineOfFile(filename, false);
        if (read != null) {
            LOG.trace("Read {}", read);
            return read;
        }
        return "";
    }

    /**
     * Read a file and return an array of whitespace-delimited string values
     * contained therein. Intended primarily for Linux /proc
     *
     * @param filename
     *            The file to read
     * @return An array of strings containing delimited values
     */
    public static String[] getSplitFromFile(String filename) {
        LOG.debug("Reading file {}", filename);
        String read = FileUtil.readFirstLineOfFile(filename, false);
        if (read != null) {
            LOG.trace("Read {}", read);
            return read.split("\\s+");
        }
        return new String[0];
    }

    /**
     * Read a file and return a map of string keys to string values contained
     * therein. Intended primarily for Linux /proc/[pid]/io
     * @param filename
     *            The file to read
     * @return The map contained in the file, if any; otherwise empty map
     */
    public static Map<String, String> getKeyValueMapFromFile(
            String filename,
            String separator) {
        Map<String, String> map = new HashMap<>();
        LOG.debug("Reading file {}", filename);
        List<String> lines = FileUtil.readFile(filename, false);
        for (String line : lines) {
            String[] parts = line.split(separator);
            if (parts.length == 2) {
                map.put(parts[0], parts[1].trim());
            }
        }
        return map;
    }
}
