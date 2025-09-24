import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class HighLoadTcpServer {
    private final int port;
    private final AtomicInteger connectionCount;
    private volatile boolean isRunning;
    private Selector selector;
    private ServerSocketChannel serverChannel;
    private final ExecutorService workerExecutor;
    
    public HighLoadTcpServer(int port) {
        this.port = port;
        this.connectionCount = new AtomicInteger(0);
        this.workerExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }
    
    public void start() throws IOException {
        selector = Selector.open();
        serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.socket().setReuseAddress(true);
        serverChannel.bind(new InetSocketAddress(port));
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        
        isRunning = true;
        
        System.out.println("HighLoad TCP Server started on port " + port);
        
        // Запуск основного цикла обработки
        Thread.startVirtualThread(this::mainLoop);
        Thread.startVirtualThread(this::monitorStatistics);
    }
    
    private void mainLoop() {
        while (isRunning) {
            try {
                if (selector.select(1000) > 0) {
                    Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();
                    
                    while (keyIterator.hasNext()) {
                        SelectionKey key = keyIterator.next();
                        keyIterator.remove();
                        
                        if (!key.isValid()) continue;
                        
                        if (key.isAcceptable()) {
                            acceptConnection(key);
                        } else if (key.isReadable()) {
                            handleRead(key);
                        }
                    }
                }
            } catch (IOException e) {
                if (isRunning) {
                    System.err.println("Selector error: " + e.getMessage());
                }
            }
        }
    }
    
    private void acceptConnection(SelectionKey key) throws IOException {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
        SocketChannel clientChannel = serverChannel.accept();
        clientChannel.configureBlocking(false);
        clientChannel.socket().setTcpNoDelay(true);
        clientChannel.socket().setSoTimeout(30000);
        
        ClientSession session = new ClientSession(clientChannel);
        clientChannel.register(selector, SelectionKey.OP_READ, session);
        
        int currentConnections = connectionCount.incrementAndGet();
        System.out.printf("Client connected: %s [Total: %d]%n", 
                         clientChannel.getRemoteAddress(), currentConnections);
        
        // Отправляем приветственное сообщение
        sendWelcomeMessage(clientChannel, currentConnections);
    }
    
    private void sendWelcomeMessage(SocketChannel channel, int currentConnections) throws IOException {
        String welcome = "Welcome to HighLoad Server! Connected clients: " + currentConnections + "\n";
        ByteBuffer buffer = ByteBuffer.wrap(welcome.getBytes(StandardCharsets.UTF_8));
        channel.write(buffer);
    }
    
    private void handleRead(SelectionKey key) {
        SocketChannel channel = (SocketChannel) key.channel();
        ClientSession session = (ClientSession) key.attachment();
        
        workerExecutor.submit(() -> processClientData(channel, session));
    }
    
    private void processClientData(SocketChannel channel, ClientSession session) {
        try {
            ByteBuffer readBuffer = ByteBuffer.allocate(1024);
            int bytesRead = channel.read(readBuffer);
            
            if (bytesRead == -1) {
                closeConnection(channel, session);
                return;
            }
            
            if (bytesRead > 0) {
                readBuffer.flip();
                List<String> messages = session.processData(readBuffer);
                
                for (String message : messages) {
                    if ("exit".equalsIgnoreCase(message)) {
                        sendResponse(channel, "Goodbye!\n");
                        closeConnection(channel, session);
                        return;
                    }
                    
                    String response = processRequest(message);
                    sendResponse(channel, response + "\n");
                }
            }
        } catch (IOException e) {
            closeConnection(channel, session);
        }
    }
    
    private String processRequest(String request) {
        // Эмуляция обработки (0-50мс)
        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(50));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        if ("stats".equalsIgnoreCase(request)) {
            return "STATS: Active connections: " + connectionCount.get();
        }
        if ("ping".equalsIgnoreCase(request)) {
            return "PONG";
        }
        
        return "ECHO: " + new StringBuilder(request).reverse().toString();
    }
    
    private void sendResponse(SocketChannel channel, String response) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(response.getBytes(StandardCharsets.UTF_8));
        
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
    }
    
    private void closeConnection(SocketChannel channel, ClientSession session) {
        try {
            if (channel.isOpen()) {
                channel.close();
            }
        } catch (IOException e) {
            // Игнорируем ошибку закрытия
        }
        
        int currentConnections = connectionCount.decrementAndGet();
        try {
            System.out.printf("Client disconnected: %s [Remaining: %d]%n", 
                             channel.socket().getRemoteSocketAddress(), currentConnections);
        } catch (Exception e) {
            System.out.printf("Client disconnected: [Unknown] [Remaining: %d]%n", currentConnections);
        }
    }
    
    private void monitorStatistics() {
        while (isRunning) {
            try {
                Thread.sleep(10000);
                int connections = connectionCount.get();
                System.out.printf("[STATS] Active connections: %d, Memory: %dMB%n",
                    connections,
                    Runtime.getRuntime().totalMemory() / (1024 * 1024));
                
                // Автоматическое масштабирование при высокой нагрузке
                if (connections > 1000) {
                    System.gc(); // Рекомендуется при работе с виртуальными потоками
                }
            } catch (InterruptedException e) {
                break;
            }
        }
    }
    
    public void stop() {
        isRunning = false;
        try {
            if (selector != null) {
                selector.close();
            }
            if (serverChannel != null) {
                serverChannel.close();
            }
            
            workerExecutor.shutdown();
            if (!workerExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                workerExecutor.shutdownNow();
            }
        } catch (Exception e) {
            System.err.println("Error during shutdown: " + e.getMessage());
        }
        System.out.println("Server stopped gracefully");
    }
    
    // Сессия клиента с буферизацией сообщений
    private static class ClientSession {
        private final SocketChannel channel;
        private final long connectedTime;
        private final ByteBuffer readBuffer;
        private final StringBuilder messageBuffer;
        private static final byte DELIMITER = '\n';
        
        public ClientSession(SocketChannel channel) {
            this.channel = channel;
            this.connectedTime = System.currentTimeMillis();
            this.readBuffer = ByteBuffer.allocate(4096); // 4KB буфер
            this.messageBuffer = new StringBuilder();
        }
        
        public List<String> processData(ByteBuffer newData) throws IOException {
            List<String> completeMessages = new ArrayList<>();
            
            // Копируем новые данные в буфер
            if (readBuffer.remaining() < newData.remaining()) {
                // Увеличиваем буфер если нужно
                resizeBuffer(readBuffer.position() + newData.remaining());
            }
            readBuffer.put(newData);
            
            // Обрабатываем данные
            readBuffer.flip();
            while (readBuffer.hasRemaining()) {
                byte b = readBuffer.get();
                
                if (b == DELIMITER) {
                    // Завершение сообщения
                    String message = messageBuffer.toString().trim();
                    if (!message.isEmpty()) {
                        completeMessages.add(message);
                    }
                    messageBuffer.setLength(0); // Очищаем буфер
                } else {
                    messageBuffer.append((char) b);
                    
                    // Защита от слишком длинных сообщений
                    if (messageBuffer.length() > 8192) {
                        throw new IOException("Message too long");
                    }
                }
            }
            
            readBuffer.compact(); // Готовим буфер для следующего чтения
            return completeMessages;
        }
        
        private void resizeBuffer(int minCapacity) {
            int newCapacity = Math.max(readBuffer.capacity() * 2, minCapacity);
            ByteBuffer newBuffer = ByteBuffer.allocate(newCapacity);
            readBuffer.flip();
            newBuffer.put(readBuffer);
            readBuffer = newBuffer;
        }
    }
    
    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        
        HighLoadTcpServer server = new HighLoadTcpServer(port);
        try {
            server.start();
            System.out.println("Press Enter to stop the server...");
            System.in.read();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            server.stop();
        }
    }
} {
    
}
