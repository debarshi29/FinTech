package com.example.oms;

import quickfix.*;
import quickfix.fix44.*;
import quickfix.field.*;
import java.io.InputStream;
import java.time.LocalDateTime;

public class OMSServer implements Application {
    public static void main(String[] args) {
        try {
            // Load configuration from file
            InputStream configStream = OMSServer.class.getClassLoader()
                .getResourceAsStream("oms.cfg");
            
            if (configStream == null) {
                System.err.println("Configuration file oms.cfg not found in resources");
                return;
            }

            SessionSettings settings = new SessionSettings(configStream);
            configStream.close();

            Application application = new OMSServer();
            MessageStoreFactory storeFactory = new FileStoreFactory(settings);
            LogFactory logFactory = new FileLogFactory(settings);
            quickfix.MessageFactory messageFactory = new DefaultMessageFactory();

            Acceptor acceptor = new SocketAcceptor(
                application, storeFactory, settings, logFactory, messageFactory);
            
            System.out.println("Starting OMS Server...");
            System.out.println("Ready to process:");
            System.out.println("- MARKET orders (no price specified)");
            System.out.println("- LIMIT orders (with specified price)");
            acceptor.start();
            System.out.println("Server started. Press Ctrl+C to stop.");
            
            // Keep server running
            Thread.currentThread().join();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void fromApp(quickfix.Message message, SessionID sessionId) 
        throws FieldNotFound, UnsupportedMessageType, IncorrectTagValue {
        
        try {
            if (message instanceof NewOrderSingle) {
                NewOrderSingle nos = (NewOrderSingle) message;
                char ordType = nos.getOrdType().getValue();
                
                // Determine order type and price
                String orderType = (ordType == OrdType.LIMIT) ? "LIMIT" : "MARKET";
                String priceDisplay = (ordType == OrdType.LIMIT) ? 
                    String.format("@ %.2f", nos.getPrice().getValue()) : "@ MARKET";
                
                System.out.printf("\n[OMS] Received %s Order: %s %s %s (Qty: %d)%n",
                    orderType,
                    nos.getSide().getValue() == Side.BUY ? "BUY" : "SELL",
                    nos.getSymbol().getValue(),
                    priceDisplay,
                    nos.getOrderQty().getValue());

                // Create execution report
                ExecutionReport execReport = createExecutionReport(nos, ordType);
                
                Session.sendToTarget(execReport, sessionId);
                System.out.println("[OMS] Sent execution confirmation");
            }
        } catch (Exception e) {
            System.err.println("[ERROR] Processing order: " + e.getMessage());
        }
    }

    private ExecutionReport createExecutionReport(NewOrderSingle order, char ordType) 
        throws FieldNotFound {
        
        // Use market price = 0 for market orders
        double executionPrice = (ordType == OrdType.LIMIT) ? 
            order.getPrice().getValue() : 0;
        
        // Create basic execution report with required fields
        ExecutionReport report = new ExecutionReport(
            new OrderID("EXEC-" + System.currentTimeMillis()),
            new ExecID("EXEC-" + System.currentTimeMillis()),
            new ExecType(ExecType.FILL),
            new OrdStatus(OrdStatus.FILLED),
            order.getSide(),
            new LeavesQty(0),
            new CumQty(order.getOrderQty().getValue()),
            new AvgPx(executionPrice)
        );
        
        // Set additional fields
        report.set(order.getClOrdID());
        report.set(new OrderQty(order.getOrderQty().getValue()));
        report.set(new LastQty(order.getOrderQty().getValue()));
        report.set(new LastPx(executionPrice));
        report.set(new Symbol(order.getSymbol().getValue()));
        report.set(new TransactTime(LocalDateTime.now()));
        report.set(order.getOrdType());  // Include original order type
        
        return report;
    }

    @Override 
    public void onCreate(SessionID sessionId) {
        System.out.println("\nSession created: " + sessionId);
    }
    
    @Override 
    public void onLogon(SessionID sessionId) {
        System.out.println("\nClient logged on: " + sessionId);
    }
    
    @Override 
    public void onLogout(SessionID sessionId) {
        System.out.println("\nClient logged out: " + sessionId);
    }
    
    @Override 
    public void toAdmin(quickfix.Message message, SessionID sessionId) {
        System.out.println("\nAdmin message sent: " + message);
    }
    
    @Override 
    public void fromAdmin(quickfix.Message message, SessionID sessionId) 
        throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, RejectLogon {
        System.out.println("\nAdmin message received: " + message);
    }
    
    @Override 
    public void toApp(quickfix.Message message, SessionID sessionId) throws DoNotSend {
        System.out.println("\nApp message sent: " + message);
    }
}