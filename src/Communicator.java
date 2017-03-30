import java.io.*;
import java.net.*;
import java.util.ArrayDeque;
import java.util.ArrayList;

public class Communicator
{
    private MessageListener messageListener;
    private MessageDispatcher messageDispatcher;
    private Socket socket;

    private InetAddress IP;
    private final int PORT = 1337;
    private final long RECONNECT_TIMEOUT = 2000;

    private MainForm form;
    private ArrayList<ConnectionStateListener> stateListeners = new ArrayList<>();

    public Communicator(MainForm form)
    {
        try {
            this.IP = InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        this.form = form;
    }

    public void addConnectionStateListener(ConnectionStateListener l) { stateListeners.add(l); }
    public void removeConnectionStateListener(ConnectionStateListener l) { stateListeners.remove(l); }

    public void connect() {
        messageListener = new MessageListener();
        messageDispatcher = new MessageDispatcher();
        messageListener.start();
    }

    public void disconnect() {
        try {
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        messageListener.interrupt();
        messageDispatcher.interrupt();
    }

    public void updateTaskQueue() { messageDispatcher.send("<updateTaskQueue>"); }
    public void updateTaskHistory() { messageDispatcher.send("<updateTaskHistory>"); }

    private class MessageListener extends Thread
    {
        private BufferedReader in;

        public MessageListener() { super("MessageListener"); }

        @Override
        public void run()
        {
            try {
                tryConnect();
                messageDispatcher.start();

                for (ConnectionStateListener l : stateListeners)
                    l.onConnectionEstablished();

                while (!Thread.currentThread().isInterrupted()) {
                    try
                    {
                        String message = in.readLine();
                        if (message == null) throw new IOException();

                        switch (message)
                        {
                            case "<updateTaskHistory>":
                                receiveTaskHistory();
                                break;
                            case "<updateTaskQueue>":
                                receiveTaskQueue();
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
            boolean callListener = true;
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    socket = new Socket(IP, PORT);
                    in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    messageDispatcher.setOutputStream(out);
                    return;
                } catch (IOException e)
                {
                    if (callListener) {
                        for (ConnectionStateListener l : stateListeners)
                            l.onConnectionFailed();
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
            String row = in.readLine();
            while (row != null && !row.equals("<end>"))
            {
                String[] values = row.split(";");
                values[2] = values[2].equals("1") ? "Completed" : "Failed";
                values[3] = values[3] + " message(s)";
                form.taskHistoryTableModel.addRow(values);
                row = in.readLine();
            }
            form.showCard(form.historyPanel, "historyCard");
        }

        private void receiveTaskQueue() throws IOException
        {
            String row = in.readLine();
            while (row != null && !row.equals("<end>"))
            {
                String[] values = row.split(";");
                form.taskQueueTableModel.addRow(values);
                row = in.readLine();
            }
            form.showCard(form.taskQueuePanel, "taskQueueCard");
        }
    }

    private class MessageDispatcher extends Thread
    {
        private PrintWriter out;
        private final Object lock = new Object();
        private boolean messageAvailable = false;
        private ArrayDeque<String> messageQueue = new ArrayDeque<>();

        public MessageDispatcher() { super("MessageDispatcher"); }

        public void send(String message)
        {
            while (messageAvailable) {
                synchronized (lock) {
                    try {
                        lock.wait();
                    } catch (InterruptedException e) { }
                }
            }

            messageQueue.addLast(message);
            messageAvailable = true;
            synchronized (lock) {
                lock.notify();
            }
        }

        public void setOutputStream(PrintWriter out) { this.out = out; }

        @Override
        public void run()
        {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    synchronized (lock) {
                        while (!messageAvailable) {
                            lock.wait();
                        }
                    }
                    messageAvailable = false;
                    synchronized (lock) {
                        lock.notify();
                    }
                    out.println(messageQueue.removeFirst());
                }
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

}
