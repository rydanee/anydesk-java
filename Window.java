import java.awt.*;
import java.awt.event.*;


public class Window{

    public void menuWindow(Frame frame){
        frame.removeAll();
        Button button = new Button("Host");

        button.setBackground(Color.WHITE);
        button.setForeground(Color.BLACK);

        button.setBounds(150, 150, 100, 30);

        button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e){
                System.out.println("Дилдо");
            }
        });

        frame.add(button);

        frame.revalidate();
        frame.repaint();
    }


    
    public void initWindow(){
        Frame frame = new Frame("Main window");

        frame.setBackground(Color.BLACK);
        frame.setSize(500, 500);
        frame.setLayout(null);
        frame.setVisible(true);

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e){
                System.exit(0);
            }
        });

        menuWindow(frame);
    }
}
