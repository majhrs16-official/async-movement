package me.majhrs16.am.common;

/**
 * Decides whether it is safe to push input state into the game right now.
 */
public interface PollGuard {

    boolean shouldPoll(boolean onlyWhenFocused);
}
