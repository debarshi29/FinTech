package com.example;

import quickfix.*;
import quickfix.fix44.NewOrderSingle;
import quickfix.field.*;

import java.time.LocalDateTime;

public class FixClient extends MessageCracker implements Application {
    private SessionID sessionId;

    public static void main(String[] args) throws Exception {
        // Load session settings from client.cfg
        SessionSettings settings = new SessionSettings("client.cfg");
        FixClient application = new FixClient();

        // Create required factories
        MessageStoreFactory storeFactory = new FileStoreFactory(settings);
        LogFactory logFactory = new FileLogFactory(settings);
        MessageFactory messageFactory = new DefaultMessageFactory();

        // Initialize initiator
        Initiator initiator = new SocketInitiator(application, storeFactory, settings, logFactory, messageFactory);
        initiator.start();
        System.out.println("FIX Client started.");

        // Wait for logon before sending order
        Thread.sleep(5000);
        application.sendNewOrder();
    }

    public void sendNewOrder() throws Exception {
        // Create a FIX 4.4 NewOrderSingle message
        NewOrderSingle order = new NewOrderSingle(
            new ClOrdID("ORD-" + System.currentTimeMillis()), // Unique client order ID
            new Side(Side.BUY), // Buy order
            new TransactTime(LocalDateTime.now()), // ✅ Fixed: Correct type for TransactTime
            new OrdType(OrdType.MARKET) // Market order
        );

        // Set additional required fields
        order.set(new HandlInst('3')); // '3' = Automated Execution Order (Not held)
        order.set(new Symbol("AAPL")); // Stock symbol
        order.set(new OrderQty(100)); // Quantity: 100 shares
        order.set(new TimeInForce(TimeInForce.DAY)); // Order valid for the day

        // Send order to target session
        try {
            Session.sendToTarget(order, sessionId);
            System.out.println("New order sent: " + order);
        } catch (SessionNotFound e) {
            System.err.println("Failed to send order: " + e.getMessage());
        }
    }

    // Session lifecycle methods
    @Override
    public void onLogon(SessionID sessionId) {
        System.out.println("Client logged on: " + sessionId);
        this.sessionId = sessionId;
    }

    @Override
    public void onCreate(SessionID sessionId) {
        System.out.println("Session created: " + sessionId);
    }

    @Override
    public void onLogout(SessionID sessionId) {
        System.out.println("Logged out: " + sessionId);
        this.sessionId = null;
    }

    @Override
    public void toAdmin(Message message, SessionID sessionId) {
        System.out.println("To Admin: " + message);
    }

    @Override
    public void fromAdmin(Message message, SessionID sessionId) 
        throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue {
        System.out.println("From Admin: " + message);
    }

    @Override
    public void toApp(Message message, SessionID sessionId) throws DoNotSend {
        System.out.println("To App: " + message);
    }

    @Override
    public void fromApp(Message message, SessionID sessionId) 
        throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, UnsupportedMessageType {
        System.out.println("From App: " + message);
        crack(message, sessionId);
    }
}
