package main.java.drfoliberg.worker;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

import main.java.drfoliberg.common.network.ClusterProtocol;
import main.java.drfoliberg.common.network.messages.cluster.AuthMessage;
import main.java.drfoliberg.common.network.messages.cluster.ConnectMessage;
import main.java.drfoliberg.common.network.messages.cluster.Message;
import main.java.drfoliberg.common.status.NodeState;
@Deprecated
public class ContactMaster implements Runnable {

	Worker worker;

	public ContactMaster(Worker worker) {
		this.worker = worker;
	}

	public boolean contactMaster() {
		boolean success = false;
		System.out.println("WORKER CONTACT: trying to contact master server at "
				+ worker.getMasterIpAddress().toString());
		Socket socket = null;
		try {
			socket = new Socket(worker.getMasterIpAddress(), worker.getMasterPort());

			ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
			out.flush();
			ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
			//Node n = new Node(worker.getWorkerIp(), worker.getListenPort(), worker.getWorkerName());
			//out.writeObject(new Message(n));
			ConnectMessage m = new ConnectMessage(worker.config.getUniqueID(), worker.config.getListenPort(),worker.config.getName(),NodeState.FREE);
			out.writeObject(m);
			out.flush();
			Object o = in.readObject();
			if (o instanceof AuthMessage) {
				AuthMessage response = (AuthMessage) o;
				if (response.getCode() == ClusterProtocol.BYE) {
					socket.close();
				} else if (response.getCode() == ClusterProtocol.CONNECT_ME) {
					success = true;
					// get unid from response
					this.worker.setUnid(response.getUnid());
					out.writeObject(new Message(ClusterProtocol.BYE));
					out.flush();
					socket.close();
				}
			} else {
				System.out.println("WORKER CONTACT: Could not read what master sent !");
			}
		} catch (IOException e) {
		} catch (ClassNotFoundException e) {
		}
		return success;
	}

	public void run() {
		boolean success = false;
		while (!success) {
			success = contactMaster();
			if (success) {
				worker.updateStatus(NodeState.FREE);
				System.out.println("WORKER CONTACT: success contacting master!");
			} else {
				System.out.println("WORKER CONTACT: could not reach the master server. Trying again in 5 seconds.");
				try {
					// TODO check for better way to sleep / handle interrupt
					Thread.currentThread();
					Thread.sleep(5000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}
}