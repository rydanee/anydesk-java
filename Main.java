import java.awt.*;
import java.awt.event.WindowAdapter;

public class Main {
    public static void main(String[] args) {
        Window win = new Window();

        win.initWindow();

        SocketManager sm = new SocketManager();
        int connectionType = 1;

        if (connectionType == 0) {
            SocketManager.Server serv = sm.initServer(9021);
        } else {
            SocketManager.Client cl = sm.initClient("127.0.0.1", 9021);
        }
    }    
}
