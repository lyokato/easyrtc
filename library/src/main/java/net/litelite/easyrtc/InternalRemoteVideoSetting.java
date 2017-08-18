package net.litelite.easyrtc;

import org.webrtc.MediaConstraints;

public class InternalRemoteVideoSetting implements RemoteVideoSetting {

    public InternalRemoteVideoSetting() {}

    @Override
    public void modifySDPConstraints(MediaConstraints ms) {
        ms.mandatory.add(
                new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
    }

}
