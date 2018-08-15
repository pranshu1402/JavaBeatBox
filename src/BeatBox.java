import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Vector;

import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

public class BeatBox {
	JFrame frame;
	JPanel mainPanel;
	JList<String> incomingList;
	JTextField userMessage;
	ArrayList<JCheckBox> checkBoxList;
	int nextNum;
	Vector<String> listVector = new Vector<>();
	String userName;
	ObjectOutputStream oos;
	ObjectInputStream ois;
	HashMap<String, boolean[]> otherSeqsMap = new HashMap<>();
	
	Sequencer player;
	Sequence sequence;
	Track track;
	File file;
	boolean playing;

	String instrumentNames[] = { "Bass Drum","Closed Hi-Hat",
			"Open Hi-Hat", "Acoustic Snare", "Crash Cymbal", "Hand Clap",
			"High Tom", "Hi Bongo", "Maracas", "Whistle", "Low Conga",
			"Cowbell", "Vibraslap", "Low-mid Tom", "High Agogo",
			"Open Hi Conga"
	};
	int instruments[] = {35,42,46,38,49,39,50,60,70,72,64,56,58,47,67,63};

	public static void main(String args[]){
		if(args.length!=0)	
			new BeatBox().startUp(args[0]);
		else{
			Date date = new Date();
			int time = (int) date.getTime()%10;
			new BeatBox().startUp("Anonymous" + time);
		}
	}

	public void startUp(String name){
		userName = name;
		try{
			@SuppressWarnings("resource")
			Socket sock = new Socket("127.0.0.1",4242);
			oos = new ObjectOutputStream(sock.getOutputStream());
			ois = new ObjectInputStream(sock.getInputStream());
			Thread remote = new Thread(new RemoteReader());
			remote.start();
		}catch(Exception e){
			System.out.println("Sorry! Couldn't connect- You'll have to play alone");
		}
		setUpMidi();
		buildGUI();
	}
	
	public void buildGUI(){
		try {
		    for (LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
		        if ("Nimbus".equals(info.getName())) {
		            UIManager.setLookAndFeel(info.getClassName());
		            break;
		        }
		    }
		} catch (Exception e) {
		   e.printStackTrace();
		}
		file = null;
		
		frame = new JFrame("BeatBox");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
//		frame.addWindowListener(new WindowAdapter() {
//			@Override
//			public void windowClosing(WindowEvent e) {
//				if(file!=null){
//					// add save dialogue opening
//				}
//			}
//		});
		BorderLayout layout = new BorderLayout();
		JPanel background = new JPanel(layout);
		background.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
		
		Box buttonBox = new Box(BoxLayout.Y_AXIS);

		JButton start = new JButton("Start");
		start.addActionListener(new MyStartListener());
		buttonBox.add(start);

		JButton stop = new JButton("Stop");
		stop.addActionListener(new MyStopListener());
		buttonBox.add(stop);

		JButton tempoUp = new JButton("Tempo Up");
		tempoUp.addActionListener(new MyTempoUpListener());
		buttonBox.add(tempoUp);

		JButton tempoDown = new JButton("Tempo Down");
		tempoDown.addActionListener(new MyTempoDownListener());
		buttonBox.add(tempoDown);
		
		JButton sendIt = new JButton("Send It");
		sendIt.addActionListener(new MySendListener());
		buttonBox.add(sendIt);
		
		userMessage = new JTextField();
		buttonBox.add(userMessage);
		
		incomingList = new JList<>();
		incomingList.addListSelectionListener(new MyListSelectionListener());
		incomingList.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
		JScrollPane theList = new JScrollPane(incomingList);
		buttonBox.add(theList);
		incomingList.setListData(listVector);
		
		Box nameBox = new Box(BoxLayout.Y_AXIS);
		for(int i=0;i<instrumentNames.length;i++){
			JLabel label = new JLabel(instrumentNames[i]);
			label.setBorder(BorderFactory.createEmptyBorder(4, 2, 4, 2));
			nameBox.add(label);
		}

		JMenuBar menuBar = new JMenuBar();
		JMenu fileMenu = new JMenu("File");
		JMenuItem newFile = new JMenuItem("New");
		newFile.addActionListener(new NewFileListener());
		JMenuItem saveFile = new JMenuItem("Save");
		saveFile.addActionListener(new SavePatternListener());
		JMenuItem restoreFile = new JMenuItem("Open");
		restoreFile.addActionListener(new RestorePatternListener());
		fileMenu.add(newFile);
		fileMenu.add(restoreFile);
		fileMenu.add(saveFile);
		menuBar.add(fileMenu);
		frame.setJMenuBar(menuBar);

		background.add(BorderLayout.WEST,nameBox);
		background.add(BorderLayout.EAST,buttonBox);

		frame.getContentPane().add(background);

		GridLayout grid = new GridLayout(16, 16);
		grid.setVgap(1);
		grid.setHgap(2);
		mainPanel = new JPanel(grid);
		background.add(BorderLayout.CENTER,mainPanel);
		checkBoxList = new ArrayList<>();
		for(int i=0;i < 256; i++){
			JCheckBox c = new JCheckBox();
			c.setSelected(false);
			checkBoxList.add(c);
			mainPanel.add(c);
		}

		frame.setBounds(50,50,300,300);
		frame.pack();
		frame.setVisible(true);
	}


	public void setUpMidi(){
		try{
			player = MidiSystem.getSequencer();
			player.open();
			sequence = new Sequence(Sequence.PPQ,4);
			track = sequence.createTrack();
			player.setTempoInBPM(120);

		}catch(MidiUnavailableException ex){
			ex.printStackTrace();
		}catch(Exception e){
			e.printStackTrace();
		}
	}

	class MyStartListener implements ActionListener{
		public void actionPerformed(ActionEvent ae){
			buildTrackAndStart();
			playing = true;
		}
	}

	class MyStopListener implements ActionListener{
		public void actionPerformed(ActionEvent ae){
			player.stop();
			playing = false;
		}
	}

	class MyTempoUpListener implements ActionListener{
		public void actionPerformed(ActionEvent ae){
			float tempoFactor = player.getTempoFactor();
			player.setTempoFactor((float)(tempoFactor*1.03));
		}
	}

	class MyTempoDownListener implements ActionListener{
		public void actionPerformed(ActionEvent ae){
			float tempoFactor = player.getTempoFactor();
			player.setTempoFactor((float)(tempoFactor*0.97));			
		}
	}
	
	class MySendListener implements ActionListener{
		public void actionPerformed(ActionEvent ae){
			boolean[] checkState = new boolean[256];
			for(int i=0;i<256;i++){
				checkState[i] = checkBoxList.get(i).isSelected();
			}
			try{
				oos.writeObject(userName+ nextNum++ + ": " + userMessage.getText());
				oos.writeObject(checkState);
			}catch(Exception e){
				System.out.println("Sorry Dude. Couldn't Send it to the server");
			}
			userMessage.setText("");
		}
	}
	
	class MyListSelectionListener implements ListSelectionListener{
		public void valueChanged(ListSelectionEvent le){
			if(!le.getValueIsAdjusting()){
				String selected = (String) incomingList.getSelectedValue();
				if(selected!=null){
					boolean[] selectedState = (boolean[]) otherSeqsMap.get(selected);
					changeSequence(selectedState);
					player.stop();
					buildTrackAndStart();
				}
			}
		}
	}
	
//	class MyPlayMineListener implements ActionListener{
//		public void actionPerformed(ActionEvent ae){
//			if(mySequence!=null)
//				sequence = mySequence;
//		}
//	}
	
	public void changeSequence(boolean[] checkBoxes){
		for(int i=0;i<256;i++){
			checkBoxList.get(i).setSelected(checkBoxes[i]);
		}
	}
	
	class RemoteReader implements Runnable{
		boolean checkBoxState[] = null;
		String nameToShow = null;
		Object obj = null;
		
		public void run(){
			try{
				while((obj = ois.readObject())!=null){
					System.out.println("Got an obj from Server");
					System.out.println(obj.getClass());
					nameToShow = (String) obj;
					checkBoxState = (boolean []) ois.readObject();
					otherSeqsMap.put(nameToShow, checkBoxState);
					listVector.add(nameToShow);
					incomingList.setListData(listVector);
				}
			}catch(Exception e){
				e.printStackTrace();
			}
		}
	}
	
	class NewFileListener implements ActionListener{
		public void actionPerformed(ActionEvent ae){
			for(int i=0;i<256;i++){
				checkBoxList.get(i).setSelected(false);
			}
			file = null;
		}
	}
	
	class SavePatternListener implements ActionListener{
		boolean checkBoxState[];
		public void actionPerformed(ActionEvent ae){
			checkBoxState = new boolean[256];
			for(int i=0;i<256;i++){
				JCheckBox check = (JCheckBox)checkBoxList.get(i) ;
				if(check.isSelected()){
					checkBoxState[i] = true;
				}
			}
			
			if(file==null){			
				JFileChooser fileSave = new JFileChooser();
				fileSave.showSaveDialog(frame);
				file = fileSave.getSelectedFile();
			}
			saveFile();
		}
		
		public void saveFile(){
			try{
				FileOutputStream fo = new FileOutputStream(file);
				ObjectOutputStream writer = new ObjectOutputStream(fo);
				writer.writeObject(checkBoxState);
				writer.close();
			}catch(Exception e){
				e.printStackTrace();
			}
		}
	}

	class RestorePatternListener implements ActionListener{
		boolean checkBoxes[] = null;
		
		public void actionPerformed(ActionEvent ae){	
			JFileChooser open = new JFileChooser();
			// before opening add save dialogue box for saving any current configs..
			open.showOpenDialog(frame);
			file = open.getSelectedFile();
			openFile();
			
			for(int i=0;i<256;i++){
				JCheckBox checkBox = (JCheckBox) checkBoxList.get(i);
				if(checkBoxes[i])
					checkBox.setSelected(true);
				else
					checkBox.setSelected(false);
			}
			
			player.stop();
			buildTrackAndStart();
		}
		
		public void openFile(){
			try{
				FileInputStream fis = new FileInputStream(file);
				ObjectInputStream ois = new ObjectInputStream(fis);
				checkBoxes = (boolean []) ois.readObject();	
				ois.close();
			}catch(Exception e){
				e.printStackTrace();
			}
		}
	}
	
	public void buildTrackAndStart() {
		int trackList[] = null;

		sequence.deleteTrack(track);
		track = sequence.createTrack();

		for(int i=0;i < 16 ; i++){
			trackList = new int[16];

			int key = instruments[i];

			for(int j=0; j < 16; j++ ){
				JCheckBox jc = (JCheckBox)checkBoxList.get(j+(16*i));
				if(jc.isSelected()){
					trackList[j] = key;
				}
			}

			makeTracks(trackList);
			track.add(makeEvent(176,1,127,0,16));
		}

		track.add(makeEvent(192,9,1,0,15));
		try{

			player.setSequence(sequence);
			player.setLoopCount(Sequencer.LOOP_CONTINUOUSLY);
			player.start();
			player.setTempoInBPM(120);
		}catch(Exception e){
			e.printStackTrace();
		}
	}

	public void makeTracks(int trackList[]){
		for(int i=0;i < trackList.length;i++){
			int key = trackList[i];

			if(key!=0){
				track.add(makeEvent(144,9,key,100,i));
				track.add(makeEvent(128,9,key,100,i+1));
			}
		}
	}

	public MidiEvent makeEvent(int comd, int chan, int one, int two, int tick){
		MidiEvent event = null;
		try{
			ShortMessage message = new ShortMessage();
			message.setMessage(comd, chan, one,two);
			event = new MidiEvent(message, tick);
		}catch(Exception e){
			e.printStackTrace();
		}
		return event;
	}

}
