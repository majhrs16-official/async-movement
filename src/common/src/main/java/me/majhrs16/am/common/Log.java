package me.majhrs16.am.common;

/**
 * Minimal logging abstraction so the common module does not depend on Minecraft
 * or on a particular logging backend.
 */
public interface Log {

    void info(String message);

    void warn(String message, Throwable error);

    void debug(String message);
}
