package net.litelite.easyrtc;

import org.webrtc.MediaConstraints;
import org.webrtc.PeerConnection;

import java.util.ArrayList;
import java.util.List;

public class NetworkSetting {

    private static final String DTLS_SRTP_KEY_AGREEMENT_CONSTRAINT = "DtlsSrtpKeyAgreement";

    private List<PeerConnection.IceServer> iceServers = new ArrayList<>();

    public NetworkSetting() { }

    public void addIceServer(String url) {
        this.iceServers.add(new PeerConnection.IceServer(url));
    }

    public void addIceServer(String url, String username, String password) {
        this.iceServers.add(new PeerConnection.IceServer(url, username, password));
    }

    public MediaConstraints constraints() {
        MediaConstraints mc = new MediaConstraints();
        mc.mandatory.add(
                new MediaConstraints.KeyValuePair(DTLS_SRTP_KEY_AGREEMENT_CONSTRAINT, "true"));
        return mc;
    }

    public PeerConnection.RTCConfiguration rtcConfig() {
        PeerConnection.RTCConfiguration conf =
                new PeerConnection.RTCConfiguration(iceServers);
        conf.tcpCandidatePolicy       = PeerConnection.TcpCandidatePolicy.DISABLED;
        conf.bundlePolicy             = PeerConnection.BundlePolicy.MAXBUNDLE;
        conf.rtcpMuxPolicy            = PeerConnection.RtcpMuxPolicy.REQUIRE;
        conf.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
        conf.keyType                  = PeerConnection.KeyType.ECDSA;
        return conf;
    }
}
