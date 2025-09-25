import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class FCGIServer2 {
    private Selector selector;
    private ServerSocketChannel serverSocketChannel;

    ExecutorService ioExecutor = Executors.newVirtualThreadPerTaskExecutor();
    ExecutorService cpuExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 4);
    AtomicInteger currentC = new AtomicInteger();
    AtomicInteger proccesedI = new AtomicInteger();
    AtomicInteger proccesedO = new AtomicInteger();
    public FCGIServer2(String host, int port) throws IOException{
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.socket().bind(new InetSocketAddress(host, port), 100);
        serverSocketChannel.configureBlocking(false);
    
        selector = Selector.open();
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
    }

    public FCGIServer2() throws IOException {
        this("127.0.0.1", 9000);
    }


    public void start() throws IOException {
        while (selector.isOpen()) {
            selector.select();
    
            Set<SelectionKey> selectedKeys = selector.selectedKeys();
            System.out.println("SelectedKeysSize: " + selectedKeys.size());
            Iterator<SelectionKey> i = selectedKeys.iterator();
            while (i.hasNext()) {
                SelectionKey key = i.next();
                i.remove();
    
                if (key.isAcceptable()) {
                    handleAccept(key);
                }
                else if (key.isReadable()) {
                    key.interestOps(key.interestOps() & ~SelectionKey.OP_READ);
                    final SocketChannel socketChannel = (SocketChannel) key.channel();
                    ioExecutor.execute(() -> {
                        System.out.println(currentC.incrementAndGet());
                        try {
                            handleRead(socketChannel);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
                }
            }
        }
    }

    private void handleRead(SocketChannel channel) throws IOException {
        
        FCGIContext fcgiContext = new FCGIContext();
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
                    }, cpuExecutor);
            futures.add(future);
        } while (record.getType() != FCGIConstants.FCGIStdin);

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenRun(() -> {
            String hi = "Status: 200\nContent-Type: text/plain\n\nHello-From-JAVA!";
            FCGIWriteContext fcgiWriteContext = new FCGIWriteContext(channel);
            try {
                fcgiWriteContext.write(hi.getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
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

    private void handleAccept(SelectionKey key) throws IOException {
        SocketChannel socketChannel = serverSocketChannel.accept();
        socketChannel.socket().setKeepAlive(true);
        socketChannel.socket().setTcpNoDelay(true);
        socketChannel.socket().setReuseAddress(true);
        socketChannel.configureBlocking(false);
        socketChannel.register(selector, SelectionKey.OP_READ);
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
        System.out.println("Input: " + proccesedI.incrementAndGet());
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
}
