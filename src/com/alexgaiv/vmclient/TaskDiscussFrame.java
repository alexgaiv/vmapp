package com.alexgaiv.vmclient;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
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
    private JScrollPane scrollPane;

    private StyleContext sc;
    private StyledDocument textPaneDoc;
    private Style messageHeaderStyle;

    private ServerListener serverListenerThread;
    private int taskId;
    private long lastMessageDate = 0;
    private int lastMessageId = 0;
    private Communicator comm;

    int getTaskId() { return taskId; }

    TaskDiscussFrame(Communicator comm, int taskId)
    {
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

        sendButton.addActionListener(e ->
        {
            String username = usernameField.getText();
            if (username.length() == 0) username = "Anonymous";
            comm.sendTaskMessage(taskId, username, messageTextField.getText(), null, e1 -> {
                JOptionPane.showMessageDialog(mainPanel,
                        "Connection Error", "Error", JOptionPane.ERROR_MESSAGE);
            });
        });

        messageTextField.addKeyListener(new KeyAdapter() {
            public void keyTyped(KeyEvent e) {
                sendButton.setEnabled(messageTextField.getText().length() != 0);
            }
        });

        messageTextField.getDocument().addDocumentListener(new DocumentListener() {
            public void changedUpdate(DocumentEvent e) { handle(); }
            public void removeUpdate(DocumentEvent e) { handle(); }
            public void insertUpdate(DocumentEvent e) { handle(); }

            private void handle() {
                boolean enable = messageTextField.getText().length() != 0;
                if (sendButton.isEnabled() != enable) sendButton.setEnabled(enable);
            }
        });

        this.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                serverListenerThread.interrupt();
            }
        });

        serverListenerThread = new ServerListener();
        serverListenerThread.start();
    }

    private void addMessage(TaskMessage m)
    {
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

    void putMessages(ArrayList<TaskMessage> messages)
    {
        if (messages.size() > 0) {
            //messages.forEach(this::addMessage);

            for (TaskMessage m : messages) {
                if (m.date.getTime() >= lastMessageDate && m.messageId != lastMessageId)
                    addMessage(m);
            }

            TaskMessage lastMessage = messages.get(messages.size() - 1);
            lastMessageDate = lastMessage.date.getTime();
            lastMessageId = lastMessage.messageId;
        }

        CardLayout layout = (CardLayout)mainPanel.getLayout();
        layout.show(mainPanel, "discussCard");
    }

    private class ServerListener extends Thread
    {
        private final static int UPDATE_TIMEOUT = 2000;

        @Override
        public void run()
        {
            try {
                ArrayList<TaskMessage> messages = new ArrayList<>();
                boolean success = comm.pullTaskMessages(taskId, 0, messages);

                if (success)
                    putMessages(messages);
                else {
                    JOptionPane.showMessageDialog(mainPanel,
                            "Connection Error", "Error", JOptionPane.ERROR_MESSAGE);
                    dispose();
                    return;
                }

                while (!Thread.currentThread().isInterrupted())
                {
                    Thread.sleep(UPDATE_TIMEOUT);
                    messages.clear();
                    success = comm.pullTaskMessages(taskId, lastMessageDate, messages);
                    if (success) putMessages(messages);
                }
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
