package net.adamsmolnik.eventhandler;

import java.nio.file.Path;
import java.nio.file.WatchEvent;

/**
 * @author ASmolnik
 *
 */
@FunctionalInterface
public interface EventHandler {

    void handle(WatchEvent<Path> watchEvent, Path eventPath);

}
