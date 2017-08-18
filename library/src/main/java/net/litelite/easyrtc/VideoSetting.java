package net.litelite.easyrtc;

import org.webrtc.EglBase;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SessionDescription;
import org.webrtc.VideoCapturer;

public class VideoSetting {

    private static final String TAG = VideoSetting.class.getSimpleName();

    public static final String CODEC_VP8   = "VP8";
    public static final String CODEC_VP9   = "VP9";
    public static final String CODEC_VH264 = "H264";

    private String             preferredCodec;
    private LocalVideoSetting  local;
    private RemoteVideoSetting remote;
    private boolean            hwAcceleration = true;
    private boolean            useVideo = false;
    private EglBase.Context    eglContext;

    public VideoSetting(EglBase.Context eglContext, boolean useHw) {
        this(eglContext, useHw, CODEC_VP8);
    }

    public VideoSetting(EglBase.Context eglContext, boolean useHw, String preferedCodec) {
        this.eglContext     = eglContext;
        this.hwAcceleration = useHw;
        this.preferredCodec = preferedCodec;
        this.local          = new NullLocalVideoSetting();
        this.remote         = new NullRemoteVideoSetting();
    }

    public void setPreferredVideoCodec(String codec) {
        this.preferredCodec = codec;
    }

    public void setUseHardwareAcceleration(boolean useHardwareAcceleration) {
        this.hwAcceleration = useHardwareAcceleration;
    }

    public boolean useHardwareAcceleration() {
        return hwAcceleration;
    }

    public void receiveRemote() {
        this.remote = new InternalRemoteVideoSetting();
        this.useVideo = true;
    }

    public LocalVideoSetting  getLocalSetting()  { return local;  }
    public RemoteVideoSetting getRemoteSetting() { return remote; }

    public SessionDescription filterSDP(SessionDescription sdp) {
        return SDPUtil.filterVideoSDP(sdp, preferredCodec);
    }

    public void setLocal(VideoCapturer capturer,
                         int width,
                         int height,
                         int fps) {
        this.local = new InternalLocalVideoSetting(capturer, width, height, fps);
        this.useVideo = true;
    }

    public void initFactory(PeerConnectionFactory factory) {
        if (useVideo && hwAcceleration) {
            factory.setVideoHwAccelerationOptions(eglContext, eglContext);
        }
    }

    public boolean useVideo() {
        return useVideo;
    }

    public void dispose() {
        if (this.local != null) {
            this.local.dispose();
            this.local = null;
        }
    }
}
