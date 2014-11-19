package net.adamsmolnik;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * @author ASmolnik
 *
 */
public enum Log {

    LOG;

    private final Handler fileHandler;

    private final ConcurrentSkipListSet<String> registeredClasses = new ConcurrentSkipListSet<>();

    private Log() {
        try {
            Path logsDir = Paths.get(new File("").getAbsolutePath(), "logs");
            if (!Files.exists(logsDir)) {
                Files.createDirectories(logsDir);
            }
            this.fileHandler = new FileHandler(logsDir.toAbsolutePath().toString() + "/watcher%u.%g.log");
            this.fileHandler.setFormatter(new SimpleFormatter());
        } catch (SecurityException | IOException e) {
            e.printStackTrace();
            throw new IllegalStateException(e);
        }
    }

    public Logger getLog(Class<?> clazz) {
        String className = clazz.getName();
        Logger logger = Logger.getLogger(className);
        if (registeredClasses.add(className)) {
            logger.addHandler(fileHandler);
        }
        return logger;
    }

    public void severe(Throwable throwable, Logger logger) {
        StringWriter sw = new StringWriter();
        throwable.printStackTrace(new PrintWriter(sw));
        logger.severe(sw.toString());
    }

}
