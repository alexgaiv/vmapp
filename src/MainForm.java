import java.io.File;
import java.awt.*;
import javax.swing.*;
import java.awt.event.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.*;
import javax.swing.table.*;

public class MainForm extends JFrame
{
    JPanel newTaskPanel;
    JPanel taskQueuePanel;
    JPanel historyPanel;
    private JPanel mainPanel;
    private JList menuList;
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

    public DefaultTableModel taskHistoryTableModel;
    public DefaultTableModel taskQueueTableModel;

    public static void main(String[] args)
    {
        try {
            UIManager.setLookAndFeel(javax.swing.plaf.nimbus.NimbusLookAndFeel.class.getCanonicalName());
            //UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (UnsupportedLookAndFeelException e) {
            e.printStackTrace();
        }

        MainForm frame = new MainForm();
        frame.setVisible(true);
    }

    void showCard(JPanel panel, String name) {
        CardLayout layout = (CardLayout)panel.getLayout();
        layout.show(panel, name);
    }

    private void onConnectionEstablished() {
        if (!connectionEstablished) {
            showCard(taskQueuePanel, "progressBarCard");
            showCard(historyPanel, "progressBarCard");
        }
        communicator.updateTaskQueue();
        communicator.updateTaskHistory();
        connectionEstablished = true;
    }

    private void onConnectionFailed() {
        if (connectionFailedFirstTime) {
            showCard(taskQueuePanel, "connectionFailedCard");
            showCard(historyPanel, "connectionFailedCard");
            connectionFailedFirstTime = false;
        }
        connectionEstablished = false;
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

        new Thread(new Runnable() {
            public void run()
            {
                programSendSuccess = true;

                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                if (programSendSuccess) {
                    showCard(progressBarPanel, "readyCard");
                    backButton.setText("New Task");
                }
                else {
                    showCard(progressBarPanel, "emptyCard");
                    enableTaskEdit(true);
                    JOptionPane.showMessageDialog(mainPanel,
                            "Connection Error", "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }).start();
    }

    public MainForm()
    {
        setContentPane(mainPanel);
        setSize(700, 400);
        setMinimumSize(new Dimension(600, 300));
        setTitle("Virtual Machine Client");

        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                communicator.disconnect();
                System.exit(0);
            }
        });

        ListCellRenderer listRenderer = menuList.getCellRenderer();
        menuList.setCellRenderer(new DefaultListCellRenderer() {
            public Component getListCellRendererComponent(
                    JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
            {
                JLabel label = (JLabel)listRenderer.getListCellRendererComponent(
                        list, value, index, isSelected, cellHasFocus);
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
        taskQueueTableModel.addColumn("Start time");

        taskHistoryTableModel = new DefaultTableModel() {
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        historyTable.setRowHeight(50);
        historyTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        historyTable.getTableHeader().setReorderingAllowed(false);
        historyTable.setModel(taskHistoryTableModel);
        taskHistoryTableModel.addColumn("Task Name");
        taskHistoryTableModel.addColumn("Start time");
        taskHistoryTableModel.addColumn("Status");
        taskHistoryTableModel.addColumn("Discussion");

        menuList.addListSelectionListener(new ListSelectionListener()
        {
            public void valueChanged(ListSelectionEvent e) {
                showCard(cardPanel, "Card" + menuList.getSelectedIndex());
            }
        });
        ActionListener createTaskListener = new ActionListener()
        {
            public void actionPerformed(ActionEvent e) {
                if (taskNameField.getText().length() == 0) {
                    JOptionPane.showMessageDialog(mainPanel, "Task name cannot be empty",
                            "Error", JOptionPane.WARNING_MESSAGE);
                }
                else showCard(newTaskPanel, "taskEditCard");
            }
        };

        nextButton.addActionListener(createTaskListener);
        taskNameField.addActionListener(createTaskListener);

        backButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) {
                if (programSendSuccess) {
                    enableTaskEdit(true);
                    taskNameField.setText("");
                    taskEditTextArea.setText("");
                    fileNameLabel.setText("File is not chosen");
                    programFile = null;
                    programSendSuccess = false;
                    showCard(progressBarPanel, "emptyCard");
                }
                showCard(newTaskPanel, "taskPropsCard");
            }
        });
        chooseAFileRadioButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) {
                openButton.setEnabled(true);
                fileNameLabel.setEnabled(true);
                taskEditTextArea.setEnabled(false);
            }
        });
        inputAProgramRadioButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) {
                openButton.setEnabled(false);
                fileNameLabel.setEnabled(false);
                taskEditTextArea.setEnabled(true);
            }
        });
        openButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) {
                JFileChooser fileChooser = new JFileChooser();
                fileChooser.setDialogTitle("Choose a file");
                int result = fileChooser.showDialog(mainPanel, "OK");
                if (result == JFileChooser.APPROVE_OPTION)
                {
                    programFile = fileChooser.getSelectedFile();
                    fileNameLabel.setText(programFile.getName());
                }
            }
        });
        sendToServerButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
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
            }
        });

        communicator = new Communicator(this);
        communicator.addConnectionStateListener(new ConnectionStateListener()
        {
            public void onConnectionEstablished() {
                MainForm.this.onConnectionEstablished();
            }
            public void onConnectionFailed() {
                MainForm.this.onConnectionFailed();
            }
        });
        communicator.connect();


        viewTaskDetailsButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) {
                TaskDetails taskDetailsFrame = new TaskDetails();
                taskDetailsFrame.setVisible(true);
            }
        });
    }
}
