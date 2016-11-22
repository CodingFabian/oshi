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
package oshi.hardware.common;

import java.lang.management.ManagementFactory;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import oshi.hardware.CentralProcessor;
import oshi.util.ParseUtil;

/**
 * A CPU as defined in Linux /proc.
 *
 * @author alessandro[at]perucchi[dot]org
 * @author alessio.fachechi[at]gmail[dot]com
 * @author widdis[at]gmail[dot]com
 */
@SuppressWarnings("restriction")
public abstract class AbstractCentralProcessor implements CentralProcessor {

    private static final long serialVersionUID = 1L;

    private static final Logger LOG = LoggerFactory.getLogger(AbstractCentralProcessor.class);

    /**
     * Instantiate an OperatingSystemMXBean for future convenience
     */
    private static final java.lang.management.OperatingSystemMXBean OS_MXBEAN = ManagementFactory
            .getOperatingSystemMXBean();

    /**
     * Calling OperatingSystemMxBean too rapidly results in NaN. Store the
     * latest value to return if polling is too rapid
     */
    private double lastCpuLoad = 0d;

    /**
     * Keep track of last CPU Load poll to OperatingSystemMXBean to ensure
     * enough time has elapsed
     */
    private long lastCpuLoadTime = 0;

    /**
     * Keep track whether MXBean supports Oracle JVM methods
     */
    private boolean sunMXBean = false;

    // Logical and Physical Processor Counts
    protected int logicalProcessorCount = 0;

    protected int physicalProcessorCount = 0;

    // Maintain previous ticks to be used for calculating usage between them.
    // System ticks
    private long tickTime;

    private long[] prevTicks;

    private long[] curTicks;

    // Per-processor ticks [cpu][type]
    private long procTickTime;

    private long[][] prevProcTicks;

    private long[][] curProcTicks;

    // Processor info
    private String cpuVendor;

    private String cpuName;

    protected String cpuSerialNumber = null;

    private String cpuIdentifier;

    private String cpuStepping;

    private String cpuModel;

    private String cpuFamily;

    private Long cpuVendorFreq;

    private Boolean cpu64;

    /**
     * Create a Processor
     */
    public AbstractCentralProcessor() {
        initMXBean();
        // Initialize processor counts
        calculateProcessorCounts();
    }

    /**
     * Initializes mxBean boolean
     */
    private void initMXBean() {
        try {
            Class.forName("com.sun.management.OperatingSystemMXBean");
            // Initialize CPU usage
            this.lastCpuLoad = ((com.sun.management.OperatingSystemMXBean) OS_MXBEAN).getSystemCpuLoad();
            this.lastCpuLoadTime = System.currentTimeMillis();
            this.sunMXBean = true;
            LOG.debug("Oracle MXBean detected.");
        } catch (ClassNotFoundException e) {
            LOG.debug("Oracle MXBean not detected.");
            LOG.trace("", e);
        }
    }

    /**
     * Initializes tick arrays
     */
    protected synchronized void initTicks() {
        // Per-processor ticks
        this.prevProcTicks = new long[this.logicalProcessorCount][TickType.values().length];
        this.curProcTicks = new long[this.logicalProcessorCount][TickType.values().length];
        updateProcessorTicks();

        // Solaris relies on procTicks init before system ticks
        // System ticks
        this.prevTicks = new long[TickType.values().length];
        this.curTicks = new long[TickType.values().length];
        updateSystemTicks();

    }

    /**
     * Updates logical and physical processor counts
     */
    protected abstract void calculateProcessorCounts();

    /**
     * {@inheritDoc}
     */
    @Override
    public String getVendor() {
        if (this.cpuVendor == null) {
            setVendor("");
        }
        return this.cpuVendor;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setVendor(String vendor) {
        this.cpuVendor = vendor;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        if (this.cpuName == null) {
            setName("");
        }
        return this.cpuName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setName(String name) {
        this.cpuName = name;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getVendorFreq() {
        if (this.cpuVendorFreq == null) {
            Pattern pattern = Pattern.compile("@ (.*)$");
            Matcher matcher = pattern.matcher(getName());

            if (matcher.find()) {
                String unit = matcher.group(1);
                this.cpuVendorFreq = Long.valueOf(ParseUtil.parseHertz(unit));
            } else {
                this.cpuVendorFreq = Long.valueOf(-1L);
            }
        }
        return this.cpuVendorFreq.longValue();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setVendorFreq(long freq) {
        this.cpuVendorFreq = Long.valueOf(freq);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getIdentifier() {
        if (this.cpuIdentifier == null) {
            StringBuilder sb = new StringBuilder();
            if (getVendor().contentEquals("GenuineIntel")) {
                sb.append(isCpu64bit() ? "Intel64" : "x86");
            } else {
                sb.append(getVendor());
            }
            sb.append(" Family ").append(getFamily());
            sb.append(" Model ").append(getModel());
            sb.append(" Stepping ").append(getStepping());
            setIdentifier(sb.toString());
        }
        return this.cpuIdentifier;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setIdentifier(String identifier) {
        this.cpuIdentifier = identifier;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isCpu64bit() {
        if (this.cpu64 == null) {
            setCpu64(false);
        }
        return this.cpu64;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setCpu64(boolean value) {
        this.cpu64 = Boolean.valueOf(value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getStepping() {
        if (this.cpuStepping == null) {
            if (this.cpuIdentifier == null) {
                return "?";
            }
            setStepping(parseIdentifier("Stepping"));
        }
        return this.cpuStepping;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setStepping(String stepping) {
        this.cpuStepping = stepping;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getModel() {
        if (this.cpuModel == null) {
            if (this.cpuIdentifier == null) {
                return "?";
            }
            setModel(parseIdentifier("Model"));
        }
        return this.cpuModel;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setModel(String model) {
        this.cpuModel = model;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getFamily() {
        if (this.cpuFamily == null) {
            if (this.cpuIdentifier == null) {
                return "?";
            }
            setFamily(parseIdentifier("Family"));
        }
        return this.cpuFamily;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setFamily(String family) {
        this.cpuFamily = family;
    }

    /**
     * Parses identifier string
     *
     * @param id
     *            the id to retrieve
     * @return the string following id
     */
    private String parseIdentifier(String id) {
        StringTokenizer stringTokenizer = new StringTokenizer(getIdentifier());
        boolean found = false;
        while (stringTokenizer.hasMoreTokens()) {
            String s = stringTokenizer.nextToken();
            // If id string found, return next value
            if (found) {
                return s;
            }
            found = s.equals(id);
        }
        // If id string not found, return empty string
        return "";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized double getSystemCpuLoadBetweenTicks() {
        // Check if > ~ 0.95 seconds since last tick count.
        long now = System.currentTimeMillis();
        LOG.trace("Current time: {}  Last tick time: {}", now, this.tickTime);
        if (now - this.tickTime > 950) {
            // Enough time has elapsed.
            updateSystemTicks();
        }
        // Calculate total
        long total = 0;
        for (int i = 0; i < this.curTicks.length; i++) {
            total += this.curTicks[i] - this.prevTicks[i];
        }
        // Calculate idle from difference in idle and IOwait
        long idle = this.curTicks[TickType.IDLE.getIndex()] + this.curTicks[TickType.IOWAIT.getIndex()]
                - this.prevTicks[TickType.IDLE.getIndex()] - this.prevTicks[TickType.IOWAIT.getIndex()];
        LOG.trace("Total ticks: {}  Idle ticks: {}", total, idle);

        return total > 0 && idle >= 0 ? (double) (total - idle) / total : 0d;
    }

    /**
     * Updates system tick information. Stores in array with seven elements
     * representing clock ticks or milliseconds (platform dependent) spent in
     * User (0), Nice (1), System (2), Idle (3), IOwait (4), IRQ (5), and
     * SoftIRQ (6) states. By measuring the difference between ticks across a
     * time interval, CPU load over that interval may be calculated.
     */
    protected void updateSystemTicks() {
        LOG.trace("Updating System Ticks");
        long[] ticks = getSystemCpuLoadTicks();
        // Skip update if ticks is all zero.
        // Iterate to find a nonzero tick value and return; this should quickly
        // find a nonzero value if one exists and be fast in checking 0's
        // through branch prediction if it doesn't
        for (long tick : ticks) {
            if (tick != 0) {
                // We have a nonzero tick array, update and return!
                this.tickTime = System.currentTimeMillis();
                // Copy to previous
                System.arraycopy(this.curTicks, 0, this.prevTicks, 0, this.curTicks.length);
                System.arraycopy(ticks, 0, this.curTicks, 0, ticks.length);
                return;
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getSystemCpuLoad() {
        if (this.sunMXBean) {
            long now = System.currentTimeMillis();
            // If called too recently, return latest value
            if (now - this.lastCpuLoadTime < 200) {
                return this.lastCpuLoad;
            }
            this.lastCpuLoad = ((com.sun.management.OperatingSystemMXBean) OS_MXBEAN).getSystemCpuLoad();
            this.lastCpuLoadTime = now;
            return this.lastCpuLoad;
        }
        return getSystemCpuLoadBetweenTicks();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getSystemLoadAverage() {
        return getSystemLoadAverage(1)[0];
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double[] getProcessorCpuLoadBetweenTicks() {
        // Check if > ~ 0.95 seconds since last tick count.
        long now = System.currentTimeMillis();
        LOG.trace("Current time: {}  Last tick time: {}", now, this.procTickTime);
        if (now - this.procTickTime > 950) {
            // Enough time has elapsed.
            // Update latest
            updateProcessorTicks();
        }
        double[] load = new double[this.logicalProcessorCount];
        for (int cpu = 0; cpu < this.logicalProcessorCount; cpu++) {
            long total = 0;
            for (int i = 0; i < this.curProcTicks[cpu].length; i++) {
                total += this.curProcTicks[cpu][i] - this.prevProcTicks[cpu][i];
            }
            // Calculate idle from difference in idle and IOwait
            long idle = this.curProcTicks[cpu][TickType.IDLE.getIndex()]
                    + this.curProcTicks[cpu][TickType.IOWAIT.getIndex()]
                    - this.prevProcTicks[cpu][TickType.IDLE.getIndex()]
                    - this.prevProcTicks[cpu][TickType.IOWAIT.getIndex()];
            LOG.trace("CPU: {}  Total ticks: {}  Idle ticks: {}", cpu, total, idle);
            // update
            load[cpu] = total > 0 && idle >= 0 ? (double) (total - idle) / total : 0d;
        }
        return load;
    }

    /**
     * Updates per-processor tick information. Stores in 2D array; an array for
     * each logical processor with with seven elements representing clock ticks
     * or milliseconds (platform dependent) spent in User (0), Nice (1), System
     * (2), Idle (3), IOwait (4), IRQ (5), and SoftIRQ (6) states. By measuring
     * the difference between ticks across a time interval, CPU load over that
     * interval may be calculated.
     */
    protected void updateProcessorTicks() {
        LOG.trace("Updating Processor Ticks");
        long[][] ticks = getProcessorCpuLoadTicks();
        // Skip update if ticks is all zero.
        // Iterate to find a nonzero tick value and return; this should quickly
        // find a nonzero value if one exists and be fast in checking 0's
        // through branch prediction if it doesn't
        for (long[] tick : ticks) {
            for (long element : tick) {
                if (element == 0L) {
                    continue;
                }
                // We have a nonzero tick array, update and return!
                this.procTickTime = System.currentTimeMillis();
                // Copy to previous
                for (int cpu = 0; cpu < this.logicalProcessorCount; cpu++) {
                    System.arraycopy(this.curProcTicks[cpu], 0, this.prevProcTicks[cpu], 0,
                            this.curProcTicks[cpu].length);
                }
                for (int cpu = 0; cpu < this.logicalProcessorCount; cpu++) {
                    System.arraycopy(ticks[cpu], 0, this.curProcTicks[cpu], 0, ticks[cpu].length);
                }
                return;
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getLogicalProcessorCount() {
        return this.logicalProcessorCount;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getPhysicalProcessorCount() {
        return this.physicalProcessorCount;
    }

    @Override
    public String toString() {
        return getName();
    }
}
