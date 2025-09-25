import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class FCGIWriteContext {
    private SocketChannel socketChannel;

    public FCGIWriteContext(SocketChannel socketChannel) {
        this.socketChannel = socketChannel;
        createEndRequest();
    }

    private byte[] createEndRequest() {
        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.putInt(0);
        buffer.put((byte) (FCGIConstants.FCGIRequestComplete & 0xFF));
        buffer.put((byte) 0); //reserved
        buffer.put((byte) 0); //reserved
        buffer.put((byte) 0); //reserved
        return buffer.array();
    }

    private byte[] createHeader(int dataLength, int type) {
        ByteBuffer buffer = ByteBuffer.allocate(FCGIConstants.FCGIHeaderLen);
        buffer.put((byte) (1 & 0xFF)); //version
        buffer.put((byte) (type & 0xFF)); //type
        buffer.putShort((short) (1 & 0xFFFF)); //requestId
        buffer.putShort((short) (dataLength & 0xFFFF)); //contentLength
        buffer.put((byte) (0 & 0xFF)); //paddingLength
        buffer.put((byte) 0); //reserved
        return buffer.array();
    }



    public void write(byte[] data) throws IOException {
        writeSTDOUT(data);
        writeSTDOUT(new byte[0]);
        writeEndRequest();
    }

    private void write(byte[] data, int type) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(data.length + 8);
        byte[] header = createHeader(data.length, type);
        buffer.put(header);
        buffer.put(data);
    
        buffer.flip();
        socketChannel.write(buffer);        
    }

    private void writeSTDOUT(byte[] data) throws IOException {
        write(data, FCGIConstants.FCGIStdout);
    }

    private void writeEndRequest() throws IOException {
        write(createEndRequest(), FCGIConstants.FCGIEndRequest);
    }
}
