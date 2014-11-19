package net.adamsmolnik;

import static java.nio.file.Files.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

/**
 * @author ASmolnik
 *
 */
public class FileProducer {

    public static void main(String[] args) throws Exception {
        Path root = Paths.get("C:/temp/test");
        int numberOfFilesToProduce = 10000;
        for (int i = 0; i < numberOfFilesToProduce; i++) {
            String name = String.valueOf(i);
            Path subDir = createDirectories(Paths.get(root.toString(), name, "intern", "project"));
            write(createFile(Paths.get(subDir.toString(), name + ".xml")), Arrays.asList(name));
        }
    }

}
