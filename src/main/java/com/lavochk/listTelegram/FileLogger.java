package com.lavochk.listTelegram;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

public class FileLogger {

    private final File logFile;

    public FileLogger(File dataFolder) {
        File logsFolder = new File(dataFolder, "logs");
        if (!logsFolder.exists()) {
            logsFolder.mkdirs();
        }
        this.logFile = new File(logsFolder, "actions.log");
    }

    public void log(String message) {
        try (FileWriter fw = new FileWriter(logFile, true);
             PrintWriter pw = new PrintWriter(fw)) {
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            pw.println("[" + timestamp + "] " + message);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
