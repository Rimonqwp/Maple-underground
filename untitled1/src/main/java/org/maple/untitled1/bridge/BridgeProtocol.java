package org.maple.untitled1.bridge;

import org.maple.untitled1.party.PartyMember;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

public final class BridgeProtocol {
    public static final String ACTION_START_RUN = "START_RUN";
    public static final String ACTION_ACK = "ACK";

    private BridgeProtocol() {
    }

    public static byte[] encodeStartRun(UUID runId, String token, String floor, UUID leaderId, List<PartyMember> members) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF(ACTION_START_RUN);
            out.writeUTF(runId.toString());
            out.writeUTF(token != null ? token : "");
            out.writeUTF(floor != null ? floor : "F1");
            out.writeUTF(leaderId.toString());
            out.writeInt(members.size());
            for (PartyMember member : members) {
                out.writeUTF(member.uuid().toString());
                out.writeUTF(member.name());
            }
            out.flush();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to encode dungeon bridge message.", e);
        }
    }

    public static AckMessage decodeAck(byte[] data) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        String action = in.readUTF();
        if (!ACTION_ACK.equals(action)) {
            throw new IOException("Unexpected action: " + action);
        }
        UUID runId = UUID.fromString(in.readUTF());
        boolean success = in.readBoolean();
        String message = in.readUTF();
        return new AckMessage(runId, success, message);
    }

    public record AckMessage(UUID runId, boolean success, String message) {
    }
}
