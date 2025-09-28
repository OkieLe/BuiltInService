package ai.brain.lxing.service;

import android.content.Context;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.hardware.display.VirtualDisplayConfig;
import android.view.Gravity;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.annotation.NonNull;

import com.android.server.SystemService;
import com.android.server.utils.Slogf;

public class BuiltInService extends SystemService {
    private static final String TAG = "BuiltInService";
    private WindowManager mWindowManager;
    private DisplayManager mDisplayManager;

    public BuiltInService(Context context) {
        super(context);
    }

    @Override
    public void onStart() {
        Slogf.d(TAG, "onStart");
    }

    @Override
    public void onBootPhase(int phase) {
        super.onBootPhase(phase);
        Slogf.d(TAG, "onBootPhase " + phase);
        if (phase >= PHASE_BOOT_COMPLETED) {
            if (mWindowManager == null) {
                mWindowManager = getContext().getSystemService(WindowManager.class);
            }
            if (mDisplayManager == null) {
                mDisplayManager = getContext().getSystemService(DisplayManager.class);
            }
        }
    }

    @Override
    public void onUserUnlocked(@NonNull TargetUser user) {
        super.onUserUnlocked(user);
        Slogf.d(TAG, "onUserUnlocked " + user.getUserIdentifier());
        if (user.getUserIdentifier() == 0) {
            addWindowForDisplay();
        }
    }
    void addWindowForDisplay() {
        SurfaceView surfaceView = new SurfaceView(getContext());
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
                addVirtualDisplay(holder.getSurface());
            }

            @Override
            public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
                Slogf.i(TAG, "surfaceChanged");
            }

            @Override
            public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
                Slogf.i(TAG, "surfaceDestroyed");
            }
        });
        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams(
                720, 1600,
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
        layoutParams.gravity = Gravity.START | Gravity.BOTTOM;
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
                "virtual-display", 720, 1600, 420
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
