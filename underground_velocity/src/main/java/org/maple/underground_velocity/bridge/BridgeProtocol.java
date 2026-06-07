package org.maple.underground_velocity.bridge;

import org.maple.underground_velocity.party.PartyMember;

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
    public static final String ACTION_END_RUN = "END_RUN";

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
        Message message = decode(data);
        if (message instanceof AckMessage ack) {
            return ack;
        }
        throw new IOException("Unexpected bridge message: " + message.getClass().getSimpleName());
    }

    public static Message decode(byte[] data) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        String action = in.readUTF();
        if (ACTION_ACK.equals(action)) {
            UUID runId = UUID.fromString(in.readUTF());
            boolean success = in.readBoolean();
            String message = in.readUTF();
            return new AckMessage(runId, success, message);
        }
        if (ACTION_END_RUN.equals(action)) {
            UUID runId = UUID.fromString(in.readUTF());
            String reason = in.readUTF();
            return new EndRunMessage(runId, reason);
        }
        throw new IOException("Unexpected action: " + action);
    }

    public sealed interface Message permits AckMessage, EndRunMessage {
    }

    public record AckMessage(UUID runId, boolean success, String message) implements Message {
    }

    public record EndRunMessage(UUID runId, String reason) implements Message {
    }
}
