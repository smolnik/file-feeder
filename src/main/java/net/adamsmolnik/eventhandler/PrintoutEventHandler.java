package net.adamsmolnik.eventhandler;

import java.nio.file.Path;
import java.nio.file.WatchEvent;

/**
 * @author ASmolnik
 *
 */
public class PrintoutEventHandler implements EventHandler {

    @Override
    public void handle(WatchEvent<Path> watchEvent, Path eventPath) {
        System.out.println("PrintoutEventHandler: " + watchEvent + ", " + eventPath);
    }

}
