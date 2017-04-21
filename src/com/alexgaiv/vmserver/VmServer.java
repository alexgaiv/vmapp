package com.alexgaiv.vmserver;

import java.net.*;
import java.io.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.concurrent.*;
import com.alexgaiv.vmserver.parser.*;

enum TaskStatus
{
    WAITING(2),
    RUNNING(3);

    TaskStatus(int code) { this.code = code; }
    int getCode() { return code; }
    private int code;
}

class Task
{
    String name = "";
    long creationDate = 0;
    String programText = "";
    TaskStatus status = TaskStatus.WAITING;

    public Task() { }

    public Task(String name, long creationDate) {
        this.name = name;
        this.creationDate = creationDate;
    }
}

public class VmServer
{
    private final int MAX_EXEC_THREAD = 5;
    private ServerThread serverThread;
    private ClientNotifier notifierThread;
    private ExecutorService executorService;
    private boolean started = false;

    private ServerSocket socket;
    private final int PORT = 4000;
    private boolean isServerShutdown = false;

    private LinkedBlockingQueue<Task> taskQueue = new LinkedBlockingQueue<>();
    private LinkedBlockingQueue<Task> runningTasks = new LinkedBlockingQueue<>();
    private ArrayList<ClientThread> clients = new ArrayList<>();
    private final Object clientsLock = new Object();

    private Connection connection;

    private static final String taskHistoryQuery =
        "SELECT id, name, creation_date, exec_time, status, IFNULL(count, 0) as count " +
            "FROM task_history LEFT JOIN (" +
                "SELECT task_id, COUNT(*) as count " +
                    "FROM messages " +
                    "GROUP BY task_id " +
                ") " +
            "ON task_id = task_history.id";

    private static final String taskDetailsQuery =
            "SELECT * FROM task_history WHERE id = ?";

    private static final String taskMessagesQuery =
            "SELECT * FROM messages WHERE task_id = ? ORDER BY date";

    private static final String pullTaskMessagesQuery =
            "SELECT * FROM messages WHERE task_id = ? AND date > ? ORDER BY date";

    private static final String newTaskMessageQuery =
            "INSERT INTO messages (task_id, username, message, date) VALUES (?, ?, ?, ?)";

    private static final String newTaskQuery =
            "INSERT INTO task_history (name, creation_date, program_text, output, status, exec_time) " +
                    "VALUES (?, ?, ?, ?, ?, ?)";

    public void start() throws IOException, IllegalStateException
    {
        if (started)
            throw new IllegalStateException("Server already running");
        started = true;

        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        try {
            connection = DriverManager.getConnection(org.sqlite.JDBC.PREFIX + "vm.db");
        } catch (SQLException e) {
            e.printStackTrace();
        }

        socket = new ServerSocket(PORT);

        serverThread = new ServerThread();
        notifierThread = new ClientNotifier();
        executorService = Executors.newFixedThreadPool(MAX_EXEC_THREAD);

        notifierThread.start();
        serverThread.start();
    }

    public void shutdown() throws IOException, IllegalStateException
    {
        if (!started)
            throw new IllegalStateException("Server is not running");

        System.out.println("server shutdown...");

        isServerShutdown = true;
        serverThread.interrupt();
        notifierThread.interrupt();
        try {
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            serverThread.join();
            notifierThread.join();
            executorService.shutdown();
            boolean terminated = executorService.awaitTermination(5, TimeUnit.SECONDS);
            if (!terminated) {
                System.out.println("warning: there are uncompleted tasks");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        synchronized (clientsLock) {
            for (ClientThread c : clients)
                c.disconnect();
            clients.clear();
        }

        try {
            connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private class ServerThread extends Thread
    {
        ServerThread() { super("ServerThread"); }

        @Override
        public void run()
        {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    ClientThread client = new ClientThread(socket.accept());
                    synchronized (clientsLock) {
                        int i = clients.size();
                        client.setIndex(i);
                        clients.add(client);
                    }
                    client.start();
                } catch (SocketException e) {
                    // Socket closed by VmServer::shutdown
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
        private DataInputStream in;
        private DataOutputStream out;
        private Statement taskHistoryStatement;
        private PreparedStatement taskDetailsStatement;
        private PreparedStatement taskMessagesStatement;
        private PreparedStatement pullTaskMessagesStatement;
        private PreparedStatement newTaskMessageStatement;
        private int index = 0;

        ClientThread(Socket socket) throws IOException {
            super("ClientThread");
            this.socket = socket;
            in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        }

        void setIndex(int i) { index = i; }

        void disconnect() {
            try {
                taskHistoryStatement.close();
                taskDetailsStatement.close();
                taskMessagesStatement.close();
                pullTaskMessagesStatement.close();
                newTaskMessageStatement.close();
                socket.close(); // causes thread to interrupt by IOException (if it's not yet happened)
            } catch (IOException | SQLException e) {
                e.printStackTrace();
            }

            if (!isServerShutdown) removeFromClients();
        }

        private void removeFromClients()
        {
            synchronized (clientsLock) {
                int size = clients.size();
                if (size > index) {
                    ClientThread c = clients.get(index);
                    if (c == this) {
                        ClientThread last = clients.get(size - 1);
                        clients.set(index, last);
                        clients.remove(size - 1);
                    }
                }
            }
        }

        @Override
        public void run()
        {
            try {
                taskHistoryStatement = connection.createStatement();
                taskDetailsStatement = connection.prepareStatement(taskDetailsQuery);
                taskMessagesStatement = connection.prepareStatement(taskMessagesQuery);
                pullTaskMessagesStatement = connection.prepareStatement(pullTaskMessagesQuery);
                newTaskMessageStatement = connection.prepareStatement(newTaskMessageQuery);

                String message;
                while (!Thread.currentThread().isInterrupted())
                {
                    message = in.readUTF();

                    switch (message)
                    {
                        case "<taskHistory>":
                            sendTaskHistory();
                            break;
                        case "<taskQueue>":
                            sendTaskQueue();
                            break;
                        case "<taskDetails>":
                            sendTaskDetails(in.readInt());
                            break;
                        case "<newTask>":
                            newTask();
                            break;
                        case "<taskMessages>":
                            sendTaskMessages(in.readInt(), in.readLong());
                            break;
                        case "<newTaskMessage>":
                            newTaskMessage(in.readInt(), in.readUTF(), in.readUTF());
                            break;
                    }
                }
            }
            catch (IOException | SQLException e) {
                // Client's connection lost or VmServer::shutdown called
                if (!isServerShutdown) disconnect();
            }
        }

        private void newTask() throws IOException
        {
            Task task = new Task();
            task.name = in.readUTF();
            task.programText = in.readUTF();
            task.creationDate = new java.util.Date().getTime();

            taskQueue.offer(task);
            notifierThread.notifyClients(true, false);
            executorService.execute(new ProgramExecutorThread());
        }

        private void newTaskMessage(int taskId, String username, String text) throws IOException
        {
            try {
                long date = new java.util.Date().getTime();
                newTaskMessageStatement.setInt(1, taskId);
                newTaskMessageStatement.setString(2, username);
                newTaskMessageStatement.setString(3, text);
                newTaskMessageStatement.setLong(4, date);
                newTaskMessageStatement.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        void sendTaskHistory() throws IOException
        {
            try (ResultSet rs = taskHistoryStatement.executeQuery(taskHistoryQuery))
            {
                out.writeUTF("<taskHistory>");
                while (rs.next()) {
                    out.writeBoolean(true);
                    out.writeInt(rs.getInt("id"));
                    out.writeUTF(rs.getString("name"));
                    out.writeLong(rs.getLong("creation_date"));
                    out.writeDouble(rs.getDouble("exec_time"));
                    out.writeInt(rs.getInt("status"));
                    out.writeInt(rs.getInt("count"));
                }
                out.writeBoolean(false);
            }
            catch (SQLException e) {
                out.writeUTF("<error>");
            }
            out.flush();
        }

        void sendTaskQueue() throws IOException
        {
            out.writeUTF("<taskQueue>");

            for (Task task : taskQueue) {
                out.writeBoolean(true);
                out.writeUTF(task.name);
                out.writeLong(task.creationDate);
                out.writeInt(task.status.getCode());
            }

            for (Task task : runningTasks) {
                out.writeBoolean(true);
                out.writeUTF(task.name);
                out.writeLong(task.creationDate);
                out.writeInt(task.status.getCode());
            }

            out.writeBoolean(false);
            out.flush();
        }

        void sendTaskDetails(int taskId) throws IOException
        {
            try {
                taskDetailsStatement.setInt(1, taskId);
                ResultSet rs = taskDetailsStatement.executeQuery();

                if (!rs.next())
                    out.writeUTF("<error>");
                else {
                    out.writeUTF("<taskDetails>");
                    out.writeInt(taskId);
                    out.writeUTF(rs.getString("name"));
                    out.writeLong(rs.getLong("creation_date"));
                    out.writeUTF(rs.getString("program_text"));
                    out.writeUTF(rs.getString("output"));
                    out.writeBoolean(rs.getInt("status") == 1);
                    out.writeDouble(rs.getDouble("exec_time"));
                }
                rs.close();
            }
            catch (SQLException e) {
                out.writeUTF("<error>");
            }
            out.flush();
        }

        void sendTaskMessages(int taskId, long since) throws IOException
        {
            try {
                pullTaskMessagesStatement.setInt(1, taskId);
                pullTaskMessagesStatement.setLong(2, since);
                ResultSet rs = pullTaskMessagesStatement.executeQuery();

                out.writeUTF("<taskMessages>");
                out.writeInt(taskId);

                while (rs.next()) {
                    out.writeBoolean(true);
                    out.writeInt(rs.getInt("id"));
                    out.writeUTF(rs.getString("username"));
                    out.writeUTF(rs.getString("message"));
                    out.writeLong(rs.getLong("date"));
                }
                out.writeBoolean(false);
                rs.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
            out.flush();
        }
    }

    private class ClientNotifier extends Thread
    {
        private final Object lock = new Object();
        private long updateTaskQueue = 0;
        private long updateTaskHistory = 0;

        ClientNotifier() { super("ClientNotifier"); }

        void notifyClients(boolean taskQueueChanged, boolean taskHistoryChanged) {
            synchronized (lock) {
                if (taskQueueChanged) updateTaskQueue++;
                if (taskHistoryChanged) updateTaskHistory++;

                if (updateTaskQueue == 1 || updateTaskHistory == 1)
                    lock.notify();
            }
        }

        @Override
        public void run()
        {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    boolean fUpdateTaskQueue;
                    boolean fUpdateTaskHistory;

                    synchronized (lock) {
                        while (updateTaskQueue == 0 && updateTaskHistory == 0)
                            lock.wait();
                        fUpdateTaskQueue = updateTaskQueue != 0;
                        fUpdateTaskHistory = updateTaskHistory != 0;
                        updateTaskQueue = updateTaskHistory = 0;
                    }

                    synchronized (clientsLock) {
                        for (ClientThread c : clients) {
                            try {
                                if (fUpdateTaskQueue)
                                    c.sendTaskQueue();
                                if (fUpdateTaskHistory)
                                    c.sendTaskHistory();
                            } catch (IOException e) {
                                // ignore
                            }
                        }
                    }

                    Thread.sleep(1000);
                }
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private class ProgramExecutorThread implements Runnable
    {
        @Override
        public void run()
        {
            try {
                Task task = taskQueue.take();
                task.status = TaskStatus.RUNNING;
                runningTasks.put(task);

                notifierThread.notifyClients(true, false);

                ProgramExecutor exec = new ProgramExecutor();

                long dt = new java.util.Date().getTime();
                ProgramExecuteResult result = exec.execute(task.programText);
                dt = new java.util.Date().getTime() - dt;

                if (!result.success)
                    result.programOutput = result.errorMessage;

                try (PreparedStatement st = connection.prepareStatement(newTaskQuery)) {
                    st.setString(1, task.name);
                    st.setLong(2, task.creationDate);
                    st.setString(3, task.programText);
                    st.setString(4, result.programOutput);
                    st.setInt(5, result.success ? 1 : 0);
                    st.setDouble(6, (double) dt);

                    st.executeUpdate();
                } catch (SQLException e) {
                    e.printStackTrace();
                }

                runningTasks.remove(task);
                notifierThread.notifyClients(true, true);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
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
