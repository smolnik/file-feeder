package net.adamsmolnik;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import net.adamsmolnik.eventhandler.EventHandler;

/**
 * @author ASmolnik
 *
 */
public class WatchDir implements AutoCloseable {

    private final class Worker extends Thread implements AutoCloseable {

        @Override
        public void run() {
            while (!isInterrupted()) {
                WatchKey key;
                try {
                    key = watcher.take();
                } catch (InterruptedException x) {
                    return;
                }

                Path dir = keys.get(key);
                if (dir == null) {
                    logger.info(String.format("WatchKey not recognized for key %s ", key));
                    continue;
                }

                for (WatchEvent<?> event : key.pollEvents()) {
                    Kind<?> kind = event.kind();

                    if (kind == OVERFLOW) {
                        logger.severe("Beware of OVERFLOW!!");
                        continue;
                    }

                    WatchEvent<Path> ev = cast(event);
                    Path name = ev.context();
                    Path pathResolved = dir.resolve(name);

                    logger.info(String.format("%s: %s\n", event.kind().name(), pathResolved));

                    // if directory is created, and watching recursively, then
                    // register it and its sub-directories
                    if (recursive && (kind == ENTRY_CREATE)) {
                        try {
                            if (Files.isDirectory(pathResolved, NOFOLLOW_LINKS)) {
                                registerAll(pathResolved);
                            } else if (Files.isRegularFile(pathResolved)) {
                                ehWorker.submit(() -> {
                                    try {
                                        eventHandlers.forEach(h -> h.handle(ev, pathResolved));
                                    } catch (Exception ex) {
                                        Log.LOG.severe(ex, logger);
                                    }
                                    return null;
                                });
                            }
                        } catch (IOException ex) {
                            Log.LOG.severe(ex, logger);
                        }
                    }
                }

                // reset key and remove from set if directory no longer accessible
                boolean valid = key.reset();
                if (!valid) {
                    keys.remove(key);

                    // all directories are inaccessible
                    if (keys.isEmpty()) {
                        break;
                    }
                }
            }
        }

        @Override
        public void close() {
            interrupt();
        };
    };

    private final static Logger logger = Log.LOG.getLog(WatchDir.class);

    private final static Map<String, WatchDir> WATCHDIR_MAP = new ConcurrentHashMap<>();

    private final EventHandlersWorker ehWorker = EventHandlersWorker.INSTANCE;

    private final WatchService watcher;

    private final Map<WatchKey, Path> keys;

    private final boolean recursive;

    private final Set<EventHandler> eventHandlers;

    private final Worker processEventsWorker = new Worker();

    @SuppressWarnings("unchecked")
    private static <T>WatchEvent<T> cast(WatchEvent<?> event) {
        return (WatchEvent<T>) event;
    }

    /**
     * Register the given directory with the WatchService
     */
    private void register(Path dir) throws IOException {
        WatchKey key = dir.register(watcher, ENTRY_CREATE);
        keys.put(key, dir);
    }

    /**
     * Register the given directory, and all its sub-directories, with the
     * WatchService.
     */
    private void registerAll(final Path start) throws IOException {
        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                register(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private WatchDir() throws IOException {
        this.watcher = FileSystems.getDefault().newWatchService();
        this.keys = new ConcurrentHashMap<>();
        Config config = Configs.INSTANCE.getMainConfig();
        this.recursive = Boolean.parseBoolean(config.getProperty("recursive", Boolean.TRUE.toString()));
        Path dirToWatch = Paths.get(config.getProperty("dirToWatch"));
        String eventHandlersAsString = config.getProperty("eventHandlerClasses", "net.adansmolnik.eventhandler.PrintoutEventHandler");
        this.eventHandlers = getEventHandlers(eventHandlersAsString);
        if (recursive) {
            logger.info(String.format("Scanning %s ...\n", dirToWatch));
            registerAll(dirToWatch);
            logger.info("Done.");
        } else {
            register(dirToWatch);
        }
        logger.info(String.format("only just registered %d dirs", keys.size()));
    }

    private Set<EventHandler> getEventHandlers(String eventHandlersAsString) {
        Set<EventHandler> eventHandlers = Arrays.asList(eventHandlersAsString.split(",")).stream().map(className -> {
            try {
                String handlerClass = className.trim();
                logger.info(String.format("handlerClass to load: %s", handlerClass));
                return (EventHandler) Class.forName(handlerClass).newInstance();
            } catch (Exception e) {
                throw new IllegalArgumentException(e);
            }
        }).collect(Collectors.toSet());
        return eventHandlers;
    }

    private void processEvents() {
        processEventsWorker.start();
        try {
            processEventsWorker.join();
        } catch (InterruptedException e) {
            // Ignored deliberately  
        }
    }

    @Override
    public void close() {
        processEventsWorker.close();
        ehWorker.close();
    }

    public static void main(String[] args) {
        try (WatchDir wd = new WatchDir()) {
            WATCHDIR_MAP.put(getProcessId(), wd);
            wd.processEvents();
        } catch (Exception e) {
            Log.LOG.severe(e, logger);
            throw new RuntimeException(e);
        }
    }

    public static void start(String[] args) {
        String processId = getProcessId();
        logger.info("WatchDir Service is about to start (" + processId + ")");
        main(args);
    }

    public static void stop(String[] args) {
        String processId = getProcessId();
        WATCHDIR_MAP.get(processId).close();
        logger.info("WatchDir Service stopped (" + processId + ")");
    }

    public static String getProcessId() {
        return ManagementFactory.getRuntimeMXBean().getName();
    }

}
