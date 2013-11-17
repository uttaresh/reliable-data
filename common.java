import java.io.*;


/**
 * 
 */

/**
 * @author Uttaresh
 *
 */
public class common {
	public static byte[] objectToBytes(TCPSegment tcp_packet){
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		ObjectOutput out = null;
		byte[] packet_data = null;
		try{
			out = new ObjectOutputStream(bos);
			out.writeObject(tcp_packet);
			packet_data = bos.toByteArray();
			out.close();
			bos.close();
		}catch (Exception e){
			e.printStackTrace();
		}
		return packet_data;
	}
	
	public static TCPSegment bytesToObject(byte[] data){
		ByteArrayInputStream bis = new ByteArrayInputStream(data);
		ObjectInput in = null;
		Object o = null;
		try {
			in = new ObjectInputStream(bis);
			 o = in.readObject(); 
			bis.close();
			in.close();
		}catch (Exception e){
			e.printStackTrace();
		}
		return (TCPSegment) o;
	}
	
	public static int SerializedPacketSize(){
		TCPSegment temp = new TCPSegment();
		return objectToBytes(temp).length;
	}
	
}
