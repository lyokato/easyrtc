package net.litelite.easyrtc;

import android.util.Log;

import org.webrtc.SessionDescription;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SDPUtil {

    private static final String TAG = SDPUtil.class.getSimpleName();

    public static boolean includeVideoSetting(SessionDescription sdp) {
        String desc = sdp.description;
        String[] lines = desc.split("\r\n");
        String mediaDescription = "m=video ";

        for (int i = 0; i < lines.length; i++) {
            if (lines[i].startsWith(mediaDescription)) {
                return true;
            }
        }
        return false;
    }

    public static SessionDescription filterVideoSDP(SessionDescription sdp, String codec) {
        String desc = sdp.description;
        String[] lines = desc.split("\r\n");
        int mLineIndex = -1;
        String codecRtpMap = null;

        String regex = "^a=rtpmap:(\\d+) " + codec + "(/\\d+)+[\r]?$";
        Pattern codecPattern = Pattern.compile(regex);
        String mediaDescription = "m=video ";

        for (int i = 0; (i < lines.length) && (mLineIndex == -1 || codecRtpMap == null); i++) {
            if (lines[i].startsWith(mediaDescription)) {
                mLineIndex = i;
                continue;
            }
            Matcher codecMatcher = codecPattern.matcher(lines[i]);
            if (codecMatcher.matches()) {
                codecRtpMap = codecMatcher.group(1);
            }
        }

        if (mLineIndex == -1) {
            Log.w(TAG, "prefered codec not found. so, do nothing");
            return sdp;
        }

        if (codecRtpMap == null) {
            Log.w(TAG, "NO rtpmap for " + codec);
            return sdp;
        }

        String[] origMLineParts = lines[mLineIndex].split(" ");
        if (origMLineParts.length > 3) {
            StringBuilder newMLine = new StringBuilder();
            int origPartIndex = 0;
            newMLine.append(origMLineParts[origPartIndex++]).append(" ");
            newMLine.append(origMLineParts[origPartIndex++]).append(" ");
            newMLine.append(origMLineParts[origPartIndex++]).append(" ");
            newMLine.append(codecRtpMap);
            for (; origPartIndex < origMLineParts.length; origPartIndex++) {
                if (!origMLineParts[origPartIndex].equals(codecRtpMap)) {
                    newMLine.append(" ").append(origMLineParts[origPartIndex]);
                }
            }
            lines[mLineIndex] = newMLine.toString();
        }
        StringBuilder newSdpDescription = new StringBuilder();
        for (String line : lines) {
            newSdpDescription.append(line).append("\r\n");
        }
        return new SessionDescription(sdp.type, newSdpDescription.toString());
    }
}
