import java.io.File;
import java.awt.*;
import javax.swing.*;
import java.awt.event.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.event.*;
import javax.swing.table.*;

public class MainForm extends JFrame
{
    private JPanel MainPanel;
    private JList menuList;
    private JPanel cardPanel;
    private JPanel newTaskPanel;
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
    private JScrollPane taskQueuePanel;
    private JTable taskQueueTable;
    private JTable historyTable;
    private JButton viewTaskDetailsButton;
    private JButton discussTaskButton;
    private JPanel historyPanel;

    private File programFile = null;
    private boolean programSendSuccess = false;
    private Communicator communicator;

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
        frame.setContentPane(frame.MainPanel);
        frame.setSize(700, 400);
        frame.setTitle("Virtual Machine Client");
        frame.setVisible(true);
    }

    public void onConnectionEstablished() {
        communicator.updateTaskQueue();
        communicator.updateHistory();
    }

    public void addMessage(String message) {
        taskEditTextArea.setText(taskEditTextArea.getText() + "\n" + message);
    }

    private void showCard(JPanel panel, String name) {
        CardLayout layout = (CardLayout)panel.getLayout();
        layout.show(panel, name);
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
                    JOptionPane.showMessageDialog(MainPanel,
                            "Connection Error", "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }).start();
    }

    public MainForm()
    {
        this.addWindowListener(new WindowAdapter() {
            @Override
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

        {
            DefaultTableModel model = new DefaultTableModel() {
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
            taskQueueTable.setRowHeight(50);
            //taskQueueTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            taskQueueTable.setRowSelectionAllowed(false);
            taskQueueTable.getTableHeader().setReorderingAllowed(false);
            taskQueueTable.setModel(model);
            model.addColumn("Task Name");
            model.addColumn("Start time");
            model.addRow(new Object[]{"Task1", "2:28:13"});
            model.addRow(new Object[]{"Task2", "13:37:41"});
            model.addRow(new Object[]{"Solution", "11:37:15"});
            model.addRow(new Object[]{"Regression", "12:37:15"});
        }

        {
            DefaultTableModel model = new DefaultTableModel() {
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
            historyTable.setRowHeight(50);
            historyTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            historyTable.getTableHeader().setReorderingAllowed(false);
            historyTable.setModel(model);
            model.addColumn("Task Name");
            model.addColumn("Start time");
            model.addColumn("Status");
            model.addColumn("Discussion");
            model.addRow(new Object[]{"Task1", "2:28:13", "Complete", "0 message(s)"});
            model.addRow(new Object[]{"Task2", "13:37:41", "Complete", "2 message(s)"});
            model.addRow(new Object[]{"Solution", "11:37:15", "Failure", "6 message(s)"});
            model.addRow(new Object[]{"Regression", "12:37:15", "Complete", "1 message(s)"});
        }

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
                    JOptionPane.showMessageDialog(MainPanel, "Task name cannot be empty",
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
                int result = fileChooser.showDialog(MainPanel, "OK");
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
                    JOptionPane.showMessageDialog(MainPanel, "Empty program",
                            "Error", JOptionPane.WARNING_MESSAGE);
                }
                else {
                    int result = JOptionPane.showConfirmDialog(MainPanel, "Send program to server?",
                            "Confirmation", JOptionPane.OK_CANCEL_OPTION);
                    if (result == 0) sendProgramToServer();
                }
            }
        });

        communicator = new Communicator(this);
        communicator.connect();
    }
}
