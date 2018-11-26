import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class Listener implements Runnable {

    private DatagramSocket socket;
    private DatagramPacket pakiet = new DatagramPacket(new byte[256], 256);
    private Client client;
    boolean warunek = true;
    boolean accepted = true;
    private int timeout;

    Listener(Client klient, DatagramSocket socket) {
        this.socket = socket;
        this.client = klient;
    }

    private void decode(String message) {
        String[] split = message.split("<<");
        String operation = split[0].split("[?]")[1];
        String answer = split[1].split("[?]")[1];

        if (operation.equals("response") && answer.equals("ACK")) {
            accepted = true;
        }
        else {
            client.decode(message);
        }
    }

    @Override
    public void run() {

        while(warunek) {
            try {
                if(!accepted)
                    socket.setSoTimeout(5000);
                else
                    socket.setSoTimeout(50);
                timeout = socket.getSoTimeout();

                socket.receive(pakiet);
                decode(new String(pakiet.getData()));

            } catch (IOException e) {
                if((accepted|| timeout == 50) && e.getMessage().equals("Receive timed out"))
                    continue;
                client.setCondition();
                System.err.println(e.getMessage());
                System.out.println("Rozłączono z serwerem");
                break;
            }
        }
    }
}
