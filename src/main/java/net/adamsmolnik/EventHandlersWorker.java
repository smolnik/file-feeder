package net.adamsmolnik;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author ASmolnik
 *
 */
public enum EventHandlersWorker implements AutoCloseable {

    INSTANCE;

    private final ExecutorService es;

    private EventHandlersWorker() {
        this.es = Executors.newFixedThreadPool(Integer.valueOf(Configs.INSTANCE.getMainConfig().getProperty("maxEventHandlerWorkersNumber", "4")));
    }

    public void submit(Callable<Void> task) {
        es.submit(task);
    }

    public void close() {
        es.shutdown();;
    }

}
