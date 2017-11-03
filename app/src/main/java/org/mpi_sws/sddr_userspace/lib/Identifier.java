package org.mpi_sws.sddr_userspace.lib;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class Identifier implements Serializable {

    private static final long serialVersionUID = -7356434238464500164L;
    private byte[] idBytes;

    public Identifier(byte[] idBytes) {
        this.idBytes = idBytes;
    }

    public byte[] getBytes() {
        return idBytes;
    }

    @Override
    public String toString() {
        return Utils.getHexString(idBytes);
    }

    @Override
    public boolean equals(Object o) {
        final Identifier other = (Identifier) o;
        boolean eq = true;
        Utils.myAssert(other.idBytes.length == idBytes.length && idBytes.length == Constants.HANDSHAKE_DH_SIZE);
        
        for (int i=0;i<idBytes.length;i++) {
            if (idBytes[i] != other.idBytes[i]) {
                eq = false;
            }
        }
        return eq;
    }

    @Override
    public int hashCode() {
        throw new UnsupportedOperationException("ID hashcode");
    }

    public static byte[] serialize(Object obj) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ObjectOutputStream os = new ObjectOutputStream(out);
        os.writeObject(obj);
        return out.toByteArray();
    }
    public static Object deserialize(byte[] data) throws IOException, ClassNotFoundException {
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        ObjectInputStream is = new ObjectInputStream(in);
        return is.readObject();
    }
}
