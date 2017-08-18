package net.litelite.easyrtc;

import org.webrtc.MediaConstraints;

public class NullRemoteVideoSetting implements RemoteVideoSetting {

    NullRemoteVideoSetting() { }

    @Override
    public void modifySDPConstraints(MediaConstraints ms) {
        ms.mandatory.add(
                new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"));
    }
}
