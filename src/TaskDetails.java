import javax.swing.*;

public class TaskDetails extends JFrame
{
    private JPanel mainPanel;
    private JTextArea textArea1;
    private JTextArea textArea2;
    private JTextField textField1;

    TaskDetails()
    {
        setContentPane(mainPanel);
        setSize(700, 400);
        setTitle("Task Details");
    }
}
