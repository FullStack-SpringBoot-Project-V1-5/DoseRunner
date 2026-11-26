package tn.rnu.eniso.doserunner.custody.bo;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Canonical byte encoding of a link: the exact bytes that are signed and hashed.
 *
 * <p>Format v1 (big-endian, every variable-length field is length-prefixed so two different links can never encode
 * to the same bytes):
 * <pre>
 *   "doserunner.custody.v1"      domain-separation tag (a signature over a link can't be replayed as anything else)
 *   str   eventId
 *   str   orderId
 *   i64   seq
 *   str   type
 *   opt   actorId                0x00 = null, 0x01 + str
 *   opt   robotId
 *   bytes payloadHash
 *   bytes prevHash
 *   i64   ts (epoch milliseconds)
 *
 *   str   = u32 length + UTF-8 bytes      bytes = u32 length + raw bytes
 * </pre>
 * Changing this format invalidates every stored signature: add a v2 tag instead.
 */
public final class LinkCodec {

    static final byte[] TAG = "doserunner.custody.v1".getBytes(StandardCharsets.US_ASCII);

    private LinkCodec() {
    }

    /** Everything except the signature itself. */
    public static byte[] signedBytes(Link link) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream(256);
        try (DataOutputStream out = new DataOutputStream(buf)) {
            out.write(TAG);
            str(out, link.eventId());
            str(out, link.orderId());
            out.writeLong(link.seq());
            str(out, link.type().name());
            optStr(out, link.actorId());
            optStr(out, link.robotId());
            bytes(out, link.payloadHash());
            bytes(out, link.prevHash());
            out.writeLong(link.ts().toEpochMilli());
        } catch (IOException e) {
            throw new UncheckedIOException(e); // ByteArrayOutputStream never throws
        }
        return buf.toByteArray();
    }

    private static void str(DataOutputStream out, String s) throws IOException {
        bytes(out, s.getBytes(StandardCharsets.UTF_8));
    }

    private static void optStr(DataOutputStream out, String s) throws IOException {
        if (s == null) {
            out.writeByte(0);
        } else {
            out.writeByte(1);
            str(out, s);
        }
    }

    private static void bytes(DataOutputStream out, byte[] b) throws IOException {
        out.writeInt(b.length);
        out.write(b);
    }
}
