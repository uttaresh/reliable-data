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
public class receiver {
	
	static int packet_size=0;
	
	
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
			port = Integer.parseInt(args[2]);			
		}catch(NumberFormatException e){
			System.out.println("Error. Receiver port must be int!");
			System.out.println("java receiver <receiver-port>");
			System.exit(1);			
		}
		packet_size = common.SerializedPacketSize();
		
		/*
		 * Open UDP listening socket
		 */
		DatagramSocket UDPServerSocket = null;
		try {
			UDPServerSocket = new DatagramSocket(port);
		} catch (SocketException e) {
			System.out.println("Error. Could not start UDP Listening Socket");
			e.printStackTrace();
			System.exit(1);
		}
		
		/*
		 * Listen for SYNs. On receiving SYN, send SYN_ACK
		 */
		
		byte[] packet_data = new byte[packet_size];
		
		DatagramPacket packet = new DatagramPacket(packet_data, packet_size);
		try {
			// TODO Set receive timeout here
			UDPServerSocket.receive(packet);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}

}
