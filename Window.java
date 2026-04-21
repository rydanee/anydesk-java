import java.awt.AWTException;
import java.awt.Button;
import java.awt.Color;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Label;
import java.awt.Robot;
import java.awt.TextField;
import java.awt.event.KeyListener;
import java.awt.event.KeyEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import javax.swing.JFrame;

public class Window{
    SocketManager sm = new SocketManager(this);
    public boolean isConnected = false;
    JFrame hostedFrame;
    public Robot robot;
    public volatile SocketManager.Server serv;
    public SocketManager.Client cli;

    Image img = null;

    public void menuWindow(Frame frame){
        frame.removeAll();
        Button hostButton = new Button("Host");
        Button clientButton = new Button("Client");
        Button backButton = new Button("Back");

        hostButton.setBackground(Color.WHITE);
        hostButton.setForeground(Color.BLACK);
        hostButton.setBounds(150, 150, 100, 30);
        
        clientButton.setBackground(Color.WHITE);
        clientButton.setForeground(Color.BLACK);
        clientButton.setBounds(150, 100, 100, 30);

        hostButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e){
                hostWindow(frame);
            }
        });

        clientButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e){
                clientWindow(frame);
            }
        });

        frame.add(clientButton);
        frame.add(hostButton);

        frame.revalidate();
        frame.repaint();
    }

    public void updateImg(BufferedImage img) {
        this.img = img;
        if( hostedFrame != null) hostedFrame.repaint();
    }

    public void hostedWindow() {
        hostedFrame = new JFrame("Photo") {
            @Override
            public void paint(Graphics g){
                if (img == null) {System.out.println("whaa"); return;}
                g.drawImage(img, 0, 0, this);
            }
        };

        if (hostedFrame.isFocusableWindow()){
            hostedFrame.addKeyListener(new KeyListener() {
                public void keyPressed(KeyEvent e) {
                    if (cli != null) cli.setLastPressed(e.getKeyChar());
                }

                public void keyReleased(KeyEvent e) {
                }

                public void keyTyped(KeyEvent e) {
                }
            });
        }

        hostedFrame.setSize(1280, 720);
        hostedFrame.setVisible(true);

        hostedFrame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) { System.exit(0); }
        });

    }

    public void hostWindow(Frame frame){
        frame.removeAll();

        TextField hostField = new TextField("6748");
        Button createButton = new Button("Create");
        Button backButton = new Button("Back");        

        hostField.setBounds(100, 100, 100, 30);
        createButton.setBounds(220, 100, 50, 30);

        createButton.setBackground(Color.WHITE);
        createButton.setForeground(Color.BLACK);

        backButton.setBackground(Color.WHITE);
        backButton.setForeground(Color.BLACK);
        backButton.setBounds(150, 150, 100, 30);

        createButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e){
                int port = Integer.parseInt(hostField.getText());
                startServer(port);
            }
        });

        backButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e){
                menuWindow(frame);
            }
        });

        frame.add(backButton);
        frame.add(hostField);
        frame.add(createButton);
        frame.revalidate();
        frame.repaint();

    }

    public void startServer(int port) {
        this.serv = sm.initServer(port);
    }

    public void clientWindow(Frame frame){
        frame.removeAll();

        TextField portField = new TextField("6748");
        TextField ipField = new TextField("127.0.0.1");

        Button createButton = new Button("Connect");
        Button backButton = new Button("Back");    

        Label ipLabel = new Label("IP:");
        Label portLabel = new Label("Port:");

        ipLabel.setLocation(50, 150);
        ipLabel.setBackground(Color.WHITE);

        portLabel.setLocation(50, 100);
        portLabel.setForeground(Color.WHITE);

        portField.setBounds(100, 100, 100, 30);
        ipField.setBounds(100, 150, 100, 30);

        createButton.setBounds(220, 125, 50, 30);

        createButton.setBackground(Color.WHITE);
        createButton.setForeground(Color.BLACK);

        backButton.setBackground(Color.WHITE);
        backButton.setForeground(Color.BLACK);
        backButton.setBounds(150, 200, 100, 30);

        createButton.addActionListener(new ActionListener() {
    public void actionPerformed(ActionEvent e){
        int port = Integer.parseInt(portField.getText());
        cli = sm.initClient(ipField.getText(), port);
        
        new Thread(() -> {
            try {
                for(int i=0; i<50; i++) {
                    if (isConnected) {
                        hostedWindow();
                        break;
                    } else {
                        System.out.println("some bullshit going on with the connection");
                    }
                    Thread.sleep(100);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }).start();
    }
});

        
        backButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e){
                menuWindow(frame);
            }
        });

        frame.add(backButton);
        frame.add(portField);
        frame.add(ipLabel);
        frame.add(portLabel);
        frame.add(ipField);
        frame.add(createButton);

        frame.revalidate();
        frame.repaint();
    }
    
    public void initWindow(){
        Frame frame = new Frame("Main window");
        try {
            robot = new Robot();
        } catch (AWTException e) {}

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

    public void setIsConnected(boolean isConnected) {
        this.isConnected = isConnected;
    }
}