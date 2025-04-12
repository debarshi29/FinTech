package com.example;

import quickfix.*;
import quickfix.fix44.*;
import quickfix.field.*;
import org.jline.reader.*;
import org.jline.terminal.*;
import java.time.LocalDateTime;
import java.io.InputStream;

public class FixClient implements Application {
    private SessionID sessionId;
    private Terminal terminal;
    private LineReader reader;

    public static void main(String[] args) throws Exception {
        new FixClient().start();
    }

    public void start() throws Exception {
        try {
            // Initialize terminal
            terminal = TerminalBuilder.builder()
                .system(true)
                .build();
            reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .build();

            // Load configuration
            InputStream configStream = getClass().getClassLoader()
                .getResourceAsStream("client.cfg");
            
            if (configStream == null) {
                terminal.writer().println("Error: client.cfg not found in resources");
                return;
            }

            SessionSettings settings = new SessionSettings(configStream);
            configStream.close();

            // Initialize FIX engine
            MessageStoreFactory storeFactory = new FileStoreFactory(settings);
            LogFactory logFactory = new FileLogFactory(settings);
            quickfix.MessageFactory messageFactory = new DefaultMessageFactory();
            Initiator initiator = new SocketInitiator(
                this, storeFactory, settings, logFactory, messageFactory);

            initiator.start();
            terminal.writer().println("FIX Client started. Waiting for logon...");
            terminal.writer().println("Available order types:");
            terminal.writer().println("- LIMIT orders: Specify price (e.g. 'buy AAPL 100 150.25')");
            terminal.writer().println("- MARKET orders: Use -1 for price (e.g. 'sell MSFT 50 -1')");

            // Wait for logon
            while (sessionId == null) {
                Thread.sleep(500);
                if (!initiator.isLoggedOn()) {
                    terminal.writer().println("Waiting for connection...");
                }
            }

            // Start interactive shell
            terminal.writer().println("\nConnected to FIX server. Type 'help' for commands");
            while (true) {
                String line;
                try {
                    line = reader.readLine("FIX> ");
                    if (line == null || "exit".equalsIgnoreCase(line.trim())) {
                        break;
                    }
                    processCommand(line.trim());
                } catch (UserInterruptException e) {
                    // Handle Ctrl+C
                    terminal.writer().println("\nType 'exit' to quit");
                } catch (EndOfFileException e) {
                    // Handle Ctrl+D
                    break;
                }
            }

            initiator.stop();
            terminal.writer().println("FIX Client stopped");
        } finally {
            if (terminal != null) {
                terminal.close();
            }
        }
    }

    private void processCommand(String command) {
        try {
            if (command.isEmpty()) return;
            
            String[] parts = command.split("\\s+");
            switch (parts[0].toLowerCase()) {
                case "buy":
                case "sell":
                    sendOrder(
                        parts[0].charAt(0),
                        parts.length > 1 ? parts[1] : "AAPL",
                        parts.length > 2 ? Integer.parseInt(parts[2]) : 100,
                        parts.length > 3 ? Double.parseDouble(parts[3]) : -1,
                        parts.length > 4 ? parts[4] : "DAY"
                    );
                    break;
                    
                case "help":
                    printHelp();
                    break;
                    
                case "status":
                    terminal.writer().println("Connection status: " + 
                        (sessionId != null ? "Connected" : "Disconnected"));
                    break;
                    
                default:
                    terminal.writer().println("Unknown command. Type 'help' for available commands");
            }
        } catch (Exception e) {
            terminal.writer().println("Error: " + e.getMessage());
            printHelp();
        }
    }

    private void printHelp() {
        terminal.writer().println("\nAvailable commands:");
        terminal.writer().println("  buy SYM QTY [PRICE] [TIF]  - Buy order (omit price or use -1 for market)");
        terminal.writer().println("  sell SYM QTY [PRICE] [TIF] - Sell order");
        terminal.writer().println("  status                   - Show connection status");
        terminal.writer().println("  help                     - Show this help");
        terminal.writer().println("  exit                     - Quit the application\n");
        terminal.writer().println("Order Types:");
        terminal.writer().println("- LIMIT: Specify price (e.g. 'buy AAPL 100 150.25 DAY')");
        terminal.writer().println("- MARKET: Use -1 for price (e.g. 'sell MSFT 50 -1 IOC')");
        terminal.writer().println("\nTime-in-Force (TIF) options: DAY, IOC, FOK, GTC");
    }

    private void sendOrder(char side, String symbol, int quantity, double price, String tif) 
        throws SessionNotFound, FieldNotFound, IllegalArgumentException {
        
        // Validate inputs
        if (quantity <= 0) throw new IllegalArgumentException("Quantity must be positive");
        if (price < -1) throw new IllegalArgumentException("Price must be positive or -1 for market orders");
        if (price == 0) throw new IllegalArgumentException("Price cannot be 0 (use -1 for market orders)");
        
        // Validate TimeInForce
        if (!tif.matches("DAY|IOC|FOK|GTC")) {
            throw new IllegalArgumentException("Invalid TimeInForce. Valid values: DAY, IOC, FOK, GTC");
        }

        boolean isMarketOrder = price == -1;
        NewOrderSingle order = new NewOrderSingle(
            new ClOrdID("ORD-" + System.currentTimeMillis()),
            new Side(side),
            new TransactTime(LocalDateTime.now()),
            new OrdType(isMarketOrder ? OrdType.MARKET : OrdType.LIMIT)
        );

        order.set(new HandlInst('1')); // Automated execution
        order.set(new Symbol(symbol));
        order.set(new OrderQty(quantity));
        if (!isMarketOrder) order.set(new Price(price));
        order.set(new TimeInForce(tif.charAt(0)));

        Session.sendToTarget(order, sessionId);
        
        // Enhanced output message
        terminal.writer().printf("Sent %s %s order: %d %s @ %s (TIF: %s)%n", 
            side == Side.BUY ? "BUY" : "SELL",
            isMarketOrder ? "MARKET" : "LIMIT",
            quantity,
            symbol,
            isMarketOrder ? "MARKET" : String.format("%.2f", price),
            tif);
    }

    @Override 
    public void fromApp(quickfix.Message message, SessionID sessionId) throws FieldNotFound {
        try {
            if (message instanceof ExecutionReport) {
                ExecutionReport report = (ExecutionReport)message;
                String orderType = report.getOrdType().getValue() == OrdType.MARKET ? 
                    "MARKET" : "LIMIT";
                String priceInfo = report.getOrdType().getValue() == OrdType.MARKET ?
                    "MARKET PRICE" : String.format("@ %.2f", report.getAvgPx().getValue());
                
                terminal.writer().printf("\n[FIX] %s Order Execution: %s %s %s %d/%d shares%n",
                    orderType,
                    report.getSide().getValue() == Side.BUY ? "BUY" : "SELL",
                    report.getSymbol().getValue(),
                    priceInfo,
                    report.getLastQty().getValue(),
                    report.getOrderQty().getValue());
            } else {
                terminal.writer().println("\n[FIX] Received: " + message);
            }
        } catch (FieldNotFound e) {
            terminal.writer().println("\n[FIX] Received execution report with missing fields");
        }
    }

    // Other callbacks remain the same...
    @Override public void onLogon(SessionID sessionId) { 
        this.sessionId = sessionId; 
        terminal.writer().println("\nSuccessfully logged on to FIX server");
    }
    
    @Override public void onLogout(SessionID sessionId) { 
        this.sessionId = null;
        terminal.writer().println("\nDisconnected from FIX server"); 
    }
    
    @Override public void onCreate(SessionID sessionId) {
        terminal.writer().println("\nSession created: " + sessionId);
    }
    
    @Override public void toAdmin(quickfix.Message message, SessionID sessionId) {
        terminal.writer().println("\n[FIX] Admin message sent: " + message);
    }
    
    @Override public void fromAdmin(quickfix.Message message, SessionID sessionId) 
        throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, RejectLogon {
        terminal.writer().println("\n[FIX] Admin message received: " + message);
    }
    
    @Override public void toApp(quickfix.Message message, SessionID sessionId) throws DoNotSend {
        terminal.writer().println("\n[FIX] App message sent: " + message);
    }
}