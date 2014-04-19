package drfoliberg.worker;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import drfoliberg.common.task.Task;

public class Work extends Thread {

	private InetAddress masterIp;
	private Task task;
	private Worker callback;

	public Work(Worker w, Task t, InetAddress masterIp) {
		this.masterIp = masterIp;
		task = t;
		callback = w;
	}

	@Override
	public void run() {
		try {
			// ffmpeg -i ~/encoding/input.mkv -c:v libx264 -b:v 1000k -strict -2
			// ~/encoding/output.mkv
			System.out.println("WORKER WORK THREAD: Executing a task!");
			File f = new File("/home/justin/encoding/output.mkv");
			if (f.exists()) {
				System.err.printf("File %s exists ! deleting file...\n",
						f.getAbsoluteFile());
				if (!f.delete()) {
					System.err.printf("Could not delete file %s ",
							f.getAbsoluteFile());
					throw new IOException();
				} else {
					System.err.println("Success deleting file");
				}
			}
			// TODO Get parameters from the task and bind parameters to process
			Process process = Runtime
					.getRuntime()
					.exec("ffmpeg -i "
							+ "/home/justin/encoding/input.mkv -c:v libx264 -b:v 1000k "
							+ "-strict -2 /home/justin/encoding/output.mkv");

			// Read from ffmpeg stderr to get progress
			InputStream stderr = process.getErrorStream();
			Scanner s = new Scanner(stderr);
			String line = "";

			Pattern currentFramePattern = Pattern.compile("frame=\\s*([0-9]*)");
			Pattern fpsPattern = Pattern.compile("fps=\\s*([0-9]*)");
			while (s.hasNext()) {
				line = s.nextLine();
				Matcher m = currentFramePattern.matcher(line);
				if (m.find()) {
					System.err.printf("frame: %s \n", m.group(1));
				}
				m = fpsPattern.matcher(line);
				if (m.find()) {
					System.err.printf("fps: %s \n", m.group(1));
				}
			}
			System.out.println("Scanner closed");
			s.close();

			callback.taskDone(task, masterIp);
		} catch (IOException e) {
			e.printStackTrace();
			System.out.println("Work was interrupted!");
		}
	}

}
