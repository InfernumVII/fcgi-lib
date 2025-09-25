
// import java.io.BufferedReader;
// import java.io.ByteArrayInputStream;
// import java.io.IOException;
// import java.net.InetSocketAddress;
// import java.net.ServerSocket;
// import java.net.Socket;
// import java.net.SocketAddress;
// import java.nio.ByteBuffer;
// import java.nio.channels.ClosedChannelException;
// import java.nio.channels.SelectionKey;
// import java.nio.channels.Selector;
// import java.nio.channels.ServerSocketChannel;
// import java.nio.channels.SocketChannel;
// import java.nio.charset.StandardCharsets;
// import java.util.ArrayList;
// import java.util.Iterator;
// import java.util.List;
// import java.util.Map;
// import java.util.Set;
// import java.util.concurrent.CompletableFuture;
// import java.util.concurrent.ConcurrentHashMap;
// import java.util.concurrent.ExecutorService;
// import java.util.concurrent.Executors;

// public class FCGIServer {
//     private Selector selector;
//     private ServerSocketChannel serverSocketChannel;
//     ExecutorService ioExecutor = Executors.newVirtualThreadPerTaskExecutor();
//     ExecutorService cpuExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 4);
//     ExecutorService responseExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);
//     private final Map<SocketChannel, ByteBuffer> channelBuffers = new ConcurrentHashMap<>();

//     public FCGIServer(String host, int port) throws IOException{
//         serverSocketChannel = ServerSocketChannel.open();
//         serverSocketChannel.socket().bind(new InetSocketAddress(host, port));
//         serverSocketChannel.configureBlocking(false);

//         selector = Selector.open();
//         serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
//     }

//     public FCGIServer() throws IOException {
//         this("127.0.0.1", 9000);
//     }

//     public void start() throws IOException {
//         while (selector.isOpen()) {
//             selector.select();

//             Set<SelectionKey> selectedKeys = selector.selectedKeys();
//             Iterator<SelectionKey> i = selectedKeys.iterator();
//             while (i.hasNext()) {
//                 SelectionKey key = i.next();
//                 i.remove();

//                 if (key.isAcceptable()) {
//                     handleAccept(key);
//                 }
//                 else if (key.isReadable()) {
//                     key.interestOps(key.interestOps() & ~SelectionKey.OP_READ);
//                     final SocketChannel socketChannel = (SocketChannel) key.channel();
//                     ioExecutor.execute(() -> {
//                         try {
//                             handleRead(socketChannel);
//                         } catch (IOException e) {
//                             throw new RuntimeException(e);
//                         }
//                     });
//                 }
//             }
//         }
//     }


//     private void handleAccept(SelectionKey key) throws IOException {
//         SocketChannel socketChannel = serverSocketChannel.accept();
//         socketChannel.configureBlocking(false);

//         channelBuffers.put(socketChannel, ByteBuffer.allocate(8192));
//         socketChannel.register(selector, SelectionKey.OP_READ);
//     }

//     private void handleRead(SocketChannel socketChannel) throws IOException {
//         cpuExecutor.execute(() -> {
//             ByteBuffer buffer = channelBuffers.get(socketChannel);
//             try {
//                 while (socketChannel.read(buffer) > 0) {
//                     buffer.flip();
                    
//                     processBuffer(socketChannel, buffer);
                    
//                 }
//             } catch (Exception e) {
//                 throw new RuntimeException(e);
//             }
            
//         });
        
//     }

//     private void processBuffer(SocketChannel socketChannel, ByteBuffer buffer) throws IOException  {
//         List<CompletableFuture<Void>> futures = new ArrayList<>();
//         FCGIContext fcgiContext = new FCGIContext();
//         FCGIRecord record;
//         do {
//             record = readHeader(buffer);
//             final FCGIRecord fcgiRecord = record;
//             CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
//                         processType(fcgiRecord.getType(), 
//                                    fcgiRecord.getContentData().asReadOnlyBuffer(), 
//                                    fcgiContext);
//                     }, cpuExecutor);
//             futures.add(future);
//         } while (record.getType() != FCGIConstants.FCGIStdin);
//         CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenRun(() -> {
//             // fcgiContext.getParams().forEach((k, v) -> {
//             //     System.out.printf("Key: %s, Value: %s\n", k, v);
//             // });
//             String hi = "Status: 200\nContent-Type: text/plain\n\nHello-From-JAVA!";
//             try {
//                 writeSTDOUT(socketChannel, hi.getBytes(StandardCharsets.UTF_8));
//                 writeSTDOUT(socketChannel, new byte[0]);
//                 writeEndRequest(socketChannel);
//                 socketChannel.close();
//             } catch (IOException e) {
//                 throw new RuntimeException(e);
//             }
//         });
//     }

//     private byte[] createHeader(int dataLength, int type) {
//         ByteBuffer buffer = ByteBuffer.allocate(FCGIConstants.FCGIHeaderLen);
//         buffer.put((byte) (1 & 0xFF)); //version
//         buffer.put((byte) (type & 0xFF)); //type
//         buffer.putShort((short) (1 & 0xFFFF)); //requestId
//         buffer.putShort((short) (dataLength & 0xFFFF)); //contentLength
//         buffer.put((byte) (0 & 0xFF)); //paddingLength
//         buffer.put((byte) 0); //reserved
//         return buffer.array();
//     }

//     private void write(SocketChannel socket, byte[] data, int type) throws IOException {
//         ByteBuffer buffer = ByteBuffer.allocate(8192);
//         buffer.put(createHeader(data.length, type));
//         buffer.put(data);

//         buffer.flip();
//         socket.write(buffer);        
//     }

//     private void writeEndRequest(SocketChannel socketChannel) throws IOException {
//         ByteBuffer buffer = ByteBuffer.allocate(8);
//         buffer.putInt(0);
//         buffer.put((byte) (FCGIConstants.FCGIRequestComplete & 0xFF));
//         buffer.put((byte) 0); //reserved
//         buffer.put((byte) 0); //reserved
//         buffer.put((byte) 0); //reserved
//         write(socketChannel, buffer.array(), FCGIConstants.FCGIEndRequest);
//     }

//     private void writeSTDOUT(SocketChannel socket, byte[] data) throws IOException {
//         write(socket, data, FCGIConstants.FCGIStdout);
//     }
    
//     private FCGIRecord readHeader(ByteBuffer buffer) throws IOException {   
//         int version = buffer.get() & 0xFF;
//         int type = buffer.get() & 0xFF;
//         int requestId = buffer.getShort() & 0xFFFF;
//         int contentLength = buffer.getShort() & 0xFFFF;
//         int paddingLength = buffer.get() & 0xFF;
//         buffer.get(); // reserved
//         byte[] contentData = new byte[contentLength];
//         buffer.get(contentData);

//         return new FCGIRecord(version, 
//                                 type, 
//                                 requestId, 
//                                 contentLength, 
//                                 paddingLength, 
//                                 ByteBuffer.wrap(contentData));
//     }



//     private void processType(int type, ByteBuffer data, FCGIContext context) {
//         switch (type) {
//             case FCGIConstants.FCGIBeginRequest:
//                 processBeginRequest(data, context);
//                 break;
//             case FCGIConstants.FCGIParams:
//                 processParams(data, context);
//                 break;
//             case FCGIConstants.FCGIStdin:
//                 processStdin(data, context);
//                 break;
//             default:
//                 break;
//         }
//     }

//     private void processBeginRequest(ByteBuffer contentData, FCGIContext context) {
//         int role = contentData.getShort() & 0xFFFF;
//         int flag = contentData.get() & 0xFF;
//         context.setRole(role);
//         context.setFlag(flag);
//     }

//     private void processParams(ByteBuffer contentData, FCGIContext context) {
//         while (contentData.hasRemaining()) {
//             int nameLength = readLength(contentData);
//             int valueLength = readLength(contentData);
            
//             byte[] nameData = new byte[nameLength];
//             byte[] valueData = new byte[valueLength];
            
//             contentData.get(nameData);
//             contentData.get(valueData);
            
//             context.getParams().put(new String(nameData, StandardCharsets.UTF_8), 
//                                         new String(valueData, StandardCharsets.UTF_8));
//         }
//     }

//     private void processStdin(ByteBuffer contentData, FCGIContext context){
//         context.setStdinData(contentData);
//         context.setReady(true);
//     }

//     private static int readLength(ByteBuffer buffer) {
//         int firstByte = buffer.get() & 0xFF;
//         if ((firstByte & 0x80) == 0) {
//             return firstByte; // 1 byte
//         }
//         // 4 bytes
//         return ((firstByte & 0x7F) << 24) | 
//                 ((buffer.get() & 0xFF) << 16) | 
//                 ((buffer.get() & 0xFF) << 8) | 
//                 (buffer.get() & 0xFF);
//     }
// }
