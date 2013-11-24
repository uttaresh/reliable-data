import java.io.*;


/**
 * 
 */

/**
 * @author  Uttaresh
 * 			uttareshm@gmail.com
 *
 */
public class common {
	
	/* Reference: 
	 * http://stackoverflow.com/questions/2836646/java-serializable-object-to-byte-array
	 */
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
	
	/* Reference:
	 * http://stackoverflow.com/questions/2836646/java-serializable-object-to-byte-array
	 */
	public static TCPSegment bytesToObject(byte[] data){
		ByteArrayInputStream bis = new ByteArrayInputStream(data);
		ObjectInput in = null;
		Object o = null;
		try {
			in = new ObjectInputStream(bis);
			 o = in.readObject(); 
			bis.close();
			in.close();
		}catch(EOFException e){
			// Damn EOFs...
			try {
				bis.close();
				in.close();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
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
