package com.example;
import quickfix.*;

public class FixServer {
    public static void main(String[] args) {
        try {
            SessionSettings settings = new SessionSettings("server.cfg");
            Application application = new FixServerApplication();
            MessageStoreFactory storeFactory = new FileStoreFactory(settings);
            LogFactory logFactory = new FileLogFactory(settings);
            MessageFactory messageFactory = new DefaultMessageFactory();

            Acceptor acceptor = new SocketAcceptor(application, storeFactory, settings, logFactory, messageFactory);
            acceptor.start();

            System.out.println("FIX Server started. Press Ctrl+C to stop.");
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                acceptor.stop();
                System.out.println("FIX Server stopped.");
            }));

            Thread.currentThread().join();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
