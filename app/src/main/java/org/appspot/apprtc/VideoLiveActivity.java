/*
 *  Copyright 2015 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.appspot.apprtc;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.Toast;

import org.appspot.apprtc.AppRTCAudioManager.AudioDevice;
import org.appspot.apprtc.AppRTCAudioManager.AudioManagerEvents;
import org.appspot.apprtc.PeerConnectionClient2.DataChannelParameters;
import org.appspot.apprtc.PeerConnectionClient2.PeerConnectionParameters;
import org.appspot.apprtc.janus.JanusCommon.JanusConnectionParameters;
import org.appspot.apprtc.janus.JanusRTCEvents2;
import org.json.JSONObject;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.EglBase;
import org.webrtc.FileVideoCapturer;
import org.webrtc.IceCandidate;
import org.webrtc.Logging;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RendererCommon.ScalingType;
import org.webrtc.ScreenCapturerAndroid;
import org.webrtc.SessionDescription;
import org.webrtc.StatsReport;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Set;
import java.util.Vector;

import javax.annotation.Nullable;

import static org.appspot.apprtc.janus.JanusUtils.convertJsonToSdp;

/**
 * Activity for JanusVideoRoom setup, call waiting and call view.
 */
public class VideoLiveActivity extends Activity implements PeerConnectionClient2.PeerConnectionEvents,
        CallFragment.OnCallEvents,
        JanusRTCEvents2{
    private static final String TAG = "VideoRoomActivity";

    public static final String EXTRA_SERVERADDR = "org.appspot.apprtc.ROOMURL";
    public static final String EXTRA_ROOMID = "org.appspot.apprtc.ROOMID";
    public static final String EXTRA_USERID = "org.appspot.apprtc.USERID";
    public static final String EXTRA_URLPARAMETERS = "org.appspot.apprtc.URLPARAMETERS";
    public static final String EXTRA_LOOPBACK = "org.appspot.apprtc.LOOPBACK";
    public static final String EXTRA_VIDEO_CALL = "org.appspot.apprtc.VIDEO_CALL";
    public static final String EXTRA_SCREENCAPTURE = "org.appspot.apprtc.SCREENCAPTURE";
    public static final String EXTRA_CAMERA2 = "org.appspot.apprtc.CAMERA2";
    public static final String EXTRA_VIDEO_WIDTH = "org.appspot.apprtc.VIDEO_WIDTH";
    public static final String EXTRA_VIDEO_HEIGHT = "org.appspot.apprtc.VIDEO_HEIGHT";
    public static final String EXTRA_VIDEO_FPS = "org.appspot.apprtc.VIDEO_FPS";
    public static final String EXTRA_VIDEO_CAPTUREQUALITYSLIDER_ENABLED =
            "org.appsopt.apprtc.VIDEO_CAPTUREQUALITYSLIDER";
    public static final String EXTRA_VIDEO_BITRATE = "org.appspot.apprtc.VIDEO_BITRATE";
    public static final String EXTRA_VIDEOCODEC = "org.appspot.apprtc.VIDEOCODEC";
    public static final String EXTRA_HWCODEC_ENABLED = "org.appspot.apprtc.HWCODEC";
    public static final String EXTRA_CAPTURETOTEXTURE_ENABLED = "org.appspot.apprtc.CAPTURETOTEXTURE";
    public static final String EXTRA_FLEXFEC_ENABLED = "org.appspot.apprtc.FLEXFEC";
    public static final String EXTRA_AUDIO_BITRATE = "org.appspot.apprtc.AUDIO_BITRATE";
    public static final String EXTRA_AUDIOCODEC = "org.appspot.apprtc.AUDIOCODEC";
    public static final String EXTRA_NOAUDIOPROCESSING_ENABLED =
            "org.appspot.apprtc.NOAUDIOPROCESSING";
    public static final String EXTRA_AECDUMP_ENABLED = "org.appspot.apprtc.AECDUMP";
    public static final String EXTRA_SAVE_INPUT_AUDIO_TO_FILE_ENABLED =
            "org.appspot.apprtc.SAVE_INPUT_AUDIO_TO_FILE";
    public static final String EXTRA_OPENSLES_ENABLED = "org.appspot.apprtc.OPENSLES";
    public static final String EXTRA_DISABLE_BUILT_IN_AEC = "org.appspot.apprtc.DISABLE_BUILT_IN_AEC";
    public static final String EXTRA_DISABLE_BUILT_IN_AGC = "org.appspot.apprtc.DISABLE_BUILT_IN_AGC";
    public static final String EXTRA_DISABLE_BUILT_IN_NS = "org.appspot.apprtc.DISABLE_BUILT_IN_NS";
    public static final String EXTRA_DISABLE_WEBRTC_AGC_AND_HPF =
            "org.appspot.apprtc.DISABLE_WEBRTC_GAIN_CONTROL";
    public static final String EXTRA_DISPLAY_HUD = "org.appspot.apprtc.DISPLAY_HUD";
    public static final String EXTRA_TRACING = "org.appspot.apprtc.TRACING";
    public static final String EXTRA_CMDLINE = "org.appspot.apprtc.CMDLINE";
    public static final String EXTRA_RUNTIME = "org.appspot.apprtc.RUNTIME";
    public static final String EXTRA_VIDEO_FILE_AS_CAMERA = "org.appspot.apprtc.VIDEO_FILE_AS_CAMERA";
    public static final String EXTRA_SAVE_REMOTE_VIDEO_TO_FILE =
            "org.appspot.apprtc.SAVE_REMOTE_VIDEO_TO_FILE";
    public static final String EXTRA_SAVE_REMOTE_VIDEO_TO_FILE_WIDTH =
            "org.appspot.apprtc.SAVE_REMOTE_VIDEO_TO_FILE_WIDTH";
    public static final String EXTRA_SAVE_REMOTE_VIDEO_TO_FILE_HEIGHT =
            "org.appspot.apprtc.SAVE_REMOTE_VIDEO_TO_FILE_HEIGHT";
    public static final String EXTRA_USE_VALUES_FROM_INTENT =
            "org.appspot.apprtc.USE_VALUES_FROM_INTENT";
    public static final String EXTRA_DATA_CHANNEL_ENABLED = "org.appspot.apprtc.DATA_CHANNEL_ENABLED";
    public static final String EXTRA_ORDERED = "org.appspot.apprtc.ORDERED";
    public static final String EXTRA_MAX_RETRANSMITS_MS = "org.appspot.apprtc.MAX_RETRANSMITS_MS";
    public static final String EXTRA_MAX_RETRANSMITS = "org.appspot.apprtc.MAX_RETRANSMITS";
    public static final String EXTRA_PROTOCOL = "org.appspot.apprtc.PROTOCOL";
    public static final String EXTRA_NEGOTIATED = "org.appspot.apprtc.NEGOTIATED";
    public static final String EXTRA_ID = "org.appspot.apprtc.ID";
    public static final String EXTRA_ENABLE_RTCEVENTLOG = "org.appspot.apprtc.ENABLE_RTCEVENTLOG";
    public static final String EXTRA_USE_LEGACY_AUDIO_DEVICE =
            "org.appspot.apprtc.USE_LEGACY_AUDIO_DEVICE";

    private static final int CAPTURE_PERMISSION_REQUEST_CODE = 1;

    // List of mandatory application permissions.
    private static final String[] MANDATORY_PERMISSIONS = {"android.permission.MODIFY_AUDIO_SETTINGS",
            "android.permission.RECORD_AUDIO", "android.permission.INTERNET"};

    // Peer connection statistics callback period in ms.
    private static final int STAT_CALLBACK_PERIOD = 1000;

    private static final int maxVideoRoomUsers = 5;

    private final int[] surfaceViewViewId = new int[] {
            R.id.fullscreen_video_view,
            R.id.pip_video_view3,
            R.id.pip_video_view2,
            R.id.pip_video_view1,
            R.id.pip_video_view0};

    private boolean isBackCamera = false;
    //@Nullable
    //private VideoFileRenderer videoFileRenderer;

    @Nullable
    private PeerConnectionClient2 PeerConnectionClient2 = null;
    @Nullable
    private VideoLiveClient videoLiveClient = null;
    @Nullable
    private AppRTCAudioManager audioManager = null;

    private Toast logToast;
    private boolean commandLineRun;
    private boolean activityRunning;
    @Nullable
    private PeerConnectionParameters peerConnectionParameters;
    private boolean iceConnected;
    private boolean isError;
    private boolean callControlFragmentVisible = true;
    private long callStartedTimeMs = 0;
    private boolean micEnabled = true;
    private boolean screencaptureEnabled = false;
    private static Intent mediaProjectionPermissionResultData;
    private static int mediaProjectionPermissionResultCode;

    // Controls
    private CallFragment callFragment;
    private HudFragment hudFragment;
    private CpuMonitor cpuMonitor;

    //user info
    private String roomUrl;
    private long roomId;
    private String userId;

    private final Vector<SurfaceViewRenderer> surfaceViewRenderers = new Vector<>();
    private final Vector<BigInteger> positionVector = new Vector<>();

    private BigInteger localHandleId = BigInteger.ZERO;

    @Override
    // TODO(bugs.webrtc.org/8580): LayoutParams.FLAG_TURN_SCREEN_ON and
    // LayoutParams.FLAG_SHOW_WHEN_LOCKED are deprecated.
    @SuppressWarnings("deprecation")
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Thread.setDefaultUncaughtExceptionHandler(new UnhandledExceptionHandler(this));

        // Set window styles for fullscreen-window size. Needs to be done before
        // adding content.
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(LayoutParams.FLAG_FULLSCREEN | LayoutParams.FLAG_KEEP_SCREEN_ON
                | LayoutParams.FLAG_SHOW_WHEN_LOCKED | LayoutParams.FLAG_TURN_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(getSystemUiVisibility());
        setContentView(R.layout.activity_call_video);

        iceConnected = false;
        // Create UI controls.
        callFragment = new CallFragment();
        hudFragment = new HudFragment();

        final Intent intent = getIntent();
        final EglBase eglBase = EglBase.create();

        for(int i = 0; i < maxVideoRoomUsers ; i++ ) {
            positionVector.add(BigInteger.ZERO);
            SurfaceViewRenderer renderer = findViewById(surfaceViewViewId[i]);
            surfaceViewRenderers.add(renderer);

            renderer.init(eglBase.getEglBaseContext(), null);
            if(i == 0) {
                renderer.setScalingType(ScalingType.SCALE_ASPECT_FILL);
                renderer.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        toggleCallControlFragmentVisibility();
                    }
                });
            } else {
                renderer.setScalingType(ScalingType.SCALE_ASPECT_FIT);
                renderer.setZOrderMediaOverlay(true);
                renderer.setEnableHardwareScaler(true /* enabled */);
            }
        }

        /*
        String saveRemoteVideoToFile = intent.getStringExtra(EXTRA_SAVE_REMOTE_VIDEO_TO_FILE);

        // When saveRemoteVideoToFile is set we save the video from the remote to a file.
        if (saveRemoteVideoToFile != null) {
            int videoOutWidth = intent.getIntExtra(EXTRA_SAVE_REMOTE_VIDEO_TO_FILE_WIDTH, 0);
            int videoOutHeight = intent.getIntExtra(EXTRA_SAVE_REMOTE_VIDEO_TO_FILE_HEIGHT, 0);
            try {
                videoFileRenderer = new VideoFileRenderer(
                        saveRemoteVideoToFile, videoOutWidth, videoOutHeight, eglBase.getEglBaseContext());
                remoteSinks.add(videoFileRenderer);
            } catch (IOException e) {
                throw new RuntimeException(
                        "Failed to open video file for output: " + saveRemoteVideoToFile, e);
            }
        }
*/

        // Check for mandatory permissions.
        for (String permission : MANDATORY_PERMISSIONS) {
            if (checkCallingOrSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                logAndToast("Permission " + permission + " is not granted");
                setResult(RESULT_CANCELED);
                finish();
                return;
            }
        }

        //roomUrl = intent.getStringExtra(EXTRA_SERVERADDR);;
        roomUrl="ws://23.106.156.204:8188";
        if (roomUrl.equals("")) {
            logAndToast(getString(R.string.missing_url));
            Log.e(TAG, "Didn't get any URL in intent!");
            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        // Get Intent parameters.
        roomId = intent.getLongExtra(EXTRA_ROOMID, 0);
        roomId=1234;
        Log.d(TAG, "Room ID: " + roomId);
        if (roomId == 0) {
            logAndToast(getString(R.string.missing_url));
            Log.e(TAG, "Incorrect room ID in intent!");
            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        userId = intent.getStringExtra(EXTRA_USERID);

        boolean loopback = intent.getBooleanExtra(EXTRA_LOOPBACK, false);
        boolean tracing = intent.getBooleanExtra(EXTRA_TRACING, false);

        int videoWidth = intent.getIntExtra(EXTRA_VIDEO_WIDTH, 0);
        int videoHeight = intent.getIntExtra(EXTRA_VIDEO_HEIGHT, 0);

        screencaptureEnabled = intent.getBooleanExtra(EXTRA_SCREENCAPTURE, false);
        // If capturing format is not specified for screencapture, use screen resolution.
        if (screencaptureEnabled && videoWidth == 0 && videoHeight == 0) {
            DisplayMetrics displayMetrics = getDisplayMetrics();
            videoWidth = displayMetrics.widthPixels;
            videoHeight = displayMetrics.heightPixels;
        }
        DataChannelParameters dataChannelParameters = null;
        if (intent.getBooleanExtra(EXTRA_DATA_CHANNEL_ENABLED, false)) {
            dataChannelParameters = new DataChannelParameters(intent.getBooleanExtra(EXTRA_ORDERED, true),
                    intent.getIntExtra(EXTRA_MAX_RETRANSMITS_MS, -1),
                    intent.getIntExtra(EXTRA_MAX_RETRANSMITS, -1), intent.getStringExtra(EXTRA_PROTOCOL),
                    intent.getBooleanExtra(EXTRA_NEGOTIATED, false), intent.getIntExtra(EXTRA_ID, -1));
        }
        peerConnectionParameters =
                new PeerConnectionParameters(intent.getBooleanExtra(EXTRA_VIDEO_CALL, true), loopback,
                        tracing, videoWidth, videoHeight, intent.getIntExtra(EXTRA_VIDEO_FPS, 0),
                        intent.getIntExtra(EXTRA_VIDEO_BITRATE, 0), intent.getStringExtra(EXTRA_VIDEOCODEC),
                        intent.getBooleanExtra(EXTRA_HWCODEC_ENABLED, true),
                        intent.getBooleanExtra(EXTRA_FLEXFEC_ENABLED, false),
                        intent.getIntExtra(EXTRA_AUDIO_BITRATE, 0), intent.getStringExtra(EXTRA_AUDIOCODEC),
                        intent.getBooleanExtra(EXTRA_NOAUDIOPROCESSING_ENABLED, false),
                        intent.getBooleanExtra(EXTRA_AECDUMP_ENABLED, false),
                        intent.getBooleanExtra(EXTRA_SAVE_INPUT_AUDIO_TO_FILE_ENABLED, false),
                        intent.getBooleanExtra(EXTRA_OPENSLES_ENABLED, false),
                        intent.getBooleanExtra(EXTRA_DISABLE_BUILT_IN_AEC, false),
                        intent.getBooleanExtra(EXTRA_DISABLE_BUILT_IN_AGC, false),
                        intent.getBooleanExtra(EXTRA_DISABLE_BUILT_IN_NS, false),
                        intent.getBooleanExtra(EXTRA_DISABLE_WEBRTC_AGC_AND_HPF, false),
                        intent.getBooleanExtra(EXTRA_ENABLE_RTCEVENTLOG, false),
                        intent.getBooleanExtra(EXTRA_USE_LEGACY_AUDIO_DEVICE, false), dataChannelParameters);
        commandLineRun = intent.getBooleanExtra(EXTRA_CMDLINE, false);
        int runTimeMs = intent.getIntExtra(EXTRA_RUNTIME, 0);

        Log.d(TAG, "VIDEO_FILE: '" + intent.getStringExtra(EXTRA_VIDEO_FILE_AS_CAMERA) + "'");

        //Create connection client.Use videoLiveClient to connect to Janus Webrtc Gateway.
        videoLiveClient = new VideoLiveClient(this);

        // Create connection parameters.
        String urlParameters = intent.getStringExtra(EXTRA_URLPARAMETERS);
        //add log here
        Log.i("CallActivity","roomUri="+roomUrl);
        Log.i("CallActivity","roomid="+roomId);
        Log.i("CallActivity","urlParameters="+urlParameters);
        //just hack it here

        //roomConnectionParameters = new RoomConnectionParameters(roomUriStr, roomIdStr, loopback, urlParameters);

        // Create CPU monitor
        if (CpuMonitor.isSupported()) {
            cpuMonitor = new CpuMonitor(this);
            hudFragment.setCpuMonitor(cpuMonitor);
        }

        // Send intent arguments to fragments.
        callFragment.setArguments(intent.getExtras());
        hudFragment.setArguments(intent.getExtras());
        // Activate call and HUD fragments and start the call.
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        ft.add(R.id.call_fragment_container, callFragment);
        ft.add(R.id.hud_fragment_container, hudFragment);
        ft.commit();

        // For command line execution run connection for <runTimeMs> and exit.
        if (commandLineRun && runTimeMs > 0) {
            (new Handler()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    disconnect();
                }
            }, runTimeMs);
        }

        // Create peer connection client.
        PeerConnectionClient2 = new PeerConnectionClient2(
                getApplicationContext(), eglBase, peerConnectionParameters, VideoLiveActivity.this);
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        PeerConnectionClient2.createPeerConnectionFactory(options);

        if (screencaptureEnabled) {
            startScreenCapture();
        } else {
            startCall();
        }
    }

    @TargetApi(17)
    private DisplayMetrics getDisplayMetrics() {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        WindowManager windowManager =
                (WindowManager) getApplication().getSystemService(Context.WINDOW_SERVICE);
        windowManager.getDefaultDisplay().getRealMetrics(displayMetrics);
        return displayMetrics;
    }

    @TargetApi(19)
    private static int getSystemUiVisibility() {
        int flags = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            flags |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        }
        return flags;
    }

    @TargetApi(21)
    private void startScreenCapture() {
        MediaProjectionManager mediaProjectionManager =
                (MediaProjectionManager) getApplication().getSystemService(
                        Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(
                mediaProjectionManager.createScreenCaptureIntent(), CAPTURE_PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != CAPTURE_PERMISSION_REQUEST_CODE)
            return;
        mediaProjectionPermissionResultCode = resultCode;
        mediaProjectionPermissionResultData = data;
        startCall();
    }

    private boolean useCamera2() {
        return Camera2Enumerator.isSupported(this) && getIntent().getBooleanExtra(EXTRA_CAMERA2, true);
    }

    private boolean captureToTexture() {
        return getIntent().getBooleanExtra(EXTRA_CAPTURETOTEXTURE_ENABLED, false);
    }

    private @Nullable VideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
        final String[] deviceNames = enumerator.getDeviceNames();

        // First, try to find front facing camera
        Logging.d(TAG, "Looking for front facing cameras.");
        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                Logging.d(TAG, "Creating front facing camera capturer.");
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        // Front facing camera not found, try something else
        Logging.d(TAG, "Looking for other cameras.");
        for (String deviceName : deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                Logging.d(TAG, "Creating other camera capturer.");
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        return null;
    }

    @TargetApi(21)
    private @Nullable VideoCapturer createScreenCapturer() {
        if (mediaProjectionPermissionResultCode != Activity.RESULT_OK) {
            reportError("User didn't give permission to capture the screen.");
            return null;
        }
        return new ScreenCapturerAndroid(
                mediaProjectionPermissionResultData, new MediaProjection.Callback() {
            @Override
            public void onStop() {
                reportError("User revoked permission to capture the screen.");
            }
        });
    }

    // Activity interfaces
    @Override
    public void onStop() {
        super.onStop();
        activityRunning = false;
        // Don't stop the video when using screencapture to allow user to show other apps to the remote
        // end.
        if (PeerConnectionClient2 != null && !screencaptureEnabled) {
            PeerConnectionClient2.stopVideoSource();
        }
        if (cpuMonitor != null) {
            cpuMonitor.pause();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        activityRunning = true;
        // Video is not paused for screencapture. See onPause.
        if (PeerConnectionClient2 != null && !screencaptureEnabled) {
            PeerConnectionClient2.startVideoSource();
        }
        if (cpuMonitor != null) {
            cpuMonitor.resume();
        }
    }

    @Override
    protected void onDestroy() {
        Thread.setDefaultUncaughtExceptionHandler(null);
        disconnect();
        if (logToast != null) {
            logToast.cancel();
        }
        activityRunning = false;
        super.onDestroy();
    }

    // CallFragment.OnCallEvents interface implementation.
    @Override
    public void onCallHangUp() {
        disconnect();
    }

    @Override
    public void onCameraSwitch() {
        if (PeerConnectionClient2 != null) {
            PeerConnectionClient2.switchCamera();

            int index = positionVector.indexOf(localHandleId);
            if(isBackCamera) {
                isBackCamera = false;
                setRendererMirror(index);
            } else {
                removeRendererMirror(index);
                isBackCamera = true;
            }
        }
    }

    @Override
    public void onVideoScalingSwitch(ScalingType scalingType) {
        surfaceViewRenderers.get(0).setScalingType(scalingType);
    }

    @Override
    public void onCaptureFormatChange(int width, int height, int framerate) {
        if (PeerConnectionClient2 != null) {
            PeerConnectionClient2.changeCaptureFormat(width, height, framerate);
        }
    }

    @Override
    public boolean onToggleMic() {
        if (PeerConnectionClient2 != null) {
            micEnabled = !micEnabled;
            PeerConnectionClient2.setAudioEnabled(micEnabled);
        }
        return micEnabled;
    }

    // Helper functions.
    private void toggleCallControlFragmentVisibility() {
        if (!iceConnected || !callFragment.isAdded()) {
            return;
        }
        // Show/hide call control fragment
        callControlFragmentVisible = !callControlFragmentVisible;
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        if (callControlFragmentVisible) {
            ft.show(callFragment);
            ft.show(hudFragment);
        } else {
            ft.hide(callFragment);
            ft.hide(hudFragment);
        }
        ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
        ft.commit();
    }

    private void startCall() {
        if (videoLiveClient == null) {
            Log.e(TAG, "AppRTC client is not allocated for a call.");
            return;
        }
        callStartedTimeMs = System.currentTimeMillis();

        JanusConnectionParameters connectionParameters = new JanusConnectionParameters(roomUrl, roomId, userId, maxVideoRoomUsers);

        // Start room connection.
        videoLiveClient.connectToServer(connectionParameters);

        // Create and audio manager that will take care of audio routing,
        // audio modes, audio device enumeration etc.
        audioManager = AppRTCAudioManager.create(getApplicationContext());
        // Store existing audio settings and change audio mode to
        // MODE_IN_COMMUNICATION for best possible VoIP performance.
        Log.d(TAG, "Starting the audio manager...");
        audioManager.start(new AudioManagerEvents() {
            // This method will be called each time the number of available audio
            // devices has changed.
            @Override
            public void onAudioDeviceChanged(
                    AudioDevice audioDevice, Set<AudioDevice> availableAudioDevices) {
                onAudioManagerDevicesChanged(audioDevice, availableAudioDevices);
            }
        });
    }

    // Should be called from UI thread
    private void callConnected() {
        final long delta = System.currentTimeMillis() - callStartedTimeMs;
        Log.i(TAG, "Call connected: delay=" + delta + "ms");
        if (PeerConnectionClient2 == null || isError) {
            Log.w(TAG, "Call is connected in closed or error state");
            return;
        }
        // Enable statistics callback.
        //PeerConnectionClient2.enableStatsEvents(true, STAT_CALLBACK_PERIOD);
        //setSwappedFeeds(false /* isSwappedFeeds */);
    }

    // This method is called when the audio manager reports audio device change,
    // e.g. from wired headset to speakerphone.
    private void onAudioManagerDevicesChanged(
            final AudioDevice device, final Set<AudioDevice> availableDevices) {
        Log.d(TAG, "onAudioManagerDevicesChanged: " + availableDevices + ", "
                + "selected: " + device);
        // TODO(henrika): add callback handler.
    }

    // Disconnect from remote resources, dispose of local resources, and exit.
    private void disconnect() {
        activityRunning = false;
/*
        if (videoFileRenderer != null) {
            videoFileRenderer.release();
            videoFileRenderer = null;
        }
*/
        if (videoLiveClient != null) {
            videoLiveClient.disconnectFromServer();
            videoLiveClient = null;
        }
        
        if (PeerConnectionClient2 != null) {
            PeerConnectionClient2.close();
            PeerConnectionClient2 = null;
        }
        if (audioManager != null) {
            audioManager.stop();
            audioManager = null;
        }
        if (iceConnected && !isError) {
            setResult(RESULT_OK);
        } else {
            setResult(RESULT_CANCELED);
        }

        for(SurfaceViewRenderer renderer : surfaceViewRenderers) {
            if (renderer == null)  continue;
            renderer.clearImage();
            renderer.setMirror(false);
            renderer.setVisibility(View.INVISIBLE);
        }
        surfaceViewRenderers.clear();

        finish();
    }

    private void disconnectWithErrorMessage(final String errorMessage) {
        if (commandLineRun || !activityRunning) {
            Log.e(TAG, "Critical error: " + errorMessage);
            disconnect();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle(getText(R.string.channel_error_title))
                    .setMessage(errorMessage)
                    .setCancelable(false)
                    .setNeutralButton(R.string.ok,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int id) {
                                    dialog.cancel();
                                    disconnect();
                                }
                            })
                    .create()
                    .show();
        }
    }

    // Log |msg| and Toast about it.
    private void logAndToast(String msg) {
        Log.d(TAG, msg);
        if (logToast != null) {
            logToast.cancel();
        }
        logToast = Toast.makeText(this, msg, Toast.LENGTH_SHORT);
        logToast.show();
    }

    private void reportError(final String description) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!isError) {
                    isError = true;
                    disconnectWithErrorMessage(description);
                }
            }
        });
    }

    private @Nullable VideoCapturer createVideoCapturer() {
        final VideoCapturer videoCapturer;
        String videoFileAsCamera = getIntent().getStringExtra(EXTRA_VIDEO_FILE_AS_CAMERA);
        if (videoFileAsCamera != null) {
            try {
                videoCapturer = new FileVideoCapturer(videoFileAsCamera);
            } catch (IOException e) {
                reportError("Failed to open video file for emulated camera");
                return null;
            }
        } else if (screencaptureEnabled) {
            return createScreenCapturer();
        } else if (useCamera2()) {
            if (!captureToTexture()) {
                reportError(getString(R.string.camera2_texture_only_error));
                return null;
            }

            Logging.d(TAG, "Creating capturer using camera2 API.");
            videoCapturer = createCameraCapturer(new Camera2Enumerator(this));
        } else {
            Logging.d(TAG, "Creating capturer using camera1 API.");
            videoCapturer = createCameraCapturer(new Camera1Enumerator(captureToTexture()));
        }
        if (videoCapturer == null) {
            reportError("Failed to open camera");
            return null;
        }
        return videoCapturer;
    }

    private void swappedFeedToFullscreen(int pipIndex) {
        SurfaceViewRenderer renderer = surfaceViewRenderers.get(pipIndex);

        BigInteger id = positionVector.get(pipIndex);
        PeerConnectionClient2.setVideoRender(id, surfaceViewRenderers.get(0));
        if (positionVector.get(0) == BigInteger.ZERO) {
            renderer.setBackground(null);
            renderer.setVisibility(View.INVISIBLE);
            removeClickListener(pipIndex);
        } else {
            PeerConnectionClient2.setVideoRender(positionVector.get(0), renderer);
        }

        if(id == localHandleId) {
            removeRendererMirror(pipIndex);
            setRendererMirror(0);
        }

        if(positionVector.get(0) == localHandleId) {
            removeRendererMirror(0);
            setRendererMirror(pipIndex);
        }

        positionVector.set(pipIndex, positionVector.get(0));
        positionVector.set(0, id);
    }

    // -----Implementation of PeerConnectionClient2.PeerConnectionEvents.---------
    // Send local peer connection SDP and ICE candidates to remote party.
    // All callbacks are invoked from peer connection client looper thread and
    // are routed to UI thread.
    @Override
    public void onLocalDescription(final BigInteger handleId, final SessionDescription sdp) {
        final long delta = System.currentTimeMillis() - callStartedTimeMs;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (videoLiveClient != null) {
                    logAndToast("Sending " + sdp.type + ", delay=" + delta + "ms");
                    if(sdp.type.equals(SessionDescription.Type.OFFER)){
                        videoLiveClient.publisherCreateOffer(handleId, sdp);
                    }
                    else{
                        videoLiveClient.subscriberCreateAnswer(handleId,sdp);
                    }

                }
            }
        });
    }

    @Override
    public void onRemoteRender(final BigInteger handleId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                for(int i = 0; i < maxVideoRoomUsers; i++) {
                    if(positionVector.get(i) == BigInteger.ZERO) {
                        positionVector.set(i, handleId);
                        SurfaceViewRenderer renderer = surfaceViewRenderers.get(i);
                        if(i != 0) renderer.setBackground(getResources().getDrawable(R.drawable.border));
                        renderer.setVisibility(View.VISIBLE);
                        PeerConnectionClient2.setVideoRender(handleId, renderer);
                        setClickListener(i);
                        return;
                    }
                }

                Log.d(TAG, "Not enough surfaceView to render the remote stream. handle id is " + handleId);
            }
        });
    }

    private void setRendererMirror(int index) {
        if(isBackCamera) return;

        surfaceViewRenderers.get(index).setMirror(true);
    }

    private void removeRendererMirror(int index) {
        if(isBackCamera) return;

        surfaceViewRenderers.get(index).setMirror(false);
    }

    @Override
    public void onLocalRender(final BigInteger handleId) { //fixme: localrender is lost, and remoterenders are reach to number of max render
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                localHandleId = handleId;

                if(positionVector.get(0) == BigInteger.ZERO) {
                    positionVector.set(0, handleId);
                    PeerConnectionClient2.setVideoRender(handleId, surfaceViewRenderers.get(0));
                } else {
                    Log.d(TAG, "Not enough surfaceView to render the remote stream. handle id is " + handleId);
                }
            }
        });
    }

    private void setClickListener(final int index) {
        if(index == 0) return;

        surfaceViewRenderers.get(index).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                swappedFeedToFullscreen(index);
            }
        });
    }

    private void removeClickListener(final int index) {
        if(index == 0) return;

        surfaceViewRenderers.get(index).setOnClickListener(null);
    }

    @Override
    public void onIceCandidate(final BigInteger handleId, final IceCandidate candidate) {
        Log.d(TAG,"========onIceCandidate=======");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (candidate != null) {
                    videoLiveClient.trickleCandidate(handleId,candidate);
                }
                else{
                    videoLiveClient.trickleCandidateComplete(handleId);
                }
            }
        });
    }

    @Override
    public void onIceCandidatesRemoved(final BigInteger handleId, final IceCandidate[] candidates) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (videoLiveClient != null) {
                    //WebSocketRTCClient.sendLocalIceCandidateRemovals(candidates);
                }
            }
        });
    }

    @Override
    public void onIceConnected(BigInteger handleId) {
        final long delta = System.currentTimeMillis() - callStartedTimeMs;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                logAndToast("ICE connected, delay=" + delta + "ms");
                iceConnected = true;
                //callConnected();
            }
        });
    }

    @Override
    public void onIceDisconnected(BigInteger handleId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                logAndToast("ICE disconnected " + handleId);
                //iceConnected = false;
                //disconnect();
            }
        });
    }

    @Override
    public void onPeerConnectionClosed(final BigInteger handleId) {}

    @Override
    public void onPeerConnectionStatsReady(final BigInteger handleId, final StatsReport[] reports) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!isError && iceConnected) {
                    hudFragment.updateEncoderStatistics(reports);
                }
            }
        });
    }

    @Override
    public void onPeerConnectionError(final BigInteger handleId, final String description) {
        reportError(description);
    }


    @Override
    public void onPublisherJoined(final BigInteger handleId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                onPublisherJoinedInternal(handleId);
            }
        });
    }

    @Override
    public void onRemoteJsep(final BigInteger handleId, final JSONObject jsep) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                onRemoteJsepInternal(handleId,jsep);
            }
        });
    }

    public void onPublisherJoinedInternal(final BigInteger handleId){
        final long delta = System.currentTimeMillis() - callStartedTimeMs;

        logAndToast("Creating peer connection, delay=" + delta + "ms");
        VideoCapturer videoCapturer = null;
        if (peerConnectionParameters.videoCallEnabled) {
            videoCapturer = createVideoCapturer();
        }

        PeerConnectionClient2.createPeerConnection(videoCapturer,handleId);

        logAndToast("Creating OFFER...");
        // Create offer. Offer SDP will be sent to answering client in
        // PeerConnectionEvents.onLocalDescription event.
        PeerConnectionClient2.createOffer(handleId);
    }

    public void onRemoteJsepInternal(final BigInteger handleId,final JSONObject jsep){

        final long delta = System.currentTimeMillis() - callStartedTimeMs;
        logAndToast("onPublisherRemoteJsepInternal, delay=" + delta + "ms");

        if (PeerConnectionClient2 == null) {
            Log.e(TAG, "Received remote SDP for non-initilized peer connection.");
            return;
        }

        SessionDescription sessionDescription = convertJsonToSdp(jsep);

        if(sessionDescription.type == SessionDescription.Type.ANSWER) {
            PeerConnectionClient2.setRemoteDescription(handleId, sessionDescription);
            logAndToast("Creating ANSWER...");
        }
        else if(sessionDescription.type == SessionDescription.Type.OFFER) {
            PeerConnectionClient2.subscriberHandleRemoteJsep(handleId, sessionDescription);
        }
    }

    @Override
    public void onLeft(final BigInteger handleId){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                onLeftInternal(handleId);
            }
        });
    }

    public void onLeftInternal(final BigInteger handleId){
        if(handleId == localHandleId) {
            disconnect();
            return;
        }

        for(int index = 0; index < maxVideoRoomUsers; index++) {

            if(positionVector.get(index) != handleId) continue;

            while(index < maxVideoRoomUsers - 1) {
                int step = index == 0 && positionVector.get(index + 1) == localHandleId ? 2 : 1;
                if(positionVector.get(index + step) == BigInteger.ZERO) break;

                PeerConnectionClient2.setVideoRender(positionVector.get(index + step), surfaceViewRenderers.get(index));
                if(positionVector.get(index + step) == localHandleId) {
                    removeRendererMirror(index + step);
                    setRendererMirror(index);
                }
                positionVector.set(index, positionVector.get(index + step));
                index += step;
            }
            PeerConnectionClient2.setVideoRender(handleId, null);
            PeerConnectionClient2.dispose(handleId);
            removeClickListener(index);

            SurfaceViewRenderer renderer = surfaceViewRenderers.get(index);
            renderer.setBackground(null);
            if(index == 0) {
                renderer.clearImage();
            } else {
                renderer.setVisibility(View.INVISIBLE);
            }
            positionVector.set(index, BigInteger.ZERO);
            break;
        }
    }

    @Override
    public void onNotification(String notificationMessage){

    }

    @Override
    public void onChannelClose() {

    }

    @Override
    public void onChannelError(String errorMessage){

    }

}

