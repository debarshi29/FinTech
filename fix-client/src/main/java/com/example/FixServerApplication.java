package com.example;
import quickfix.*;

public class FixServerApplication extends MessageCracker implements Application {
    @Override
    public void onCreate(SessionID sessionId) {
        System.out.println("FIX Server: Session created -> " + sessionId);
    }

    @Override
    public void onLogon(SessionID sessionId) {
        System.out.println("FIX Server: Client logged in -> " + sessionId);
    }

    @Override
    public void onLogout(SessionID sessionId) {
        System.out.println("FIX Server: Client logged out -> " + sessionId);
    }

    @Override
    public void toAdmin(Message message, SessionID sessionId) {
        System.out.println("FIX Server: Sending admin message -> " + message);
    }

    @Override
    public void fromAdmin(Message message, SessionID sessionId) {
        System.out.println("FIX Server: Received admin message -> " + message);
    }

    @Override
    public void toApp(Message message, SessionID sessionId) {
        System.out.println("FIX Server: Sending application message -> " + message);
    }

    @Override
    public void fromApp(Message message, SessionID sessionId) {
        System.out.println("FIX Server: Received application message -> " + message);
    }
}
