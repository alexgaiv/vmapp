package com.alexgaiv.vmclient;

import java.io.File;
import java.awt.*;
import javax.swing.*;
import java.awt.event.*;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.LinkedList;
import javax.swing.border.EmptyBorder;
import javax.swing.table.*;

public class MainForm extends JFrame
{
    JPanel newTaskPanel;
    JPanel taskQueuePanel;
    JPanel historyPanel;
    final LinkedList<TaskDetailsFrame> taskDetailsFrames = new LinkedList<>();

    DefaultTableModel taskHistoryTableModel;
    DefaultTableModel taskQueueTableModel;

    private JPanel mainPanel;
    private JList<String> menuList;
    private JPanel cardPanel;
    private JButton nextButton;
    private JButton backButton;
    private JButton sendToServerButton;
    private JTextField taskNameField;
    private JTextArea taskEditTextArea;
    private JRadioButton chooseAFileRadioButton;
    private JRadioButton inputAProgramRadioButton;
    private JButton openButton;
    private JLabel fileNameLabel;
    private JPanel progressBarPanel;
    private JTable taskQueueTable;
    private JTable historyTable;
    private JButton viewTaskDetailsButton;
    private JButton discussTaskButton;

    private File programFile = null;
    private boolean programSendSuccess = false;
    private boolean connectionEstablished = false;
    private boolean connectionFailedFirstTime = true;
    private Communicator communicator;

    public static void main(String[] args)
    {
        try {
            UIManager.setLookAndFeel(javax.swing.plaf.nimbus.NimbusLookAndFeel.class.getCanonicalName());
            //UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (
                ClassNotFoundException |
                InstantiationException |
                IllegalAccessException |
                UnsupportedLookAndFeelException e)
        {
            e.printStackTrace();
        }

        MainForm frame = new MainForm();
        frame.setVisible(true);
    }

    void showCard(JPanel panel, String name) {
        CardLayout layout = (CardLayout)panel.getLayout();
        layout.show(panel, name);
    }

    void onConnectionEstablished() {
        if (!connectionEstablished) {
            showCard(taskQueuePanel, "progressBarCard");
            showCard(historyPanel, "progressBarCard");
        }
        communicator.updateTaskQueue();
        communicator.updateTaskHistory();
        connectionEstablished = true;
        connectionFailedFirstTime = false;
    }

    void onConnectionFailed() {
        if (connectionFailedFirstTime) {
            showCard(taskQueuePanel, "connectionFailedCard");
            showCard(historyPanel, "connectionFailedCard");
            connectionFailedFirstTime = false;
        }
        connectionEstablished = false;
    }

    private int getSelectedTaskId()
    {
        int row = historyTable.getSelectedRow();
        return Integer.valueOf((String)taskHistoryTableModel.getValueAt(row, 0));
    }

    private String getSelectedTaskName()
    {
        int row = historyTable.getSelectedRow();
        return (String)taskHistoryTableModel.getValueAt(row, 1);
    }

    private void enableTaskEdit(boolean enabled) {
        taskEditTextArea.setEditable(enabled);
        chooseAFileRadioButton.setEnabled(enabled);
        inputAProgramRadioButton.setEnabled(enabled);
        if (chooseAFileRadioButton.isSelected()) {
            openButton.setEnabled(enabled);
            fileNameLabel.setEnabled(enabled);
        }
        sendToServerButton.setEnabled(enabled);
    }

    private void sendProgramToServer()
    {
        showCard(progressBarPanel, "progressBarCard");
        enableTaskEdit(false);
        programSendSuccess = false;

        String programText = null;
        if (inputAProgramRadioButton.isSelected()) {
            programText = taskEditTextArea.getText();
        }
        else {
            try {
                programText = new String(Files.readAllBytes(programFile.toPath()), Charset.defaultCharset());
            } catch (IOException e) {
                showCard(progressBarPanel, "emptyCard");
                enableTaskEdit(true);
                JOptionPane.showMessageDialog(mainPanel,
                        "Cannot open file \"" + programFile.getName() + "\"", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }

        communicator.sendTask(taskNameField.getText(), programText, e -> {
            showCard(progressBarPanel, "readyCard");
            backButton.setText("New Task");
            programSendSuccess = true;
        }, e -> {
            showCard(progressBarPanel, "emptyCard");
            enableTaskEdit(true);
            JOptionPane.showMessageDialog(mainPanel,
                    "Connection Error", "Error", JOptionPane.ERROR_MESSAGE);
        });
    }

    private void openTaskDetailsFrame(int taskId)
    {
        TaskDetailsFrame frame = new TaskDetailsFrame(communicator, taskId);

        frame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                synchronized (taskDetailsFrames) {
                    taskDetailsFrames.remove(frame);
                }
            }
        });

        synchronized (taskDetailsFrames) {
            taskDetailsFrames.add(frame);
        }
        frame.update();
        frame.setLocationRelativeTo(this);
        frame.setVisible(true);
    }

    private void openTaskDiscussFrame(int taskId)
    {
        TaskDiscussFrame frame = new TaskDiscussFrame(communicator, taskId);

        frame.setLocationRelativeTo(this);
        frame.setTitle("Discuss - " + getSelectedTaskName());
        frame.setVisible(true);
    }

    public MainForm()
    {
        setContentPane(mainPanel);
        setSize(900, 500);
        setLocationByPlatform(true);
        setTitle("Virtual Machine Client");

        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                communicator.disconnect();
                System.exit(0);
            }
        });

        ListCellRenderer<? super String> listRenderer = menuList.getCellRenderer();
        menuList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                    JList list, Object value, int index, boolean isSelected, boolean cellHasFocus)
            {
                JLabel label = (JLabel) listRenderer.getListCellRendererComponent(
                        menuList, (String)value, index, isSelected, cellHasFocus);
                label.setBorder(new EmptyBorder(10, 10, 10, 10));
                return label;
            }
        });
        menuList.setSelectedIndex(0);
        menuList.setFixedCellWidth(150);
        menuList.setFixedCellHeight(50);

        taskQueueTableModel = new DefaultTableModel() {
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        taskQueueTable.setRowHeight(50);
        taskQueueTable.setRowSelectionAllowed(false);
        taskQueueTable.getTableHeader().setReorderingAllowed(false);
        taskQueueTable.setModel(taskQueueTableModel);
        taskQueueTableModel.addColumn("Task Name");
        taskQueueTableModel.addColumn("Creation date");

        taskHistoryTableModel = new DefaultTableModel() {
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        historyTable.setRowHeight(50);
        historyTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        historyTable.getTableHeader().setReorderingAllowed(false);
        historyTable.setModel(taskHistoryTableModel);
        taskHistoryTableModel.addColumn("id");
        taskHistoryTableModel.addColumn("Task Name");
        taskHistoryTableModel.addColumn("Creation Date");
        taskHistoryTableModel.addColumn("Execution Time");
        taskHistoryTableModel.addColumn("Status");
        taskHistoryTableModel.addColumn("Discussion");
        historyTable.removeColumn(historyTable.getColumnModel().getColumn(0));

        historyTable.getSelectionModel().addListSelectionListener(e -> {
            viewTaskDetailsButton.setEnabled(true);
            discussTaskButton.setEnabled(true);
        });

        menuList.addListSelectionListener(e -> {
            showCard(cardPanel, "Card" + menuList.getSelectedIndex());
        });
        ActionListener createTaskListener = e -> {
            if (taskNameField.getText().length() == 0) {
                JOptionPane.showMessageDialog(mainPanel, "Task name cannot be empty",
                        "Error", JOptionPane.WARNING_MESSAGE);
            }
            else showCard(newTaskPanel, "taskEditCard");
        };

        nextButton.addActionListener(createTaskListener);
        taskNameField.addActionListener(createTaskListener);

        backButton.addActionListener(e -> {
            if (programSendSuccess) {
                enableTaskEdit(true);
                backButton.setText("Back");
                taskNameField.setText("");
                taskEditTextArea.setText("");
                fileNameLabel.setText("File is not chosen");
                programFile = null;
                programSendSuccess = false;
                showCard(progressBarPanel, "emptyCard");
            }
            showCard(newTaskPanel, "taskPropsCard");
        });
        chooseAFileRadioButton.addActionListener(e -> {
            openButton.setEnabled(true);
            fileNameLabel.setEnabled(true);
            taskEditTextArea.setEnabled(false);
        });
        inputAProgramRadioButton.addActionListener(e -> {
            openButton.setEnabled(false);
            fileNameLabel.setEnabled(false);
            taskEditTextArea.setEnabled(true);
        });
        openButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Choose a file");
            int result = fileChooser.showDialog(mainPanel, "OK");
            if (result == JFileChooser.APPROVE_OPTION)
            {
                programFile = fileChooser.getSelectedFile();
                fileNameLabel.setText(programFile.getName());
            }
        });
        sendToServerButton.addActionListener(e -> {
            if (programFile == null && taskEditTextArea.getText().length() == 0)
            {
                JOptionPane.showMessageDialog(mainPanel, "Empty program",
                        "Error", JOptionPane.WARNING_MESSAGE);
            }
            else {
                int result = JOptionPane.showConfirmDialog(mainPanel, "Send program to server?",
                        "Confirmation", JOptionPane.OK_CANCEL_OPTION);
                if (result == 0) sendProgramToServer();
            }
        });

        communicator = new Communicator(this);
        communicator.connect();

        AbstractAction viewTaskDetailsAction = new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                openTaskDetailsFrame(getSelectedTaskId());
            }
        };

        viewTaskDetailsButton.addActionListener(viewTaskDetailsAction);

        KeyStroke enter = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0);
        historyTable.getInputMap(JTable.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(enter, "viewDetails");
        historyTable.getActionMap().put("viewDetails", viewTaskDetailsAction);
        historyTable.addMouseListener(new MouseAdapter()
        {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2)
                {
                    Point p = e.getPoint();
                    int row = historyTable.rowAtPoint(p);
                    int taskId = Integer.valueOf((String)taskHistoryTableModel.getValueAt(row, 0));
                    openTaskDetailsFrame(taskId);
                }
            }
        });

        discussTaskButton.addActionListener(e -> {
            openTaskDiscussFrame(getSelectedTaskId());
        });
    }
}
