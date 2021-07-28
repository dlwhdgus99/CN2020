import java.io.Serializable;

public class CtrPacket implements Serializable {

    static final long serialVersionUID = 1L;
    private byte SeqNo;
    private short CHKsum;

    CtrPacket(int i){
        this.SeqNo = (byte) i;
        this.CHKsum = 0x0000;
    }

    public int getSeqNo(){
        return this.SeqNo;
    }

    public int getCHKsum(){
        return this.CHKsum;
    }
}
