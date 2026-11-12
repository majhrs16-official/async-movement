package me.majhrs16.am.shared;

import me.majhrs16.am.common.Log;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Logging adapter backed by Log4j, which Minecraft always provides. This avoids
 * a hard runtime dependency on SLF4J, since some launchers do not put it on the
 * game classpath.
 */
public final class Log4jLog implements Log {

    private final Logger logger;

    public Log4jLog(String name) {
        this.logger = LogManager.getLogger(name);
    }

    @Override
    public void info(String message) {
        logger.info(message);
    }

    @Override
    public void warn(String message, Throwable error) {
        if (error == null) {
            logger.warn(message);
        } else {
            logger.warn(message, error);
        }
    }

    @Override
    public void debug(String message) {
        logger.debug(message);
    }
}
