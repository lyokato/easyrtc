package net.litelite.easyrtc;

import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;

public interface LocalVideoSetting {
    void init(PeerConnectionFactory factory);
    void attachTrackToStream(MediaStream stream);
    void pickRtpSender(PeerConnection conn);
    void startCapture();
    void stopCapture();
    void dispose();
}
