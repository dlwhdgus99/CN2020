import java.io.*;
import java.net.Socket;
import java.util.Timer;
import java.util.TimerTask;

public class SenderSendingThread implements Runnable{

    private Socket dataSocket;
    private FileInputStream fis;
    private boolean[] isAcked;
    private static int send_base;
    private int fileSize;
    private boolean[] isDrop;
    private boolean[] isTimeOut;
    private boolean[] isBitError;

    SenderSendingThread(Socket s, FileInputStream f, boolean[] isAcked, int fileSize){
        this.dataSocket = s;
        this.fis = f;
        this.isAcked = isAcked;
        this.send_base = 0;
        this.fileSize = fileSize;
    }

    @Override
    public void run() {

        try {
            ObjectOutputStream oos = new ObjectOutputStream(dataSocket.getOutputStream());
            int nextSeqNum = 0;
            int windowSize = 5;
            Timer[] timers = new Timer[fileSize/1000 + 1];
            TimerTask[] timerTasks = new TimerTask[fileSize/1000 + 1];
            TimerTask[] timerTasks_TimeOut = new TimerTask[fileSize/1000 + 1];
            DataPacket[] sendPackets = new DataPacket[fileSize/1000 + 1];

            for(int i = 0; i < fileSize/1000 + 1; i++) {
                //패킷 만들기
                sendPackets[i] = new DataPacket((byte) (i%16), fileSize);

                int packetSize = (i == fileSize / 1000) ? fileSize % 1000 : 1000;
                fis.read(sendPackets[i].getDataChunk(), 0, packetSize);

                //타이머 세팅
                timers[i] = new Timer();
                int finalI = i;

                timerTasks[finalI] = new TimerTask() {
                    @Override
                    public void run() {
                        try {
                            if (isAcked[finalI]) {
                                timerTasks[finalI].cancel();
                            }
                            else {
                                resend(oos, sendPackets[finalI]);
                                System.out.println(sendPackets[finalI].getSeqNo() + " timeout & retransmitted");
                            }
                        }
                        catch (IOException e){
                            e.printStackTrace();
                        }
                    }
                };

                timerTasks_TimeOut[i] = new TimerTask() {
                    @Override
                    public void run() {
                        try {
                            send(oos, sendPackets[finalI]);
                            timerTasks_TimeOut[finalI].cancel();
                        }
                        catch (IOException e) {
                            try {
                                dataSocket.close();
                            } catch (IOException ioException) {
                                ioException.printStackTrace();
                            }
                        }
                    }
                };
            }

            System.out.println("file size: " + fileSize);
            for (int i = 0; i < fileSize / 1000 + 1; i++) {
                //window 벗어나면 대기
                if (nextSeqNum >= send_base + windowSize){
                    Thread.sleep(1);
                    i--;
                    continue;
                }

                System.out.println(i%16);

                if(isDrop[i]) {
                    timers[i].schedule(timerTasks[i], 1000, 1000);
                    isDrop[i] = false;
                    continue;
                }
                if(isTimeOut[i]) {
                    timers[i].schedule(timerTasks[i], 1000, 1000);
                    timers[i].schedule(timerTasks_TimeOut[i],2000);
                    isTimeOut[i] = false;
                    continue;
                }
                if(isBitError[i]) {
                    sendPackets[i].setCHKsum((short) 0xFFFF);
                    send(oos,sendPackets[i]);
                    timers[i].schedule(timerTasks[i], 1000, 1000);
                    isBitError[i] = false;
                    continue;
                }
                send(oos, sendPackets[i]);
                nextSeqNum = i + 1;
                timers[i].schedule(timerTasks[i], 1000, 1000);
            }

            boolean allTrue = false;
            while(!allTrue){
                int count = 0;
                for(int i = 0; i < isAcked.length; i++){
                    if(isAcked[i]) count++;
                }
                if(isAcked.length == count) allTrue = true;
            }
            send(oos,null);
            oos.close();
        }
        catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public synchronized void send(ObjectOutputStream oos, DataPacket dataPacket) throws IOException {

        oos.writeObject(dataPacket);
        oos.flush();
    }

    public synchronized void resend(ObjectOutputStream oos, DataPacket dataPacket) throws IOException {

        DataPacket resendPacket = new DataPacket((byte) dataPacket.getSeqNo(), dataPacket.getSize());
        resendPacket.setDataChunk(dataPacket.getDataChunk());
        oos.writeObject(resendPacket);
        oos.flush();
    }

    public static int getSend_base(){
        return send_base;
    }

    public static void setSend_base(int send_base){
        SenderSendingThread.send_base = send_base;
    }

    public void setIsDrop(boolean[] isDrop){
        this.isDrop = isDrop;
    }

    public void setIsTimeOut(boolean[] isTimeOut){
        this.isTimeOut = isTimeOut;
    }

    public void setIsBitError(boolean[] isBitError){
        this.isBitError = isBitError;
    }

}