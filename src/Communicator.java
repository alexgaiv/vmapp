import java.io.*;
import java.net.*;

public class Communicator
{
    private MessageListener messageListener;
    private MessageDispatcher messageDispatcher;
    private Socket socket;

    private InetAddress IP;
    private final int PORT = 1337;
    private final long RECONNECT_TIMEOUT = 5000;

    private MainForm form;

    public Communicator(MainForm form)
    {
        try {
            this.IP = InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        this.form = form;
    }

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

    public void updateTaskQueue() {
        //messageDispatcher.send("updateTaskQueue");
    }

    public void updateHistory() {
        //messageDispatcher.send("updateHistory");
    }

    private class MessageListener extends Thread
    {
        private BufferedReader in;

        public MessageListener() {
            super("MessageListener");
        }

        @Override
        public void run()
        {
            try {
                tryConnect();
                messageDispatcher.start();

                while (!Thread.currentThread().isInterrupted()) {
                    try
                    {
                        String message;
                        do {
                            message = in.readLine();
                            //form.addMessage(message);
                        } while (message != null);
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
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    socket = new Socket(IP, PORT);
                    in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    messageDispatcher.setOutputStream(out);
                    return;
                } catch (IOException e) {
                    Thread.sleep(RECONNECT_TIMEOUT);
                }
            }
            // Thread interrupted by Communicator::disconnect()
            throw new InterruptedException();
        }
    }

    private class MessageDispatcher extends Thread
    {
        private PrintWriter out;
        private final Object sendLock = new Object();
        private String messageToSend;

        public MessageDispatcher() {
            super("MessageDispatcher");
        }

        public void send(String message) {
            messageToSend = message;
            synchronized (sendLock) {
                sendLock.notify();
            }
        }

        public void setOutputStream(PrintWriter out) {
            this.out = out;
        }

        @Override
        public void run()
        {
            try {
                form.onConnectionEstablished();
                while (!Thread.currentThread().isInterrupted()) {
                    synchronized (sendLock) {
                        sendLock.wait();
                    }
                    out.println(messageToSend);
                }
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

}
