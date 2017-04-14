package com.alexgaiv.vmclient;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;

interface EventCallback
{
    void eventFired(Object arg);
}

class Communicator
{
    private MessageListener messageListener = null;
    private MessageDispatcher messageDispatcher = null;
    private Socket socket = null;

    private InetAddress IP;
    private final int PORT = 1337;
    private final long RECONNECT_TIMEOUT = 2000;

    private MainForm form = null;

    Communicator(MainForm form)
    {
        try {
            this.IP = InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        this.form = form;
    }

    void connect() {
        messageListener = new MessageListener();
        messageDispatcher = new MessageDispatcher();
        messageListener.start();
        messageDispatcher.start();
    }

    void disconnect() {
        try {
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        messageListener.interrupt();
        messageDispatcher.interrupt();
    }

    void updateTaskQueue() { messageDispatcher.send("<taskQueue>"); }
    void updateTaskHistory() { messageDispatcher.send("<taskHistory>"); }

    void updateTaskDetails(int taskId, EventCallback onError) {
        messageDispatcher.send(null, onError, "<taskDetails>", taskId);
    }

    void sendTask(String name, String programText, EventCallback onSuccess, EventCallback onFail) {
        messageDispatcher.send(onSuccess, onFail, "<newTask>", name, programText);
    }

    void sendTaskMessage(int taskId, String username, String messageText,
                         EventCallback onSuccess, EventCallback onFail)
    {
        messageDispatcher.send(onSuccess, onFail, "<newTaskMessage>", taskId, username, messageText);
    }

    void pullTaskMessages(int taskId, long since, EventCallback onSuccess, EventCallback onFail)
    {
        messageDispatcher.send(onSuccess, onFail, "<taskMessages>", taskId, since);
    }

    private class MessageListener extends Thread
    {
        private DataInputStream in;

        MessageListener() { super("MessageListener"); }

        @Override
        public void run()
        {
            try {
                tryConnect();



                while (!Thread.currentThread().isInterrupted()) {
                    try
                    {
                        String message = in.readUTF();

                        switch (message)
                        {
                            case "<taskHistory>":
                                receiveTaskHistory();
                                break;
                            case "<taskQueue>":
                                receiveTaskQueue();
                                break;
                            case "<taskDetails>":
                                receiveTaskDetails();
                                break;
                            case "<taskMessages>":
                                receiveTaskMessages();
                                break;
                        }
                    }
                    catch (IOException e) {
                        tryConnect();
                    }
                }
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        private void tryConnect() throws InterruptedException
        {
            if (socket != null && !socket.isClosed()) {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            boolean callListener = true;
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    socket = new Socket(IP, PORT);
                    in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                    DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
                    messageDispatcher.setOutputStream(out);
                    form.onConnectionEstablished();
                    return;
                } catch (IOException e)
                {
                    if (callListener) {
                        form.onConnectionFailed();
                    }
                    callListener = false;
                    Thread.sleep(RECONNECT_TIMEOUT);
                }
            }
            // Thread interrupted by Communicator::disconnect()
            throw new InterruptedException();
        }

        private void receiveTaskHistory() throws IOException
        {
            boolean hasNext = in.readBoolean();
            if (hasNext) form.taskHistoryTableModel.setRowCount(0);

            while (hasNext) {
                int taskId = in.readInt();
                String taskName = in.readUTF();
                Date creationDate = new Date(in.readLong());
                double execTime = in.readDouble();
                int status = in.readInt();
                int messageCount = in.readInt();

                TaskStatus taskStatus;
                try {
                    taskStatus = TaskStatus.values()[status];
                }
                catch (IndexOutOfBoundsException e) {
                    taskStatus = TaskStatus.WAITING;
                }

                form.taskHistoryTableModel.addRow(new Object[] {
                        taskId, taskName, creationDate, execTime,
                        taskStatus, messageCount });
                hasNext = in.readBoolean();
            }

            form.showCard(form.historyPanel, "historyCard");
        }

        private void receiveTaskQueue() throws IOException
        {
            form.taskQueueTableModel.setRowCount(0);
            boolean hasNext = in.readBoolean();

            while (hasNext) {
                String taskName = in.readUTF();
                Date creationDate = new Date(in.readLong());
                int status = in.readInt();
                TaskStatus taskStatus;

                try {
                    taskStatus = TaskStatus.values()[status];
                }
                catch (IndexOutOfBoundsException e) {
                    taskStatus = TaskStatus.WAITING;
                }

                form.taskQueueTableModel.addRow(new Object[] {
                    taskName, creationDate, taskStatus
                });
                hasNext = in.readBoolean();
            }

            form.showCard(form.taskQueuePanel, "taskQueueCard");
        }

        private void receiveTaskDetails() throws IOException
        {
            TaskInfo t = new TaskInfo();
            int taskId = in.readInt();
            t.name = in.readUTF();
            t.creationDate = new Date(in.readLong());
            t.programText = in.readUTF();
            t.programOutput = in.readUTF();
            t.status = in.readBoolean();
            t.executionTime = in.readDouble();

            synchronized (form.taskDetailsFrames) {
                for (TaskDetailsFrame frame : form.taskDetailsFrames) {
                    if (frame.getTaskId() == taskId) {
                        frame.setTaskInfo(t);
                    }
                }
            }
        }

        private void receiveTaskMessages() throws IOException
        {
            int taskId = in.readInt();
            boolean hasNext = in.readBoolean();

            ArrayList<TaskMessage> messages = new ArrayList<>();

            while (hasNext) {
                TaskMessage m = new TaskMessage();
                m.taskId = taskId;
                m.messageId = in.readInt();
                m.username = in.readUTF();
                m.messageText = in.readUTF();
                m.date = new Date(in.readLong());
                messages.add(m);
                hasNext = in.readBoolean();
            }

            synchronized (form.taskDiscussFrames) {
                for (TaskDiscussFrame frame : form.taskDiscussFrames) {
                    if (frame.getTaskId() == taskId)
                        frame.putMessages(messages);
                }
            }
        }
    }

    private class MessageDispatcher extends Thread
    {
        private DataOutputStream out = null;
        private ArrayBlockingQueue<MessageQueueEntry> messageQueue =
                new ArrayBlockingQueue<>(100);

        private class MessageQueueEntry
        {
            Object[] messages;
            EventCallback onSuccess;
            EventCallback onFail;

            MessageQueueEntry(Object[] messages, EventCallback onSuccess, EventCallback onFail) {
                this.messages = messages;
                this.onSuccess = onSuccess;
                this.onFail = onFail;
            }
        }

        MessageDispatcher() { super("MessageDispatcher"); }

        void setOutputStream(DataOutputStream out) { this.out = out; }

        void send(Object... messages) { send(null, null, messages); }

        void send(EventCallback onSuccess, EventCallback onFail, Object... messages)
        {
            try {
                messageQueue.put(new MessageQueueEntry(messages, onSuccess, onFail));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public void run()
        {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    MessageQueueEntry entry = messageQueue.take();
                    Object[] messages = entry.messages;

                    try {
                        for (Object obj : messages) {
                            if (obj instanceof String)
                                out.writeUTF((String)obj);
                            else if (obj instanceof Integer)
                                out.writeInt((Integer)obj);
                            else if (obj instanceof Long) {
                                out.writeLong((Long)obj);
                            }
                        }

                        out.flush();
                        if (entry.onSuccess != null) entry.onSuccess.eventFired(messages);
                    } catch (IOException | NullPointerException e) { // in case 'out' is null
                        if (entry.onFail != null) entry.onFail.eventFired(messages);
                    }
                }
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

}
