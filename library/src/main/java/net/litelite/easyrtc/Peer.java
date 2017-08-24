package net.litelite.easyrtc;

import android.content.Context;
import android.util.Log;

import org.webrtc.DataChannel;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.VideoCapturer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import io.reactivex.Single;
import io.reactivex.SingleEmitter;
import io.reactivex.SingleOnSubscribe;
import io.reactivex.annotations.NonNull;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;

public class Peer implements LocalIceCandidateHandler.Listener {

    private static final String TAG = Peer.class.getSimpleName();

    public interface Listener {
        void onLocalIceCandidatesFound(IceCandidate[] candidate);
        void onAddRemoteStream(MediaStream ms);
        void onAddLocalStream(MediaStream ms);
        void onRemoveRemoteStream(String label);
        void onHandshakeCompleted();
        void onConnected();
        void onError(String message);
        void onClosed();
    }

    public static boolean rtcInitialized = false;
    public static void initializeRtcIfNeeded(Context context) {
        if (!rtcInitialized) {
            PeerConnectionFactory.initializeAndroidGlobals(context, true);
            PeerConnectionFactory.initializeFieldTrials("");
            rtcInitialized = true;
        }
    }

    public static class Builder {

        private Context                       context;
        private Listener                      listener;
        private NetworkSetting                networkSetting;
        private AudioSetting                  audioSetting;
        private VideoSetting                  videoSetting;
        private LocalIceCandidateHandler.Type candidateHandlingType;

        public Builder(Context         context,
                       EglBase.Context eglContext,
                       String          preferredVideoCodec) {

            this.context               = context;
            this.networkSetting        = new NetworkSetting();
            this.audioSetting          = new AudioSetting(true, true, false, false);
            this.videoSetting          = new VideoSetting(eglContext, true, preferredVideoCodec);
            this.candidateHandlingType = LocalIceCandidateHandler.Type.Buffered;
        }

        public Builder candidateHandlingType(LocalIceCandidateHandler.Type type) {
            this.candidateHandlingType = type;
            return this;
        }

        public Builder receiveRemoteVideo() {
            this.videoSetting.receiveRemote();
            return this;
        }

        public Builder useHardwareAcceleration(boolean useHW) {
            this.videoSetting.setUseHardwareAcceleration(useHW);
            return this;
        }

        public Builder setLocalVideoCapturer(VideoCapturer capturer,
                                             int width, int height, int fps) {
            this.videoSetting.setLocal(capturer, width, height, fps);
            return this;
        }

        public Builder addIceServer(String url) {
            this.networkSetting.addIceServer(url);
            return this;
        }

        public Builder setAudioSetting(boolean send,
                                       boolean receive,
                                       boolean levelControl,
                                       boolean processing) {
            this.audioSetting =
                    new AudioSetting(send, receive, levelControl, processing);
            return this;
        }

        public Builder addIceServer(String url, String user, String pass) {
            this.networkSetting.addIceServer(url, user, pass);
            return this;
        }

        public Builder setListener(Listener listener) {
            this.listener = listener;
            return this;
        }

        public Peer build() {

            LocalIceCandidateHandler.Handler candidateHandler =
                    LocalIceCandidateHandler.create(this.candidateHandlingType);

            Peer conn =
                    new Peer(
                            this.context,
                            this.networkSetting,
                            this.audioSetting,
                            this.videoSetting,
                            candidateHandler,
                            this.listener);

            conn.init();
            return conn;
        }
    }

    private Context                  context;
    private PeerConnection           conn;
    private ScheduledExecutorService executor;
    private VideoSetting             videoSetting;
    private AudioSetting             audioSetting;
    private NetworkSetting           networkSetting;
    private MediaConstraints         sdpConstraints;
    private boolean                  isCrushed = false;
    private Listener                 listener;
    private PeerConnectionFactory    factory;
    private boolean                  firstHandshakeIsFinished = false;
    private List<IceCandidate>       bufferedRemoteCandidates = new ArrayList<IceCandidate>();

    private LocalIceCandidateHandler.Handler candidateHandler;

    private PeerConnection.Observer connectionObserver = new PeerConnection.Observer() {

        @Override
        public void onSignalingChange(PeerConnection.SignalingState signalingState) {
            Log.d(TAG, "SignalingChange: " + signalingState);
        }

        @Override
        public void onIceConnectionChange(final PeerConnection.IceConnectionState state) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    switch (state) {
                        case CONNECTED:
                            Log.d(TAG, "iceConnectionChange CONNECTED");
                            listener.onConnected();
                            break;
                        case DISCONNECTED:
                            crush("iceConnectionChange DISCONNECTED");
                            break;
                        case CLOSED:
                            crush("ICE connection closed");
                            break;
                        case FAILED:
                            crush("ICE connection failed");
                            break;
                        default:
                            break;
                    }
                }
            });
        }

        @Override
        public void onIceConnectionReceivingChange(boolean b) {
            Log.d(TAG, "IceConnectionReceiving change to " + b);
        }

        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
            Log.d(TAG, "IceGatheringState: " + iceGatheringState);
        }

        @Override
        public void onIceCandidate(final IceCandidate iceCandidate) {
            Log.d(TAG, "gatherer found new local ice candidate");

            if (iceCandidate == null) {
                return;
            }

            executor.execute(new Runnable() {
                @Override
                public void run() {
                    candidateHandler.handleCandidate(iceCandidate);
                }
            });
        }

        @Override
        public void onIceCandidatesRemoved(final IceCandidate[] iceCandidates) {
            Log.d(TAG, "onIceCandidatesRemoved");
        }

        @Override
        public void onAddStream(final MediaStream ms) {
            Log.d(TAG, "remote stream added, try to associate renderer");
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    if (isCrushed) return;
                    listener.onAddRemoteStream(ms);
                }
            });
        }

        @Override
        public void onRemoveStream(final MediaStream ms) {
            Log.d(TAG, "remote stream removed:" + ms.label());
            final String label = ms.label();
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    if (isCrushed) return;
                    listener.onRemoveRemoteStream(label);
                }
            });
        }

        @Override
        public void onDataChannel(DataChannel dataChannel) {
            Log.d(TAG, "onDataChannel");

        }

        @Override
        public void onRenegotiationNeeded() {
            Log.d(TAG, "onRenegotiationNeeded");
            // createOffer?
        }

        @Override
        public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
            Log.d(TAG, "onAddTrack");
        }
    };

    private Peer(Context                          context,
                 NetworkSetting                   networkSetting,
                 AudioSetting                     audioSetting,
                 VideoSetting                     videoSetting,
                 LocalIceCandidateHandler.Handler candidateHandler,
                 Listener                         listener) {

        this.context          = context;
        this.listener         = listener;
        this.networkSetting   = networkSetting;
        this.audioSetting     = audioSetting;
        this.videoSetting     = videoSetting;
        this.executor         = Executors.newSingleThreadScheduledExecutor();
        this.candidateHandler = candidateHandler;
        this.candidateHandler.setListener(this);
    }

    private void init() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    initInternal();
                } catch (Exception e) {
                    crush("Failed to initialize PeerConnection: " + e.getMessage());
                    throw e;
                }
            }
        });
    }

    private void initInternal() {

        Peer.initializeRtcIfNeeded(context);

        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        factory = new PeerConnectionFactory(options);

        videoSetting.initFactory(factory);

        this.sdpConstraints = new MediaConstraints();
        this.audioSetting.modifySDPConstraints(this.sdpConstraints);
        this.videoSetting.getRemoteSetting().modifySDPConstraints(this.sdpConstraints);

        this.conn = factory.createPeerConnection(this.networkSetting.rtcConfig(),
                this.networkSetting.constraints(), this.connectionObserver);

        this.audioSetting.init(factory);
        this.videoSetting.getLocalSetting().init(factory);

        MediaStream ms = factory.createLocalMediaStream(UUID.randomUUID().toString());
        this.audioSetting.attachLocalTrackToStream(ms);
        this.videoSetting.getLocalSetting().attachTrackToStream(ms);

        this.listener.onAddLocalStream(ms);

        this.conn.addStream(ms);

        // To handle sending bit-rate later
        this.videoSetting.getLocalSetting().pickRtpSender(conn);
    }

    private Single<SessionDescription> createOfferSDP() {

        return Single.create(new SingleOnSubscribe<SessionDescription>() {

            @Override
            public void subscribe(@NonNull final SingleEmitter<SessionDescription> e) throws Exception {

                conn.createOffer(new SdpObserver() {

                    @Override
                    public void onCreateSuccess(SessionDescription sdp) {

                        SessionDescription filtered =
                                videoSetting.filterSDP(sdp);

                        e.onSuccess(filtered);
                    }

                    @Override
                    public void onCreateFailure(String s) {
                        e.onError(new Error(s));
                    }

                    @Override
                    public void onSetSuccess() {
                        e.onError(new Error("must not come here"));
                    }

                    @Override
                    public void onSetFailure(String s) {
                        e.onError(new Error("must not come here"));
                    }

                }, sdpConstraints);
            }
        }).subscribeOn(Schedulers.from(executor));
    }

    private Single<SessionDescription> createAnswerSDP() {

        return Single.create(new SingleOnSubscribe<SessionDescription>() {
            @Override
            public void subscribe(@NonNull final SingleEmitter<SessionDescription> e) throws Exception {

                conn.createAnswer(new SdpObserver() {

                    @Override
                    public void onCreateSuccess(SessionDescription sdp) {

                        SessionDescription filtered =
                                videoSetting.filterSDP(sdp);

                        e.onSuccess(filtered);
                    }

                    @Override
                    public void onCreateFailure(String s) {
                        e.onError(new Error(s));
                    }

                    @Override
                    public void onSetSuccess() {
                        e.onError(new Error("must not come here"));
                    }

                    @Override
                    public void onSetFailure(String s) {
                        e.onError(new Error("must not come here"));
                    }

                }, sdpConstraints);
            }
        }).subscribeOn(Schedulers.from(executor));
    }

    private Single<SessionDescription> setLocalDescription(final SessionDescription sdp) {

        return Single.create(new SingleOnSubscribe<SessionDescription>() {
            @Override
            public void subscribe(@NonNull final SingleEmitter<SessionDescription> e) throws Exception {

                conn.setLocalDescription(new SdpObserver() {

                    @Override
                    public void onCreateSuccess(SessionDescription sdp) {
                        e.onError(new Error("must not come here"));
                    }

                    @Override
                    public void onCreateFailure(String s) {
                        e.onError(new Error("must not come here"));
                    }

                    @Override
                    public void onSetSuccess() {
                        e.onSuccess(sdp);
                    }

                    @Override
                    public void onSetFailure(String s) {
                        e.onError(new Error(s));
                    }

                }, sdp);
            }
        }).subscribeOn(Schedulers.from(executor));
    }

    private Single<SessionDescription> setRemoteDescription(final SessionDescription remoteSDP) {

        final SessionDescription filtered =
                videoSetting.filterSDP(remoteSDP);

        return Single.create(new SingleOnSubscribe<SessionDescription>() {
            @Override
            public void subscribe(@NonNull final SingleEmitter<SessionDescription> e) throws Exception {

                conn.setRemoteDescription(new SdpObserver() {

                    @Override
                    public void onCreateSuccess(SessionDescription sdp) {
                        e.onError(new Error("must not come here"));
                    }

                    @Override
                    public void onCreateFailure(String s) {
                        e.onError(new Error("must not come here"));
                    }

                    @Override
                    public void onSetSuccess() {
                        e.onSuccess(filtered);
                    }

                    @Override
                    public void onSetFailure(String s) {
                        e.onError(new Error(s));
                    }

                }, filtered);
            }
        }).subscribeOn(Schedulers.from(executor));
    }

    private void onHandshakeCompleted() {
        // SHOULD BE next loop?
        firstHandshakeIsFinished = true;
        listener.onHandshakeCompleted();
        candidateHandler.onOfferAnswerExchanged();
        addBufferedRemoteIceCandidates();
    }

    public Single<SessionDescription> createCapabilities() {
        return createOfferSDP();
    }

    public Single<SessionDescription> createOffer() {
        return createOfferSDP().flatMap(new Function<SessionDescription, Single<SessionDescription>>() {
            @Override
            public Single<SessionDescription> apply(@NonNull SessionDescription sdp) throws Exception {
                return setLocalDescription(sdp);
            }
        });
    }

    public Single<SessionDescription> setRemoteOffer(final SessionDescription remoteOffer) {
        return setRemoteDescription(remoteOffer).flatMap(new Function<SessionDescription, Single<SessionDescription>>(){
            @Override
            public Single<SessionDescription> apply(@NonNull SessionDescription remoteOffer) throws Exception {
                return createAnswerSDP();
            }
        }).flatMap(new Function<SessionDescription, Single<SessionDescription>>() {
            @Override
            public Single<SessionDescription> apply(@NonNull SessionDescription localAnswer) throws Exception {
                return setLocalDescription(localAnswer);
            }
        }).map(new Function<SessionDescription, SessionDescription>(){
            // TODO: shouldn't be map?
            @Override
            public SessionDescription apply(@NonNull SessionDescription sdp) throws Exception {
                onHandshakeCompleted();
                return sdp;
            }
        });
    }

    public Single<SessionDescription> setRemoteAnswer(final SessionDescription remoteAnswer) {
        return setRemoteDescription(remoteAnswer).map(new Function<SessionDescription, SessionDescription>(){
            @Override
            public SessionDescription apply(@NonNull SessionDescription sdp) throws Exception {
                onHandshakeCompleted();
                return sdp;
            }
        });
    }

    /*
     * This method is called when received new candidate from remote through signaling channel.
     */
    public void addRemoteIceCandidate(final IceCandidate candidate) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (isCrushed)
                    return;
                if (firstHandshakeIsFinished) {
                    Log.d(TAG, "offer/answer is completed so candidate is passed to peer");
                    conn.addIceCandidate(candidate);
                } else {
                    Log.d(TAG, "offer/answer is not completed, buffer this candidate");
                    bufferedRemoteCandidates.add(candidate);
                }
            }
        });
    }

    public void removeIceCandidates(final IceCandidate[] candidates) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (isCrushed)
                    return;
                addBufferedRemoteIceCandidates();
                conn.removeIceCandidates(candidates);
            }
        });
    }

    @Override
    public void onLocalIceCandidateHandlerPushCandidate(IceCandidate candidate) {
        IceCandidate[] results = {candidate};
        listener.onLocalIceCandidatesFound(results);
    }

    @Override
    public void onLocalIceCandidateHandlerPushCandidates(List<IceCandidate> candidates) {
        IceCandidate[] results =
                candidates.toArray(new IceCandidate[candidates.size()]);
        listener.onLocalIceCandidatesFound(results);
    }

    private void addBufferedRemoteIceCandidates() {
        for (IceCandidate candidate : bufferedRemoteCandidates) {
            conn.addIceCandidate(candidate);
        }
        bufferedRemoteCandidates.clear();
    }

    private void crush(final String message) {
        Log.d(TAG, "PeerConnection error: " + message);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (!isCrushed) {
                    listener.onError(message);
                    closeInternal();
                }
            }
        });
    }

    public void close() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                closeInternal();
            }
        });
    }

    public void startVideoCapture() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                videoSetting.getLocalSetting().startCapture();
            }
        });
    }

    public void stopVideoCapture() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                videoSetting.getLocalSetting().stopCapture();
            }
        });
    }

    private void closeInternal() {
        if (isCrushed) return;

        if (this.audioSetting != null) {
            this.audioSetting.dispose();
            this.audioSetting = null;
        }

        if (this.videoSetting != null) {
            this.videoSetting.dispose();
            this.videoSetting = null;
        }

        if (this.candidateHandler != null) {
            this.candidateHandler.dispose();
            this.candidateHandler = null;
        }

        if (this.listener != null) {
            this.listener.onClosed();
            this.listener = null;
        }

        if (conn != null) {
            conn.dispose();
            conn = null;
        }

        if (this.factory != null) {
            this.factory.dispose();
            this.factory = null;
        }

        // PeerConnectionFactory.stopInternalTracingCapture();
        // PeerConnectionFactory.shutdownInternalTracer();

        isCrushed = true;
        Log.d(TAG, "connection closed internally.");
    }
}

