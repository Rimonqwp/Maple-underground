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
    public static final String ACTION_RESET_SLOTS = "RESET_SLOTS";
    public static final String ACTION_ACK = "ACK";
    public static final String ACTION_END_RUN = "END_RUN";

    private BridgeProtocol() {
    }

    public static byte[] encodeResetSlots(String token, String preloadFloor, List<SlotReset> slots) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF(ACTION_RESET_SLOTS);
            out.writeUTF(token != null ? token : "");
            out.writeUTF(preloadFloor != null ? preloadFloor : "F1");
            out.writeInt(slots != null ? slots.size() : 0);
            if (slots != null) {
                for (SlotReset slot : slots) {
                    out.writeUTF(slot.slotId() != null ? slot.slotId() : "");
                    out.writeUTF(slot.worldName() != null ? slot.worldName() : "");
                }
            }
            out.flush();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to encode dungeon slot reset message.", e);
        }
    }

    public static byte[] encodeStartRun(
            UUID runId,
            String token,
            String floor,
            String slotId,
            String worldName,
            UUID leaderId,
            List<PartyMember> members
    ) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF(ACTION_START_RUN);
            out.writeUTF(runId.toString());
            out.writeUTF(token != null ? token : "");
            out.writeUTF(floor != null ? floor : "F1");
            out.writeUTF(slotId != null ? slotId : "");
            out.writeUTF(worldName != null ? worldName : "");
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
            String slotId = in.available() > 0 ? in.readUTF() : "";
            String worldName = in.available() > 0 ? in.readUTF() : "";
            return new AckMessage(runId, success, message, slotId, worldName);
        }
        if (ACTION_END_RUN.equals(action)) {
            UUID runId = UUID.fromString(in.readUTF());
            String slotId = in.available() > 0 ? in.readUTF() : "";
            String worldName = in.available() > 0 ? in.readUTF() : "";
            String reason = in.available() > 0 ? in.readUTF() : "";
            return new EndRunMessage(runId, slotId, worldName, reason);
        }
        throw new IOException("Unexpected action: " + action);
    }

    public sealed interface Message permits AckMessage, EndRunMessage {
    }

    public record AckMessage(UUID runId, boolean success, String message, String slotId, String worldName) implements Message {
    }

    public record EndRunMessage(UUID runId, String slotId, String worldName, String reason) implements Message {
    }

    public record SlotReset(String slotId, String worldName) {
    }
}
