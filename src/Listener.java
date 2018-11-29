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

        //pobieramy odniesienie do obiektu klienta i gniazda
        this.socket = socket;
        this.client = klient;
    }

    private void decode(String message) {

        //wstępne dekodowanie wiadomości
        String[] split = message.split("<<");
        String operation = split[0].split("[?]")[1];
        String answer = split[1].split("[?]")[1];

        //jeśli serwer odpowiedział potwierdzeniem, przerywamy oczekiwanie i kontynuujemy pracę
        if (operation.equals("response") && answer.equals("ACK")) {

            //informacja, że ostatni wysłany pakiet jest potwierdzony
            accepted = true;
        }
        else {

            //jeśli otrzymany pakiet nie jest potwierdzeniem to przekazujemy go do dokładnego dekodowania
            client.decode(message);
        }
    }

    @Override
    public void run() {

        //pętla nasłuchująca na gnieździe
        while(warunek) {
            try {

                //jeśli osatani wysłany pakiet nie został potwierdzony:
                if(!accepted)
                    //ustawiamy czas oczekiwania na 5s
                    socket.setSoTimeout(5000);
                else
                    //w przeciwnym przypadku ustawiamy mały czas oczekiwania w celu ciągłego odbierania
                    socket.setSoTimeout(50);
                //pobieramy czas oczekiwania do tymczasowej zmiennej, operacja ta musi się wykonać
                //w sekcji try{}
                timeout = socket.getSoTimeout();

                //odbieramy komuniakt
                socket.receive(pakiet);

                //po odebraniu, komunikat jest przekazany do wstępnego dekodowania
                decode(new String(pakiet.getData()));

            } catch (IOException e) {

                //w specjalnych warunkach (mały czas oczekiwania albo kiedy wszystkie komunikaty są potwierdzone)
                //ignorujemy błąd odbioru datagramu, ponieważ co 50 ms ponawiamy nasłuchiwanie
                if((accepted|| timeout == 50) && e.getMessage().equals("Receive timed out"))
                    continue;

                //jeśli jednak potwierdzenie nie nadejdzie w określonym czasie, kończymy działanie programu
                client.setCondition();
                System.err.println(e.getMessage());
                System.out.println("Rozłączono z serwerem");
                break;
            }
        }
    }
}
