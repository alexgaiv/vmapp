import com.sun.corba.se.spi.activation.Server;

import java.net.*;
import java.io.*;

public class VmServer
{
    private ServerThread serverThread;
    private ServerSocket socket;
    private final int port = 1337;

    public void start() throws IOException {
        socket = new ServerSocket(port);
        serverThread = new ServerThread();
        serverThread.start();
    }

    public void shutdown() throws IOException {
        serverThread.interrupt();
        socket.close();
    }

    private class ServerThread extends Thread
    {
        @Override
        public void run()
        {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    ClientThread client = new ClientThread(socket.accept());
                    client.start();
                } catch (SocketException e) {
                    System.out.println("server shutdown");
                    return;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private class ClientThread extends Thread
    {
        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;

        public ClientThread(Socket socket) throws IOException {
            this.socket = socket;
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
        }

        @Override
        public void run() {
            try {
                String message;
                //out.println("123");
                do {
                    message = in.readLine();

                }
                while (message != null);
            }
            catch (IOException e) {
                try {
                    socket.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        }
    }

    public static void main(String[] args)
    {
        VmServer server = new VmServer();

        try {
            server.start();
            System.out.println("server started\ntype \"stop\" to quit");
        } catch (IOException e) {
            System.out.println("failed to start server");
        }

        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
            String line;
            do {
                line = br.readLine().toLowerCase();
            }
            while (!line.equals("stop"));
            server.shutdown();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }
}
