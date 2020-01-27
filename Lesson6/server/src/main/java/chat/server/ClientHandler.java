package chat.server;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class ClientHandler {
    private Server server;
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private String nickname;

    private boolean authFlag = true;
    private boolean chatFlag = true;

    public ClientHandler(Server server, Socket socket) throws IOException{
        this.server = server;
        this.socket = socket;
        this.in = new DataInputStream(socket.getInputStream());
        this.out = new DataOutputStream(socket.getOutputStream());
        new Thread(() ->{
            try{
                while (authFlag){
                    authenticatingStage();
                }
                while (chatFlag){
                    chattingStage();
                }
            } catch (IOException e){
                e.printStackTrace();
            } finally {
                close();
            }
        }).start();
    }

    private void authenticatingStage() throws IOException {
        String message = in.readUTF();
        System.out.println("Message from Client: " + message);
        if(message.startsWith("/auth ")){
            String[] tokens = message.split(" ", 3);
            String nickFromAuthManager = server.getAuthManager().getNicknameByLoginAndPassword(tokens[1], tokens[2]);
            if(nickFromAuthManager != null){
                if(server.isNickBusy(nickFromAuthManager)){
                    sendMessage("User is already logged in.");
                    return;
                }
                nickname = nickFromAuthManager;
                server.subscribe(this);
                sendMessage("/authok " + nickname);
                authFlag = false;
                return;
            } else {
                sendMessage("Login or password is incorrect");
            }
        }
    }

    private void chattingStage() throws IOException {
        String message = in.readUTF();
        System.out.println("Message from Client: " + message);
        if (message.startsWith("/")){
            if (message.startsWith("/w ")){
                String recipient = message.split(" ", 3)[1];
                message = message.split(" ", 3)[2];
                if(nickname.equals(recipient)){
                    sendMessage("Cannot whisper to self.");
                    return;
                }
                if(!server.whisperMessage(nickname, recipient, message)){
                    sendMessage(recipient + " is not online");
                }
            }
            if (message.equals("/end")){
                sendMessage("/end_confirm");
                chatFlag = false;
                return;
            }
        } else {
            server.broadcastMessage(nickname + ": " + message);
        }
    }

    public void sendMessage(String message){
        try{
            out.writeUTF(message);
        } catch (IOException e){
            e.printStackTrace();
        }
    }

    public String getNickname() {
        return nickname;
    }

    public void close(){
        server.unsubscribe(this);
        nickname = null;
        tryClose(in);
        tryClose(out);
        tryClose(socket);
    }

    private void tryClose(Closeable closeable){
        try{
            closeable.close();
        } catch (IOException e){
            e.printStackTrace();
        }
    }
}