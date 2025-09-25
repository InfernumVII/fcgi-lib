import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class FCGIReadContext {
    SocketChannel socketChannel;
    FCGIContext fcgiContext = new FCGIContext();
    FCGIWriteContext fcgiWriteContext = new FCGIWriteContext(socketChannel);

    public FCGIReadContext(SocketChannel socketChannel) {
        this.socketChannel = socketChannel;
    }


    private ByteBuffer readExact(SocketChannel socketChannel, ByteBuffer buffer) throws IOException{
        while (buffer.hasRemaining()) {
            int bytesRead = socketChannel.read(buffer);
            if (bytesRead == -1) {
                throw new IOException("End of stream reached");
            }
        }
        buffer.flip();
        return buffer;
    }


    public boolean handleFCGI(SocketChannel channel) throws IOException {
        
        FCGIRecord record;
        
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        do {
            record = readHeader(readExact(channel, ByteBuffer.allocate(8)));
            record.setContentData(readExact(channel, ByteBuffer.allocate(record.getContentLength())));
            final FCGIRecord fcgiRecord = record;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                        processType(fcgiRecord.getType(), 
                                   fcgiRecord.getContentData(), 
                                   fcgiContext);
                    }, FCGIServer.getCpuExecutor());
            futures.add(future);
        } while (record.getType() != FCGIConstants.FCGIStdin);
    
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        return true;
    } 

    private FCGIRecord readHeader(ByteBuffer buffer) throws IOException {   
        int version = buffer.get() & 0xFF;
        int type = buffer.get() & 0xFF;
        int requestId = buffer.getShort() & 0xFFFF;
        int contentLength = buffer.getShort() & 0xFFFF;
        int paddingLength = buffer.get() & 0xFF;
        buffer.get(); // reserved
        // byte[] contentData = new byte[contentLength];
        // buffer.get(contentData);
    
        return new FCGIRecord(version, 
                                type, 
                                requestId, 
                                contentLength, 
                                paddingLength, 
                                null);
    }
    
    private void processType(int type, ByteBuffer data, FCGIContext context) {
        switch (type) {
            case FCGIConstants.FCGIBeginRequest:
                processBeginRequest(data, context);
                break;
            case FCGIConstants.FCGIParams:
                processParams(data, context);
                break;
            case FCGIConstants.FCGIStdin:
                processStdin(data, context);
                break;
            default:
                break;
        }
    }
    
    private void processBeginRequest(ByteBuffer contentData, FCGIContext context) {
        int role = contentData.getShort() & 0xFFFF;
        int flag = contentData.get() & 0xFF;
        context.setRole(role);
        context.setFlag(flag);
    }
    
    private void processParams(ByteBuffer contentData, FCGIContext context) {
        while (contentData.hasRemaining()) {
            int nameLength = readLength(contentData);
            int valueLength = readLength(contentData);
            
            byte[] nameData = new byte[nameLength];
            byte[] valueData = new byte[valueLength];
            
            contentData.get(nameData);
            contentData.get(valueData);
            
            context.getParams().put(new String(nameData, StandardCharsets.UTF_8), 
                                        new String(valueData, StandardCharsets.UTF_8));
        }
    }
    
    private void processStdin(ByteBuffer contentData, FCGIContext context){
        context.setStdinData(contentData);
    }
    
    private static int readLength(ByteBuffer buffer) {
        int firstByte = buffer.get() & 0xFF;
        if ((firstByte & 0x80) == 0) {
            return firstByte; // 1 byte
        }
        // 4 bytes
        return ((firstByte & 0x7F) << 24) | 
                ((buffer.get() & 0xFF) << 16) | 
                ((buffer.get() & 0xFF) << 8) | 
                (buffer.get() & 0xFF);
    }


    public SocketChannel getSocketChannel() {
        return socketChannel;
    }


    public FCGIContext getFcgiContext() {
        return fcgiContext;
    }


    public FCGIWriteContext getFcgiWriteContext() {
        return fcgiWriteContext;
    }

    
}
