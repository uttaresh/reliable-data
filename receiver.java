/**
 * CS 438 -- MP2
 * Reliable data transport over UDP
 */
import java.net.*;
import java.util.PriorityQueue;
import java.io.*;


/**
 * @author 	Uttaresh
 * 			mehta32@illinois.edu
 * 			uttareshm@gmail.com
 */
public class receiver {
	
	static int packet_size=0;
	static int MSS=100;
	
	//Flags:
	static int DATA = 1;
	static int DATAACK = 2;
	static int FIN = 3;
	static int FINACK = 4;	
	
	static int firstUnACKed;
	static PriorityQueue<TCPSegment> packet_queue;
	
	static InetAddress senderAddress;
	static int senderPort;
	
	static DatagramSocket receiverSocket;
	
	/**
	 *  Function to send one TCPSegment
	 */
	static void transmitSeg(int flag, int nextData){
		// Create packet from function parameters
		DatagramPacket packet = null;
		TCPSegment segment = new TCPSegment();
		segment.flag = flag;
		
		if(flag == DATAACK){
			segment.ack_no = nextData;
		}
		
		byte[] packet_data = common.objectToBytes(segment);
		packet = new DatagramPacket(packet_data,packet_data.length, senderAddress, senderPort);
		// Send packet over UDP
		try {
			receiverSocket.send(packet);
		} catch (IOException e) {
			// Could not send packet over UDP
			e.printStackTrace();
			System.exit(1);
		}
	}	
	
	/**
	 * To compile the receiver, use the following command:
	 * javac receiver
	 * 
	 * To start receiver process, use the following command:
	 * java receiver <receiver-port>
	 * @param args
	 */
	public static void main(String[] args) {
		/*
		 * Verify command line args
		 */
		if (args.length != 1){
			System.out.println("Error. Incorrect syntax.");
			System.out.println("java receiver <receiver-port>");
			System.exit(1);
		}
		int port = 0;		
		try{
			port = Integer.parseInt(args[0]);			
		}catch(NumberFormatException e){
			System.out.println("Error. Receiver port must be int!");
			System.out.println("java receiver <receiver-port>");
			System.exit(1);			
		}
		packet_size = common.SerializedPacketSize();
		
		/*
		 * Open UDP listening socket
		 */
		receiverSocket = null;
		try {
			receiverSocket = new DatagramSocket(port);
		} catch (SocketException e) {
			System.out.println("Error. Could not start UDP Listening Socket");
			e.printStackTrace();
			System.exit(1);
		}		
		// We will store all unwritten (out of order) packets in a priority queue
		packet_queue = new PriorityQueue<TCPSegment>();
		FileOutputStream f = null;
		try {
			f = new FileOutputStream("output.txt");
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		}

		/*
		 * Receive data segments from sender and process received segments
		 */
		firstUnACKed = 0;
		boolean loopForever = true;
		while (loopForever){		
			byte[] packet_data = new byte[packet_size];		
			DatagramPacket packet = new DatagramPacket(packet_data, packet_size);
			try {
				receiverSocket.receive(packet);
				senderAddress = packet.getAddress();
				senderPort = packet.getPort();
				
				TCPSegment this_seg = common.bytesToObject(packet_data);
				
				if (this_seg.flag == FIN) break;
				
				// If this segment was not received before, save it to our queue
				if(packet_queue.contains(this_seg)==false){
					packet_queue.add(this_seg);
				}
				
				/* If this segment is the first UnACKed segment, then remove
				 * it from the queue and append to file along with all consecutive
				 * segments available after this. Also send an ACK for the last
				 * continuous segment available.
				 */
				if (this_seg.seq_no==firstUnACKed){
					TCPSegment temp;
					while( (temp = packet_queue.peek()) != null ){
						if (temp.seq_no==firstUnACKed){			
							int size;
							for (size=MSS;temp.data[size-1]==0&&size>0;size--);
							f.write(packet_queue.poll().data, 0, size);						
							firstUnACKed += MSS;
						}
					}
				}
				transmitSeg(DATAACK, firstUnACKed);

				// Process DATA segment here
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		/*
		 * Broke out of loop. Must have received FIN. Close file and start session termination
		 */		
		try {
			f.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		
		
		
		
		
		receiverSocket.close();
		
		
	}

}
