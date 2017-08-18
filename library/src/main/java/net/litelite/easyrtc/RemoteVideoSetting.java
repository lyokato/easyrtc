package net.litelite.easyrtc;

import org.webrtc.MediaConstraints;

public interface RemoteVideoSetting {
    void modifySDPConstraints(MediaConstraints ms);
}
