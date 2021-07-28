import java.io.*;
import java.net.*;

public class FtpClient {

    public static void main(String[] args) {

        String command;
        String[] request;
        String response;
        String[] status;
        Socket clientSocket = null;
        BufferedReader inFromUser = null;
        BufferedReader inFromServer = null;
        PrintWriter outToServer = null;

        String ip = "127.0.0.1";
        int commandPort = 2020;
        int dataPort = 2121;

        if(args.length == 1){
            ip = args[0];
        }
        else if(args.length == 2){
            ip = args[0];
            commandPort = Integer.parseInt(args[1]);
        }
        else if(args.length == 3){
            ip = args[0];
            commandPort = Integer.parseInt(args[1]);
            dataPort = Integer.parseInt(args[2]);
        }

        try {
            clientSocket = new Socket(ip, commandPort); // command channel
            inFromUser = new BufferedReader(new InputStreamReader(System.in));
            inFromServer = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            outToServer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream())));
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        boolean[] isDrop = new boolean[100];
        boolean[] isTimeOut = new boolean[100];
        boolean[] isBitError = new boolean[100];

        loop:
        while(true) {
            try {
                command = inFromUser.readLine();
                request = command.split(" ");

                outToServer.println(command);
                outToServer.flush();

                switch (request[0]) {
                    case "CD":
                        response = inFromServer.readLine();
                        status = response.split(" ", 2);

                        if (200 <= Integer.parseInt(status[0]) && Integer.parseInt(status[0]) < 300) {
                            String[] tempArr = status[1].split(" ");
                            System.out.println(tempArr[2]);
                        }
                        else if(Integer.parseInt(status[0]) == 501){
                            System.out.println("Failed-directory name is invalid");
                        }
                        else if(Integer.parseInt(status[0]) == 502){
                            System.out.println("Failed-already root directory");
                        }
                        else{
                            System.out.println("Failed");
                        }
                        break;

                    case "LIST":
                        response = inFromServer.readLine();
                        status = response.split(" ", 2);

                        if (200 <= Integer.parseInt(status[0]) && Integer.parseInt(status[0]) < 300) {
                            String fileList = inFromServer.readLine();
                            String[] tempArr = fileList.split(",");
                            for (int i = 0; i < tempArr.length - 1; i += 2) {
                                System.out.println(tempArr[i] + "," + tempArr[i + 1]);
                            }
                        }
                        else if(Integer.parseInt(status[0]) == 503){
                            System.out.println("It's over the root directory");
                        }
                        else{
                            System.out.println("Failed-directory name is invalid");
                        }
                        break;

                    case "DROP":
                        String[] d = request[1].split(",");
                        for(int i = 0; i < d.length; i++){
                            int j = Integer.parseInt(d[i].replaceAll("[^0-9]", ""));
                            isDrop[j-1] = true;
                        }
                        break;

                    case "TIMEOUT":
                        String[] t = request[1].split(",");
                        for(int i = 0; i < t.length; i++){
                            int j = Integer.parseInt(t[i].replaceAll("[^0-9]", ""));
                            isTimeOut[j-1] = true;
                        }
                        break;

                    case "BITERROR":
                        String[] b = request[1].split(",");
                        for(int i = 0; i < b.length; i++){
                            int j = Integer.parseInt(b[i].replaceAll("[^0-9]", ""));
                            isBitError[j-1] = true;
                        }
                        break;

                    case "GET":
                        response = inFromServer.readLine();
                        status = response.split(" ", 2);

                        String[] path;
                        int fileSize;

                        if(200 <= Integer.parseInt(status[0]) && Integer.parseInt(status[0]) < 300) {
                            path = request[1].split("/");
                            String fileName = path[path.length - 1];
                            String extension[] = fileName.split("\\.");

                            fileSize = Integer.parseInt(status[1].split(" ")[1]);
                            System.out.println("Received " + fileName + "   " + status[1].split(" ")[1] + " bytes");

                            Socket dataSocket = new Socket(ip, dataPort);
                            FileOutputStream fos = new FileOutputStream(extension[0] + "_copy." + extension[1], true);
                            String isFinish = "false";

                            ReceiverReceivingThread receiverThread = new ReceiverReceivingThread(dataSocket, fos, fileSize, isFinish);
                            Thread rcv = new Thread(receiverThread);

                            rcv.start();
                            if(isFinish.equals("true")) dataSocket.close();
                        }
                        else if(400 <= Integer.parseInt(status[0]) && Integer.parseInt(status[0]) < 500){
                            System.out.println("Failed-Such file does not exist!");
                        }
                        else{
                            System.out.println("Failed");
                        }
                        break;

                    case "PUT":
                        File sendFile = new File(request[1]);
                        fileSize = (int) sendFile.length();
                        outToServer.println(fileSize);
                        outToServer.flush();

                        response = inFromServer.readLine();
                        status = response.split(" ", 2);

                        if(200 <= Integer.parseInt(status[0]) && Integer.parseInt(status[0]) < 300){

                            System.out.println(request[1] + " transferred  /" + fileSize + " bytes");

                            Socket dataSocket = new Socket(ip, dataPort);
                            FileInputStream fis = new FileInputStream(request[1]);

                            boolean[] isAcked = new boolean[fileSize/1000 + 1];

                            SenderReceivingThread receiverThread = new SenderReceivingThread(dataSocket, isAcked, fileSize);
                            SenderSendingThread senderThread = new SenderSendingThread(dataSocket, fis, isAcked, fileSize);

                            senderThread.setIsDrop(isDrop);
                            senderThread.setIsTimeOut(isTimeOut);
                            senderThread.setIsBitError(isBitError);

                            Thread rcv = new Thread(receiverThread);
                            Thread send = new Thread(senderThread);

                            rcv.start();
                            send.start();
                        }
                        else{
                            System.out.println("Failed");
                        }
                        break;

                    case "QUIT":
                        clientSocket.close();
                        System.exit(0);
                }
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}