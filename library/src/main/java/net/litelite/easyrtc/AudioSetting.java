package net.litelite.easyrtc;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnectionFactory;

import java.util.UUID;

public class AudioSetting {

    private static final String ECHO_CANCELLATION_CONSTRAINT = "googEchoCancellation";
    private static final String AUTO_GAIN_CONTROL_CONSTRAINT = "googAutoGainControl";
    private static final String HIGH_PASS_FILTER_CONSTRAINT  = "googHighpassFilter";
    private static final String NOISE_SUPPRESSION_CONSTRAINT = "googNoiseSuppression";
    private static final String LEVEL_CONTROL_CONSTRAINT     = "levelControl";

    private boolean send         = true;
    private boolean receive      = true;
    private boolean levelControl = true;
    private boolean processing   = true;

    private AudioSource source;
    private AudioTrack track;

    public AudioSetting(boolean send,
                        boolean receive,
                        boolean levelControl,
                        boolean processing) {

        this.send         = send;
        this.receive      = receive;
        this.levelControl = levelControl;
        this.processing   = processing;
    }

    public void init(PeerConnectionFactory factory) {
        if (send) {
            MediaConstraints constraints = new MediaConstraints();
            if (this.levelControl) {
                constraints.mandatory.add(
                        new MediaConstraints.KeyValuePair(LEVEL_CONTROL_CONSTRAINT, "true"));
            }
            if (!this.processing) {
                constraints.mandatory.add(
                        new MediaConstraints.KeyValuePair(ECHO_CANCELLATION_CONSTRAINT, "false"));
                constraints.mandatory.add(
                        new MediaConstraints.KeyValuePair(AUTO_GAIN_CONTROL_CONSTRAINT, "false"));
                constraints.mandatory.add(
                        new MediaConstraints.KeyValuePair(HIGH_PASS_FILTER_CONSTRAINT, "false"));
                constraints.mandatory.add(
                        new MediaConstraints.KeyValuePair(NOISE_SUPPRESSION_CONSTRAINT, "false"));
            }

            source = factory.createAudioSource(constraints);
            track = factory.createAudioTrack(UUID.randomUUID().toString(), source);
            track.setEnabled(true);
        }
    }

    public boolean useAudio() {
        return true;
    }

    public void modifySDPConstraints(MediaConstraints ms) {
        if (receive) {
            ms.mandatory.add(
                    new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        } else {
            ms.mandatory.add(
                    new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"));
        }
    }

    public void attachLocalTrackToStream(MediaStream stream) {
        if (send) {
            stream.addTrack(this.track);
        }
    }

    public void dispose() {
        if (source != null) {
            source.dispose();
            source = null;
        }
    }
}
