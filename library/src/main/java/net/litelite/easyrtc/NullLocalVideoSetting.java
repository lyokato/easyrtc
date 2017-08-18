package net.litelite.easyrtc;

import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;

public class NullLocalVideoSetting implements LocalVideoSetting {

    public NullLocalVideoSetting() { }

    @Override
    public void init(PeerConnectionFactory factory) { }

    @Override
    public void attachTrackToStream(MediaStream stream) { }

    @Override
    public void pickRtpSender(PeerConnection conn) { }

    @Override
    public void startCapture() { }

    @Override
    public void stopCapture() { }

    @Override
    public void dispose() { }
}
