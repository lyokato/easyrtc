package net.litelite.easyrtc;

import android.content.Context;

import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;

public class CameraCapturerFactory {

    public static CameraVideoCapturer createCapturer(Context context) {
        CameraVideoCapturer videoCapturer = null;
        if (Camera2Enumerator.isSupported(context)) {
            videoCapturer = createCapturer(new Camera2Enumerator(context));
        } else {
            videoCapturer = createCapturer(new Camera1Enumerator(true));
        }
        return videoCapturer;
    }

    private static CameraVideoCapturer createCapturer(CameraEnumerator enumerator) {
        final String[] deviceNames = enumerator.getDeviceNames();
        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                CameraVideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);
                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        for (String deviceName : deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                CameraVideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);
                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }
        return null;
    }
}
