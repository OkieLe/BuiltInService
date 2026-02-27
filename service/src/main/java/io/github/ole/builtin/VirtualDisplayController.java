package io.github.ole.builtin;

import android.annotation.NonNull;
import android.content.Context;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.hardware.display.VirtualDisplayConfig;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.view.WindowManager;

import com.android.server.UiThread;
import com.android.server.utils.Slogf;

import java.util.List;

public final class VirtualDisplayController implements Controller {
    private static final String TAG = BuiltInService.TAG;
    private static final List<String> ACTIONS = List.of(
            BuiltInContracts.VirtualDisplay.ACTION_CREATE,
            BuiltInContracts.VirtualDisplay.ACTION_DESTROY,
            BuiltInContracts.VirtualDisplay.ACTION_ATTACH,
            BuiltInContracts.VirtualDisplay.ACTION_DETACH,
            BuiltInContracts.VirtualDisplay.ACTION_REPARENT,
            BuiltInContracts.VirtualDisplay.ACTION_RESET
    );
    private final Context mContext;
    private final Handler mHandler;

    private WindowManager mWindowManager;
    private DisplayManager mDisplayManager;

    private SurfaceControl mDisplayControl;
    private SurfaceHolder mDisplayHolder;
    private SurfaceControl mMirrorSurface;
    private VirtualDisplay mVirtualDisplay;

    VirtualDisplayController(Context context) {
        mContext = context;
        mHandler = UiThread.getHandler();
    }

    @Override
    public void onStart() {
        mWindowManager = mContext.getSystemService(WindowManager.class);
        mDisplayManager = mContext.getSystemService(DisplayManager.class);
        DisplayManager.DisplayListener listener = new DisplayManager.DisplayListener() {
            @Override
            public void onDisplayAdded(int displayId) {
                Slogf.d(TAG, "onDisplayAdded(" + displayId + ")");
            }

            @Override
            public void onDisplayRemoved(int displayId) {
                Slogf.v(TAG, "onDisplayRemoved(" + displayId + ")");
            }

            @Override
            public void onDisplayChanged(int displayId) {
                Slogf.v(TAG, "onDisplayChanged(" + displayId + ")");
            }
        };

        Slogf.v(TAG, "Registering listener " + listener);
        mDisplayManager.registerDisplayListener(listener, null);
    }

    @Override
    public void onUserStarted(int userId) {
        if (userId == 0) {
            addWindowForDisplay();
        }
    }

    @Override
    public boolean supportsAction(String action) {
        return ACTIONS.contains(action);
    }

    @Override
    public boolean apply(String action, Bundle args) {
        switch (action) {
            case BuiltInContracts.VirtualDisplay.ACTION_CREATE: {
                if (mVirtualDisplay != null) {
                    return false;
                }
                addVirtualDisplay(mDisplayHolder.getSurface());
                return mVirtualDisplay != null;
            }
            case BuiltInContracts.VirtualDisplay.ACTION_DESTROY: {
                destroyVirtualDisplay();
                return true;
            }
            case BuiltInContracts.VirtualDisplay.ACTION_ATTACH: {
                SurfaceControl parent = args.getParcelable(
                        BuiltInContracts.VirtualDisplay.ARG_PARENT_SC, SurfaceControl.class);
                return attachDisplayMirror(parent);
            }
            case BuiltInContracts.VirtualDisplay.ACTION_DETACH: {
                detachDisplayMirror();
                return true;
            }
            case BuiltInContracts.VirtualDisplay.ACTION_REPARENT: {
                Surface parent = args.getParcelable(
                        BuiltInContracts.VirtualDisplay.ARG_PARENT_SC, Surface.class);
                return reparentVirtualDisplay(parent);
            }
            case BuiltInContracts.VirtualDisplay.ACTION_RESET: {
                if (mDisplayHolder == null) {
                    return false;
                }
                return reparentVirtualDisplay(mDisplayHolder.getSurface());
            }
            default: {
                Slogf.d(TAG, "Noop for action " + action);
                return true;
            }
        }
    }

    private boolean attachDisplayMirror(SurfaceControl parentSc) {
        if (mDisplayHolder == null || mDisplayControl == null) {
            Slogf.w(TAG, "No surface view to create display");
            return false;
        }
        if (mVirtualDisplay != null) {
            mHandler.post(() -> {
                mMirrorSurface = SurfaceControl.mirrorSurface(mDisplayControl);
                Slogf.i(TAG, "Mirror of display surface " + mMirrorSurface);
                if (mMirrorSurface != null && mMirrorSurface.isValid()) {
                    new SurfaceControl.Transaction().show(mMirrorSurface)
                            .reparent(mMirrorSurface, parentSc)
                            .apply();
                }
            });
            return true;
        }
        return false;
    }

    private void detachDisplayMirror() {
        if (mMirrorSurface != null) {
            new SurfaceControl.Transaction().remove(mMirrorSurface).apply();
            mMirrorSurface = null;
        }
    }

    private void addWindowForDisplay() {
        SurfaceView surfaceView = new SurfaceView(mContext);
        surfaceView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        surfaceView.setPadding(0, 0, 0, 0);
        // add window with a SurfaceView
        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(@NonNull SurfaceHolder holder) {
                Slogf.i(TAG, "surfaceCreated");
                mDisplayControl = surfaceView.getSurfaceControl();
                mDisplayHolder = holder;
            }

            @Override
            public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
                Slogf.i(TAG, "surfaceChanged");
            }

            @Override
            public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
                Slogf.i(TAG, "surfaceDestroyed");
                destroyVirtualDisplay();
                mDisplayHolder = null;
                mDisplayControl = null;
            }
        });
        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams(
                840, 1200,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                        | WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.RGBA_8888
        );
        layoutParams.privateFlags = WindowManager.LayoutParams.SYSTEM_FLAG_SHOW_FOR_ALL_USERS;
        layoutParams.setTitle("display-container");
        layoutParams.x = -640;
        layoutParams.gravity = Gravity.END | Gravity.BOTTOM;
        mWindowManager.addView(surfaceView, layoutParams);
    }

    private void addVirtualDisplay(Surface surface) {
        int flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                | DisplayManager.VIRTUAL_DISPLAY_FLAG_ROTATES_WITH_CONTENT
                | DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED
                | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_DISPLAY_GROUP
                | DisplayManager.VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH
                | DisplayManager.VIRTUAL_DISPLAY_FLAG_ALWAYS_UNLOCKED
                | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_FOCUS
                | DisplayManager.VIRTUAL_DISPLAY_FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS;

        Slogf.d(TAG, "Creating display 'virtual-display'");
        VirtualDisplayConfig config = new VirtualDisplayConfig.Builder(
                "virtual-display", 840, 1200, 420)
                .setSurface(surface)
                .setFlags(flags)
                .build();
        mVirtualDisplay = mDisplayManager.createVirtualDisplay(config);

        if (mVirtualDisplay == null) {
            Slogf.i(TAG, "Failed to create display");
        } else {
            int displayId = mVirtualDisplay.getDisplay().getDisplayId();
            Slogf.i(TAG, "Created display: " + displayId);
        }
    }

    private boolean reparentVirtualDisplay(Surface surface) {
        if (mVirtualDisplay != null) {
            mVirtualDisplay.setSurface(surface);
            return true;
        }
        return false;
    }

    private void destroyVirtualDisplay() {
        if (mVirtualDisplay != null) {
            mVirtualDisplay.setSurface(null);
            mVirtualDisplay.release();
            mVirtualDisplay = null;
        }
    }
}
