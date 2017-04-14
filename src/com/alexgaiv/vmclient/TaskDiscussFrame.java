package com.alexgaiv.vmclient;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

class TaskMessage
{
    int messageId;
    int taskId;
    String username;
    Date date;
    String messageText;
}

class TaskDiscussFrame extends JFrame
{
    private JPanel mainPanel;
    private JTextPane textPane;
    private JTextArea messageTextField;
    private JButton sendButton;
    private JTextField usernameField;

    private StyleContext sc;
    private StyledDocument textPaneDoc;
    private Style messageHeaderStyle;

    private ServerListener serverListenerThread;
    private int taskId;
    private long lastMessageDate = 0;
    private int lastMessageId = 0;
    private static String username = "";
    private Communicator comm;

    int getTaskId() {
        return taskId;
    }

    TaskDiscussFrame(Communicator comm, int taskId) {
        this.comm = comm;
        this.taskId = taskId;

        setContentPane(mainPanel);
        setSize(700, 500);
        setTitle("Discuss");

        sc = new StyleContext();
        textPaneDoc = new DefaultStyledDocument(sc);

        messageHeaderStyle = sc.addStyle("heading", null);
        messageHeaderStyle.addAttribute(StyleConstants.Bold, true);
        textPane.setDocument(textPaneDoc);

        sendButton.addActionListener(e -> {
            sendMessage();
        });

        messageTextField.getDocument().addDocumentListener(new DocumentListener()
        {
            public void changedUpdate(DocumentEvent e) {
                handle();
            }

            public void removeUpdate(DocumentEvent e) {
                handle();
            }

            public void insertUpdate(DocumentEvent e) {
                handle();
            }

            private void handle() {
                boolean enable = messageTextField.getText().length() != 0;
                if (sendButton.isEnabled() != enable) sendButton.setEnabled(enable);
            }
        });

        usernameField.addFocusListener(new FocusAdapter()
        {
            public void focusLost(FocusEvent e) {
                username = usernameField.getText();
            }
        });

        this.addWindowListener(new WindowAdapter()
        {
            @Override
            public void windowClosing(WindowEvent e) {
                serverListenerThread.interrupt();
            }
        });

        this.usernameField.setText(username);

        serverListenerThread = new ServerListener();

        comm.pullTaskMessages(taskId, 0, e -> {
                    serverListenerThread.start();
                },
                e -> {
                    JOptionPane.showMessageDialog(mainPanel,
                            "Connection Error", "Error", JOptionPane.ERROR_MESSAGE);
                    dispatchEvent(new WindowEvent(TaskDiscussFrame.this, WindowEvent.WINDOW_CLOSING));
                    dispose();
                });
    }

    private void sendMessage() {
        String username = usernameField.getText();
        if (username.length() == 0) username = "Anonymous";

        comm.sendTaskMessage(taskId, username, messageTextField.getText(),
                e1 -> {
                    comm.pullTaskMessages(taskId, lastMessageDate, null, null);
                },
                e1 -> {
                    JOptionPane.showMessageDialog(mainPanel,
                            "Connection Error", "Error", JOptionPane.ERROR_MESSAGE);
                });
    }

    private void addMessage(TaskMessage m) {
        String dateString = new SimpleDateFormat("MMM dd, yyyy, hh:mm:ss a", Locale.US).format(m.date);
        String s = String.format("%s [%s]\n%s\n\n", m.username, dateString, m.messageText);

        int l = textPaneDoc.getLength();
        try {
            textPaneDoc.insertString(l, s, null);
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
        textPaneDoc.setParagraphAttributes(l, 1, messageHeaderStyle, false);
    }

    void putMessages(ArrayList<TaskMessage> messages) {
        if (messages.size() > 0) {
            for (TaskMessage m : messages) {
                if (m.date.getTime() >= lastMessageDate && m.messageId != lastMessageId)
                    addMessage(m);
            }

            TaskMessage lastMessage = messages.get(messages.size() - 1);
            lastMessageDate = lastMessage.date.getTime();
            lastMessageId = lastMessage.messageId;
        }

        CardLayout layout = (CardLayout) mainPanel.getLayout();
        layout.show(mainPanel, "discussCard");
    }

    private class ServerListener extends Thread
    {
        private final static int UPDATE_TIMEOUT = 1500;

        @Override
        public void run() {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    comm.pullTaskMessages(taskId, lastMessageDate, null, null);
                    Thread.sleep(UPDATE_TIMEOUT);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
