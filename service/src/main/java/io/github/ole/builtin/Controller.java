package io.github.ole.builtin;

import android.os.Bundle;

public interface Controller {
    void onStart();
    default void onUserStarted(int userId) {}
    default void onUserStopped(int userId) {}
    default boolean supportsAction(String action) {
        return false;
    }
    default boolean apply(String action, Bundle args) {
        return true;
    }
}
