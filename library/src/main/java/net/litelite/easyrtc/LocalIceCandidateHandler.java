package net.litelite.easyrtc;

import org.webrtc.IceCandidate;

import java.util.ArrayList;
import java.util.List;

public class LocalIceCandidateHandler {

    public enum Type {
        Dumb,
        Direct,
        Buffered
    }

    public interface Listener {
        void onLocalIceCandidateHandlerPushCandidate(IceCandidate candidate);
        void onLocalIceCandidateHandlerPushCandidates(List<IceCandidate> candidates);
    }

    public interface Handler {
        void setListener(Listener listener);
        void handleCandidate(IceCandidate candidate);
        void onOfferAnswerExchanged();
        void dispose();
    }

    public static class BufferedHandler implements Handler {

        private Listener listener;
        private List<IceCandidate> bufferedCandidates = new ArrayList<IceCandidate>();
        private boolean initialOfferAnswerExchanged = false;

        @Override
        public void setListener(Listener listener) {
            this.listener = listener;
        }

        @Override
        public void handleCandidate(IceCandidate candidate) {
            if (this.initialOfferAnswerExchanged) {
                if (this.listener != null) {
                    this.listener.onLocalIceCandidateHandlerPushCandidate(candidate);
                }
            } else {
                this.bufferedCandidates.add(candidate);
            }
        }

        @Override
        public void onOfferAnswerExchanged() {
            if (this.initialOfferAnswerExchanged) {
                return;
            }
            this.initialOfferAnswerExchanged = true;
            if (this.bufferedCandidates.size() == 0) {
                return;
            }
            if (this.listener != null) {
                this.listener.onLocalIceCandidateHandlerPushCandidates(this.bufferedCandidates);
                this.bufferedCandidates.clear();
            }
        }

        @Override
        public void dispose() {
            this.listener = null;
        }
    }

    public static class DirectHandler implements Handler {

        private Listener listener;

        @Override
        public void setListener(Listener listener) {

        }

        @Override
        public void handleCandidate(IceCandidate candidate) {
            if (this.listener != null) {
                this.listener.onLocalIceCandidateHandlerPushCandidate(candidate);
            }
        }

        @Override
        public void onOfferAnswerExchanged() {

        }

        @Override
        public void dispose() {
            this.listener = null;
        }
    }

    public static class DumbHandler implements Handler {

        @Override
        public void setListener(Listener listener) {

        }

        @Override
        public void handleCandidate(IceCandidate candidate) {

        }

        @Override
        public void onOfferAnswerExchanged() {

        }

        @Override
        public void dispose() {

        }
    }

    public static Handler create(Type type) {
        switch (type) {
            case Buffered:
                return new BufferedHandler();
            case Direct:
                return new DirectHandler();
            case Dumb:
                return new DumbHandler();
            default:
                return null;
        }
    }
}
