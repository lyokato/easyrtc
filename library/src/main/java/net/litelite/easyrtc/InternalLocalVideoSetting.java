package net.litelite.easyrtc;

import android.util.Log;

import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpSender;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.UUID;

public class InternalLocalVideoSetting implements LocalVideoSetting {

    private static final String TAG = InternalLocalVideoSetting.class.getSimpleName();

    private VideoCapturer capturer;
    private int           width;
    private int           height;
    private int           fps;
    private boolean       isCapturing = false;
    private VideoSource   source;
    private VideoTrack    track;
    private RtpSender     sender;

    public InternalLocalVideoSetting(VideoCapturer capturer,
                                     int width,
                                     int height,
                                     int fps) {
        this.capturer      = capturer;
        this.width         = width;
        this.height        = height;
        this.fps           = fps;
    }

    @Override
    public void init(PeerConnectionFactory factory) {
        Log.d(TAG, "init local video");
        source = factory.createVideoSource(capturer);
        startCapture();
        track = factory.createVideoTrack(UUID.randomUUID().toString(), source);
        track.setEnabled(true);
    }

    @Override
    public void attachTrackToStream(MediaStream stream) {
        Log.d(TAG, "attach local video track to stream");
        stream.addTrack(this.track);
    }

    @Override
    public void pickRtpSender(PeerConnection conn) {
        for (RtpSender sender : conn.getSenders()) {
            if (sender.track() != null) {
                String trackType = sender.track().kind();
                if (trackType.equals("video")) {
                    this.sender = sender;
                }
            }
        }
    }

    @Override
    public void startCapture() {
        if (capturer != null && !isCapturing) {
            capturer.startCapture(width, height, fps);
            isCapturing = true;
        }
    }

    @Override
    public void stopCapture() {
        if (capturer != null && isCapturing) {
            isCapturing = false;
            try {
                capturer.stopCapture();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void dispose() {
        if (capturer != null) {
            stopCapture();
            capturer.dispose();
            capturer = null;
        }
        if (source != null) {
            source.dispose();
            source = null;
        }
    }
}
