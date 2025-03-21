package dev.brickwedde.rearcamera;

import android.animation.Animator;
import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.annotation.NonNull;


import com.serenegiant.common.BaseActivity;
import com.serenegiant.usb.DeviceFilter;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.UVCCamera;
import com.serenegiant.usbcameracommon.UVCCameraHandler;
import com.serenegiant.utils.ViewAnimationHelper;
import com.serenegiant.widget.CameraViewInterface;

import java.util.List;

public final class MainActivity extends BaseActivity {
        private static final boolean DEBUG = true;	// TODO set false on release
        private static final String TAG = "MainActivity";

        /**
         * set true if you want to record movie using MediaSurfaceEncoder
         * (writing frame data into Surface camera from MediaCodec
         *  by almost same way as USBCameratest2)
         * set false if you want to record movie using MediaVideoEncoder
         */
        private static final boolean USE_SURFACE_ENCODER = false;

        /**
         * preview resolution(width)
         * if your camera does not support specific resolution and mode,
         * {@link UVCCamera#setPreviewSize(int, int, int)} throw exception
         */
        private static final int PREVIEW_WIDTH = 1920;
        /**
         * preview resolution(height)
         * if your camera does not support specific resolution and mode,
         * {@link UVCCamera#setPreviewSize(int, int, int)} throw exception
         */
        private static final int PREVIEW_HEIGHT = 1080;
        /**
         * preview mode
         * if your camera does not support specific resolution and mode,
         * {@link UVCCamera#setPreviewSize(int, int, int)} throw exception
         * 0:YUYV, other:MJPEG
         */
        private static final int PREVIEW_MODE = 1;

        protected static final int SETTINGS_HIDE_DELAY_MS = 2500;

        /**
         * for accessing USB
         */
        private USBMonitor mUSBMonitor;
        /**
         * Handler to execute camera related methods sequentially on private thread
         */
        private UVCCameraHandler mCameraHandler;
        /**
         * for camera preview display
         */
        private CameraViewInterface mUVCCameraView;

        /**
         * button for start/stop recording
         */
        private ImageButton mCaptureButton;

        private View mBrightnessButton, mContrastButton;
        private View mToolsLayout, mValueLayout;
        private SeekBar mSettingSeekbar;

        @Override
        protected void onCreate(final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            if (DEBUG) Log.v(TAG, "onCreate:");
            setContentView(R.layout.activity_main);
            mCaptureButton = (ImageButton)findViewById(R.id.capture_button);
            mCaptureButton.setOnClickListener(mOnClickListener);
            mCaptureButton.setVisibility(View.INVISIBLE);
            final View view = findViewById(R.id.camera_view);
            view.setOnLongClickListener(mOnLongClickListener);
            mUVCCameraView = (CameraViewInterface)view;
            mUVCCameraView.setAspectRatio(PREVIEW_WIDTH / (float)PREVIEW_HEIGHT);

            mBrightnessButton = findViewById(R.id.brightness_button);
            mBrightnessButton.setOnClickListener(mOnClickListener);
            mContrastButton = findViewById(R.id.contrast_button);
            mContrastButton.setOnClickListener(mOnClickListener);
            mSettingSeekbar = (SeekBar)findViewById(R.id.setting_seekbar);
            mSettingSeekbar.setOnSeekBarChangeListener(mOnSeekBarChangeListener);

            mToolsLayout = findViewById(R.id.tools_layout);
            mToolsLayout.setVisibility(View.INVISIBLE);
            mValueLayout = findViewById(R.id.value_layout);
            mValueLayout.setVisibility(View.INVISIBLE);

            mUSBMonitor = new USBMonitor(this, mOnDeviceConnectListener);
            mCameraHandler = UVCCameraHandler.createHandler(this, mUVCCameraView,
                    USE_SURFACE_ENCODER ? 0 : 1, PREVIEW_WIDTH, PREVIEW_HEIGHT, PREVIEW_MODE);
        }

        @Override
        protected void onStart() {
            super.onStart();
            if (DEBUG) Log.v(TAG, "onStart:");
            mUSBMonitor.register();
            if (mUVCCameraView != null)
                mUVCCameraView.onResume();
        }

        @Override
        protected void onStop() {
            if (DEBUG) Log.v(TAG, "onStop:");
            mCameraHandler.close();
            if (mUVCCameraView != null)
                mUVCCameraView.onPause();
            setCameraButton(false);

            if (mUSBMonitor.isRegistered()) {
                mUSBMonitor.unregister();
            }
            attached = false;

            super.onStop();
        }

        @Override
        public void onDestroy() {
            if (DEBUG) Log.v(TAG, "onDestroy:");
            if (mCameraHandler != null) {
                mCameraHandler.release();
                mCameraHandler = null;
            }
            if (mUSBMonitor != null) {
                mUSBMonitor.destroy();
                mUSBMonitor = null;
            }
            mUVCCameraView = null;
            mCaptureButton = null;
            super.onDestroy();
        }

        static boolean attached = false;
        void checkDevice() {
            if (attached) { return; };
            attached = true;
            final List<DeviceFilter> filter = DeviceFilter.getDeviceFilters(getBaseContext(), R.xml.device_filter);
            List<UsbDevice> l = mUSBMonitor.getDeviceList(filter);
            if (l.size() == 1) {
                UsbDevice device = l.get(0);
                Log.e("XXX", device.getDeviceName());
                mUSBMonitor.requestPermission(device);
            } else {
                attached = false;
            }
        }

        /**
         * event handler when click camera / capture button
         */
        private final View.OnClickListener mOnClickListener = new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                final var viewId = view.getId();

                if (viewId == R.id.capture_button) {
                    if (mCameraHandler.isOpened()) {
                        if (checkPermissionWriteExternalStorage() && checkPermissionAudio()) {
                            if (!mCameraHandler.isRecording()) {
                                mCaptureButton.setColorFilter(0xffff0000);    // turn red
                                mCameraHandler.startRecording();
                            } else {
                                mCaptureButton.setColorFilter(0);    // return to default color
                                mCameraHandler.stopRecording();
                            }
                        }
                    }
                } else if (viewId == R.id.brightness_button) {
                    showSettings(UVCCamera.PU_BRIGHTNESS);
                } else if (viewId == R.id.contrast_button) {
                    showSettings(UVCCamera.PU_CONTRAST);
                } else if (viewId == R.id.reset_button) {
                    resetSettings();
                }
            }
        };

        /**
         * capture still image when you long click on preview image(not on buttons)
         */
        private final View.OnLongClickListener mOnLongClickListener = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(final View view) {
                if (view.getId() != R.id.camera_view) {
                    return false;
                }

                if (mCameraHandler.isOpened()) {
                    if (checkPermissionWriteExternalStorage()) {
                        mCameraHandler.captureStill();
                    }
                    return true;
                }
                return false;
            }
        };

        private void setCameraButton(final boolean isOn) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (!isOn && (mCaptureButton != null)) {
                        mCaptureButton.setVisibility(View.INVISIBLE);
                    }
                }
            }, 0);
            updateItems();
        }

        private void startPreview() {
            final SurfaceTexture st = mUVCCameraView.getSurfaceTexture();
            mCameraHandler.startPreview(new Surface(st));
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    //mCaptureButton.setVisibility(View.VISIBLE);
                }
            });
            updateItems();
        }

        private final USBMonitor.OnDeviceConnectListener mOnDeviceConnectListener = new USBMonitor.OnDeviceConnectListener() {
            @Override
            public void onAttach(final UsbDevice device) {
                checkDevice();
            }

            @Override
            public void onConnect(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock, final boolean createNew) {
                if (DEBUG) Log.v(TAG, "onConnect:");
                mCameraHandler.open(ctrlBlock);
                startPreview();
                updateItems();
            }

            @Override
            public void onDisconnect(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock) {
                if (DEBUG) Log.v(TAG, "onDisconnect:");
                if (mCameraHandler != null) {
                    queueEvent(new Runnable() {
                        @Override
                        public void run() {
                            mCameraHandler.close();
                        }
                    }, 0);
                    setCameraButton(false);
                    updateItems();
                }
            }
            @Override
            public void onDettach(final UsbDevice device) {
                Toast.makeText(MainActivity.this, "USB_DEVICE_DETACHED", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onCancel(final UsbDevice device) {
                setCameraButton(false);
            }
        };

        //================================================================================
        private boolean isActive() {
            return mCameraHandler != null && mCameraHandler.isOpened();
        }

        private boolean checkSupportFlag(final int flag) {
            return mCameraHandler != null && mCameraHandler.checkSupportFlag(flag);
        }

        private int getValue(final int flag) {
            return mCameraHandler != null ? mCameraHandler.getValue(flag) : 0;
        }

        private int setValue(final int flag, final int value) {
            return mCameraHandler != null ? mCameraHandler.setValue(flag, value) : 0;
        }

        private int resetValue(final int flag) {
            return mCameraHandler != null ? mCameraHandler.resetValue(flag) : 0;
        }

        private void updateItems() {
            runOnUiThread(mUpdateItemsOnUITask, 100);
        }

        private final Runnable mUpdateItemsOnUITask = new Runnable() {
            @Override
            public void run() {
                if (isFinishing()) return;
                final int visible_active = isActive() ? View.VISIBLE : View.INVISIBLE;
                mToolsLayout.setVisibility(visible_active);
                mBrightnessButton.setVisibility(
                        checkSupportFlag(UVCCamera.PU_BRIGHTNESS)
                                ? visible_active : View.INVISIBLE);
                mContrastButton.setVisibility(
                        checkSupportFlag(UVCCamera.PU_CONTRAST)
                                ? visible_active : View.INVISIBLE);
            }
        };

        private int mSettingMode = -1;
        /**
         * 設定画面を表示
         * @param mode
         */
        private final void showSettings(final int mode) {
            if (DEBUG) Log.v(TAG, String.format("showSettings:%08x", mode));
            hideSetting(false);
            if (isActive()) {
                switch (mode) {
                    case UVCCamera.PU_BRIGHTNESS:
                    case UVCCamera.PU_CONTRAST:
                        mSettingMode = mode;
                        mSettingSeekbar.setProgress(getValue(mode));
                        ViewAnimationHelper.fadeIn(mValueLayout, -1, 0, mViewAnimationListener);
                        break;
                }
            }
        }

        private void resetSettings() {
            if (isActive()) {
                switch (mSettingMode) {
                    case UVCCamera.PU_BRIGHTNESS:
                    case UVCCamera.PU_CONTRAST:
                        mSettingSeekbar.setProgress(resetValue(mSettingMode));
                        break;
                }
            }
            mSettingMode = -1;
            ViewAnimationHelper.fadeOut(mValueLayout, -1, 0, mViewAnimationListener);
        }

        /**
         * 設定画面を非表示にする
         * @param fadeOut trueならばフェードアウトさせる, falseなら即座に非表示にする
         */
        protected final void hideSetting(final boolean fadeOut) {
            removeFromUiThread(mSettingHideTask);
            if (fadeOut) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ViewAnimationHelper.fadeOut(mValueLayout, -1, 0, mViewAnimationListener);
                    }
                }, 0);
            } else {
                try {
                    mValueLayout.setVisibility(View.GONE);
                } catch (final Exception e) {
                    // ignore
                }
                mSettingMode = -1;
            }
        }

        protected final Runnable mSettingHideTask = new Runnable() {
            @Override
            public void run() {
                hideSetting(true);
            }
        };

        /**
         * 設定値変更用のシークバーのコールバックリスナー
         */
        private final SeekBar.OnSeekBarChangeListener mOnSeekBarChangeListener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(final SeekBar seekBar, final int progress, final boolean fromUser) {
                // 設定が変更された時はシークバーの非表示までの時間を延長する
                if (fromUser) {
                    runOnUiThread(mSettingHideTask, SETTINGS_HIDE_DELAY_MS);
                }
            }

            @Override
            public void onStartTrackingTouch(final SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(final SeekBar seekBar) {
                // シークバーにタッチして値を変更した時はonProgressChangedへ
                // 行かないみたいなのでここでも非表示までの時間を延長する
                runOnUiThread(mSettingHideTask, SETTINGS_HIDE_DELAY_MS);
                if (isActive() && checkSupportFlag(mSettingMode)) {
                    switch (mSettingMode) {
                        case UVCCamera.PU_BRIGHTNESS:
                        case UVCCamera.PU_CONTRAST:
                            setValue(mSettingMode, seekBar.getProgress());
                            break;
                    }
                }	// if (active)
            }
        };

        private final ViewAnimationHelper.ViewAnimationListener
                mViewAnimationListener = new ViewAnimationHelper.ViewAnimationListener() {
            @Override
            public void onAnimationStart(@NonNull final Animator animator, @NonNull final View target, final int animationType) {
//			if (DEBUG) Log.v(TAG, "onAnimationStart:");
            }

            @Override
            public void onAnimationEnd(@NonNull final Animator animator, @NonNull final View target, final int animationType) {
                final int id = target.getId();
                switch (animationType) {
                    case ViewAnimationHelper.ANIMATION_FADE_IN:
                    case ViewAnimationHelper.ANIMATION_FADE_OUT:
                    {
                        final boolean fadeIn = animationType == ViewAnimationHelper.ANIMATION_FADE_IN;
                        if (id == R.id.value_layout) {
                            if (fadeIn) {
                                runOnUiThread(mSettingHideTask, SETTINGS_HIDE_DELAY_MS);
                            } else {
                                mValueLayout.setVisibility(View.GONE);
                                mSettingMode = -1;
                            }
                        } else if (!fadeIn) {
//					target.setVisibility(View.GONE);
                        }
                        break;
                    }
                }
            }

            @Override
            public void onAnimationCancel(@NonNull final Animator animator, @NonNull final View target, final int animationType) {
//			if (DEBUG) Log.v(TAG, "onAnimationStart:");
            }
        };

    }