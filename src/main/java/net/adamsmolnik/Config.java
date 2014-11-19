package net.adamsmolnik;

/**
 * @author ASmolnik
 *
 */
public interface Config {

    String getProperty(String key);

    String getProperty(String key, String defaultValue);

}
