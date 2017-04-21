package com.alexgaiv.vmclient;

import java.io.File;
import java.awt.*;
import javax.swing.*;
import java.awt.event.*;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.Locale;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.nimbus.NimbusLookAndFeel;
import javax.swing.table.*;

public class MainForm extends JFrame
{
    JPanel newTaskPanel;
    JPanel taskQueuePanel;
    JPanel historyPanel;
    final LinkedList<TaskDetailsFrame> taskDetailsFrames = new LinkedList<>();
    final LinkedList<TaskDiscussFrame> taskDiscussFrames = new LinkedList<>();

    DefaultTableModel taskHistoryTableModel;
    DefaultTableModel taskQueueTableModel;

    private ArrayList<Image> frameIcons = new ArrayList<>();

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

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(NimbusLookAndFeel.class.getCanonicalName());
        } catch (
                ClassNotFoundException |
                        InstantiationException |
                        IllegalAccessException |
                        UnsupportedLookAndFeelException e) {
            e.printStackTrace();
        }

        MainForm frame = new MainForm();
        frame.setVisible(true);
    }

    void showCard(JPanel panel, String name) {
        CardLayout layout = (CardLayout) panel.getLayout();
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

    private int getSelectedTaskId() {
        int row = historyTable.convertRowIndexToModel(historyTable.getSelectedRow());
        return (Integer) taskHistoryTableModel.getValueAt(row, 0);
    }

    private String getSelectedTaskName() {
        int row = historyTable.convertRowIndexToModel(historyTable.getSelectedRow());
        return (String) taskHistoryTableModel.getValueAt(row, 1);
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

    private void sendProgramToServer() {
        showCard(progressBarPanel, "progressBarCard");
        enableTaskEdit(false);
        programSendSuccess = false;

        String programText;
        if (inputAProgramRadioButton.isSelected()) {
            programText = taskEditTextArea.getText();
        } else {
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

    private void openTaskDetailsFrame(int taskId) {
        TaskDetailsFrame frame = new TaskDetailsFrame(communicator, taskId);
        frame.setIconImages(frameIcons);

        frame.addWindowListener(new WindowAdapter()
        {
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

    private void openTaskDiscussFrame(int taskId) {
        TaskDiscussFrame frame = new TaskDiscussFrame(this, communicator, taskId);
        frame.setIconImages(frameIcons);

        frame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                synchronized (taskDiscussFrames) {
                    taskDiscussFrames.remove(frame);
                }
                communicator.updateTaskHistory();
            }
        });

        synchronized (taskDiscussFrames) {
            taskDiscussFrames.add(frame);
        }

        frame.setLocationRelativeTo(this);
        frame.setTitle("Discuss - " + getSelectedTaskName());
        frame.setVisible(true);
        communicator.updateTaskHistory();
    }

    public MainForm() {
        setContentPane(mainPanel);
        setSize(900, 500);
        setLocationByPlatform(true);
        setTitle("Virtual Machine Client");

        int[] iconSizes = {16, 32, 64, 128};
        for (int s : iconSizes) {
            String resourceName = String.format("com/alexgaiv/vmclient/resources/icon%d.png", s);
            URL iconUrl = ClassLoader.getSystemResource(resourceName);
            if (iconUrl != null)
                frameIcons.add(new ImageIcon(iconUrl).getImage());
        }
        this.setIconImages(frameIcons);

        addWindowListener(new WindowAdapter()
        {
            public void windowClosing(WindowEvent e) {
                communicator.disconnect();
                System.exit(0);
            }
        });

        ListCellRenderer<? super String> listRenderer = menuList.getCellRenderer();
        menuList.setCellRenderer(new DefaultListCellRenderer()
        {
            @Override
            public Component getListCellRendererComponent(
                    JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) listRenderer.getListCellRendererComponent(
                        menuList, (String) value, index, isSelected, cellHasFocus);
                label.setBorder(new EmptyBorder(10, 10, 10, 10));
                return label;
            }
        });
        menuList.setSelectedIndex(0);
        menuList.setFixedCellWidth(150);
        menuList.setFixedCellHeight(50);

        DefaultTableCellRenderer dateCellRenderer = new DefaultTableCellRenderer()
        {
            SimpleDateFormat format = new SimpleDateFormat("MMM dd, yyyy, hh:mm:ss a", Locale.US);

            public void setValue(Object value) {
                setText(value == null ? "" : format.format(value));
            }
        };

        taskQueueTableModel = new DefaultTableModel()
        {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                switch (columnIndex) {
                    case 1:
                        return Date.class;
                    case 2:
                        return TaskStatus.class;
                    default:
                        return String.class;
                }
            }
        };

        taskQueueTable.setRowHeight(50);
        taskQueueTable.setRowSelectionAllowed(false);
        taskQueueTable.getTableHeader().setReorderingAllowed(false);
        taskQueueTable.setModel(taskQueueTableModel);
        taskQueueTable.setAutoCreateRowSorter(true);
        taskQueueTableModel.addColumn("Task Name");
        taskQueueTableModel.addColumn("Creation date");
        taskQueueTableModel.addColumn("Status");

        taskQueueTable.getColumnModel().getColumn(1).setCellRenderer(dateCellRenderer);

        taskHistoryTableModel = new DefaultTableModel()
        {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                switch (columnIndex) {
                    case 0:
                        return Integer.class;
                    case 2:
                        return Date.class;
                    case 3:
                        return Double.class;
                    case 4:
                        return TaskStatus.class;
                    case 5:
                        return Integer.class;
                    default:
                        return String.class;
                }
            }
        };
        historyTable.setRowHeight(50);
        historyTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        historyTable.getTableHeader().setReorderingAllowed(false);
        historyTable.setModel(taskHistoryTableModel);
        historyTable.setAutoCreateRowSorter(true);
        taskHistoryTableModel.addColumn("id");
        taskHistoryTableModel.addColumn("Task Name");
        taskHistoryTableModel.addColumn("Creation Date");
        taskHistoryTableModel.addColumn("Execution Time");
        taskHistoryTableModel.addColumn("Status");
        taskHistoryTableModel.addColumn("Discussion");

        TableColumnModel columnModel = historyTable.getColumnModel();
        columnModel.getColumn(2).setCellRenderer(dateCellRenderer);
        columnModel.getColumn(3).setCellRenderer(new DefaultTableCellRenderer()
        {
            public void setValue(Object value) {
                setText(value == null ? "" : value.toString() + " ms");
            }
        });
        columnModel.getColumn(5).setCellRenderer(new DefaultTableCellRenderer()
        {
            public void setValue(Object value) {
                setText(value == null ? "" : value.toString() + " message(s)");
            }
        });

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
            } else showCard(newTaskPanel, "taskEditCard");
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
            if (result == JFileChooser.APPROVE_OPTION) {
                programFile = fileChooser.getSelectedFile();
                fileNameLabel.setText(programFile.getName());
            }
        });
        sendToServerButton.addActionListener(e -> {
            if (programFile == null && taskEditTextArea.getText().length() == 0) {
                JOptionPane.showMessageDialog(mainPanel, "Empty program",
                        "Error", JOptionPane.WARNING_MESSAGE);
            } else {
                int result = JOptionPane.showConfirmDialog(mainPanel, "Send program to server?",
                        "Confirmation", JOptionPane.OK_CANCEL_OPTION);
                if (result == 0) sendProgramToServer();
            }
        });

        communicator = new Communicator(this);
        communicator.connect();

        AbstractAction viewTaskDetailsAction = new AbstractAction()
        {
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
                if (e.getClickCount() == 2) {
                    Point p = e.getPoint();
                    int row = historyTable.convertRowIndexToModel(historyTable.rowAtPoint(p));
                    int taskId = (Integer) taskHistoryTableModel.getValueAt(row, 0);
                    openTaskDetailsFrame(taskId);
                }
            }
        });

        discussTaskButton.addActionListener(e -> {
            openTaskDiscussFrame(getSelectedTaskId());
        });
    }
}
