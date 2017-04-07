package com.alexgaiv.vmclient;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.text.SimpleDateFormat;
import java.util.Locale;

class TaskDetailsFrame extends JFrame
{
    private JPanel mainPanel;
    private JTextArea programTextField;
    private JTextArea programOutputField;
    private JLabel taskNameLabel;
    private JLabel creationDateLabel;
    private JLabel execTimeLabel;
    private JLabel statusLabel;

    private int taskId;
    private Communicator comm;

    int getTaskId() { return taskId; }

    TaskDetailsFrame(Communicator comm, int taskId)
    {
        this.comm = comm;
        this.taskId = taskId;

        setContentPane(mainPanel);
        setSize(700, 500);
        setTitle("Task Details");
    }

    void update() {
        comm.updateTaskDetails(taskId, e -> {
            JOptionPane.showMessageDialog(mainPanel,
                    "Connection Error", "Error", JOptionPane.ERROR_MESSAGE);
            dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
            dispose();
        });
    }

    void setTaskInfo(TaskInfo t)
    {
        String dateString = new SimpleDateFormat("MMM dd, yyyy, hh:mm:ss a", Locale.US).format(t.creationDate);

        setTitle("Task Details - " + t.name);
        taskNameLabel.setText(t.name);
        creationDateLabel.setText(dateString);
        execTimeLabel.setText(t.executionTime + " ms");
        statusLabel.setText(t.status ? "Completed" : "Failed");
        programTextField.setText(t.programText);
        programOutputField.setText(t.programOutput);
        CardLayout layout = (CardLayout)mainPanel.getLayout();
        layout.show(mainPanel, "taskDetailsCard");
    }
}
