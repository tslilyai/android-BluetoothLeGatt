package org.mpi_sws.sddr_userspace.lib;

public class NotificationIDs {
    public static class Platform {
        public static final int LocationLoggerNotification = 1;
        public static final int EbNCoreNotification = 2;
    }

    public static class Updater {
        public static final int updateProgress = 42;
        public static final int updateAvailable = 43;
    }
    
    public static class Crypto {
        public static final int decryptionInProgress = 50;
    }

}
