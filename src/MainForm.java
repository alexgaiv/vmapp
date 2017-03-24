import java.io.File;
import java.awt.*;
import javax.swing.*;
import java.awt.event.*;
import javax.swing.event.*;

public class MainForm
{

    private JPanel MainPanel;
    private JList menuList;
    private JPanel cardPanel;
    private JPanel newTaskPanel;
    private JPanel taskQueuePanel;
    private JPanel HistoryPanel;
    private JButton nextButton;
    private JPanel taskEditCard;
    private JButton backButton;
    private JButton sendToServerButton;
    private JTextField taskNameField;
    private JPanel taskPropsCard;
    private JTextArea taskEditTextArea;
    private JRadioButton chooseAFileRadioButton;
    private JRadioButton inputAProgramRadioButton;
    private JButton openButton;
    private JLabel fileNameLabel;
    private JPanel progressBarPanel;
    private JProgressBar sendProgressBar;
    private File programFile = null;

    boolean programSendSuccess = false;
    private final static Object sendLock = new Object();

    public static void main(String[] args)
    {
        try {
            UIManager.setLookAndFeel(javax.swing.plaf.nimbus.NimbusLookAndFeel.class.getCanonicalName());
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (UnsupportedLookAndFeelException e) {
            e.printStackTrace();
        }

        JFrame frame = new JFrame("MainForm");
        frame.setContentPane(new MainForm().MainPanel);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(700, 400);
        frame.setTitle("Virtual Machine Client");
        frame.setVisible(true);
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
        new Thread(new Runnable()
        {
            public void run() {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                programSendSuccess = true;
                synchronized (sendLock) {
                    sendLock.notify();
                }
            }
        }).start();
    }

    public MainForm()
    {
        menuList.setSelectedIndex(0);
        menuList.setFixedCellWidth(150);
        menuList.setFixedCellHeight(50);

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

                    if (result == 0) {
                        showCard(progressBarPanel, "progressBarCard");
                        enableTaskEdit(false);

                        sendProgramToServer();

                        new Thread(new Runnable()
                        {
                            public void run() {
                                try {
                                    synchronized (sendLock) {
                                        sendLock.wait();
                                    }
                                } catch (InterruptedException e1) {
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
                }
            }
        });
    }
}
