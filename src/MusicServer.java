import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Iterator;

public class MusicServer {
	ArrayList<ObjectOutputStream> clientOutputStream;
	
	public static void main(String args[]){
		new MusicServer().go();
	}
	
	class ClientHandler implements Runnable{
		ObjectInputStream ois;
		Socket clientSocket;
		
		public ClientHandler(Socket socket){
			try{
				clientSocket = socket;
				ois = new ObjectInputStream(clientSocket.getInputStream());
			}catch(Exception e){
				e.printStackTrace();
			}
		}
		
		public void run(){
			Object o2 = null;
			Object o1 = null;
			try{
				while((o1 = ois.readObject())!=null){
					o2 = ois.readObject();
					System.out.println("read two objects");
					tellEveryone(o1,o2);
				}
			}catch(Exception e){e.printStackTrace();}
		}
	}
	
	public void go(){
		clientOutputStream = new ArrayList<>();
		
		try{
			@SuppressWarnings("resource")
			ServerSocket serverSock = new ServerSocket(4242);
			while(true){
				Socket client = serverSock.accept();
				ObjectOutputStream oos = new ObjectOutputStream(client.getOutputStream());
				clientOutputStream.add(oos);
				
				Thread t = new Thread(new ClientHandler(client));
				t.start();
				System.out.println("Got a new connection");
			}
		}catch(Exception e){
			e.printStackTrace();
		}
	}
	
	public void tellEveryone(Object o1, Object o2){
		Iterator<ObjectOutputStream> it = clientOutputStream.iterator();
		while(it.hasNext()){
			try{
				ObjectOutputStream oos = it.next();
				oos.writeObject(o1);
				oos.writeObject(o2);
			}catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}
