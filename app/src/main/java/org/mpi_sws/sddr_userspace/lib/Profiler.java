package org.mpi_sws.sddr_userspace.lib;

import android.util.Log;

public class Profiler {

    private long startTime;
    private String tag;

    public void start(final String tag) {
        startTime = System.currentTimeMillis();
        this.tag = tag;
        Log.d("[Profiler]", "[" + tag + "] ============== Starting ============");
    }

    public void snapshot(final String msg) {
        final long snapshotTime = System.currentTimeMillis();
        Log.d("[Profiler]","[" + tag + "::" + msg + "]"+
                "============== " + (snapshotTime - startTime) + " ms ============");
    }

    public void finish() {
        final long endTime = System.currentTimeMillis();
        Log.d("[Profiler]","[" + tag + "] ============== Done in " + (endTime - startTime) + " ms ============");
    }
}
