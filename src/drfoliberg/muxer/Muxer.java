package drfoliberg.muxer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

import drfoliberg.common.Service;
import drfoliberg.common.job.Job;
import drfoliberg.common.task.Task;

public class Muxer extends Service {

	private ArrayList<MuxerListener> listeners;
	private Job job;
	private String absoluteJobFolder;

	public Muxer(MuxerListener listener, Job job, String absoluteJobFolder) {
		listeners = new ArrayList<>();
		listeners.add(listener);
		this.job = job;
		this.absoluteJobFolder = absoluteJobFolder;
	}

	@Override
	public void run() {
		boolean success = false;
		File muxedFile = new File(absoluteJobFolder, job.getOutputFileName());
		ArrayList<String> args = new ArrayList<>();
		Collections.addAll(args, new String[] { "mkvmerge", "-o", muxedFile.getAbsolutePath() });
		for (int i = 0; i < job.getTasks().size(); i++) {
			Task t = job.getTasks().get(i);
			File path = new File(t.getOutputFile());
			path = new File(job.getPartsFolderName(), path.getName());
			args.add(path.getPath());
			if (i != job.getTasks().size() - 1) {
				args.add("+");
			}
		}
		ProcessBuilder pb = new ProcessBuilder(args);
		System.out.println("MUXER: " + args.toString());
		pb.directory(new File(absoluteJobFolder));
		System.out.println("MUXER: trying to mux in path " + absoluteJobFolder);
		fireMuxingStarting();

		try {
			Process m = pb.start();
			m.waitFor();
			success = m.exitValue() == 0 ? true : false;
		} catch (IOException e) {
			fireMuxingFailed(e);
		} catch (InterruptedException e) {
			fireMuxingFailed(e);
		} finally {
			if (success) {
				fireMuxingCompleted();
			} else {
				fireMuxingFailed();
			}
		}
	}

	private void fireMuxingStarting() {
		for (MuxerListener l : this.listeners) {
			l.muxingStarting(job);
		}
	}

	private void fireMuxingCompleted() {
		for (MuxerListener l : this.listeners) {
			l.muxingCompleted(job);
		}
	}

	private void fireMuxingFailed(Exception e) {
		for (MuxerListener l : this.listeners) {
			l.muxingFailed(job, e);
		}
	}

	private void fireMuxingFailed() {
		Exception e = new Exception("Unknown error");
		for (MuxerListener l : this.listeners) {
			l.muxingFailed(job, e);
		}
	}

}
