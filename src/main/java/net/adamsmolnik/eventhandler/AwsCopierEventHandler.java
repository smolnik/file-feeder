package net.adamsmolnik.eventhandler;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.logging.Logger;
import net.adamsmolnik.Config;
import net.adamsmolnik.Configs;
import net.adamsmolnik.Log;
import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;

public class AwsCopierEventHandler implements EventHandler {

    private final static Logger logger = Log.LOG.getLog(AwsCopierEventHandler.class);

    private static final String SLASH = "/";

    private final AmazonS3Client s3Client;

    private final String bucketName;

    private final String extDir;

    private final int retryAttemptsNumber;

    public AwsCopierEventHandler() {
        try {
            final Config config = Configs.INSTANCE.getConfig("awsCopierEventHandler");
            AWSCredentials awsCredentials = new AWSCredentials() {
                @Override
                public String getAWSSecretKey() {
                    return config.getProperty("awsSecretKey");
                }

                @Override
                public String getAWSAccessKeyId() {
                    return config.getProperty("awsAccessKeyId");
                }
            };

            this.s3Client = new AmazonS3Client(awsCredentials);
            this.bucketName = config.getProperty("bucketName");
            this.extDir = config.getProperty("extDir");
            this.retryAttemptsNumber = Integer.valueOf(config.getProperty("retryAttemptsNumber", "3"));
        } catch (Exception e) {
            logger.severe(e.getLocalizedMessage());
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void handle(WatchEvent<Path> watchEvent, Path eventPath) {
        String destPath = extDir + "/" + LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE) + "/" + eventPath.getFileName();
        StringBuilder sb = new StringBuilder();
        eventPath.forEach(p -> sb.append(SLASH).append(p));
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.addUserMetadata("originFilePath", sb.toString());
        Path absSrcPath = eventPath.toAbsolutePath();
        try {
            sendToS3(destPath, absSrcPath, metadata);
        } catch (AmazonClientException acex) {
            logger.severe(acex.getLocalizedMessage());
            Throwable cause = acex.getCause();
            logger.severe(() -> cause != null ? cause.getLocalizedMessage() : "cause in null");
            if (cause instanceof FileNotFoundException) {
                Object internalLock = new Object();
                logger.warning("The failover attempt will be applied through retries & exponential backoffs stategy in place for " + absSrcPath);
                failoverLoop: for (int i = 1; i <= retryAttemptsNumber; i++) {
                    synchronized (internalLock) {
                        try {
                            internalLock.wait((long) (1000 * (5 * Math.exp(i - 1))));
                            logger.warning("Before attempt " + i + ". for " + absSrcPath);
                            sendToS3(destPath, absSrcPath, metadata);
                            logger.info("The failover attempt succeeded after attempt " + i + ". for " + absSrcPath);
                            break failoverLoop;
                        } catch (Exception e) {
                            logger.warning("Exception raised after failover attempt " + i + ". for " + absSrcPath);
                            if (i == retryAttemptsNumber) {
                                throw new RuntimeException("The failover attemps failed finished after " + i + " tries for " + absSrcPath);
                            }
                        }
                    }
                }
            } else {
                throw acex;
            }
        }
        try {
            Files.deleteIfExists(absSrcPath);
        } catch (IOException e) {
            logger.severe(e.getLocalizedMessage());
        }
    }

    private void sendToS3(String destPath, Path srcPath, ObjectMetadata metadata) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, destPath, srcPath.toFile()).withMetadata(metadata);
        s3Client.putObject(putObjectRequest);
    }

}
