import java.net.*;
import java.io.*;
import java.sql.Timestamp;
import java.util.Hashtable;
import java.util.Scanner;

public class Client implements Runnable {

    private int idsesji;
    private DatagramSocket socket;
    private static boolean cond = true;
    private boolean ingame = false;
    private InetAddress ip;
    private int port;
    private Listener listener;
    private Timestamp timestamp = new Timestamp(System.currentTimeMillis());

    private Client(String inet, int port) {
        try {
            //przypisanie portu serwera
            this.port = port;

            //przypisanie adresu ip serwera
            ip = InetAddress.getByName(inet);
            System.out.println("Oczekiwanie na połączenie...");

            //stworzenie gniazda klienta na domyślnym adresie i losowym porcie
            socket = new DatagramSocket();

            //wysłanie do serwera komunikatu o żądaniu połączenia
            socket.send(generujPakiet("connect", "chce", 0, 0));

            //stworzenie tymczasowego pakietu do odbioru potwierdzenia
            DatagramPacket pakiet = new DatagramPacket(new byte[256], 256);

            //ustawienie czasu oczekiwania na odowiedź na 5 sekund
            socket.setSoTimeout(5000);

            //odebranie pakietu potwierdzenia
            socket.receive(pakiet);

            //odczyt potwierdzenia
            decode(new String(pakiet.getData()));

            //ustawienie domyślnego czasu oczekiwania
            socket.setSoTimeout(100);

            //tworzymy obiekt nasłuchujący
            listener = new Listener(this,socket);
        } catch (IOException e) {
            System.err.println(e.getMessage());

            //w przypadku błędu resetujemy gniazdo - program wie wtedy, że należy zakończyć pracę
            socket = null;
        }

    }

    private void execute(String operacja, String odpowiedz, int liczba, int czas, int id) {

        //przy każdym wykonaniu operacji komunikatu wysyłamy potwierdzenie jego otrzymania
        //wyjątkiem jest kamunikat ACK, na który nie trzeba odpowiadać
        if(!operacja.equals("response") && !odpowiedz.equals("ACK")) {
            send("response", "ACK", idsesji, 0);
            try{

                //chwila wstrzymania, aby komunikaty nie prześcigały się
                Thread.sleep(100);
            }catch (InterruptedException e){
                System.out.println(e.getMessage());
            }
        }
        //wybór działania na podstawie operacji
        switch (operacja) {
            case "start":
                //odpowiedź start
                if (odpowiedz.equals("start") && !ingame) {
                    System.out.println("Start!");

                    //przejście do stany gry - odblokowanie odbierania pakietów od serwera
                    ingame = true;
                }
                break;
            case "notify":
                if (ingame) {

                    //informacja, że zgadywana liczba jest za mała
                    if (odpowiedz.equals("mala")) {
                        System.out.println("Liczba jest za mała");
                    }

                    //informacja o pozostałym czasie
                    if (odpowiedz.equals("czas")) {
                        System.out.println("Pozostało " + czas + " sekund");
                    }

                    //informacja, że zgadywana liczba jest za duża
                    if (odpowiedz.equals("duza")) {
                        System.out.println("Liczba jest za duża");
                    }
                }
                break;
            case "end":

                //informacja, że grę wygrał inny gracz
                if (odpowiedz.equals("przegrana")) {
                    System.out.println("Wygrał inny gracz, poprawna liczba to: " + liczba);
                }

                //informacja, że klient wygrał grę
                if (odpowiedz.equals("wygrana")) {
                    System.out.println("Wygrałeś!");
                }

                //informacja o skończeniu czasu gry
                if (odpowiedz.equals("koniecCzasu")) {
                    System.out.println("Czas się skończył.");
                }
                try {

                    //każdy klient wyśle komunikat zakończenia połączenia w innym czasie
                    //zależnym od identyfikatora sesji
                    Thread.sleep(idsesji);
                }
                catch(InterruptedException e){
                    System.err.println(e.getMessage());
                }

                //zakończenie połączenia
                send("end", "zakonczPol", idsesji, 0);

                //zakończenie pętli nasłuchującej
                listener.warunek = false;

                //zakończenie trybu gry i pętli gry
                ingame = cond = false;
                break;
            case "answer":

                //informacja o zaakceptowaniu klienta przez serwer i przypisanie identyfikatora sesji
                if (odpowiedz.equals("accept")) {
                    idsesji = id;
                    System.out.println("Połączono z serwerem. Otrzymano ID: "+id);
                }
                break;
            case "response":
                //w przypadku nadejścia komunikatu ack na początku pracy klienta nie są podejmowane żadne akcje
                return;
            default:

                //inne komunikaty nie sąobsłużone, ale jest informacja o nieznanym typie operacji
                System.out.println("Otrzymano nieznany komunikat");
        }
    }

    void decode(String data) {

        int liczba, czas, id;

        //rozdzeielenie pól komunikatu
        String[] options = data.split("<<");

        Hashtable<String, String> optionsSplit = new Hashtable<>();

        //przeszukiwanie pól komunikatu
        for (String elem : options) {

            //rozdzielenie pola na klucz i wartość
            String[] temp = elem.split("[?]");

            //jeśli pole zawiera dokładnie dwa elementy to zapisujemy je do tablicy
            if(temp.length == 2)
                optionsSplit.put(temp[0], temp[1]);
        }

        //przyppisujemy otrzymane wartości do tymczasowych zmiennych
        liczba = Integer.parseInt(optionsSplit.get("LI"));
        czas = Integer.parseInt(optionsSplit.get("CZ"));
        id = Integer.parseInt(optionsSplit.get("ID"));

        //sprawdzamy poprawność identyfikatora otrzymanego z sieci z tym zapisanym lokalnie
        if (id == idsesji || idsesji == 0) {
            execute(optionsSplit.get("OP"), optionsSplit.get("OD"), liczba, czas, id);
        } else {
            System.out.println("Odebrano niepoprawny komunikat od serwera");
        }

    }

    private DatagramPacket generujPakiet(String operacja, String odpowiedz, int id, int liczba) {

        //stworzenie bufora datagramu
        byte[] buff = new byte[256];

        //stworzenie datagramu do wysłania
        DatagramPacket pakiet = new DatagramPacket(buff, 256);

        //budowanie komunikatu na podstawie wartości zmiennych przekazanych do funkcji
        String komunikat = "";

        komunikat += "OP?" + operacja + "<<";
        komunikat += "OD?" + odpowiedz + "<<";
        komunikat += "ID?" + id + "<<";
        komunikat += "LI?" + liczba + "<<";
        komunikat += "CZ?" + 0 + "<<";
        komunikat += "TS?" + timestamp.getTime()+"<<";
        //komunikat += "\0";

        //przekazanie komunikatu do bufora datagramu
        pakiet.setData(komunikat.getBytes());

        //ustawienie adresu i portu adresata
        pakiet.setAddress(ip);
        pakiet.setPort(port);

        //zwracanie gotowego pakietu
        return pakiet;
    }

    private void send(String operacja, String odpowiedz, int id, int liczba) {
        try {
            //przypisanie do zmiennej generowanego pakietu
            DatagramPacket pakiet = generujPakiet(operacja,odpowiedz,id,liczba);

            //wysłanie pakietu
            socket.send(pakiet);

            //jeśli wysyłamy operację inną niż odpowiedź powiadamiamy wątek nasłuchujący
            //że oczekujemy potwierdzenia otrzymania pakietu
            if(!(operacja.equals("response") && odpowiedz.equals("ACK"))){
                listener.accepted = false;
            }
        } catch (IOException r) {
            System.err.println(r.getMessage());
        }
    }

    //to jest funkcja wywoływana przez wątek nasłuchujący w momencie wymogu zamknięcia pętli programu
    void setCondition(){
        cond = false;
    }

    public static void main(String[] args) {
        //sprawdzamy czy liczba argumentów jest poprawna
        if (args.length == 2) {

            //tworzymy obiekt klienta
            Client client = new Client(args[0], Integer.parseInt(args[1]));

            //jeśli wystąpią błędy podczas połączenia z serwerem gniazdo zostaje nieutworzone
            if (client.socket != null) {

                //przekazanie działania klienta do innego wątku
                new Thread(client).start();
            } else {
                //
                System.out.println("Nie można było połączyć z serwerem");
                cond = false;
            }
        }
    }

    public void run() {
        Scanner scanner = new Scanner(System.in);
        int liczba;

        //stworzenie wątku dla obiektu nasłuchującenie i uruchomienie go
        Thread listen = new Thread(listener);
        listen.start();

        //główna pętla gry
        while (cond) {
            try {
                //sprawdzenie czy na wejściu jest jakaś wartość
                if (System.in.available() > 0) {

                    //pobieramy liczbę ze strumienia wejściowego i wysyłamy go serwerowi do sprawdzenia
                    liczba = scanner.nextInt();
                    send("notify", "liczba", idsesji, liczba);
                }
            } catch (Throwable e) {

                //inne wartości niż liczby są pomijane
                scanner.next();
            }
        }
        // po wyjściu z pętli zamykamy gniazdo
        socket.close();
    }
}
