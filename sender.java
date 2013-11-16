/**
 * CS 438 -- MP2
 * Reliable data transport over UDP
 */
import java.net.*;
import java.io.*;


/**
 * @author 	Uttaresh
 * 			mehta32@illinois.edu
 * 			uttareshm@gmail.com
 */
public class sender implements Serializable {
	private static final long serialVersionUID = -3322139518908278000L;

	static enum flag_t{
		SYN, SYNACK, ACK
	}
	public class TCPPacket implements Serializable{
		private static final long serialVersionUID = -6349365949955895950L;
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
	
	private static byte[] objectToBytes(sender.TCPPacket tcp_packet){
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
	
	private static sender.TCPPacket bytesToObject(byte[] data){
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
		return (TCPPacket) o;
	}
	
	
	/**
	 * To compile the sender, use the following command:
	 * javac sender
	 * 
	 * To start sender process, use the following command:
	 * java sender <filename> <receiver-domain-name> <receiver-port>
	 * @param args
	 */
	public static void main(String[] args) {
		
		/*
		 * Parse command line arguments
		 */
		if (args.length != 3){
			System.out.println("Error. Incorrect syntax!");
			System.out.println("java sender <filename> <receiver-domain-name> <receiver-port>");
			System.exit(1);
		}
		int port = 0;
		try{
			port = Integer.parseInt(args[2]);			
		}catch(NumberFormatException e){
			System.out.println("Error. Receiver port must be int!");
			System.out.println("java sender <filename> <receiver-domain-name> <receiver-port>");
			System.exit(1);			
		}
		File f = new File(args[0]);
		if (!f.exists()){
			System.out.println("Error. File does not exist!");
			System.exit(1);
		}
		
		/*
		 * Establish UDP socket
		 */
		DatagramSocket UDPConnection = null;
		try {
			UDPConnection = new DatagramSocket();
		} catch (SocketException e) {
			System.out.println("Error. Could not create UDP connection");
			System.exit(1);
		}
		
		
		/*
		 * Send SYN and wait for SYN ACK
		 */
		boolean syn_acked = false;
		while(!syn_acked){
			sender senderInstance = new sender();
			sender.TCPPacket syn = senderInstance.new TCPPacket();
	
			syn.dest_port = port;
			syn.flag = flag_t.SYN;
			byte[] packet_data = sender.objectToBytes(syn);
			byte[] recv_buff = new byte[packet_data.length];
			
			DatagramPacket packet = null;
			DatagramPacket received = null;
			try{
				packet = new DatagramPacket(packet_data,packet_data.length, InetAddress.getByName(args[1]), port);
				received = new DatagramPacket(recv_buff, recv_buff.length);
			}catch(UnknownHostException e){
				
				// Could not recognize host		
				e.printStackTrace();
				System.exit(1);
			}
			try {
				UDPConnection.send(packet);
			} catch (IOException e) {
				// Could not send packet over UDP
				e.printStackTrace();
				System.exit(1);
			}
			
			// Wait for SYN ACK until timeout
			// TODO Can we send/receive in the same UDP socket??
			boolean socketTimeout = false;
			try {
				UDPConnection.setSoTimeout(1000);	// What's the timeout time/mechanism?
				UDPConnection.receive(received);
			
			} catch (SocketTimeoutException e){
				syn_acked = false;
				socketTimeout = true;
				
			} catch (SocketException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}catch (IOException e){
				e.printStackTrace();
			}finally{
				// convert datagram bytes[] to TCPPacket and check if SYN ACK
				if (socketTimeout == false){
					sender.TCPPacket received_packet = bytesToObject(received.getData());
					if (received_packet.flag == flag_t.SYNACK) syn_acked = true;	
				}
			}
		}
		
		/*
		 * Start sending packets. Wait for ACK
		 */
		
		/*
		 * Upon receiving ACK, decrement unacked count. If total_size_successfully_sent>=total_data_size, end transmission
		 * Otherwise, loop to sending packets
		 */
		
		/*
		 * Once all packets sent, terminate session
		 */
		
		/*
		 * Terminate UDP connection
		 */
		UDPConnection.close();
		
	}

}
