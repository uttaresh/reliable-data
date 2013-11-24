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
	 * java receiver <receiver-port> <loss-pattern>
	 * @param args
	 */
	public static void main(String[] args) {
		/*
		 * Verify command line args
		 */
		if (args.length != 2){
			System.out.println("Error. Incorrect syntax.");
			System.out.println("java receiver <receiver-port> <loss-pattern>");
			System.exit(1);
		}
		int port = 0;		
		try{
			port = Integer.parseInt(args[0]);			
		}catch(NumberFormatException e){
			System.out.println("Error. Receiver port must be int!");
			System.out.println("java receiver <receiver-port> <loss-pattern>");
			System.exit(1);			
		}
		if (new File(args[1]).exists()==false){
			System.out.println("Error. Could not find loss file.");
			System.exit(1);
		}
		
		packet_size = common.SerializedPacketSize();
		
		/* Get required loss pattern from file */
		byte[] lossdata=null;
		try{
			RandomAccessFile lossfile = new RandomAccessFile(args[1],"r");
			lossdata = new byte[(int)lossfile.length()];
			lossfile.readFully(lossdata);
			lossfile.close();
		}catch(Exception e){
			e.printStackTrace();
		}
		String loss_text = new String(lossdata);
		
		// No packets received initially
		long RSN=0;
		
		int temp_num = 0;
		// Read in first number to determine loss method
		int loss_method=0;
		int i;
		for (i=0; i<loss_text.length(); i++){
			if (loss_text.charAt(i) >= '0' && loss_text.charAt(i) <= '9'){
				loss_method = loss_text.charAt(i) - '0';
				break;
			}
		}
		
		// Drop every few packets
		int drop_every=0;
		if (loss_method == 1){
			for (i=i+1;i<loss_text.length(); i++){
				if (loss_text.charAt(i)>='0'&&loss_text.charAt(i)<='9'){
					temp_num = temp_num*10+(loss_text.charAt(i)-'0');
				}else{
					if (temp_num!=0){
						drop_every = temp_num;
						break;
					}
				}
			}			
		}
		
		// Create predetermined queue of packets to drop
		PriorityQueue<Integer> drop_queue=null;
		if (loss_method == 2){
			drop_queue = new PriorityQueue<Integer>();
			for (i=i+1; i<loss_text.length(); i++){
				if (loss_text.charAt(i)>='0'&&loss_text.charAt(i)<='9'){
					temp_num = temp_num*10+(loss_text.charAt(i)-'0');
				}else{
					if (temp_num!=0){
						drop_queue.add(new Integer(temp_num));
						temp_num = 0;
					}
				}
			}
		}
		
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
				RSN++;
				if (RSN==1) System.out.print("Receiving file..");
				//else System.out.print(".");
				if (loss_method == 1 && (RSN%drop_every==1)) continue;
				if (loss_method == 2 && (drop_queue.peek()!=null)){
					if (drop_queue.peek().intValue()==RSN){
						drop_queue.poll().intValue();
						continue;
					}	
				}
				
				senderAddress = packet.getAddress();
				senderPort = packet.getPort();
				
				TCPSegment this_seg = common.bytesToObject(packet_data);
			//System.out.println("Seq received: " + this_seg.seq_no + " wanted: " + firstUnACKed);
				
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
						if (temp.seq_no<=firstUnACKed){			
							int size;
							// Don't write trailing NULs
							for (size=MSS;temp.data[size-1]==0&&size>0;size--);
							f.write(packet_queue.poll().data, 0, size);						
							firstUnACKed += MSS;
						}else break;
					}
				}
				transmitSeg(DATAACK, firstUnACKed);

				// Process DATA segment here
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		/*
		 * Send FINACK for every FIN received.
		 */
		transmitSeg(FINACK, 0);
		loopForever = true;
		byte[] temp_data = new byte[packet_size];
		DatagramPacket temp = new DatagramPacket(temp_data, packet_size);
		try {
			receiverSocket.setSoTimeout(2000);	// Wait for 2 seconds. Enough time for our purposes
		} catch (SocketException e1) {
			e1.printStackTrace();
		}
		while (loopForever){
			try {
				receiverSocket.receive(temp);
				if (common.bytesToObject(temp_data).flag==FIN){
					// Send FINACK to server
					transmitSeg(FINACK, 0);
					loopForever = false;
				}
			}catch(SocketTimeoutException e){
				break;
			}catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		/*
		 * Send FIN until FINACK received
		 */
		loopForever = true;
		while(loopForever){
			transmitSeg(FIN, 0);
			try {
				receiverSocket.setSoTimeout(500);	// Are we supposed to keep a timeout calculation
			} catch (SocketException e) {			// JUST for this? Seems wasteful
				System.out.println("Error. Could not set socket timeout");
				e.printStackTrace();
			}
			try {
				receiverSocket.receive(temp);
				if (common.bytesToObject(temp_data).flag == FINACK)
					loopForever = false;				
			}catch(SocketTimeoutException e){
				continue;
			}catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		/* Close connection */
		receiverSocket.close();
		
		/*
		 * Print to screen and close file
		 */
		try {
			f.close();
		} catch (IOException e) {
			e.printStackTrace();
		}	
		byte[] filedata=null;
		try {
			RandomAccessFile fin = new RandomAccessFile("output.txt", "r");
			filedata = new byte[(int)fin.length()];
			fin.readFully(filedata);
			fin.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println("\nFile successfully received. File contents:\n");
		System.out.println(new String(filedata));
	}

}
