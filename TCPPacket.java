import java.io.Serializable;

/**
 * 
 */

/**
 * Format for TCP Packet being sent
 * @author Uttaresh
 *
 */
public class TCPPacket implements Serializable{
	private static final long serialVersionUID = -6349365949955895950L;
	public static enum flag_t{
		SYN, SYNACK, ACK
	}
	public
		int src_port;
		int dest_port;
		flag_t flag;
		int seq_no;
		int ack_no;
		int window_size;
		/*
		 *  TODO : need "data" in here. not pointer... actual data.
		 *  For now, let's make it the MSS as static, i.e. 100 bytes
		 */
		byte[] data = new byte[100];			
}
