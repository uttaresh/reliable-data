import java.io.Serializable;

/**
 * 
 */

/**
 * Format for TCP Packet being sent
 * @author Uttaresh
 *
 */
public class TCPSegment implements Serializable{
	private static final long serialVersionUID = -6349365949955895950L;
	public static enum flag_t{
		DATA, DATAACK, FIN, FINACK
	}
	public
		flag_t flag;
		int seq_no;
		int ack_no;
		/*
		 *  TODO : need "data" in here. not pointer... actual data.
		 *  For now, let's make it the MSS as static, i.e. 100 bytes
		 */
		byte[] data = new byte[100];			
}
