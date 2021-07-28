import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.Socket;

public class SenderReceivingThread implements Runnable{

    private Socket dataSocket;
    private boolean[] isAcked;
    private int fileSize;

    SenderReceivingThread(Socket s, boolean[]isAcked, int fileSize){
        this.dataSocket = s;
        this.isAcked = isAcked;
        this.fileSize = fileSize;
    }

    @Override
    public synchronized void run() {

        ObjectInputStream ois = null;
        try {
            ois = new ObjectInputStream(dataSocket.getInputStream());

            int windowSize = 5;
            CtrPacket rcvPacket;

            while (true) {
                rcvPacket = (CtrPacket) ois.readObject();
                for(int i = SenderSendingThread.getSend_base(); i < SenderSendingThread.getSend_base() + windowSize && i < fileSize/1000 + 1; i++){
                    if(rcvPacket.getSeqNo() == i % 16 && !isAcked[i]){

                        isAcked[i] = true;
                        System.out.println("ack " + i%16);

                        if(i == SenderSendingThread.getSend_base()){
                            int j = i;
                            while(j != isAcked.length && isAcked[j]) j++;
                            SenderSendingThread.setSend_base(j);
                        }
                        break;
                    }
                }
            }
        }
        catch (IOException | ClassNotFoundException e) {
            try {
                ois.close();
                dataSocket.close();
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }
    }
}
