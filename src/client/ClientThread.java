package client;

import constants.Authentication;
import constants.SendKeys;
import constants.ServerInfo;
import gui.Controller;
import gui.chat.Message;
import gui.contacts.ContactsController;
import logger.Log;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

/**
 * Singleton
 */
public class ClientThread extends Thread {
    public static final int TIMEOUT_FRIEND_REQUEST_SECONDS = 2;
    private final String TAG = this.getClass().getSimpleName();
    private static final String HOST = ServerInfo.HOST;
    private static final int PORT = ServerInfo.PORT;
    private static Controller controller;
    private Client client;

    private Queue<Object> outcome = new ConcurrentLinkedQueue<>();
    private List<Object> income = Collections.synchronizedList(new ArrayList<>());

    private Socket socket;

    private static ClientThread instance = new ClientThread();
    public static ClientThread getInstance() {
        return instance;
    }
    private ClientThread() {
        start();
    }

    @Override
    public void run() {
        try {
            initSocket();

            write();
            read();
            while (!isInterrupted() && socket.isConnected());
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            close();
        }
    }

    private void initSocket() throws IOException {
        Log.d(TAG, "Clinet about to connect...");
        socket = new Socket(HOST, PORT);
        Log.d(TAG, "Clinet connected.");
    }

    private void write() {
        new Thread(() -> {
            ObjectOutputStream objectOutput = null;
            try {
                objectOutput = new ObjectOutputStream(socket.getOutputStream());
                while ( !ClientThread.this.isInterrupted() && socket.isConnected()) {
                    while (outcome.isEmpty()){
                        Thread.yield();
                    }
                    Object o = outcome.poll();
                    objectOutput.flush();
                    objectOutput.writeObject(o);
                    objectOutput.flush();
                    Log.i("", "Sending " + ((HashMap) o).get(SendKeys.TITLE));
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (objectOutput != null) {
                    try {
                        objectOutput.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }

    private void read() {
        new Thread(() -> {
            ObjectInputStream objectInput = null;
            try {
                objectInput = new ObjectInputStream(socket.getInputStream());
                try {
                    while (!ClientThread.this.isInterrupted() && socket.isConnected()) {
                        Object o = objectInput.readObject();
                        if (o instanceof HashMap) {
                            income.add(o);
                            Log.i("", "Receiving " + ((HashMap) o).get(SendKeys.TITLE));
                        }
                    }
                } catch (IOException | ClassNotFoundException e) {
                    e.printStackTrace();
                } finally {
                    if (objectInput != null) {
                        try {
                            objectInput.close();
                        } catch (IOException e1) {
                            e1.printStackTrace();
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void close() {
        try {
            this.interrupt();
            if(socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String authentication(Client client){
        String email = client.getEmail();
        String pass = client.getPass();
        HashMap<String, String> mapOut = new HashMap<>();
        mapOut.put(SendKeys.TITLE, SendKeys.AUTHENTICATION);
        mapOut.put(SendKeys.EMAIL, email);
        mapOut.put(SendKeys.PASS, pass);

        outcome.add(mapOut);

        String status = "";

        HashMap<String, Object> map = findMapByTitle(SendKeys.AUTHENTICATION_ANSWER);

        status = (String) map.get(SendKeys.AUTHENTICATION_ANSWER);

        Log.d(TAG, "Authentication status: " + status);

        if(Authentication.SUCCESS.equals(status)){
            String nick = (String) map.get(SendKeys.NICK);
            client.setNick(nick);
            client.setConfirmed(true);
            this.client = client;
        }

        return status;
    }

    public String registration(Client client) {
        String email = client.getEmail();
        String nick = client.getNick();
        String pass = client.getPass();
        String status = "";

        HashMap<String, String> mapOut = new HashMap<>();
        mapOut.put(SendKeys.TITLE, SendKeys.REGISTER);
        mapOut.put(SendKeys.EMAIL, email);
        mapOut.put(SendKeys.NICK, nick);
        mapOut.put(SendKeys.PASS, pass);

        outcome.add(mapOut);

        HashMap<String, Object> mapIn = findMapByTitle(SendKeys.REGISTRATION_ANSWER);
        status = (String) mapIn.get(SendKeys.REGISTRATION_ANSWER);

        return status;
    }

    public void sendNewNick(String nick) {
        HashMap<String, String> map = new HashMap<>();
        map.put(SendKeys.TITLE, SendKeys.NEWNICK);
        map.put(SendKeys.NEWNICK, nick);
        map.put(SendKeys.EMAIL, client.getEmail());
        outcome.add(map);
    }

    public void askForFriends() {
        new Thread(() -> {
            while (!ClientThread.this.isInterrupted()) {
                try {
                    TimeUnit.SECONDS.sleep(TIMEOUT_FRIEND_REQUEST_SECONDS);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                if (controller == null || !(controller instanceof ContactsController)) {
                    return;
                }
                HashMap<String, String> mapOut = new HashMap<>();
                mapOut.put(SendKeys.TITLE, SendKeys.GET_FRIENDS);

                outcome.add(mapOut);

                HashMap<String, Object> mapIn = findMapByTitle(SendKeys.FRIENDS_ANSWER);

                ArrayList<Client> clients = (ArrayList<Client>) mapIn.get(SendKeys.FRIENDS_ANSWER);

                if (controller != null && controller instanceof ContactsController) {
                    ((ContactsController) controller).addFriends(clients);
                }

                System.out.println(client.getEmail() + " received " + clients.size() + " friends.");
            }
        }).start();
    }

    public void sendMessage(Message message) {
        outcome.add(message);
    }

    public static void setController(Controller c) {
        controller = c;
    }

    public Client getClient() {
        return client;
    }

    private HashMap<String, Object> findMapByTitle(String key){
        HashMap<String, Object> map = null;
        while (map == null){
//            for (HashMap<String, String> mapIn : income) {
//                if (mapIn.get(SendKeys.TITLE).equals(key)) {
//                    map = mapIn;
//                    income.remove(mapIn);
//                    break;
//                }
//            }

//            for (Iterator<HashMap<String, String>> it = income.iterator(); it.hasNext(); ) {
//                HashMap<String, String> mapIn = it.next();
//                if (mapIn.get(SendKeys.TITLE).equals(key)) {
//                    map = mapIn;
//                    it.remove();
//                    break;
//                }
//            }

            Iterator<Object> iterator = income.iterator();
            try {
            while(iterator.hasNext()) {
                Object o = iterator.next();
                if (o instanceof HashMap) {
                    HashMap<String, Object> mapIn = (HashMap<String, Object>) o;
                    if (mapIn.get(SendKeys.TITLE).equals(key)) {
                        map = mapIn;
                        iterator.remove();
                        break;
                    }
                    }
            }
            } catch (ConcurrentModificationException e) {
                System.out.println("Concurrent error. Trying again.");
            }
        }
        return map;
    }
}
