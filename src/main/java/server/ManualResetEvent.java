package server;

/**
 * Created by un on 15. 8. 20..
 */
public class ManualResetEvent {

	private final Object monitor = new Object();
	private volatile boolean open = false;

	public ManualResetEvent(boolean open) {
		this.open = open;
	}

	public void waitOne() throws InterruptedException {
		synchronized (monitor) {
			while (open==false) {
				monitor.wait();
			}
		}
	}

	public boolean waitOne(long milliseconds) throws InterruptedException {
		synchronized (monitor) {
			if (open)
				return true;
			monitor.wait(milliseconds);
			return open;
		}
	}

	public void set() {//open start
		synchronized (monitor) {
			open = true;
			monitor.notifyAll();
		}
	}

	public void reset() {//close stop
		open = false;
	}
}
