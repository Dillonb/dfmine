package com.dillonbeliveau.dfhack.remotefunction;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Created by dillon on 7/16/16.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RPCMessageHeader {
    private short id;
    private int size;

    public RPCMessageHeader(byte[] buffer) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN);
        id = byteBuffer.getShort();
        byteBuffer.getShort(); // Throw away 2 bytes because DFHack is dumb
        size = byteBuffer.getInt();
    }

    public byte[] getBytes() {
        return ByteBuffer.allocate(8)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putShort(id)
                .putShort((short)0) // Insert two empty bytes because DFHack is dumb
                .putInt(size)
                .array();
    }
}
