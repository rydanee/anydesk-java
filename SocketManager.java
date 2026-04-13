import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class SocketManager {

    public Server initServer(int port) {
            Server serv = null;
            try {
                serv = new Server(port);
            } catch (IOException e) {
            }
            return serv;
        }

    public Client initClient(String ip, int port) {
        Client cl = new Client(ip, port);
        return cl;
    }

    public class Server {
        public int port = 9021;
        Socket s;
        ServerSocket ss;
        DataInputStream in;

        public Server(int port) throws IOException {
            ss = new ServerSocket(port);
            
            s = ss.accept();

            in = new DataInputStream(
                new BufferedInputStream(s.getInputStream()));
            
            new Thread(new Runnable() {
                String m = "";
                
                @Override
                public void run() {
                    while (s.isConnected()) {
                        try {
                            m = in.readUTF();
                            System.out.println(m);
                        } catch (IOException e) {
                        }
                    }
                    try {
                        s.close();
                        in.close();
                    } catch (IOException e) {
                    }
                }
            }).start();


        }

    }

    public class Client {
        public String ip = "127.0.0.1";
        public int port = 9021;
        Socket cs;
        public boolean stop = false;
        DataInputStream in;
        DataOutputStream out;

        public Client(String ip, int port) {    
            try {
                cs = new Socket(ip, port);
                in = new DataInputStream(System.in);
                out = new DataOutputStream(cs.getOutputStream());


            } catch (IOException e) {
            }

            new Thread(new Runnable() {
                String m = "";

                @Override
                public void run() {
                    while (cs.isConnected()) {
                        try {
                            m = in.readLine();
                            out.writeUTF(m);
                        } catch (IOException e) {
                        }
                    }
                    try {
                        cs.close();
                        in.close();
                        out.close();
                    } catch (IOException e) {
                    }
                }
            }).start();
        }

    }
}
