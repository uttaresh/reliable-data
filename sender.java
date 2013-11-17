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
	
	static DatagramSocket senderSocket = null;

	static byte[] filedata = null;
	
	/**
	 * Function to read file into byte[] array
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
	
	static enum event_t{
		newACK,
		dupACK,
		timeout,
		noEvent
	}
	
	static int packet_size = 0;
	
	static int MSS=100;
	static int receiverBuffer = 2500;
	
	/**
	 *  Function to send one TCPSegment
	 */
	static void transmitSeg(TCPSegment.flag_t flag, byte[] data, int offset){
		// Create packet from function parameters
		DatagramPacket packet = null;
		TCPSegment segment = new TCPSegment();
		segment.flag = flag;
		
		if(flag == TCPSegment.flag_t.DATA){
			segment.data = data;
			segment.seq_no = offset;
		}
		
		byte[] packet_data = common.objectToBytes(segment);
		packet = new DatagramPacket(packet_data,packet_data.length, dest_address, dest_port);
		// Send packet over UDP
		try {
			senderSocket.send(packet);
		} catch (IOException e) {
			// Could not send packet over UDP
			e.printStackTrace();
			System.exit(1);
		}
	}
	
	static void startStateMachine(){
		EstimatedRTT = 1000;	// Estimated RTT is 1000ms by default
		DevRTT = 0;
		TimeoutInterval = (long)(EstimatedRTT + 4*DevRTT);
		
		dupACKcount = 0;
		cwnd = MSS;
		
		window_start = 0;
		window_end = (int)cwnd-1;		
		
		ssthresh = 1000;
		lastByteSent = -1;
		lastByteACKed = 0;
		currentState = senderStates_t.SlowStart;
		
		// Send first segment and set timer
		transmitSeg(TCPSegment.flag_t.DATA, Arrays.copyOfRange(filedata, 0, 99) ,0);
		lastByteSent = 99;
		start_time = System.currentTimeMillis();
		end_time = start_time + TimeoutInterval;
	}
	
	static event_t updateACKs(DatagramPacket packet){		
		TCPSegment receivedACK = common.bytesToObject(packet.getData());
		
		if (receivedACK.ack_no == window_start){
			return event_t.dupACK;
		}else{
			// Shift window
			lastByteACKed = receivedACK.ack_no - 1;
			window_start = receivedACK.ack_no;
			
			// Update timeout interval
			double SampleRTT = System.currentTimeMillis()-start_time;
			EstimatedRTT = 0.875*EstimatedRTT + 0.125*(SampleRTT);
			DevRTT = 0.75*DevRTT + 0.25*Math.abs(SampleRTT - EstimatedRTT);
			TimeoutInterval = (long)(EstimatedRTT + 4*DevRTT);
			return event_t.newACK;
		}
		
	}
	
	static void transmitNew(){		
		window_start = lastByteACKed+1;	
		window_end = Math.min(lastByteACKed+(int)cwnd, filedata.length-1);
		// Send all packets in window that have not been sent
		for (int i=lastByteSent+1;i<=window_end;i+=100){
			if (lastByteSent-lastByteACKed <= cwnd){
				transmitSeg(TCPSegment.flag_t.DATA, Arrays.copyOfRange(filedata, i, i+99), i);
				lastByteSent = i+99;
			}					
		}	
		// Start timer if not already running
		if (end_time<=System.currentTimeMillis){
			start_time = System.currentTimeMillis();
			end_time = start_time + TimeoutInterval;
		}
			
	}
	
	static void retransmit(){
		transmitSeg(TCPSegment.flag_t.DATA, Arrays.copyOfRange(filedata, lastByteACKed+1, lastByteACKed+100), lastByteACKed+1);
		start_time = System.currentTimeMillis();
		end_time = start_time + TimeoutInterval;
	}

	
	/**
	 * Call this function after processing a packet. Will transition into
	 * next state if necessary, making all state variable changes required.
	 * 
	 * Based on Figure 3.52 from Computer Networking, A Top Down Approach (6E)
	 */
	static void nextState(event_t event){
		if (currentState == senderStates_t.SlowStart){
			if (cwnd >= ssthresh){
				currentState = senderStates_t.CongestionAvoidance;
			}else if (dupACKcount == 3){
				ssthresh=cwnd/2;
				cwnd=Math.min(ssthresh+3*MSS, receiverBuffer);
				currentState = senderStates_t.FastRecovery;
				retransmit();
			}else if (event == event_t.dupACK)
				dupACKcount++;
			else if (event == event_t.newACK){
				cwnd=Math.min(cwnd+MSS, receiverBuffer);
				dupACKcount=0;
				transmitNew();
			}else if (event == event_t.timeout){
				ssthresh=cwnd/2;
				cwnd=MSS;
				dupACKcount=0;
				retransmit();
			}
		}else if (currentState == senderStates_t.CongestionAvoidance){
			if (event == event_t.timeout){
				ssthresh=cwnd/2;
				cwnd=MSS;
				dupACKcount=0;
				currentState = senderStates_t.SlowStart;
				retransmit();
			}else if (dupACKcount==3){
				ssthresh=cwnd/2;
				cwnd=Math.min(ssthresh+3*MSS, receiverBuffer);
				currentState = senderStates_t.FastRecovery;
				retransmit();
			}else if (event == event_t.newACK){
				cwnd=Math.min(cwnd+MSS*(MSS/cwnd), receiverBuffer);
				dupACKcount=0;
				transmitNew();
			}else if (event == event_t.dupACK) dupACKcount++;
		}else if (currentState == senderStates_t.FastRecovery){
			if (event == event_t.newACK){
				cwnd=ssthresh;
				dupACKcount=0;
				currentState = senderStates_t.CongestionAvoidance;
			}else if (dupACKcount == 3){
				ssthresh=cwnd/2;
				cwnd=Math.min(ssthresh+3*MSS, receiverBuffer);
				currentState = senderStates_t.SlowStart;
				retransmit();
			}else if(event == event_t.dupACK){
				cwnd=Math.min(cwnd+MSS, receiverBuffer);
				transmitNew();
			}
		}		
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
		while(loopForever){
			// TODO set timeout for all ACKs in window
			
			
			byte[] ack_data = new byte[packet_size];
			DatagramPacket ack_packet = new DatagramPacket(ack_data, packet_size);
			
			event_t this_event;
			try {
				senderSocket.receive(ack_packet);				
				this_event = UpdateACKs(ack_packet);
				
			}catch (IOException e) {
				e.printStackTrace();
			}
			
			if (all_received)
				loopForever = false;
			
			nextState(this_event);
			
		}
		
		
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
		senderSocket.close();
		
	}

}
