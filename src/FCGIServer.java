import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class FCGIServer {
    private Selector selector;
    private ServerSocketChannel serverSocketChannel;

    private static ExecutorService ioExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private static ExecutorService cpuExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 4);

    public FCGIServer(String host, int port) throws IOException{
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.socket().bind(new InetSocketAddress(host, port), 100);
        serverSocketChannel.configureBlocking(false);
    
        selector = Selector.open();
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
    }

    

    public FCGIServer() throws IOException {
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
    

    private void handleRead(SocketChannel socketChannel) throws IOException {
       FCGIReadContext fcgiReadContext = new FCGIReadContext(socketChannel);
       fcgiReadContext.handleFCGI(socketChannel);
    }



    private void handleAccept(SelectionKey key) throws IOException {
        SocketChannel socketChannel = serverSocketChannel.accept();
        socketChannel.socket().setKeepAlive(true);
        socketChannel.socket().setTcpNoDelay(true);
        socketChannel.socket().setReuseAddress(true);
        socketChannel.configureBlocking(false);
        socketChannel.register(selector, SelectionKey.OP_READ);
    }


    public Selector getSelector() {
        return selector;
    }



    public void setSelector(Selector selector) {
        this.selector = selector;
    }



    public ServerSocketChannel getServerSocketChannel() {
        return serverSocketChannel;
    }



    public void setServerSocketChannel(ServerSocketChannel serverSocketChannel) {
        this.serverSocketChannel = serverSocketChannel;
    }



    public static ExecutorService getIoExecutor() {
        return ioExecutor;
    }



    public static void setIoExecutor(ExecutorService ioExecutor) {
        FCGIServer.ioExecutor = ioExecutor;
    }



    public static ExecutorService getCpuExecutor() {
        return cpuExecutor;
    }



    public static void setCpuExecutor(ExecutorService cpuExecutor) {
        FCGIServer.cpuExecutor = cpuExecutor;
    }
}
