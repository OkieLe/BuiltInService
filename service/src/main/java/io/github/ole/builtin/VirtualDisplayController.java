package io.github.ole.builtin;

import static android.view.Display.DEFAULT_DISPLAY;

import android.annotation.NonNull;
import android.content.Context;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.hardware.display.VirtualDisplayConfig;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
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
            BuiltInContracts.VirtualDisplay.ACTION_ATTACH,
            BuiltInContracts.VirtualDisplay.ACTION_DETACH
    );
    private final Context mContext;
    private final Handler mHandler;

    private WindowManager mWindowManager;
    private DisplayManager mDisplayManager;

    private SurfaceControl mDisplayControl;
    private SurfaceControl mMirrorSurface;

    VirtualDisplayController(Context context) {
        mContext = context;
        mHandler = UiThread.getHandler();
    }

    @Override
    public void onStart() {
        mWindowManager = mContext.getSystemService(WindowManager.class);
        mDisplayManager = mContext.getSystemService(DisplayManager.class);
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
            case BuiltInContracts.VirtualDisplay.ACTION_ATTACH: {
                SurfaceControl parent = args.getParcelable(
                        BuiltInContracts.VirtualDisplay.ARG_PARENT_SC, SurfaceControl.class);
                int displayId = args.getInt(
                        BuiltInContracts.VirtualDisplay.ARG_DISPLAY_ID, DEFAULT_DISPLAY);
                attachEmbeddedSurfaceControl(parent, displayId);
                return true;
            }
            case BuiltInContracts.VirtualDisplay.ACTION_DETACH: {
                tearDownEmbeddedSurfaceControl();
                return true;
            }
            default: {
                Slogf.d(TAG, "Noop for action " + action);
                return true;
            }
        }
    }

    private void attachEmbeddedSurfaceControl(SurfaceControl parentSc, int displayId) {
        mHandler.post(() -> {
            if (displayId != DEFAULT_DISPLAY && mDisplayControl != null) {
                mMirrorSurface = SurfaceControl.mirrorSurface(mDisplayControl);
                Log.i(TAG, "Mirror of display surface " + mMirrorSurface);
                if (mMirrorSurface != null && mMirrorSurface.isValid()) {
                    new SurfaceControl.Transaction().show(mMirrorSurface)
                            .reparent(mMirrorSurface, parentSc)
                            .apply();
                }
            }
        });
    }

    private void tearDownEmbeddedSurfaceControl() {
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
                addVirtualDisplay(holder.getSurface());
            }

            @Override
            public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
                Slogf.i(TAG, "surfaceChanged");
            }

            @Override
            public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
                Slogf.i(TAG, "surfaceDestroyed");
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
        layoutParams.x = -800;
        layoutParams.gravity = Gravity.END | Gravity.BOTTOM;
        mWindowManager.addView(surfaceView, layoutParams);
    }

    private void addVirtualDisplay(Surface surface) {
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

        int flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                | DisplayManager.VIRTUAL_DISPLAY_FLAG_ROTATES_WITH_CONTENT
                | DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED
                | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_DISPLAY_GROUP
                | DisplayManager.VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH
                | DisplayManager.VIRTUAL_DISPLAY_FLAG_ALWAYS_UNLOCKED
                | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_FOCUS
                | DisplayManager.VIRTUAL_DISPLAY_FLAG_STEAL_TOP_FOCUS_DISABLED
                | DisplayManager.VIRTUAL_DISPLAY_FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS;

        Slogf.d(TAG, "Creating display 'virtual-display'");
        VirtualDisplayConfig config = new VirtualDisplayConfig.Builder(
                "virtual-display", 840, 1200, 420
        )
                .setSurface(surface)
                .setFlags(flags)
                .build();
        VirtualDisplay virtualDisplay = mDisplayManager.createVirtualDisplay(config);

        if (virtualDisplay == null) {
            Slogf.i(TAG, "Failed to create display");
        } else {
            int displayId = virtualDisplay.getDisplay().getDisplayId();
            Slogf.i(TAG, "Created display: " + displayId);
        }
    }
}
