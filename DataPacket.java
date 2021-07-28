import java.io.Serializable;

public class DataPacket implements Serializable {

    static final long serialVersionUID = 1L;
    private byte SeqNo;
    private short CHKsum;
    private short Size;
    private byte[] dataChunk;

    DataPacket(byte i, int size){
        this.SeqNo = i;
        this.CHKsum = 0;
        dataChunk = new byte[1000];
        this.Size = (short) size;
    }

    public int getSeqNo() {
        return SeqNo;
    }

    public void setSeqNo(byte seqNo) {
        SeqNo = seqNo;
    }

    public int getCHKsum() {
        return CHKsum;
    }

    public void setCHKsum(short CHKsum) {
        this.CHKsum = CHKsum;
    }

    public int getSize() {
        return Size;
    }

    public void setSize(int size) {
        Size = (short) size;
    }

    public byte[] getDataChunk() {
        return dataChunk;
    }

    public void setDataChunk(byte[] dataChunk) {
        this.dataChunk = dataChunk;
    }


}
