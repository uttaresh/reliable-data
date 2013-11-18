import java.io.Serializable;

/**
 * 
 */

/**
 * Format for TCP Packet being sent
 * @author Uttaresh
 *
 */
public class TCPSegment implements Serializable, Comparable<TCPSegment> {
	private static final long serialVersionUID = -6349365949955895950L;

	@Override
	public int compareTo(TCPSegment anotherSegment) {
	    if (this.seq_no<anotherSegment.seq_no) return -1;
	    else if (this.seq_no==anotherSegment.seq_no) return 0;
	    else return 1;
	}
	
	public
		int flag;
		int seq_no;
		int ack_no;
		/*
		 *  TODO : need "data" in here. not pointer... actual data.
		 *  For now, let's make it the MSS as static, i.e. 100 bytes
		 */
		byte[] data = new byte[100];			
}
