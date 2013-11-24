/**
 * CS 438 -- MP2
 * Reliable data transport over UDP
 */
import java.net.*;
import java.io.*;
import java.util.*;

/**
 * @author 	Uttaresh
 * 			mehta32@illinois.edu
 * 			uttareshm@gmail.com
 */
public class sender {
	
	static FileOutputStream trace_file = null;
	static FileOutputStream cwnd_file = null;
	static long program_start_time;
	
	static DatagramSocket senderSocket = null;
	
	//Flags:
	static int DATA = 1;
	static int DATAACK = 2;
	static int FIN = 3;
	static int FINACK = 4;	
	
	
	static byte[] filedata = null;
	
	/**
	 * Function to read file into byte[] array
	 * 
	 * Reference: I found this somewhere on StackOverflow, I can't find the link
	 * tho... Sorry! :(
	 */
	public static byte[] readFile(File file) throws IOException {
        // Open file
        RandomAccessFile f = new RandomAccessFile(file, "r");
        try {
            // Get and check length
            long longlength = f.length();
            int length = (int) longlength;
            if (length != longlength)
                throw new IOException("File size >= 2 GB");
            // Read file and return data
            byte[] data = new byte[length];
            f.readFully(data);
            return data;
        } finally {
            f.close();
        }
    }
	
	/*
	 * State machine handler
	 */
	public static enum senderStates_t{
		SlowStart,
		CongestionAvoidance,
		FastRecovery
	}
	
	static float cwnd;
	static int window_start;
	static int window_end;
	static float ssthresh;
	
	static int lastByteACKed;
	static int lastByteSent;
	static int dupACKcount;
	
	static int dest_port;
	static InetAddress dest_address;
	
	// all times in milliseconds
	static double DevRTT;
	static double EstimatedRTT;
	static long start_time;
	static long end_time;
	static long TimeoutInterval;
	
	static senderStates_t currentState;
	static event_t event;
	
	static enum event_t{
		newACK,
		dupACK,
		timeout,
		noEvent
	}
	
	static int packet_size = 0;
	
	static int MSS=100;
	static int receiverBuffer = 2500;
	
	static void writeCWNDfile(){
		// Write time to cwnd file
		String cwndRecord = (new Float(((float)(System.nanoTime() - program_start_time)/1000000))).toString();
		cwndRecord += " " + (int)Math.ceil(cwnd) + "\n";
		try {
			cwnd_file.write(cwndRecord.getBytes());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 *  Function to send one TCPSegment
	 */
	static void transmitSeg(int flag, byte[] data, int offset){
		// Create packet from function parameters
		DatagramPacket packet = null;
		TCPSegment segment = new TCPSegment();
		segment.flag = flag;
		
		if(flag == DATA){
			segment.data = data;
			segment.seq_no = offset;
		}
		
		byte[] packet_data = common.objectToBytes(segment);
		packet = new DatagramPacket(packet_data,packet_data.length, dest_address, dest_port);
		// Send packet over UDP
		try {
			senderSocket.send(packet);
			//System.out.print(".");
		} catch (IOException e) {
			// Could not send packet over UDP
			e.printStackTrace();
			System.exit(1);
		}
	}
	
	static void startStateMachine(){
		start_time = System.currentTimeMillis();
		EstimatedRTT = 1000;	// Estimated RTT is 1000ms by default
		DevRTT = 100;
		TimeoutInterval = (long)(EstimatedRTT + 4*DevRTT);
		end_time = start_time + TimeoutInterval;
		
		dupACKcount = 0;

		cwnd = MSS;
		writeCWNDfile();
		
		window_start = 0;
		window_end = (int)cwnd-1;		
		
		ssthresh = 1000;
		lastByteSent = -1;
		lastByteACKed = -1;
		currentState = senderStates_t.SlowStart;
		
		// Send first segment and set timer
		transmitSeg(DATA, Arrays.copyOfRange(filedata, 0, 100) ,0);
		lastByteSent = 99;
		System.out.print("Sending file..");
	}
	
	/*
	 * Process a received ACK. Update window and timer attributes
	 */
	static event_t updateACKs(TCPSegment receivedACK){		

		if (receivedACK.ack_no == window_start){
			return event_t.dupACK;
		}else{
			// Shift window
			lastByteACKed = receivedACK.ack_no - 1;
			window_start = receivedACK.ack_no;
			window_end = Math.min((int) (window_start+cwnd-1), filedata.length-1);
			
			// Write time to trace file
			String ackRecord = (new Float(((float)(System.nanoTime() - program_start_time)/1000000))).toString();
			ackRecord += " " + window_start + "\n";
			try {
				trace_file.write(ackRecord.getBytes());
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			// Update timeout interval
			double SampleRTT = System.currentTimeMillis()-start_time;
			EstimatedRTT = 0.875*EstimatedRTT + 0.125*(SampleRTT);
			DevRTT = 0.75*DevRTT + 0.25*Math.abs(SampleRTT - EstimatedRTT);
			TimeoutInterval = (long)(EstimatedRTT + 4*DevRTT);
			return event_t.newACK;
		}
	}
	
	/*
	 * Send all unsent packets in the new window
	 */
	static void transmitNew(){		
		// Start timer if not already running
		if (end_time<=System.currentTimeMillis()){
			start_time = System.currentTimeMillis();
			end_time = start_time + TimeoutInterval;
		}
			
		
		window_start = lastByteACKed+1;	
		window_end = Math.min(lastByteACKed+(int)Math.ceil(cwnd), filedata.length-1);
		// Send all packets in window that have not been sent
		for (int i=lastByteSent+1;i<=window_end;i+=100){
			if (lastByteSent-lastByteACKed <= cwnd){
				transmitSeg(DATA, Arrays.copyOfRange(filedata, i, i+100), i);
				lastByteSent = i+99;
			}					
		}	

	}
	
	static void retransmit(){
		transmitSeg(DATA, Arrays.copyOfRange(filedata, lastByteACKed+1, lastByteACKed+101), lastByteACKed+1);
		start_time = System.currentTimeMillis();
		end_time = start_time + TimeoutInterval;
	}

	
	/**
	 * Call this function after processing a packet. Will transition into
	 * next state if necessary, making all state variable changes required.
	 * 
	 * Based on Figure 3.52 from Computer Networking, A Top Down Approach (6E)
	 */
	static void nextState(){
		boolean loop_over;
		do{
			loop_over=false;
			if (currentState == senderStates_t.SlowStart){
				//System.out.println("Slow Start");
				if (event == event_t.dupACK) dupACKcount++;	
				if (dupACKcount >= 3){
					ssthresh=cwnd/2;
					cwnd=Math.min(ssthresh+3*MSS, receiverBuffer);
					writeCWNDfile();
					currentState = senderStates_t.FastRecovery;
					retransmit();
				}else if (cwnd >= ssthresh){
					currentState = senderStates_t.CongestionAvoidance;
					loop_over=true;
					event = event_t.newACK;
				}else if (event == event_t.timeout){
					ssthresh=cwnd/2;
					cwnd=MSS;
					writeCWNDfile();
					dupACKcount=0;
					retransmit();
				}else if (event == event_t.newACK){
					cwnd=Math.min(cwnd+MSS, receiverBuffer);
					writeCWNDfile();
					dupACKcount=0;
					transmitNew();
				}
			}else if (currentState == senderStates_t.FastRecovery){
				//System.out.println("Fast Recovery");
				if (event == event_t.timeout){
					ssthresh=cwnd/2;
					cwnd=MSS;
					writeCWNDfile();
					dupACKcount=0;
					currentState = senderStates_t.SlowStart;
					retransmit();
				}else if(event == event_t.dupACK){
					cwnd=Math.min(cwnd+MSS, receiverBuffer);
					writeCWNDfile();
					transmitNew();
				}else if (event == event_t.newACK){
					cwnd=ssthresh;
					writeCWNDfile();
					dupACKcount=0;
					currentState = senderStates_t.CongestionAvoidance;
					loop_over = true;
					event = event_t.newACK;
				}
			}else if (currentState == senderStates_t.CongestionAvoidance){
				//System.out.println("Congestion Avoidance");
				if (event == event_t.dupACK) dupACKcount++;
				if (dupACKcount>=3){
					ssthresh=cwnd/2;
					cwnd=Math.min(ssthresh+3*MSS, receiverBuffer);
					writeCWNDfile();
					currentState = senderStates_t.FastRecovery;
					//loop_over=true;
					//event = event_t.noEvent;
					retransmit();
				}else if (event == event_t.timeout){
					ssthresh=cwnd/2;
					cwnd=MSS;
					writeCWNDfile();
					dupACKcount=0;
					currentState = senderStates_t.SlowStart;
					retransmit();
				}else if (event == event_t.newACK){
					//System.out.println("congestion to congestion");
					cwnd=Math.min(cwnd+MSS*(MSS/cwnd), receiverBuffer);
					writeCWNDfile();
					dupACKcount=0;
					transmitNew();
				}
			}	
		}while(loop_over);
	}
	
	/**
	 * To compile the sender, use the following command:
	 * javac sender
	 * 
	 * To start sender process, use the following command:
	 * java sender <filename> <receiver-domain-name> <receiver-port>
	 * @param args	command line arguments from terminal
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
		try{
			dest_port = Integer.parseInt(args[2]);			
		}catch(NumberFormatException e){
			System.out.println("Error. Receiver port must be int!");
			System.out.println("java sender <filename> <receiver-domain-name> <receiver-port>");
			System.exit(1);			
		}
		File f = new File(args[0]);
		if (!f.exists()){
			System.out.println("Error. File " + args[0] + " does not exist!");
			System.exit(1);
		}
		try {
			filedata = readFile(f);
		} catch (IOException e1) {
			System.out.println("Error. Could not read file.");
			System.exit(1);
		}
		try {
			dest_address = InetAddress.getByName(args[1]);
		} catch (UnknownHostException e1) {
			e1.printStackTrace();
		}
		// What's the size of one packet, including header and data?
		packet_size = common.SerializedPacketSize();
		
		// Initialize trace file
		program_start_time = System.nanoTime();
		try {
			trace_file = new FileOutputStream("trace");
			cwnd_file = new FileOutputStream("cwnd");
		} catch (FileNotFoundException e2) {
			e2.printStackTrace();
		}
		
		/*
		 * Establish UDP socket
		 */
		try {
			senderSocket = new DatagramSocket(18808);
		} catch (SocketException e) {
			System.out.println("Error. Could not create UDP connection");
			System.exit(1);
		}

			
		/*
		 * Start state machine, send first packet
		 */
		startStateMachine();
		
		/*
		 * State machine loop
		 */
		boolean loopForever = true;
		int i =0;
		while(loopForever){
			// must receive an ACK by end of timer
			long current_time = System.currentTimeMillis();
			event = event_t.noEvent;
			try {
				if (end_time - current_time > 0){
					senderSocket.setSoTimeout((int)(end_time - current_time));
				}else{
					event = event_t.timeout;
					end_time += TimeoutInterval;
				}
					
			} catch (SocketException e1) {
				System.out.println("Error. Could not set socket timeout");
				e1.printStackTrace();
			}
			
			byte[] ack_data = new byte[packet_size];
			DatagramPacket ack_packet = new DatagramPacket(ack_data, packet_size);
			
			try {
				if (event != event_t.timeout){
					senderSocket.receive(ack_packet);
					
					TCPSegment receivedACK = common.bytesToObject(ack_data);
					//System.out.println(i++ + " ACK Received: " + receivedACK.ack_no);
					
					event = updateACKs(receivedACK);
				}
			}catch (SocketTimeoutException e){
				// Timeout!
				event = event_t.timeout;
				end_time += TimeoutInterval;
			}catch (IOException e) {
				e.printStackTrace();
			}finally{
				if (lastByteACKed >= filedata.length){
					// All file data sent? Exit loop
					break;
				}else
					nextState();
			}
		}
		/*
		 * Keep sending FINs until FINACK received
		 */		
		byte[] temp_data = new byte[packet_size];
		DatagramPacket temp = new DatagramPacket(temp_data, packet_size);
		loopForever = true;
		while(loopForever){
			transmitSeg(FIN, null, 0);
			try {
				senderSocket.setSoTimeout((int)TimeoutInterval);
			} catch (SocketException e) {
				System.out.println("Error. Could not set socket timeout");
				e.printStackTrace();
			}
			
			try {
				senderSocket.receive(temp);
				if (common.bytesToObject(temp_data).flag == FINACK)
					loopForever = false;				
			}catch(SocketTimeoutException e){
				continue;
			}catch (IOException e) {
				e.printStackTrace();
			}
		}

		/*
		 * For every FIN from server, send FINACK
		 */
		try {
			senderSocket.setSoTimeout(10000);
		} catch (SocketException e) {
			System.out.println("Error. Could not set socket timeout");
			e.printStackTrace();
		}
		loopForever = true;
		while (loopForever){
			try {
				senderSocket.receive(temp);
				if (common.bytesToObject(temp_data).flag==FIN){
					// Send FINACK to server
					transmitSeg(FINACK, null, 0);
					loopForever = false;
				}
			}catch(SocketTimeoutException e){
			}catch (IOException e) {
				e.printStackTrace();
			}
		}		
		senderSocket.close();		
		System.out.println("\nFile successfully sent.");
		
		// Close trace file
		try {
			trace_file.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
