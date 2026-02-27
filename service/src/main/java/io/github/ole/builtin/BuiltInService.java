package io.github.ole.builtin;

import android.annotation.NonNull;
import android.content.Context;
import android.os.Binder;
import android.os.Bundle;
import android.os.RemoteCallbackList;
import android.os.RemoteException;

import com.android.server.SystemService;
import com.android.server.utils.Slogf;

import java.util.HashMap;

public class BuiltInService extends SystemService {
    static final String TAG = "BuiltInService";
    private static final String SERVICE_NAME = "builtin";
    private final HashMap<String, Controller> mControllers = new HashMap<>();

    private final IBuiltIn mService = new IBuiltIn.Stub() {
        private final RemoteCallbackList<IBuiltInCallback> remoteCallbackList = new RemoteCallbackList<>();

        @Override
        public void addCallback(IBuiltInCallback callback) {
            remoteCallbackList.register(callback);
        }

        @Override
        public void removeCallback(IBuiltInCallback callback) {
            remoteCallbackList.unregister(callback);
        }

        @Override
        public boolean performAction(String controller, String action, Bundle args) throws RemoteException {
            Binder.clearCallingIdentity();
            Controller targetController = mControllers.get(controller);
            if (targetController != null) {
                if (!targetController.supportsAction(action)) {
                    throw new RemoteException("Not supported action: " + action);
                }
                return targetController.apply(action, args);
            } else {
                throw new RemoteException("Unknown controller " + controller);
            }
        }
    };

    public BuiltInService(Context context) {
        super(context);
    }

    @Override
    public void onStart() {
        Slogf.d(TAG, "onStart");
        mControllers.put(BuiltInContracts.VirtualDisplay.ID, new VirtualDisplayController(getContext()));
        publishBinderService(SERVICE_NAME, mService.asBinder());
    }

    @Override
    public void onBootPhase(int phase) {
        super.onBootPhase(phase);
        Slogf.d(TAG, "onBootPhase " + phase);
        if (phase == PHASE_BOOT_COMPLETED) {
            mControllers.values().forEach(Controller::onStart);
        }
    }

    @Override
    public void onUserUnlocked(@NonNull TargetUser user) {
        super.onUserUnlocked(user);
        Slogf.d(TAG, "onUserUnlocked " + user.getUserIdentifier());
        for (Controller controller : mControllers.values()) {
            controller.onUserStarted(user.getUserIdentifier());
        }
    }

    @Override
    public void onUserStopped(@NonNull TargetUser user) {
        Slogf.d(TAG, "onUserStopped " + user.getUserIdentifier());
        for (Controller controller : mControllers.values()) {
            controller.onUserStopped(user.getUserIdentifier());
        }
        super.onUserStopped(user);
    }
}
