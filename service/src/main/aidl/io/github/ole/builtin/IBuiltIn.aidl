package io.github.ole.builtin;

import android.os.Bundle;
import io.github.ole.builtin.IBuiltInCallback;

interface IBuiltIn {
    oneway void addCallback(in IBuiltInCallback callback);
    oneway void removeCallback(in IBuiltInCallback callback);
    boolean performAction(String controller, String action, in Bundle args);
}
