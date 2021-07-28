import java.io.*;
import java.net.*;
import java.nio.file.*;

public class FtpServer {

    public static void main(String[] args){

        String command;
        String[] request;
        ServerSocket welcomeSocket;
        ServerSocket dataWelcomeSocket;
        Socket serverSocket;
        BufferedReader inFromClient;
        PrintWriter outToClient;
        File currentDirectory = new File(".");

        int commandPort = 2020;
        int dataPort = 2121;

        if(args.length == 1){
            commandPort = Integer.parseInt(args[0]);
        }

        if(args.length == 2){
            commandPort = Integer.parseInt(args[0]);
            dataPort = Integer.parseInt(args[1]);
        }

        boolean[] isDrop = new boolean[100];
        boolean[] isTimeOut = new boolean[100];
        boolean[] isBitError = new boolean[100];

        try {
            welcomeSocket = new ServerSocket(commandPort);
            dataWelcomeSocket = new ServerSocket(dataPort);

            while(true) {

                serverSocket = welcomeSocket.accept(); // command channel
                inFromClient = new BufferedReader(new InputStreamReader(serverSocket.getInputStream()));
                outToClient = new PrintWriter(new BufferedWriter(new OutputStreamWriter(serverSocket.getOutputStream())));

                innerLoop:
                while (true) {

                    command = inFromClient.readLine();
                    System.out.println("Request: " + command);
                    request = command.split(" ");

                    switch (request[0]) {

                        case "CD":
                            if(request.length == 1){
                                System.out.println("Response: 200 Moved to " + currentDirectory.getCanonicalPath());
                                System.out.println();
                                outToClient.println("200 Moved to " + currentDirectory.getCanonicalPath());
                            }
                            else {
                                Path path = Paths.get(request[1]);

                                if (!path.isAbsolute()) { //상대경로인 경우
                                    String pathName = currentDirectory.getCanonicalPath() + "/" + request[1];
                                    path = Paths.get(pathName);
                                }
                                if (!path.toFile().isDirectory()) { //디렉토리 아닌 경우
                                    System.out.println("Response: 501 Failed-directory name is invalid");
                                    System.out.println();
                                    outToClient.println("501 Failed-directory name is invalid");
                                }
                                else if(isRoot(currentDirectory) && request[1].equals("..")){
                                    System.out.println("Response: 502 Failed-Already root directory");
                                    System.out.println();
                                    outToClient.println("502 Failed-already root directory");
                                    outToClient.flush();
                                }
                                else { //디렉토리인 경우
                                    currentDirectory = path.toFile();
                                    System.out.println("Response: 200 Moved to " + currentDirectory.getCanonicalPath());
                                    System.out.println();
                                    outToClient.println("200 Moved to " + currentDirectory.getCanonicalPath());
                                }
                            }
                            outToClient.flush();
                            break;

                        case "LIST":
                            File[] allFiles;
                            String listFiles;

                            if(request.length == 1){
                                allFiles = currentDirectory.listFiles();
                                listFiles = concatenate(allFiles);
                                System.out.println("Response: 200 Comprising " + allFiles.length + " entries");
                                System.out.println();
                                outToClient.println("200 Comprising " + allFiles.length + " entries\r\n" + listFiles);
                            }
                            else {
                                Path path = Paths.get(request[1]);
                                if (!path.isAbsolute()) {
                                    String pathName = currentDirectory.getCanonicalPath() + "/" + request[1];
                                    path = Paths.get(pathName);
                                }
                                if (!path.toFile().isDirectory()) { //디렉토리 아닌 경우
                                    System.out.println("Response: 501 Failed-directory name is invalid");
                                    System.out.println();
                                    outToClient.println("501 Failed-directory name is invalid");
                                } else if (isRoot(currentDirectory) && request[1].equals("..")) {
                                    System.out.println("Response: 502 Failed-Already root directory");
                                    System.out.println();
                                    outToClient.println("502 Failed-already root directory");
                                    outToClient.flush();
                                } else { //디렉토리인 경우
                                    allFiles = path.toFile().listFiles();
                                    listFiles = concatenate(allFiles);
                                    System.out.println("Response: 200 Comprising " + allFiles.length + " entries");
                                    System.out.println();
                                    outToClient.println("200 Comprising " + allFiles.length + " entries\r\n" + listFiles);
                                }
                            }
                            outToClient.flush();
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
                            Path path = Paths.get(request[1]);
                            if(!path.isAbsolute()){
                                String pathName = currentDirectory.getCanonicalPath() + "/" + request[1];
                                path = Paths.get(pathName);
                            }
                            File sendFile = path.toFile();
                            if(!sendFile.exists()){ // 파일이 존재하지 않을 때
                                System.out.println("Response: 401 Failed-No such file exists");
                                System.out.println();
                                outToClient.println("401 Failed-No such file exists");
                                outToClient.flush();
                                break;
                            }
                            //파일이 존재할 때
                            long fileSize = sendFile.length();

                            //response 전송
                            System.out.println("Response: 200 Containing " + fileSize + " bytes in total");
                            System.out.println();
                            outToClient.println("200 Containing " + fileSize + " bytes in total");
                            outToClient.flush();

                            Socket dataSocket = dataWelcomeSocket.accept();
                            FileInputStream fis = new FileInputStream(sendFile);
                            boolean[] isAcked = new boolean[(int) (fileSize/1000 + 1)];

                            //데이터 전송
                            SenderReceivingThread receivingThread = new SenderReceivingThread(dataSocket, isAcked, (int) fileSize);
                            SenderSendingThread sendingThread = new SenderSendingThread(dataSocket, fis, isAcked, (int) fileSize);

                            sendingThread.setIsDrop(isDrop);
                            sendingThread.setIsTimeOut(isTimeOut);
                            sendingThread.setIsBitError(isBitError);

                            Thread rcv = new Thread(receivingThread);
                            Thread send = new Thread(sendingThread);

                            rcv.start();
                            send.start();
                            break;

                        case "PUT":
                            fileSize = Long.parseLong(inFromClient.readLine());

                            System.out.println("Request: " + fileSize);
                            System.out.println("Response: 200 Ready to receive");
                            System.out.println();
                            outToClient.println("200 Ready to receive");
                            outToClient.flush();

                            dataSocket = dataWelcomeSocket.accept();
                            String extension[] = request[1].split("\\.");
                            FileOutputStream fos = new FileOutputStream(currentDirectory.getCanonicalPath() + "/" +extension[0]+ "_copy." + extension[1], true);
                            String isFinish = "false";

                            ReceiverReceivingThread receiverThread = new ReceiverReceivingThread(dataSocket, fos, (int)fileSize, isFinish);
                            rcv = new Thread(receiverThread);

                            rcv.start();
                            if(isFinish.equals("true")) dataSocket.close();
                            break;

                        case "QUIT":
                            serverSocket.close();
                            break innerLoop;
                    }
                }
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static boolean isRoot(File currentDirectory) throws IOException {
        String pathName = currentDirectory.getCanonicalPath();
        if(pathName.equals("/")){
            return true;
        }
        return false;
    }

    public static String concatenate(File[] allFiles) {

        String listFiles = "";

        for(int i = 0; i < allFiles.length-1; i++){
            if(allFiles[i].isDirectory()){
                listFiles += allFiles[i].getName() + ",-,";
            }
            else {
                listFiles += allFiles[i].getName() + "," + allFiles[i].length() + ",";
            }
        }
        if(allFiles[allFiles.length-1].isDirectory()){
            listFiles += allFiles[allFiles.length-1].getName() + ",-";
        }
        else {
            listFiles += allFiles[allFiles.length-1].getName() + "," + allFiles[allFiles.length-1].length();
        }
        return listFiles;
    }
}
