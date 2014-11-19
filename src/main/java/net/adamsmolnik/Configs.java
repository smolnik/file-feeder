package net.adamsmolnik;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author ASmolnik
 *
 */
public enum Configs {

    INSTANCE;

    private static class ConfigImpl implements Config {

        private final Properties properties;

        private ConfigImpl(String configName) {
            properties = new Properties();
            try {
                properties.load(Files.newInputStream(Paths.get(Configs.class.getResource("/config/" + configName + ".properties").toURI())));
            } catch (Exception e) {
                throw new IllegalArgumentException(e);
            }

        }

        @Override
        public String getProperty(String key) {
            return properties.getProperty(key);
        }

        @Override
        public String getProperty(String key, String defaultValue) {
            return properties.getProperty(key, defaultValue);
        }
    }

    private ConcurrentMap<String, Config> configMap = new ConcurrentHashMap<>();

    public Config getMainConfig() {
        return compute("main");
    }

    public Config getConfig(String configName) {
        return compute(configName);
    }

    private Config compute(String configName) {
        return configMap.computeIfAbsent(configName, ConfigImpl::new);
    }
}
