package me.majhrs16.am.shared;

import me.majhrs16.am.common.Log;
import org.slf4j.Logger;

public final class Slf4jLog implements Log {

    private final Logger logger;

    public Slf4jLog(Logger logger) {
        this.logger = logger;
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
