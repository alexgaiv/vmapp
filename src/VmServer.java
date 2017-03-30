import java.net.*;
import java.io.*;
import java.sql.*;
import java.util.ArrayDeque;
import java.util.ListIterator;

public class VmServer
{
    private ServerThread serverThread;
    private ServerSocket socket;
    private final int port = 1337;

    Connection connection;
    Statement statement;

    private class Task
    {
        String name = "";
        String startTime = "";

        public Task() { }

        public Task(String name, String startTime) {
            this.name = name;
            this.startTime = startTime;
        }
    }

    ArrayDeque<Task> taskQueue = new ArrayDeque<>();

    static private final String taskHistoryQuery =
        "SELECT name, start_time, status, IFNULL(count, 0) as count FROM " +
            "task_history LEFT JOIN (" +
                "SELECT task_id, COUNT(*) as count " +
                    "FROM messages " +
                    "GROUP BY task_id " +
                ")" +
            "ON task_id = task_history.id";

    public void start() throws IOException
    {
        taskQueue.addLast(new Task("Bubble", "15:00:00"));
        taskQueue.addLast(new Task("Complex", "15:30:02"));
        taskQueue.addLast(new Task("MPI", "15:40:01"));
        taskQueue.addLast(new Task("OpenMP", "15:50:20"));

        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        try {
            connection = DriverManager.getConnection(org.sqlite.JDBC.PREFIX + "vm.db");
            statement = connection.createStatement();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        socket = new ServerSocket(port);
        serverThread = new ServerThread();
        serverThread.start();
    }

    public void shutdown() throws IOException {
        serverThread.interrupt();
        socket.close();

        try {
            serverThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        try {
            connection.close();
            statement.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
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
            out = new PrintWriter(socket.getOutputStream());
        }

        @Override
        public void run() {
            try {
                String message;
                while ((message = in.readLine()) != null) {
                    switch (message)
                    {
                        case "<updateTaskHistory>":
                            sendTaskHistory();
                            break;
                        case "<updateTaskQueue>":
                            sendTaskQueue();
                            break;
                    }
                }
            }
            catch (IOException e) {
                try {
                    socket.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        }

        private void sendTaskHistory()
        {
            try (ResultSet rs = statement.executeQuery(taskHistoryQuery))
            {
                out.println("<updateTaskHistory>");
                while (rs.next()) {
                    String msg = String.format("%s;%s;%d;%d",
                            rs.getString("name"), rs.getString("start_time"),
                            rs.getInt("status"), rs.getInt("count"));
                    out.println(msg);
                }
                out.println("<end>");
                out.flush();

            }
            catch (SQLException e) {
                out.println("<error>");
                out.flush();
            }
        }

        private void sendTaskQueue()
        {
            out.println("<updateTaskQueue>");

            for (Task task : taskQueue)
                out.println(task.name + ";" + task.startTime);
            out.println("<end>");
        }
    }

    public static void main(String[] args)
    {
        try {
            VmServer server = new VmServer();

            System.out.print("starting server...");
            server.start();
            System.out.println("done\ntype \"stop\" to quit");

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
        } catch (IOException e) {
            System.out.println("failed to start server");
        }
    }
}
