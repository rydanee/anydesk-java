import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Image;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

public class Window {
    SocketManager sm = new SocketManager(this);
    JFrame frame = new JFrame("Main window");

    JPanel upper = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));

    JTextField ip = new JTextField("127.0.0.1", 15);
    JTextField port = new JTextField("6748", 5);
    JButton connect = new JButton("Connect");
    JButton disconnect = new JButton("Disconnect");
    JButton start = new JButton("Start Server");
    JTextArea log = new JTextArea(5, 30);
    JLabel screenLabel = new JLabel("Remote screen will appear here");
    JPanel screenPanel = new JPanel(new BorderLayout());
    JScrollPane screenScrollPane;

    private int remoteScreenWidth = 1920;
    private int remoteScreenHeight = 1080;
    private BufferedImage originalImage = null;

    public Robot robot;
    public SocketManager.Client cli;
    public SocketManager.Server serv;

    public boolean isConnected = false;

    Image img = null;

    public void setRemoteScreenSize(int width, int height) {
        this.remoteScreenWidth = width;
        this.remoteScreenHeight = height;
    }

    private void setupEventHandlers() {
        connect.addActionListener(e -> {
            int pr = 9021;
            try {
                pr = Integer.parseInt(port.getText());
            } catch (Exception ex) {
                appendLog("Wrong port type, cannot start.");
                return;
            }

            sm.initClient(ip.getText(), pr);
            cli = sm.cli;

            connect.setEnabled(false);
            start.setEnabled(false);
            disconnect.setEnabled(true);

            ip.setEditable(false);
            port.setEditable(false);

            screenPanel.requestFocusInWindow();
        });

        start.addActionListener(e -> {
            int pr = 9021;
            try {
                pr = Integer.parseInt(port.getText());
            } catch (Exception ex) {
                appendLog("Wrong port type, cannot start.");
                return;
            }

            sm.initServer(pr);
            serv = sm.serv;

            connect.setEnabled(false);
            start.setEnabled(false);
            disconnect.setEnabled(true);

            ip.setEditable(false);
            port.setEditable(false);
        });

        disconnect.addActionListener(e -> {
            sm.closeSockets();

            connect.setEnabled(true);
            start.setEnabled(true);
            disconnect.setEnabled(false);

            ip.setEditable(true);
            port.setEditable(true);

            sm.cli = null;
            sm.serv = null;
        });

        setupKeyListener();
    }

    private void setupKeyListener() {
        screenPanel.addKeyListener(new KeyListener() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (sm.cli != null) {
                    e.consume();
                    sm.cli.pressSignal(e.getKeyCode(), true);
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                if (sm.cli != null) {
                    e.consume();
                    sm.cli.pressSignal(e.getKeyCode(), false);
                }
            }

            @Override
            public void keyTyped(KeyEvent e) {
                e.consume();
            }
        });

        screenPanel.addMouseListener(new MouseListener() {
            @Override
            public void mouseClicked(MouseEvent arg0) {
                screenPanel.requestFocus();
            }

            @Override
            public void mouseEntered(MouseEvent arg0) {
                // TODO Auto-generated catch block
            }

            @Override
            public void mouseExited(MouseEvent arg0) {
            }

            @Override
            public void mousePressed(MouseEvent arg0) {
                screenPanel.requestFocus();
                if (sm.cli != null) {
                    Point scaled = getScaledCoordinates(arg0.getPoint());
                    if (scaled != null) {
                        sm.cli.mouseClick(scaled.x, scaled.y, arg0.getButton(), true);
                    }
                }
            }

            @Override
            public void mouseReleased(MouseEvent arg0) {
                if (sm.cli != null) {
                    Point scaled = getScaledCoordinates(arg0.getPoint());
                    if (scaled != null) {
                        sm.cli.mouseClick(scaled.x, scaled.y, arg0.getButton(), false);
                    }
                }
            }
        });

        screenPanel.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                if (sm.cli != null) {
                    Point scaled = getScaledCoordinates(e.getPoint());
                    if (scaled != null) {
                        sm.cli.mouseMove(scaled.x, scaled.y);
                    }
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (sm.cli != null) {
                    Point scaled = getScaledCoordinates(e.getPoint());
                    if (scaled != null) {
                        sm.cli.mouseMove(scaled.x, scaled.y);
                    }
                }
            }
        });
    }

    private Point getScaledCoordinates(Point panelPoint) {
        if (screenLabel.getIcon() == null) {
            return panelPoint;
        }

        ImageIcon icon = (ImageIcon) screenLabel.getIcon();
        int displayedWidth = icon.getIconWidth();
        int displayedHeight = icon.getIconHeight();

        Point labelLocation = screenLabel.getLocation();
        int labelWidth = screenLabel.getWidth();
        int labelHeight = screenLabel.getHeight();

        int offsetX = labelLocation.x + (labelWidth - displayedWidth) / 2;
        int offsetY = labelLocation.y + (labelHeight - displayedHeight) / 2;

        int relativeX = panelPoint.x - offsetX;
        int relativeY = panelPoint.y - offsetY;

        if (relativeX < 0 || relativeY < 0 || relativeX > displayedWidth || relativeY > displayedHeight) {
            return null;
        }

        double scaleX = (double) remoteScreenWidth / displayedWidth;
        double scaleY = (double) remoteScreenHeight / displayedHeight;

        int scaledX = (int) (relativeX * scaleX);
        int scaledY = (int) (relativeY * scaleY);

        return new Point(scaledX, scaledY);
    }

    private void setLayout() {
        frame.setLayout(new BorderLayout());

        log.setEditable(false);
        disconnect.setEnabled(false);

        setupEventHandlers();

        upper.add(ip);
        upper.add(port);
        upper.add(connect);
        upper.add(start);
        upper.add(disconnect);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, screenScrollPane, new JScrollPane(log));
        splitPane.setResizeWeight(0.85);
        splitPane.setBorder(null);

        frame.add(upper, BorderLayout.NORTH);
        frame.add(splitPane, BorderLayout.CENTER);
    }

    public void updateImg(BufferedImage img) {
        SwingUtilities.invokeLater(() -> {
            if (img != null) {
                originalImage = img;

                int panelWidth = screenPanel.getWidth();
                int panelHeight = screenPanel.getHeight();

                if (panelWidth <= 0)
                    panelWidth = screenScrollPane.getWidth();
                if (panelHeight <= 0)
                    panelHeight = screenScrollPane.getHeight();
                if (panelWidth <= 0)
                    panelWidth = 800;
                if (panelHeight <= 0)
                    panelHeight = 600;

                Image scaled = img.getScaledInstance(panelWidth, panelHeight, Image.SCALE_SMOOTH);
                screenLabel.setIcon(new ImageIcon(scaled));
                screenLabel.setText(null);
            }
        });
    }

    public void initWindow() {
        screenLabel.setHorizontalAlignment(SwingConstants.CENTER);
        screenLabel.setFont(new Font("SansSerif", Font.PLAIN, 18));
        screenPanel.setLayout(new BorderLayout());
        screenPanel.add(screenLabel, BorderLayout.CENTER);
        screenPanel.setBorder(BorderFactory.createLineBorder(new Color(60, 63, 65)));
        screenPanel.setPreferredSize(new Dimension(800, 600));
        screenPanel.setMinimumSize(new Dimension(100, 100));

        screenScrollPane = new JScrollPane(screenPanel);
        screenScrollPane.setPreferredSize(new Dimension(800, 600));

        setLayout();

        try {
            robot = new Robot();
        } catch (AWTException e) {
        }

        frame.setBackground(Color.WHITE);
        frame.setSize(700, 700);
        frame.setVisible(true);

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                System.exit(0);
            }
        });
    }

    public void setIsConnected(boolean isConnected) {
        this.isConnected = isConnected;
    }

    public void appendLog(String message) {
        SwingUtilities.invokeLater(() -> {
            log.append(message + "\n");
            log.setCaretPosition(log.getDocument().getLength());
        });
    }
}
