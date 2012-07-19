package com.euronext.client;

import org.quickfixj.jmx.JmxExporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import quickfix.*;

import javax.swing.*;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;

public class FixClient {
    private static final CountDownLatch shutdownLatch = new CountDownLatch(1);

    /** enable logging for this class */
    private static Logger log = LoggerFactory.getLogger(FixClient.class);
    private static FixClient banzai;
    private boolean initiatorStarted = false;
    private Initiator initiator = null;
    private JFrame frame = null;

    public FixClient(String[] args) throws Exception {
        InputStream inputStream = null;
        if (args.length == 0) {
            inputStream = FixClient.class.getResourceAsStream("banzai.cfg");
        } else if (args.length == 1) {
            inputStream = new FileInputStream(args[0]);
        }
        if (inputStream == null) {
            System.out.println("usage: " + FixClient.class.getName() + " [configFile].");
            return;
        }
        SessionSettings settings = new SessionSettings(inputStream);
        inputStream.close();

        boolean logHeartbeats = Boolean.valueOf(System.getProperty("logHeartbeats", "true")).booleanValue();

        FixClientApplication application = new FixClientApplication();
        MessageStoreFactory messageStoreFactory = new FileStoreFactory(settings);
        LogFactory logFactory = new ScreenLogFactory(true, true, true, logHeartbeats);
        MessageFactory messageFactory = new DefaultMessageFactory();

        initiator = new SocketInitiator(application, messageStoreFactory, settings, logFactory,
                messageFactory);

        JmxExporter exporter = new JmxExporter();
        exporter.register(initiator);
    }

    public synchronized void logon() {
        if (!initiatorStarted) {
            try {
                initiator.start();
                initiatorStarted = true;
            } catch (Exception e) {
                log.error("Logon failed", e);
            }
        } else {
            Iterator<SessionID> sessionIds = initiator.getSessions().iterator();
            while (sessionIds.hasNext()) {
                SessionID sessionId = (SessionID) sessionIds.next();
                Session.lookupSession(sessionId).logon();
            }
        }
    }

    public void logout() {
        Iterator<SessionID> sessionIds = initiator.getSessions().iterator();
        while (sessionIds.hasNext()) {
            SessionID sessionId = (SessionID) sessionIds.next();
            Session.lookupSession(sessionId).logout("user requested");
        }
    }

    public void stop() {
        shutdownLatch.countDown();
    }

    public JFrame getFrame() {
        return frame;
    }

    public static FixClient get() {
        return banzai;
    }

    public static void main(String args[]) throws Exception {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            log.info(e.getMessage(), e);
        }
        banzai = new FixClient(args);
        if (!System.getProperties().containsKey("openfix")) {
            banzai.logon();
        }
        shutdownLatch.await();
    }
}