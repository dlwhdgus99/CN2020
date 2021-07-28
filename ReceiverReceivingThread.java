import javax.xml.crypto.Data;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class ReceiverReceivingThread implements Runnable{

    private Socket dataSocket;
    private FileOutputStream fos;
    private int fileSize;
    private String isFinish;

    ReceiverReceivingThread(Socket s, FileOutputStream f, int fileSize, String isFinish){
        this.dataSocket = s;
        this.fos = f;
        this.fileSize = fileSize;
        this.isFinish = isFinish;
    }

    @Override
    public synchronized void run() {

        try {
            ObjectInputStream ois = new ObjectInputStream(dataSocket.getInputStream());
            ObjectOutputStream oos = new ObjectOutputStream(dataSocket.getOutputStream());

            DataPacket[] packetBuffer = new DataPacket[fileSize/1000 + 1];

            int rcv_base = 0;
            int windowSize = 5;

            while (true) {
                DataPacket rcvPacket = (DataPacket) ois.readObject();
                if(rcvPacket == null){
                    ois.close();
                    oos.close();
                    isFinish = "true";
                    break;
                }

                for(int i = Math.max(rcv_base - windowSize, 0); (i < rcv_base + windowSize) && (i < fileSize/1000 + 1); i++){

                    if(i%16 == rcvPacket.getSeqNo() && rcvPacket.getCHKsum() == 0){

                        if(i >= rcv_base && packetBuffer[i] == null) {
                            System.out.println("ack " + rcvPacket.getSeqNo());
                            packetBuffer[i] = rcvPacket;

                            if(i == rcv_base){
                                int j = i;
                                while(j != packetBuffer.length && packetBuffer[j] != null){
                                    int packetSize = (j == packetBuffer.length-1) ? (fileSize % 1000) : 1000;
                                    fos.write(packetBuffer[j].getDataChunk(), 0, packetSize);
                                    fos.flush();
                                    j++;
                                }
                                rcv_base = j;
                            }
                        }
                        CtrPacket ackPacket = new CtrPacket(rcvPacket.getSeqNo());
                        oos.writeObject(ackPacket);
                        oos.flush();
                        break;
                    }
                }
            }
        }
        catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }
}
